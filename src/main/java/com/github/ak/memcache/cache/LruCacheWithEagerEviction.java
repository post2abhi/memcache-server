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
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * An in-memory cache implementation with LRU eviction policy.
 * The retrieval and storage are thread safe. Cache capacity is
 * guaranteed to not exceed.
 * <p/>
 * To maintain recency, this implementation uses a {@link ConcurrentHashMap}
 * backed by {@link DListNode}, a doubly linked list. Most recently accessed
 * are pointed by Head and least recently accessed by Tail.
 * <p/>
 * When an item is stored/updated/retrieved in/from cache, a new link is added
 * (or existing updated) in linked list and moved to head. This node is then
 * associated with the provided key into the index map.
 * <p/>
 * If the cache has reached its capacity, then add/update is preceded by eviction
 * of eldest entry.
 * <p/>
 * The 2 internal data structures ({@link ConcurrentHashMap} and {@link DListNode})
 * are guarded by a {@link ReentrantLock}. Due to this reason, the concurrency level
 * of {@link ConcurrentHashMap} is set to 1 to avoid its internal lock striping
 * overhead.
 *
 * @param <K> the type of {@code keys} this cache supports
 * @param <V> the type of {@code values} this cache supports
 */
@ThreadSafe
public class LruCacheWithEagerEviction<K,V> implements Cache<K,V> {

    static final Logger logger = LoggerFactory.getLogger(LruCacheWithEagerEviction.class);

    private final int capacity;
    private final ReentrantLock lock;

    @GuardedBy("lock")
    private int numEntries;
    @GuardedBy("lock")
    private DListNode<K,V> head;
    @GuardedBy("lock")
    private DListNode<K,V> tail;
    @GuardedBy("lock")
    private Map<K, DListNode> index;

    public LruCacheWithEagerEviction(@Nonnegative int capacity) {
        this.capacity = capacity;
        index = new ConcurrentHashMap<>(16,0.75f,1);
        lock = new ReentrantLock();
    }

    /**
     * Stores the {@code key} and associated {@code value} in cache.
     * If cache already has the {@code key}, its {@code value} is updated.
     * <p/>
     * If cache has reached its {@code capacity}, the eldest value is evicted.
     *
     * @param key the key whose value is to be stored
     * @param value the associated value for the provided key
     * @throws NullPointerException if the {@code key} is {@code null}
     */
    @Override
    public void put(@Nonnull K key, V value) {
        Preconditions.checkNotNull(key,"key must not be null");
        logger.debug("Storing {}", key);

        DListNode n;
        lock.lock();
        try {
            if (!index.containsKey(key)) {
                if (numEntries == capacity) {
                    evictLru();
                }

                n = new DListNode(key, value);
                index.put(key, n);
                addToTop(n);

                // If tail is not set yet, this node is 1st node in cache.
                // Make it the tail.
                if (tail == null) {
                    tail = n;
                }
                numEntries++;

            } else {
                n = index.get(key);
                n.value = value;
                moveToTop(n);
            }
        } finally {
            lock.unlock();
        }

    }

    /**
     * Returns the associated value for specified (@code key}. Returns
     * {@code null} if cache does not contain key.
     * <p/>
     * If {@code key} is found in cache, its moved to head of the
     * linked list.
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

        lock.lock();

        try {
            DListNode<K,V> n = index.get(key);
            if (n != null){
                moveToTop(n);
                return n.value;
            }else{
                //key not in cache
                return null;
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns the current number of entries in cache.
     *
     * @return the number of cache entries
     */
    @Override
    public int size(){
        return index.size();
    }

    /**
     * Add the provided new cache entry to head of the linkedlist.
     *
     * @param n {@link DListNode} representing new cache entry
     */
    @GuardedBy("lock")
    private void addToTop(DListNode<K,V> n) {
        n.next = head;
        n.prev = null;
        if (head != null) {
            head.prev = n;
        }
        head = n;
    }

    /**
     * Move the provided cache entry to head of the linkedlist.
     *
     * @param n {@link DListNode} representing existing cache entry
     */
    @GuardedBy("lock")
    private void moveToTop(DListNode<K,V> n) {

        //First remove the node
        if (n.equals(head)) {
            return;
        } else if (n.equals(tail)) {
            //fix tail
            tail.prev.next = null;
            tail = tail.prev;
        } else {
            //fix middle
            n.prev.next = n.next;
            n.next.prev = n.prev;
        }

        //then add it to top
        addToTop(n);
    }

    /**
     * Removes the entry from linked list and the index map
     */
    @GuardedBy("lock")
    private void evictLru() {

        logger.debug("Evicting {}", tail.key);

        index.remove(tail.key);

        if (tail.equals(head)) {
            tail = null;
            head = null;
        } else {
            tail = tail.prev;
            tail.next = null;
        }
        numEntries--;
    }

    /**
     * Backing doubly linked list node.
     *
     * @param <K>  the type of key
     * @param <V>  the type of value
     */
    public static class DListNode<K,V> {

        K key;
        V value;

        DListNode prev;
        DListNode next;

        public DListNode(K key, V val) {
            this.key = key;
            this.value = val;
        }

    }
}
