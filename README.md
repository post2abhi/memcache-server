# memcache-server
A simple in-memory memcache server. It partially implements memcache protocol defined [here](https://github.com/memcached/memcached/blob/master/doc/protocol.txt).
This server supports `get` with multiple keys, `set` and `quit` commands. 

## Quick Start
Clone the repo as the first step. Then follow the instructions below.

### Start server
1. cd memcache-server
2. ./gradlew
3. ./start.sh

At this point server would be running at the default port `11211`. You should see following
lines on console:
````text
2018-06-18 21:56:03.575  INFO 78191 --- [ntLoopGroup-2-1] io.netty.handler.logging.LoggingHandler  : [id: 0x483423c3] BIND: 0.0.0.0/0.0.0.0:11211
2018-06-18 21:56:03.585  INFO 78191 --- [ntLoopGroup-2-1] io.netty.handler.logging.LoggingHandler  : [id: 0x483423c3, L:/0:0:0:0:0:0:0:0:11211] ACTIVE
```` 

### Connect to server
A telnet client or netcat (on mac) can be used to connect. Below are some examples.
````text
$ nc localhost 11211
set key1 0 0 6
value1
STORED
get key1
VALUE key1 0 6
value1
END
set key2 0 0 6
value2
STORED
get key1 key2
VALUE key1 0 6
value1
VALUE key2 0 6
value2
END
set key3 0 0 2
abcd
CLIENT_ERROR Data size exceeded
quit
$
````
## Design

## Load

## Further improvements

