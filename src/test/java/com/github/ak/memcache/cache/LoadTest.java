/*
 * Copyright 2018 Abhishek Kumar. All Rights Reserved.
 */
package com.github.ak.memcache.cache;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Load tests both cache implementations {@link LruCacheWithEagerEviction}
 * and {@link LruCacheWithBatchEviction}.
 * <p/>
 * It readies a number of threads {@code numThreads} that runs {@link LoadTest#executeTask(Cache, int)},
 * all at the same time using a barrier mechanism described in <em>Java Concurrency in Practice</em>
 * book by Brian Goetz.
 * <p/>
 * Each task executes queries against the cache {@code NUM_QUERIES} times - 70% or those
 * being {@code get} call and 30% being {@code set} call.
 * <p/>
 * Each thread creates its own unique key to store data. For retrieval, however, each thread
 * tries to retrieve any random key from the cache irrespective of which thread stored it.
 * <p/>
 * In 70% {@code get} call, preference is given to most recently created entries by that thread
 * to simulate the situation where newer entries are more frequently accessed than older entries.
 * <p/>
 * A basic report is printed on console showing:
 * <ul>
 * <li>Time taken(ms) - How much time it took to execute NUM_QUERIES*numThreads calls to cache</li>
 * <li>Cache missed - How many cache misses we got</li>
 * </ul>
 */
class LoadTest {

    static final Logger logger = LoggerFactory.getLogger(LoadTest.class);

    /** Number of queries each thread task makes to the cache */
    static final int NUM_QUERIES = 1000000;

    static int numThreads;
    static ExecutorService threadPool;
    AtomicInteger cacheMisses;
    Random r;

    @BeforeAll
    static void init(){
        numThreads = Runtime.getRuntime().availableProcessors() + 1;
        threadPool = Executors.newFixedThreadPool(numThreads);
    }

    @AfterAll
    static void cleanup(){
        threadPool.shutdown();
    }

    @BeforeEach
    void reset(){
        cacheMisses = new AtomicInteger(0);
         r = new Random();
    }

    @Test
    void loadTestCacheWithEagerEviction(){
        int capacity = 2000000;
        Cache<String,String> cache = new LruCacheWithEagerEviction<>(capacity);
        loadTest(cache);
    }

    @Test
    void loadTestCacheWithBatchEviction(){
        int capacity = 2000000;
        Cache<String,String> cache = new LruCacheWithBatchEviction<>(capacity);
        loadTest(cache);
    }

    /**
     * This ensures that all threads run at the sametime. Countdown latches are used
     * such that they provide a barrier where all threads must meet before proceeding.
     * A good explanation is provided in <em>Java Concurrency in Practice</em> book by
     * Brian Goetz.
     *
     * @param cache     cache to execute queries against.
     */
    void loadTest(Cache<String,String> cache) {

        CountDownLatch startSignal = new CountDownLatch(1);
        CountDownLatch readySignal = new CountDownLatch(numThreads);
        CountDownLatch doneSignal = new CountDownLatch(numThreads);
        for (int i=0;i<numThreads;i++){
            threadPool.execute(loadTask(startSignal, readySignal, doneSignal, i, cache));
        }
        try {
            readySignal.await();
            long start = System.currentTimeMillis();
            logger.info("--------------------");
            logger.info("Starting all threads");
            logger.info("--------------------");

            startSignal.countDown();
            doneSignal.await();

            logger.info("---------------------");
            logger.info("All threads completed");
            logger.info("---------------------");

            logger.info("Time taken(ms): {}", (System.currentTimeMillis()-start));
            logger.info("Cache missed: {}", cacheMisses.get());
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Returns a {@link Runnable} task that waits for {@code startSignal} to execute
     * its task. Once the task is completed, it signals via {@code doneSignal}
     *
     * @param startSignal   countdown latch that provides start signal
     * @param readySignal   countdown latch to signal that this thread is ready
     * @param doneSignal    countdown latch to signal that this thread is done
     * @param prefix        thread identifier
     * @param cache         cache to execute queries against
     * @return              a runnable task that can be executed in a thread
     */
    Runnable loadTask(final CountDownLatch startSignal,
                      final CountDownLatch readySignal,
                      final CountDownLatch doneSignal,
                      final int prefix,
                      final Cache<String,String> cache) {
        return () -> {
                try {
                    logger.info("Thread {} ready", prefix);
                    readySignal.countDown();
                    startSignal.await();
                    logger.info("Thread {} started", prefix);
                    executeTask(cache, prefix);
                    logger.info("Thread {} done", prefix);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }finally{
                    doneSignal.countDown();
                }
        };
    }

    /**
     * This task is run by each thread. Each of this task makes {@link LoadTest#NUM_QUERIES}
     * to cache.
     * <p/>
     * Of those, 30% is {@code set} and 70% is {@code get}. Of all {@code get} calls, preference
     * is given to most recently written entries. Most recent 30% written entries are retrieved
     * 70% of times. This is to simulate situation where newer entries are accessed more
     * frequently. As entries get older, their access frequency declines.
     * <p/>
     * Each thread writes its own entry. No 2 thread writes the same entry. {@code prefix} is
     * thread identifier and is encoded into the key.
     * <p/>
     * Each thread randomly tries to fetch entries created by any thread.
     *
     * @param cache     cache to test against
     * @param prefix    identifies the thread, it gets coded into the key
     */
    void executeTask(Cache<String,String> cache, int prefix){
        int wCounter=0,miss=0;
        String id;

        for (int i=0;i<NUM_QUERIES;i++){

            if (r.nextInt(10)<7 && wCounter>10){//get call 70% times
                //70% reads on latest 30% written entries
                int threadId = r.nextInt(numThreads)+1;

                int writePartition = (int)Math.floor(wCounter*0.3);

                if (r.nextInt(10) < 7){
                    //read latest 30% of writes
                    id=threadId+"-"+r.nextInt(writePartition);
                }else {
                    //read oldest 70% of writes
                    id=threadId+"-"+(writePartition+r.nextInt(wCounter-writePartition));
                }

                if (cache.get("key-"+id) == null){
                    miss++;
                }

            }else{//set call
                id=prefix+"-"+wCounter++;
                cache.put("key-"+id, "value-"+id);
            }
        }

        cacheMisses.addAndGet(miss);
    }
}