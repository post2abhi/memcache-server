/*
 * Copyright 2018 Abhishek Kumar. All Rights Reserved.
 */
package com.github.ak.memcache.server.codec;

import com.google.common.base.Preconditions;

import java.util.ArrayList;
import java.util.List;

public class GetCommandRequest implements MemcacheRequest {
    private String message;
    List<String> keys;

    public GetCommandRequest(String request){
        Preconditions.checkNotNull(request);
        this.message = request;
        keys = new ArrayList<>();
        parse();
    }

    @Override
    public String getRawMessage() {
        return message;
    }

    public List<String> getKeys(){
        return keys;
    }

    private void parse(){
        String[] tokens = message.split("\\s+");

        //ignore 1st token; its command name i.e 'get'
        for (int i=1;i<tokens.length;i++){
            keys.add(tokens[i]);
        }
    }
}
