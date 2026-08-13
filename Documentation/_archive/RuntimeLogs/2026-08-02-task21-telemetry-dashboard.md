# 2026-08-02-task21-telemetry-dashboard.md

**Agent:** System  
**Task:** 21 - Create telemetry dashboard/summary script

---

## Objective

Create a shell script that aggregates AI player telemetry metrics in a single view.

---

## Deliverable

File: `/home/volodro/L2JM/scripts/telemetry_dashboard.sh`

---

## Dashboard Sections

1. **Database Metrics** - AI player counts by type (Combat/Quest/Merchant/Social)
2. **Server Packet Telemetry** - Counts of PROTOCOL, MOVED, ATTACKING, CHAT events
3. **AI Status Logs Summary** - Lists log files in AIStatusLogs/
4. **Packet Logger Telemetry** - Documents PacketLogger.java tracking
5. **Build Status** - Verifies Java compilation

---

## Verification

```bash
$ bash scripts/telemetry_dashboard.sh
================================================================
  🤖 AI PLAYER TELEMETRY DASHBOARD
...
  TELEMETRY DASHBOARD COMPLETE
================================================================
BUILD SUCCESS (Maven compile)
```

---

## Files Modified

| File | Action |
|------|--------|
| `scripts/telemetry_dashboard.sh` | CREATED |

---

## Related Tasks

- Task 19: PacketLogger.java (provides counters used by dashboard)
- Task 20: Telemetry hooks in AI modules (COMBAT/QUEST/MERCHANT/SOCIAL)
- Task 28: `scripts/verify_telemetry.sh` (future validation script)