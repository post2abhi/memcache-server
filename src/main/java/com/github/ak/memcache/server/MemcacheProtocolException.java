/*
 * Copyright 2018 Abhishek Kumar. All Rights Reserved.
 */
package com.github.ak.memcache.server;

/**
 * These classes map <em>memcache protocol errors</em> to java {@link Exception}.
 *
 * @see <a href="https://github.com/memcached/memcached/blob/master/doc/protocol.txt">memcache protocol</a>
 *
 */
public abstract class MemcacheProtocolException extends Exception {
    private String code;

    public MemcacheProtocolException(String code){
        this.code = code;
    }
    public MemcacheProtocolException(String code, String msg){
        super(msg);
        this.code = code;
    }

    public String getCode(){
        return code;
    }

    /**
     *
     */
    public static class InvalidCommandException extends MemcacheProtocolException{

        public static final String ERROR = "ERROR";

        public InvalidCommandException() {
            super(ERROR);
        }
    }

    /**
     *
     */
    public static class ClientException extends MemcacheProtocolException{
        public static final String CLIENT_ERROR = "CLIENT_ERROR";

        public ClientException() {
            super(CLIENT_ERROR);
        }

        public ClientException(String msg) {
            super(CLIENT_ERROR, msg);
        }
    }

    /**
     *
     */
    public static class ServerException extends MemcacheProtocolException{
        public static final String SERVER_ERROR = "SERVER_ERROR";

        public ServerException() {
            super(SERVER_ERROR);
        }

        public ServerException(String msg) {
            super(SERVER_ERROR, msg);
        }
    }
}
