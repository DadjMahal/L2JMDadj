# Tools & Log Subsystem Audit (Iteration 21)

## Purpose
Covers administrative utility tools (`java/org/l2jmobius/tools/`) and the logging framework (`java/org/l2jmobius/log/`) used by both the Login Server and Game Server.

## Tools Module (`tools/`)
Administrative utilities with both GUI (Swing) and console interfaces.

### Classes
| Class | Purpose |
|---|---|
| `AccountManager` | Manages user accounts: create, delete, update, list. Supports both Swing GUI and console menu. Passwords are hashed (MD5). |
| `DatabaseInstaller` | Installs/initializes the Login Server and Game Server databases. Provides Swing GUI with database connection testing, schema installation, and database dump functionality. |
| `GameServerRegister` | Registers/unregisters game server instances with the Login Server. Supports both Swing GUI and console modes. Manages the `gameserver` table. |
| `Search` | Recursive file-content search utility. Supports regex patterns, file extension filtering, and ignore lists. Provides both Swing GUI and command-line interface (`-ext`, `-caseSensitive`). |

### Public API Surface
- `AccountManager.main(String[] args)` – launches console or GUI mode.
- `AccountManager.createAccountCmd(Scanner)` – creates account via console.
- `AccountManager.deleteAccountCmd(Scanner)` – deletes account via console.
- `AccountManager.updateAccountCmd()` – updates account via console.
- `AccountManager.listAccountsCmd()` – lists all accounts via console.
- `DatabaseInstaller.main(String[] args)` – launches the installer GUI.
- `DatabaseInstaller.getDatabaseConnection()` – establishes MySQL connection.
- `DatabaseInstaller.installDatabase()` – installs schema for login/game DB.
- `DatabaseInstaller.dumpDatabase()` – dumps database to SQL file.
- `GameServerRegister.main(String[] args)` – launches registration GUI or console.
- `GameServerRegister.registerServerConsole()` – registers server via console.
- `GameServerRegister.removeServer(int serverId)` – removes a registered server.
- `Search.main(String[] args)` – launches search tool with CLI args.
- `Search.searchFiles(...)` – performs recursive file search with regex.

### Control Flow
- **AccountManager**: GUI mode uses SwingWorker for async DB operations; console mode uses Scanner for input. Password hashing via `MessageDigest.getInstance("MD5")`. DB access via `DatabaseFactory.getConnection()`.
- **DatabaseInstaller**: GUI collects DB credentials, tests connection, then runs SQL scripts from the `sql/` directory. Dump uses JDBC `DatabaseMetaData` and `ResultSet` to generate SQL.
- **GameServerRegister**: Reads/writes the `gameserver` table via `GameServerTable`. Console mode uses Scanner for interactive input.
- **Search**: Walks file tree using `Files.walkFileTree`, filters by extension and ignore list, counts regex matches per file, displays results in Swing table or console output.

### I/O
- **Database**: JDBC connections via `DatabaseFactory` (game) or direct `DriverManager` (tools). Tables accessed: `accounts`, `gameserver`.
- **Files**: `Search` reads files with UTF-8 encoding; `DatabaseInstaller` writes SQL dumps to `dumps/` directory.
- **Console**: All tools support console mode via `System.in`/`Scanner` and `System.out`.
- **GUI**: Swing-based interfaces with tables, buttons, and progress bars.

### Gotchas / Refactor Candidates
- Password hashing uses MD5 (weak); consider bcrypt or PBKDF2.
- `DatabaseInstaller` uses `DriverManager.getConnection` instead of `DatabaseFactory`, bypassing the connection pool.
- `Search` loads entire file content into memory (`Files.readString`) — could cause OOM on very large files.
- No unified CLI interface across tools; each has its own argument parsing.
- GUI and console modes are tightly coupled in the same classes, making testing harder.

## Notes (Resume Checkpoint)
- Read files:
  - Tools: AccountManager.java, DatabaseInstaller.java, GameServerRegister.java, Search.java
  - Log handlers: ErrorLogHandler.java, ChatLogHandler.java, ItemLogHandler.java, AuditLogHandler.java, EnchantItemLogHandler.java, EnchantSkillLogHandler.java, OlympiadLogHandler.java, AccountingLogHandler.java, GMAuditLogHandler.java
  - Log filters: ErrorFilter.java, ChatFilter.java, ItemFilter.java, AuditFilter.java, EnchantItemFilter.java, EnchantSkillFilter.java, GMAuditFilter.java
  - Log formatters: FileLogFormatter.java, ChatLogFormatter.java, ItemLogFormatter.java, OlympiadFormatter.java, AuditFormatter.java, EnchantFormatter.java, AccountingFormatter.java, GMAuditFormatter.java, ConsoleLogFormatter.java
  - ServerLogManager.java
- Result: All 25 files analyzed and documented
- Next step: Mark iteration 21 complete, update PROGRESS.md

## Log Module (`log/`)
A `java.util.logging` (JUL) based framework with custom handlers, filters, and formatters. Configured via `dist/game/log.cfg` and `dist/login/log.cfg`.

### Structure
- **Handlers** (`org.l2jmobius.log.handler.*`) — extend `java.util.logging.FileHandler` or `java.util.logging.Handler`:
  - `ErrorLogHandler` – logs exceptions/errors (formatted with `SimpleFormatter`).
  - `ChatLogHandler` – logs chat messages (formatted with `ChatLogFormatter`).
  - `ItemLogHandler` – logs item transactions (formatted with `ItemLogFormatter`).
  - `AuditLogHandler` – logs audit events (formatted with `AuditFormatter`).
  - `EnchantItemLogHandler` / `EnchantSkillLogHandler` – logs enchantment results (formatted with `EnchantFormatter`).
  - `OlympiadLogHandler` – logs olympiad matches (formatted with `OlympiadFormatter`, outputs CSV).
  - `AccountingLogHandler` – logs account login/logout events (formatted with `AccountingFormatter`).
  - `GMAuditLogHandler` – logs GM command usage (formatted with `GMAuditFormatter`).
- **Filters** (`org.l2jmobius.log.filter.*`) — implement `java.util.logging.Filter`:
  - `ErrorFilter`, `ChatFilter`, `ItemFilter`, `AuditFilter`, `EnchantItemFilter`, `EnchantSkillFilter`, `GMAuditFilter` — each filters by logger name (e.g., `"item".equals(record.getLoggerName())`).
- **Formatters** (`org.l2jmobius.log.formatter.*`) — extend `java.util.logging.Formatter`:
  - `FileLogFormatter` – tab-separated: timestamp, level, thread ID, logger name, message. Java 16 compatible (uses `getLongThreadID`).
  - `ChatLogFormatter` – timestamp + message params.
  - `ItemLogFormatter` – timestamp + message + item details (objectId, enchant level, name, count).
  - `AuditFormatter`, `EnchantFormatter`, `OlympiadFormatter`, `AccountingFormatter`, `GMAuditFormatter`, `ConsoleLogFormatter` – specialized formatting per log type.
- **ServerLogManager** – extends `java.util.logging.LogManager`; overrides `reset()` to prevent premature handler closure during shutdown.

### Configuration (`log.cfg`)
- Each logger namespace (e.g., `chat`, `item`, `audit`) is configured with its own handler, formatter, filter, file pattern, rotation limit (100MB), backup count (20), and level.
- Global handler: `java.util.logging.FileHandler` (default), `ConsoleHandler`, `ErrorLogHandler`.
- Facility-specific levels: `org.l2jmobius.gameserver.level = CONFIG`, `org.l2jmobius.gameserver.network.serverpackets.level = FINER`.

### Control Flow
- On server startup, JUL reads `log.cfg` and instantiates handlers/filters/formatters via reflection.
- Game code calls `Logger.getLogger("item")` etc. to log events; the appropriate handler writes to its configured file.
- `ServerLogManager` ensures handlers remain open throughout the shutdown sequence.

### Gotchas / Refactor Candidates
- Handlers are tightly coupled to specific log types; an event‑bus or unified logging abstraction could reduce coupling.
- `ItemLogFormatter` formats `Item` parameters specially; other formatters use `toString()` — inconsistent.
- `ServerLogManager.reset()` is a no‑op, requiring explicit `doReset()` for shutdown — could confuse developers.
- No structured logging (JSON/XML); parsing log files requires custom logic.
- `OlympiadLogHandler` outputs CSV while others output plain text — inconsistent formats across handlers.
---

## Detailed Log Module Analysis

### Log Handlers (`org.l2jmobius.log.handler.*`)

All handlers extend `java.util.logging.FileHandler` and follow a simple pattern:

| Handler | Purpose | Format | Output File |
|---------|---------|--------|-------------|
| `ErrorLogHandler` | Exception/error logging | SimpleFormatter | `error.log` |
| `ChatLogHandler` | Player chat messages | ChatLogFormatter | `chat.log` |
| `ItemLogHandler` | Item transactions | ItemLogFormatter | `item.log` |
| `AuditLogHandler` | Admin/GM actions | AuditFormatter | `audit.log` |
| `EnchantItemLogHandler` | Item enchantments | EnchantFormatter | `enchant_item.log` |
| `EnchantSkillLogHandler` | Skill enchantments | EnchantFormatter | `enchant_skill.log` |
| `OlympiadLogHandler` | Olympiad matches | OlympiadFormatter | `olympiad.csv` |
| `AccountingLogHandler` | Login/logout events | AccountingFormatter | `accounting.log` |
| `GMAuditLogHandler` | GM command usage | GMAuditFormatter | `gm_audit.log` |

#### Handler Pattern

```java
public class XxxLogHandler extends FileHandler {
    public XxxLogHandler() throws IOException {
        super();  // Uses default FileHandler pattern
    }
}
```

Handlers are configured via `log.cfg` with:
- Pattern: `logs/xxx.log` or `logs/xxx.csv`
- Limit: 100MB per file
- Count: 20 backup files
- Level: Configurable per logger

### Log Filters (`org.l2jmobius.log.filter.*`)

Filters implement `java.util.logging.Filter` and filter by logger name:

```java
public class ErrorFilter implements Filter {
    @Override
    public boolean isLoggable(LogRecord record) {
        return record.getThrown() != null;  // Only log if exception present
    }
}
```

| Filter | Filters By | Purpose |
|--------|------------|---------|
| `ErrorFilter` | `record.getThrown() != null` | Only error/exception logs |
| `ChatFilter` | `"chat".equals(loggerName)` | Chat logger only |
| `ItemFilter` | `"item".equals(loggerName)` | Item logger only |
| `AuditFilter` | `"audit".equals(loggerName)` | Audit logger only |
| `EnchantItemFilter` | `"enchant_item".equals(loggerName)` | Item enchant logs |
| `EnchantSkillFilter` | `"enchant_skill".equals(loggerName)` | Skill enchant logs |
| `GMAuditFilter` | `"gm_audit".equals(loggerName)` | GM command logs |

### Log Formatters (`org.l2jmobius.log.formatter.*`)

#### FileLogFormatter (Baseline)
- Format: `timestamp\tlevel\tthread_id\tlogger_name\tmessage`
- Java 16 compatible using `getLongThreadID()`
- Used for general purpose logging

#### ChatLogFormatter
- Format: `[timestamp] message params`
- Used for chat.log

#### ItemLogFormatter
- Format: `[timestamp] message, item ObjectId: +n name(count)`
- Handles `Item` parameter specially with enchant level display
- Example: `[01 Jan 12:00:00] Item dropped, item 1234:+3 Sword(1)`

#### OlympiadFormatter
- Format: `timestamp,message,param1,param2,...`
- **Outputs CSV format** - unique among formatters
- Used for match data export

#### AccountingFormatter
- Format: Account login/logout events
- Example: `[01/Jan/2024 12:00:00] Account user1 logged in from 192.168.1.1`

#### GMAuditFormatter
- Format: GM command usage with timestamp and details
- Used for admin action auditing

#### ConsoleLogFormatter
- Format for console output with color support
- Timestamp format: `dd/MM/yyyy H:mm:ss`

### ServerLogManager

The `ServerLogManager` is critical for proper shutdown:

```java
public class ServerLogManager extends LogManager {
    @Override
    public void reset() {
        // do nothing - prevents premature handler closure
    }
    
    public void doReset() {
        super.reset();  // Actual reset on explicit call
    }
}
```

**Usage in shutdown sequence**:
1. GameServer calls `ServerLogManager.doReset()` explicitly
2. Handlers remain open until all shutdown tasks complete
3. Ensures final log entries are written

### Configuration Files

**`dist/game/log.cfg`** and **`dist/login/log.cfg`**:
```
# Example logger configuration
handlers = java.util.logging.FileHandler, org.l2jmobius.log.handler.ErrorLogHandler

# Item logger
org.l2jmobius.gameserver.item.level = INFO
org.l2jmobius.gameserver.item.handler = org.l2jmobius.log.handler.ItemLogHandler
org.l2jmobius.gameserver.item.formatter = org.l2jmobius.log.formatter.ItemLogFormatter
org.l2jmobius.gameserver.item.filter = org.l2jmobius.log.filter.ItemFilter
org.l2jmobius.gameserver.item.pattern = logs/item.log
org.l2jmobius.gameserver.item.limit = 104857600
org.l2jmobius.gameserver.item.count = 20
```

---

## Tools Module Detailed Analysis

### AccountManager.java

**Location**: `java/org/l2jmobius/tools/AccountManager.java`

- **Purpose**: Manage user accounts (create, delete, update, list)
- **Modes**: GUI (Swing) or Console
- **Password**: MD5 hashed via `MessageDigest.getInstance("MD5")`
- **Key methods**:
  - `main(String[])` - entry point
  - `createAccountCmd(Scanner)` - console account creation
  - `deleteAccountCmd(Scanner)` - console account deletion
  - `updateAccountCmd()` - account updates
  - `listAccountsCmd()` - list all accounts

**Gotchas**:
- MD5 is weak (consider bcrypt/PBKDF2)
- GUI and console tightly coupled

### DatabaseInstaller.java

**Location**: `java/org/l2jmobius/tools/DatabaseInstaller.java`

- **Purpose**: Install/refresh database schema
- **Features**: Connection testing, schema installation, database dump
- **SQL Scripts**: Located in `sql/login/` and `sql/game/` directories
- **Key methods**:
  - `getDatabaseConnection()` - establishes JDBC connection
  - `installDatabase()` - runs schema SQL files
  - `dumpDatabase()` - generates SQL dump via `DatabaseMetaData`

**Gotchas**:
- Uses `DriverManager` instead of `DatabaseFactory` (no connection pooling)

### GameServerRegister.java

**Location**: `java/org/l2jmobius/tools/GameServerRegister.java`

- **Purpose**: Register/unregister game servers with LoginServer
- **Database**: Writes to `gameserver` table
- **Key methods**:
  - `registerServerConsole()` - console registration
  - `removeServer(int serverId)` - unregister server

### Search.java

**Location**: `java/org/l2jmobius/tools/Search.java`

- **Purpose**: Recursive file content search
- **Features**: Regex patterns, extension filtering, ignore lists
- **Key methods**:
  - `searchFiles(...)` - main search method
  - Uses `Files.walkFileTree()` for traversal

**Gotchas**:
- Loads entire files into memory (`Files.readString`) - can cause OOM on large files
