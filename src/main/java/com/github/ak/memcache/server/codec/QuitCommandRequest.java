/*
 * Copyright 2018 Abhishek Kumar. All Rights Reserved.
 */
package com.github.ak.memcache.server.codec;

import com.google.common.base.Preconditions;

public class QuitCommandRequest implements MemcacheRequest {

    private final String message;
    public QuitCommandRequest(String request){
        Preconditions.checkNotNull(request);
        this.message = request;
    }
    @Override
    public String getRawMessage() {
        return message;
    }
}
