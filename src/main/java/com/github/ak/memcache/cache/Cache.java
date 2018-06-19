/*
 * Copyright 2018 Abhishek Kumar. All Rights Reserved.
 */
package com.github.ak.memcache.cache;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * An in-memory storage for key value pairs. Eviction policy for
 * entries is implementation specific. Implementations should
 * provide thread safety and can be accessed concurrently.
 *
 * @param <K> the type of keys this cache can store
 * @param <V> the type of values this cache can store
 */
public interface Cache<K,V> {
    /**
     * Returns the associated value for specified (@code key}. Returns
     * {@code null} if cache does not contain key.
     *
     * @param key key whose associated value is to be returned
     * @return   the value to which the specified key is mapped, or
     *          {@code null} if this cache does not contain the key
     * @throws NullPointerException if specified key is {@code null}
     */
    @Nullable V get(@Nonnull K key);

    /**
     * Stores the provided {@code value} for given {@code key} in the
     * cache.
     *
     * @param key key whose associated value is to be stored
     * @param value the value to which the specified key should be mapped
     * @throws NullPointerException if specified key is {@code null}
     */
    void put(@Nonnull K key, V value);

    /**
     * Returns the current number of entries in cache.
     *
     * @return the number of cache entries
     */
    int size();
}
