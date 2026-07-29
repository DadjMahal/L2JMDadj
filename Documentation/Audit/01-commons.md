# 01 — commons/

Shared infrastructure used by LoginServer, GameServer, and all tools.
45 files, ~10.5k lines. Stable, rarely changed. Based partly on async-mmocore (JoeAlisson).

## config/ — static config holders

Three classes, each loads a `.ini` file via `ConfigReader` into `public static` fields.

| Class | File | Fields | Notes |
|-------|------|--------|-------|
| `DatabaseConfig` | `./config/Database.ini` | DRIVER, URL, LOGIN, PASSWORD, MAX_CONNECTIONS, TEST_CONNECTIONS, BACKUP_DATABASE, MYSQL_BIN_PATH, BACKUP_PATH, BACKUP_DAYS | Defaults: `com.mysql.cj.jdbc.Driver`, `jdbc:mysql://localhost/l2jmobius`, `root`, 10 connections |
| `InterfaceConfig` | `./config/Interface.ini` | ENABLE_GUI, DARK_THEME | GUI auto-disabled on headless |
| `ThreadConfig` | `./config/Threads.ini` | SCHEDULED_THREAD_POOL_SIZE, HIGH_PRIORITY_SCHEDULED_THREAD_POOL_SIZE, INSTANT_THREAD_POOL_SIZE, THREADS_FOR_LOADING | -1 = auto (procs*4 scheduled, procs*2 instant); high priority = scheduled/4 |

Pattern: `XxxConfig.load()` called once at startup. Fields then read directly.

## database/ — HikariCP pool

### DatabaseFactory
- Singleton `HikariDataSource` (`DATABASE_POOL`), `synchronized init()`.
- `init()` calls `DatabaseConfig.load()`, builds `HikariConfig`:
  - maxPoolSize/minimumIdle computed from `DATABASE_MAX_CONNECTIONS` (auto-adjusts if connections fail).
  - connectionTimeout 60s, idleTimeout 5min, maxLifetime 10min, leakDetection 10min.
  - Pool name `L2JMobiusPool`, MBeans registered, `InitializationFailTimeout(-1)`.
- Tests connections at startup: if all succeed → logs count; if partial → `adjustPoolSize()` (floor to 100/50, min 20).
- `getConnection()` — borrows from pool, throws RuntimeException on failure.
- `close()` — graceful pool shutdown.

### DatabaseBackup
- `performBackup(description)` — runs `mysqldump` via `Runtime.exec`.
- Deletes backups older than `BACKUP_DAYS` from `BACKUP_PATH`.
- Windows uses `MYSQL_BIN_PATH` prefix; Unix uses bare `mysqldump`.

## threads/ — thread pools

### ThreadPool
- Three pools, all created in `init()`:
  1. `HIGH_PRIORITY_SCHEDULED_POOL` (ScheduledThreadPoolExecutor, priority 8, CallerRunsPolicy) — only if size > 0.
  2. `SCHEDULED_POOL` (ScheduledThreadPoolExecutor, priority 5, custom RejectedExecutionHandlerImpl).
  3. `INSTANT_POOL` (ThreadPoolExecutor, LinkedBlockingQueue, 1min keepalive, priority 5).
- Public API: `schedule(Runnable, delay)`, `scheduleAtFixedRate`, `scheduleWithFixedDelay`, `execute(Runnable)` (instant pool).
- All tasks wrapped in `RunnableWrapper` (catches Throwable → UncaughtExceptionHandler).
- `validateDelay()` clamps to [0, 100 years].
- Purge task runs every 60s to clear cancelled scheduled tasks.
- `shutdown()` — `shutdownNow()` on all three.

### ThreadProvider (ThreadFactory)
- Name prefix + atomic counter → `prefix N`.
- Sets priority + daemon flag.

### ThreadPriority
- Enum PRIORITY_1..PRIORITY_10 (maps to Thread priorities 1-10).

## network/ — async NIO stack (critical for AI players)

Based on async-mmocore. All buffers are **direct ByteBuffer, LITTLE_ENDIAN**. Packet header is **2 bytes (unsigned short = payload size + 2)**.

### Connection flow
```
ConnectionManager binds AsynchronousServerSocketChannel
  -> accept -> AcceptConnectionHandler.completed(channel)
     -> new Connection(channel, readHandler, writeHandler, config)
     -> clientFactory.apply(connection) -> Client
     -> connection.setClient(client)
     -> client.onConnected() + client.read()
```

### Read flow (ReadHandler)
```
read header (2 bytes) -> handleHeader
  -> dataSize = Short.toUnsignedInt(header) - 2
  -> if dataSize > 0: client.readPayload(dataSize)
read payload -> handlePayload
  -> buffer.flip()
  -> client.decrypt(buffer, 0, remaining)   [abstract]
  -> PacketHandler.handlePacket(buffer, client) -> ReadablePacket
  -> packet.init(client, buffer)
  -> packet.read()  [abstract, must return true to execute]
  -> PacketExecutor.execute(packet)  [ThreadPool, runs packet.run()]
  -> client.read()  [continue reading next]
```

### Write flow (Client.writePacket -> WriteHandler)
```
Client.writePacket(WritablePacket)
  -> queue packet (ConcurrentLinkedQueue)
  -> writeFairPacket() (fair scheduling across pending clients)
  -> WritablePacket.writeData(client)
     -> choosePacketBuffer (array-backed if broadcast, dynamic otherwise)
     -> buffer.position(2)  [reserve header]
     -> packet.write(client, buffer)  [abstract, must return true]
     -> writeHeader(buffer, totalSize)
  -> client.encrypt(buffer)  [abstract]
  -> connection.write(ByteBuffer[])
  -> WriteHandler.completed -> finishWriting or resumeSend
```

### Key classes
| Class | Role |
|-------|------|
| `ConnectionManager<T extends Client>` | Binds socket, accepts connections, creates Connection+Client. `shutdown()` closes gracefully. |
| `Connection<T>` | Wraps AsynchronousSocketChannel. Manages read/write ByteBuffers. `readHeader()`, `read(size)`, `write(buffers)`, `close()`. Releases buffers to ResourcePool. |
| `Client<T>` | Abstract. Holds connection + write queue. Abstract: `encrypt()`, `decrypt()`, `onConnected()`, `onDisconnection()`. `writePacket()`, `disconnect()`, `isConnected()`. Fair write scheduling via PENDING_CLIENTS queue. Can drop packets if queue exceeds threshold. |
| `PacketHandler<T>` | Functional interface: `ReadablePacket handlePacket(ReadableBuffer, T)`. Converts raw decrypted bytes → packet instance. |
| `PacketExecutor<T>` | ThreadPoolExecutor that runs `packet.run()`. Catches Throwable → UncaughtExceptionHandler. |
| `ReadHandler<T>` | CompletionHandler<Integer, T>. Drives header→payload→decrypt→parse→execute→read-next loop. |
| `WriteHandler<T>` | CompletionHandler<Long, T>. Handles partial writes, finishWriting on completion. |
| `ConnectionConfig` | Loads `config/Network.ini`. Builds ResourcePool with buffer pools (header=2B, segment size, custom pools via `BufferPool.<name>.Size`). `HEADER_SIZE=2`, `dropPackets`, `dropPacketThreshold=250`, `useNagle`, `threadPoolSize`, `threadPriority`, `shutdownWaitTime`. |
| `ResourcePool` | TreeMap<Integer, BufferPool> keyed by buffer size. `getHeaderBuffer()`, `getBuffer(size)`, `recycleAndGetNew()`, `recycleBuffer()`. Auto-creates pools for unseen sizes. Direct ByteBuffer, LITTLE_ENDIAN. |

### Buffer interfaces
| Interface | Methods |
|-----------|---------|
| `Buffer` | Indexed read/write: `readByte/Short/Int(index)`, `writeByte/Short/Int(index, value)`, `limit()`, `limit(newLimit)` |
| `ReadableBuffer extends Buffer` | Sequential: `readChar/Byte/Bytes/Short/Int/Long/Float/Double`, `remaining()`. `static of(ByteBuffer)` → `SinglePacketBuffer`. |
| `WritableBuffer extends Buffer` | Sequential: `writeChar/Byte/Bytes/Short/Int/Float/Long/Double`, `writeString` (UTF-16LE + null term), `writeSizedString` (short length + UTF-16LE). |

### Packet abstractions
| Class | Role |
|-------|------|
| `ReadablePacket<T> implements Runnable` | Abstract. `init(client, buffer)`. Read helpers: `readByte/Short/Int/Long/Float/Double/Boolean/String/SizedString`. **`abstract boolean read()`** — must return true for packet to execute. `run()` = packet logic (implemented by subclasses, executed by PacketExecutor). |
| `WritablePacket<T>` | Abstract. **`abstract boolean write(T client, WritableBuffer buffer)`** — must return true. `sendInBroadcast()` — caches data once, copies per client (each copy encrypted separately). `canBeDropped(client)` — if true, packet may be dropped under load. `writeHeader(buffer, size)`. |
| `base/BaseReadablePacket` | Simpler byte[]-backed reader (no Client/Connection generic). `readByte()` returns unsigned int. Used by login server packets and tools. |
| `base/BaseWritablePacket` | Simpler byte[]-backed writer. Max 65533 bytes. `getSendableBytes()` writes header (unsigned short) + trims. Caches max packet size per class. `write()` override hook. |

### internal/ — buffer pool implementations
| Class | Role |
|-------|------|
| `BufferPool` | Pool of ByteBuffers per size. `recycle(buffer)`, auto-expand capacity. |
| `ArrayPacketBuffer` | byte[]-backed WritableBuffer (for broadcast caching). |
| `DynamicPacketBuffer` | ByteBuffer-backed dynamic WritableBuffer (grows as needed). |
| `SinglePacketBuffer` | ReadableBuffer wrapping a single ByteBuffer. |
| `InternalWritableBuffer` | Factory + adapter for writable buffers. `arrayBacked()`, `dynamicOf()`. |
| `MMOThreadFactory` | ThreadFactory with naming for NIO threads. |

### Data format conventions (IMPORTANT for AI players)
- **Endianness: LITTLE-ENDIAN** throughout.
- **Header: 2 bytes** unsigned short = total packet size (payload + header).
- **Strings: UTF-16LE**, null-terminated (2 zero bytes) for `readString`/`writeString`; or short-length-prefixed for `readSizedString`/`writeSizedString`.
- **Booleans: byte** (0/1) or short or int depending on write method used.
- **Max packet: ~65533 bytes** (BaseWritablePacket) / ~65535 (WritablePacket).

## crypt/ — Blowfish packet encryption

### NewCrypt
- Wraps `BlowfishEngine`. Block size 8 bytes, ECB mode.
- `decrypt(data, offset, size)` / `crypt(data, offset, size)` — Blowfish ECB in 8-byte blocks.
- `verifyChecksum(data, off, size)` — validates 4-byte trailing checksum (size must be multiple of 4, > 4).
- `appendChecksum(data, off, size)` — computes and writes 4-byte checksum.
- `encXORPass(data, off, size, xorKey)` — XOR encryption with rolling key (used in login handshake). Appends key at end.

### BlowfishEngine
- Standard Blowfish implementation (1468 lines). `init(key)`, `encryptBlock(data, offset)`, `decryptBlock(data, offset)`. P-array + S-boxes initialized from key.

**Encryption lifecycle:** Login server uses XOR pass for initial handshake, then Blowfish. Game server uses Blowfish with per-session key (set after BlowFishKeygen exchange). Checksum verified/appended on each packet.

## time/

### TimeUtil
- `parseDuration("5days")` → `Duration`. Units: sec(s), min(s), hour(s), day(s), week(s) (=7d), month(s) (=30d), year(s) (=365d).
- `formatDuration(Duration)` → human readable.
- `formatDate(Date, pattern)`, `getDateString`, `getDateTimeString` (dd/MM/yyyy [HH:mm:ss]).
- `getNextDayTime(dayOfWeek, hour, minute)` → next occurrence Calendar (rolls to next week if passed).
- `getNextTime(hour, minute)` → next occurrence today or tomorrow.

### SchedulingPattern
- UNIX cron-like parser (5-6 fields: min hour day month weekday [weekOffset]).
- Extended: `~N` random delay, `+N` time offset, `L` last day of month, `|` OR separator, `*` , ranges `-`, steps `/`, month/weekday aliases (jan-dec, sun-sat).
- `getNextMatch(afterMillis, timeZone)` — searches up to 4 years ahead.

## ui/

| Class | Role |
|-------|------|
| `DarkTheme` | `activate()` — sets Nimbus L&F with dark colors (dark gray base, white text). |
| `SplashScreen` | `JWindow` showing image for N ms, then shows parent JFrame. |
| `LineLimitListener` | `DocumentListener` capping max lines in a Swing text component (removes from start or end). |

## util/

| Class | Role |
|-------|------|
| `ConfigReader` | `.ini`/Properties loader. Typed getters with defaults + warning logs: `getString/Int/Long/Float/Double/Boolean/Enum/Duration/IntArray`. `containsKey()`, `getValue()`, `getStringPropertyNames()`. |
| `IXmlReader` | Interface with ~40 default XML parsing helpers. `load()` abstract. `parseFile(File)` (validation on by default), `parseDatapackFile(path)`. Helpers: `parseInteger/Boolean/Double/Enum/Attributes`, `forEach(node, action)`, `isNode()`. Supports async parallel loading via `ThreadConfig.THREADS_FOR_LOADING`. |
| `StringUtil` | `concat(String...)`, `append(StringBuilder, args)`, `isAlphaNumeric`, `isNumeric`, `isInteger`, `isFloat`, `isDouble`, `isEnum`. |
| `Rnd` | `ThreadLocalRandom` wrapper. `get(bound)`, `get(origin, bound)` (inclusive), `nextBoolean/Int/Long/Double/Gaussian`, `nextBytes`. |
| `Subnet` | CIDR `"ip/prefix"` parser. `isInSubnet(byte[])`. IPv4 + IPv6 (handles IPv4-in-IPv6). `equals(InetAddress/Subnet)`. |
| `DeadlockWatcher extends Thread` | Polls `ThreadMXBean.findDeadlockedThreads()` every checkInterval. Full or minimal report. Invokes callback on detection. |
| `HexUtil` | `generateHexBytes(size)` — random non-zero bytes. |
| `TraceUtil` | `getStackTrace(Throwable)`, `getTraceString(StackTraceElement[])`. |

## Where to change X

| I want to... | Go to |
|-------------|-------|
| Change DB connection | `DatabaseConfig.java` + `config/Database.ini` |
| Change pool sizing/timeout | `DatabaseFactory.init()` (hardcoded) + `DatabaseConfig.DATABASE_MAX_CONNECTIONS` |
| Change thread pool sizes | `ThreadConfig.java` + `config/Threads.ini` |
| Add a scheduled task | `ThreadPool.schedule/scheduleAtFixedRate` |
| Change network buffer pools | `ConnectionConfig` + `config/Network.ini` (`BufferPool.*` properties) |
| Change packet drop behavior | `ConnectionConfig.dropPackets/dropPacketThreshold` + `Client.packetCanBeDropped` |
| Change Blowfish/key exchange | `NewCrypt`, `BlowfishEngine`, server-specific `Encryption` classes |
| Add a new XML data loader | implement `IXmlReader`, call `parseDatapackFile()` in `load()` |
| Parse a cron schedule | `SchedulingPattern` |
