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
    public String getCode(){
        return code;
    }
    public static class InvalidCommandException extends MemcacheProtocolException{
        public InvalidCommandException() {
            super("ERROR");
        }
        public String toString(){
            return getCode();
        }
    }

    public static class ClientException extends MemcacheProtocolException{
        private String msg;

        public ClientException() {
            super("CLIENT_ERROR");
        }
        public ClientException(String msg) {
            super("CLIENT_ERROR");
            this.msg = msg;
        }
        public String getMessage(){
            return msg;
        }
        public String toString(){
            return getCode() + " " + getMessage();
        }
    }

    public static class ServerException extends MemcacheProtocolException{
        private String msg;

        public ServerException() {
            super("SERVER_ERROR");
        }
        public ServerException(String msg) {
            super("SERVER_ERROR");
            this.msg = msg;
        }
        public String getMessage(){
            return msg;
        }
        public String toString(){
            return getCode() + " " + getMessage();
        }
    }

}
