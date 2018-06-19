/*
 * Copyright 2018 Abhishek Kumar. All Rights Reserved.
 */
package com.github.ak.memcache.cache;

import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * An in-memory cache implementation with LRU eviction policy.
 * The retrieval and storage are thread safe. The implementation
 * makes best effort to keep the cache size close to capacity.
 * <p/>
 * To maintain recency, this implementation uses a {@link LruPolicy}
 * that provides {@link LruPolicy#record(Object)} method to record
 * recently retrieved or stored key. {@link LruPolicy#record(Object)}
 * tries to add the {@code key} to {@link LruPolicy#buffer} and returns
 * immediately whether or not it was successful. Add to {@link LruPolicy#buffer}
 * may fail if buffer is full. This approximation is intentional to keep
 * optimal performance.
 * <p/>
 * Cache {@code key}/{@code value} are stored in a {@link ConcurrentHashMap}.
 * The implementation uses a basic lock striping mechanism to reduce contention
 * and increase concurrency. Its based on section 11.4.3 from Brian Goetz's book
 * "Java Concurrency In Practice". As a side note, an alternative to implementing
 * {@link StrippedLock} was to use {@link ConcurrentHashMap}'s internal lock striping
 * which is lot more efficient and optimized. {@link StrippedLock} was implemented
 * primarily as experimental exercise.
 * <p/>
 * Cache access events are queued up in {@link LruPolicy#buffer} and periodically read
 * by {@link LruPolicy#bufferReader}. The reader quickly drains all entries into a
 * {@link LruPolicy#stagingBuffer}. Draining entire queue is more efficient than
 * {@link BlockingQueue#take()} an entry at a time since lock is acquired just once.
 * Entries from {@link LruPolicy#stagingBuffer} are then updated into {@link LruPolicy#index}.
 * This index is a {@link LinkedHashMap} that keeps entries ordered by recency.
 * <p/>
 * When cache reaches its capacity, {@link LruPolicy} starts collecting eldest entries
 * into {@link LruPolicy#removals}. This set is accessed by {@link LruPolicy#evictor} for
 * eviction.
 *
 * @see "Java Concurrency In Practice: Brian Goetz, Section 11.4.3"
 * @see <a href="http://highscalability.com/blog/2016/1/25/design-of-a-modern-cache.html">
 *     Design of a modern Cache</a>
 *
 * @param <K> the type of {@code keys} this cache supports
 * @param <V> the type of {@code values} this cache supports
 *
 */
@ThreadSafe
public class LruCacheWithBatchEviction<K,V> implements Cache<K,V>, AutoCloseable{

    static final Logger logger = LoggerFactory.getLogger(LruCacheWithBatchEviction.class);

    private final int capacity;
    private final Map<K,V> data;
    private final LruPolicy policy;
    private final StrippedLock<K> lock;

    public LruCacheWithBatchEviction(@Nonnegative int capacity){
        Preconditions.checkArgument(capacity>0, "Capacity must be a positive integer");

        this.capacity = capacity;
        data = new ConcurrentHashMap<>(16,0.75f,1);
        policy = new LruPolicy(this);
        lock = new StrippedLock<>();
    }

    /**
     * Returns the associated value for specified (@code key}. Returns
     * {@code null} if cache does not contain key.
     * <p/>
     * If {@code key} is found in cache, its recorded in {@link LruPolicy}.
     *
     * @param key key whose associated value is to be returned
     * @return   the value to which the specified key is mapped, or
     *          {@code null} if this cache does not contain the key
     * @throws NullPointerException if specified key is {@code null}
     */
    @Override
    public @Nullable V get(@Nonnull K key) {
        Preconditions.checkNotNull(key,"key must not be null");
        logger.debug("Retrieving {}", key);

        Lock readLock = lock.readLock(key);
        readLock.lock();
        V value = null;
        try{
            if (data.containsKey(key)){
                value = data.get(key);
                policy.record(key);
            }
        }finally{
            readLock.unlock();
        }

        return value;
    }

    /**
     * Stores the {@code key} and associated {@code value} in cache.
     * If cache already has the {@code key}, its {@code value} is updated.
     * <p/>
     * The access is recored in {@link LruPolicy}.
     *
     * @param key the key whose value is to be stored
     * @param value the associated value for the provided key
     * @throws NullPointerException if the {@code key} is {@code null}
     */
    @Override
    public @Nullable void put(@Nonnull K key, V value) {
        Preconditions.checkNotNull(key, "key must not be null");
        logger.debug("Storing {}", key);

        Lock writeLock = lock.writeLock(key);
        writeLock.lock();

        try{
            data.put(key, value);
            policy.record(key);
        }finally{
            writeLock.unlock();
        }

    }

    /**
     * Returns the current number of entries in cache.
     *
     * @return the number of cache entries
     */
    @Override
    public int size() {
        return data.size();
    }

    @Override
    public void close() throws Exception {
        policy.close();
        logger.info("Cache stopped");
    }

    /**
     * Removes the entry from cache
     *
     * @param key the key to be removed
     * @throws NullPointerException if the {@code key} is {@code null}
     */
    private void delete(@Nonnull K key){
        Preconditions.checkNotNull(key, "key must not be null");
        logger.debug("Removing {}", key);

        if (!data.containsKey(key)){
            return;
        }

        Lock writeLock = lock.writeLock(key);
        writeLock.lock();

        try{
            data.remove(key);
        }finally{
            writeLock.unlock();
        }

    }

    static class LruPolicy<K,V>{

        /** How often eviction thread runs (in ms) */
        private static final int EVICTION_SCHEDULE = 10;
        /** Initial wait before eviction thread starts (in ms) */
        private static final int EVICTION_INITIAL_WAIT = 10;
        /** How often buffer is read (in ms) */
        private static final int BUFFER_READ_SCHEDULE = 10;
        /** Intial wait before buffer reading thread starts (in ms) */
        private static final int BUFFER_READ_INITIAL_WAIT = 1;
        /** Number of entries to batch up before eviction */
        private static final int BATCH_SIZE = 500;

        private final ScheduledExecutorService evictor = Executors.newSingleThreadScheduledExecutor();
        private final ScheduledExecutorService bufferReader = Executors.newSingleThreadScheduledExecutor();

        private final Lock lock;
        private final Map<K,V> index;
        private final Set<K> removals;
        private final List<K> stagingBuffer;
        private final BlockingQueue<K> buffer;
        private final LruCacheWithBatchEviction cache;

        public LruPolicy(LruCacheWithBatchEviction cache){
            this.cache = cache;
            lock = new ReentrantLock();
            removals = new HashSet<>();
            stagingBuffer = new ArrayList<>();
            buffer = new LinkedBlockingQueue<>();

            index = new LinkedHashMap<K,V>(){
                @Override
                protected boolean removeEldestEntry(Map.Entry<K,V> eldest) {
                    if (index.size() > cache.capacity){
                        lock.lock();
                        try {
                            removals.add(eldest.getKey());
                        }finally {
                            lock.unlock();
                        }
                        return true;
                    }
                    return false;
                }
            };

            evictor.scheduleAtFixedRate(
                    cleanup(),
                    EVICTION_INITIAL_WAIT,
                    EVICTION_SCHEDULE,
                    TimeUnit.MILLISECONDS);
            bufferReader.scheduleAtFixedRate(
                    readBuffer(),
                    BUFFER_READ_INITIAL_WAIT,
                    BUFFER_READ_SCHEDULE,
                    TimeUnit.MILLISECONDS);
        }

        /**
         * Inserts the specified element into this {@link LruPolicy#buffer} if it is
         * possible to do so immediately or returns if there's no space.
         *
         * @param key   recently accessed key
         * @throws NullPointerException if the {@code key} is {@code null}
         */
        public void record(@Nonnull K key){
            Preconditions.checkNotNull(key, "key must not be null");
            logger.debug("Recording access for {}", key);

            buffer.offer(key);
        }

        /** Time (in secs) the thread would wait for thread pools to shutdown */
        private static final int AWAIT_TIMEOUT = 60;

        /**
         * Closes all the threads that {@link LruPolicy} runs. The implementation
         * is based on documentation at
         * <a href="https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ExecutorService.html">java docs</a>
         */
        protected void close(){
            // Disable new tasks from being submitted
            logger.info("Closing policy");
            evictor.shutdown();
            bufferReader.shutdown();
            try {
                // Wait a while for existing tasks to terminate
                if (!bufferReader.awaitTermination(AWAIT_TIMEOUT, TimeUnit.SECONDS)) {
                    bufferReader.shutdownNow(); // Cancel currently executing tasks
                    // Wait a while for tasks to respond to being cancelled
                    if (!bufferReader.awaitTermination(AWAIT_TIMEOUT, TimeUnit.SECONDS))
                        logger.error("Buffer reader thread did not terminate");
                }

                if (!evictor.awaitTermination(AWAIT_TIMEOUT, TimeUnit.SECONDS)) {
                    evictor.shutdownNow();
                    if (!evictor.awaitTermination(AWAIT_TIMEOUT, TimeUnit.SECONDS))
                        logger.error("Eviction thread did not terminate");
                }
            } catch (InterruptedException ie) {
                // (Re-)Cancel if current thread also interrupted
                evictor.shutdownNow();
                bufferReader.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        /**
         * Task that drains the buffer into a staging buffer. It then adds/updates
         * they key into index. The value in index is not used, so {@code null} is
         * inserted.
         *
         * @return a {@link Runnable} task that reads {@link LruPolicy#buffer}
         */
        private Runnable readBuffer() {
            return () -> {
                buffer.drainTo(stagingBuffer);
                for (K key: stagingBuffer){
                    index.put(key, null);
                }
                stagingBuffer.clear();
            };
        }

        /**
         * Task that evicts entries from Cache when number of entries in
         * {@link LruPolicy#removals} reaches {@link LruPolicy#BATCH_SIZE}.
         *
         * @return  a {@link Runnable} task that evicts LRU entries.
         */
        private Runnable cleanup() {
            return () -> {
                if (removals.size() < BATCH_SIZE){
                    return;
                }

                lock.lock();
                try{
                    for (K key: removals){
                        cache.delete(key);
                    }
                    removals.clear();
                }finally {
                    lock.unlock();
                }
            };
        }
    }

    /**
     * Basic implementation of lock striping based on section 11.4.3 from
     * Brian Goetz's book "Java Concurrency In Practice". It maintains a
     * fixed set of "bins" equal to the number of CPUs. An instance of
     * {@link ReadWriteLock} is associated with each bin. Access to a lock
     * is based on {@code key} which is hashed to one of the bins.
     *
     * @see "Java Concurrency In Practice: Brian Goetz, Section 11.4.3"
     * @param <T>   the type of key this lock supports.
     */
    static class StrippedLock<T>{

        /** Number of bins to partition the lock into */
        private final int NUM_BINS = Runtime.getRuntime().availableProcessors();

        private final ReadWriteLock[] locks;

        public StrippedLock(){
            locks = new ReadWriteLock[NUM_BINS];
            for (int i = 0; i < locks.length; i++) {
                locks[i] = new ReentrantReadWriteLock();
            }
        }

        public Lock readLock(T key) {
            ReadWriteLock lock = locks[Math.abs(key.hashCode()) % NUM_BINS];;
            return lock.readLock();
        }

        public Lock writeLock(T key) {
            ReadWriteLock lock = locks[Math.abs(key.hashCode()) % NUM_BINS];;
            return lock.writeLock();

        }
    }

}
