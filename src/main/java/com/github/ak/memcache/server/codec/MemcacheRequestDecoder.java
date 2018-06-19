/*
 * Copyright 2018 Abhishek Kumar. All Rights Reserved.
 */
package com.github.ak.memcache.server.codec;

public interface MemcacheRequestDecoder {

    static MemcacheRequest decode(String request){

        if (request.startsWith(MemcacheRequest.Command.GET.toString())){
            return new GetCommandRequest(request);
        }else if (request.startsWith(MemcacheRequest.Command.SET.toString())){
            return new SetCommandRequest(request);
        }else if (request.startsWith(MemcacheRequest.Command.QUIT.toString())){
            return new QuitCommandRequest(request);
        }else {
            return new DataRequest(request);
        }
    }
}
