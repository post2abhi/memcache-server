package com.github.ak.memcache.cache;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SimpleCache implements Cache<String,String> {
    private Map<String,String> data = new ConcurrentHashMap<>();

    @Override
    public String get(String key) {
        return data.get(key);
    }

    @Override
    public void put(String key, String value) {
        data.put(key, value);
    }

    @Override
    public int size(){
        return data.size();
    }

}
