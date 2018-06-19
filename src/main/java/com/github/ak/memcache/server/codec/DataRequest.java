/*
 * Copyright 2018 Abhishek Kumar. All Rights Reserved.
 */
package com.github.ak.memcache.server.codec;

public class DataRequest implements MemcacheRequest {
    private String message;
    public DataRequest(String msg){
        this.message = msg;
    }
    @Override
    public String getRawMessage() {
        return message;
    }
}
