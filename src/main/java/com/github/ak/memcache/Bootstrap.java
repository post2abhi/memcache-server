/*
 * Copyright 2018 Abhishek Kumar. All Rights Reserved.
 */
package com.github.ak.memcache;

import com.github.ak.memcache.server.Initializer;
import com.github.ak.memcache.server.MemcacheProtocolListener;
import com.github.ak.memcache.cache.Cache;
import com.github.ak.memcache.cache.LruCacheWithBatchEviction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main class that bootstraps the application. Spring boot calls
 * {@link Bootstrap#run(ApplicationArguments)} to initiate the application.
 */
@SpringBootApplication
public class Bootstrap implements ApplicationRunner {

    private final Logger logger = LoggerFactory.getLogger(Bootstrap.class);

    @Autowired
    private ApplicationProperties properties;

    public static void main(String[] args) {
        SpringApplication.run(Bootstrap.class, args);
    }

    /**
     * Initializes the cache and passes it to memcache server ({@link MemcacheProtocolListener}).
     *
     * @param args
     * @throws Exception
     */
    @Override
    public void run(ApplicationArguments args) throws Exception {
        logger.info("Starting cache[{}]", "LruCacheWithBatchEviction");
        Cache<String, String> cache = new LruCacheWithBatchEviction(properties.getCacheCapacity());
        MemcacheProtocolListener.start(properties.getPort(), new Initializer(cache));
    }
}