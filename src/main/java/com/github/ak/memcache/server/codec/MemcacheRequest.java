/*
 * Copyright 2018 Abhishek Kumar. All Rights Reserved.
 */
package com.github.ak.memcache.server.codec;

public interface MemcacheRequest {
    enum Command{
        GET, SET, QUIT;

        @Override
        public String toString(){
            return this.name().toLowerCase();
        }
    }

    String getRawMessage();
}
