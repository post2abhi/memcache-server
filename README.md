# memcache-server
A simple in-memory memcache server. It partially implements memcache protocol defined [here](https://github.com/memcached/memcached/blob/master/doc/protocol.txt).
This server supports `get` with multiple keys, `set` and `quit` commands. 

## Quick Start
Clone the repo as the first step. Then follow the instructions below.

### Build
1. cd memcache-server
2. ./gradlew

### Start server
1. cd memcache-server
3. ./start.sh [port]

At this point server would be running at the specified port or default `11211`. You should see following
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
This application is bootstrapped using [Spring Boot](https://spring.io/projects/spring-boot). 
There are 2 distinct components in the application.
1. Server - a tcp server that listens for memcache requests, decodes them and pass the request to cache component. It also encodes the response and exceptions.
2. Cache - its an in-memory cache that stores keys and associated values.

### Server
Server is based on [netty.io](http://netty.io/) framework that utilizes java [nio](https://docs.oracle.com/javase/8/docs/api/java/nio/package-summary.html) APIs.  
It allows you to write asynchronous event driven IO applications that can scale very high.  

Server itself works as a state machine. It starts in `AcceptCommandState` where it listens for memcache commands from clients.
It transitions to `AcceptDataState` after receiving a `set` command. This state is maintained until all data is received
or exception happens. After which point, it goes back to `AcceptCommandState`.

`MemcacheProtocolListener` is the main server. When a new client connection is received, it instantiates `MemcacheProtocolHandler` that gets bound to this
connection. `MemcacheProtocolHandler` is the state machine that handles all client requests and response for this connection.

### Cache
There are 3 cache implementations:
1. `SimpleCache`:
This is just a wrapper around java collection's `ConcurrentHashMap`. This was written just to get some baseline performance numbers.
There's no eviction in this cache and the store will grow to occupy all available memory. Although not implemented, to simulate eviction,
we could perhaps use [WeakReference](https://docs.oracle.com/javase/8/docs/api/java/lang/ref/WeakReference.html) for `values` to allow JVM 
GC to remove these objects to reclaim memory.

2. `LruCacheWithEagerEviction`:
This also uses `ConcurrentHashMap`. It augments it with a doubly linked list that keeps the keys ordered by recency. Most recenltly accessed
key to oldest key is stored from `head` to `tail`.
When a key is accessed/stored/updated, its moved to `head`. Furthermore, when a key is stored, cache checks if capacity allows storage. If
cache is full, `tail` is removed to make room for new entry.
Both `map` and `linkedlist` data structures are guarded by `ReentrantLock` to allow concurrency. 

3. `LruCacheWithBatchEviction`:
This implementation builds upon `LruCacheWithEagerEviction` and tries to minimize the time spent in `Critical Section`. In eager eviction, the
`Critical Section` comprises of updating the map and adjusting the linked list accordingly. This implementation tries to eliminate the update
to linked list altogether thereby shortening the time in `Critical Section` to map update only. The `key` access is recorded in a `BlockingQueue`
which is periodically drained. A dedicated thread periodically wakes up, reads all entries from the queue and updates a `LinkedHashMap` that
keeps entries ordered by LRU. When cache goes over its capacity, this thread starts evicting older entries until the cache size goes below
configured capacity.
Data structures are guarded by `ReadWriteLock`. Lock is stripped to reduce contention. Stripping is based on [Java Concurrency In Practice](http://jcip.net/).
Recording `key` access into `BlockingQueue` does not block, so its immediate. This means if the queue is full, the access wont be recorded. This
trade-off is done for performance. There have been studies that shows probabilistic algorithms tend to provide good performance benefits
without loosing accuracy. See [TinyLFU: A Highly Efficient Cache Admission Policy](http://www.cs.technion.ac.il/~gilga/TinyLFU_PDP2014.pdf).
The design is loosely based on `Commit Log` that many `SSTables` based DBs use where the changes are recoded into an `append-only` log. These
changes are then replayed on to *main* data structure asynchronously.

## Performance
On my system (8 core), load test used 9 threads with each thread making 1 million requests (Read(70%)/Write(30%)). For read, test gives preference
to recently stored keys to simulate frequent access to recent data.

#### Simple Cache
````Text
Time taken(ms): 4159
Cache size: 2699958
````

#### Eager Eviction
````text
Time taken(ms): 15286
Cache missed: 922066
Cache size: 2000000
````
#### Batch Eviction
````text
Time taken(ms): 10553
Cache missed: 907195
Cache size: 2413140
````

### Summary
Batch eviction improves the performance by roughly 30%. The improvement is due to 2 optimizations:
1. Lock striping
2. Reduce time spent in `Critical Section`



