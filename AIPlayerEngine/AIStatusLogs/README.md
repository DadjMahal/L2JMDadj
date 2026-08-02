# 🚀 AI Player Engine - Log Analysis Suite

## 📁 What's In AIStatusLogs/

This folder contains monitoring tools that query real server state.

### 📊 Scripts Available:

1. **real_status.sh** - Queries actual server state (database + logs)
2. **check_server_status.sh** - Server health check
3. **analyze_logs.sh** - Full activity analysis
4. **count_ai_players.sh** - Player progress tracking

## 🎮 How to Use:

### Check Real AI Player Status:
```bash
./real_status.sh
```

### Check Server Status:
```bash
./check_server_status.sh
```

### Analyze AI Activity:
```bash
./analyze_logs.sh
# Output: ai_activity_report.txt
```

## 📈 Current Status (from real query):

**Server:** Running on ports 2106, 7777
**AI Players Online:** Query database for `account_name LIKE 'ai_%' AND online = 1`
**Activity:** Check server logs via grep

## ⚠️ Policy: Real Data Only

All status reports are generated from real queries:
- Database: MySQL gameserver - characters table (online field + account_name pattern)
- Logs: grep counts from ServerBuild/game/log/stdout.log
- Ports: nc checks on localhost
