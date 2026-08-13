# Known Bugs and Issues - L2JMobius Interlude
## Audit Iteration 29

## Purpose
Documentation of known bugs, issues, and potential problems in the L2JMobius Interlude server codebase.

## Status
Completed - Analysis of source code for TODO/FIXME comments and potential issues.

## Important Note
This document contains issues identified through source code analysis (TODO/FIXME comments, potential logical issues). 
It does **not** include:
- Bugs reported by users on forums or external trackers (requires web scraping/external access)
- Issues requiring runtime observation or testing
- Security vulnerabilities (requires specialized analysis)
- Performance issues (requires benchmarking)

All findings are strictly from static analysis of the current L2JMobius Interlude source code.

## Findings from Source Code Analysis

### 1. Quest System Issues (TODO/FIXME Comments)

#### Q00336_CoinsOfMagic.java
- **File**: `dist/game/data/scripts/quests/Q00336_CoinsOfMagic/Q00336_CoinsOfMagic.java`
- **Issue**: `// TODO: getBaseHpConsumeRate was 0.` (line approximately where SHACKLE constant is used)
- **Description**: Indicates unfinished implementation where HP consumption rate calculation may be incorrect or placeholder.

#### Saga Quest Lines (Multiple Files)
- **Files**: Various `Q000xx_SagaOfThe*.java` quest files
- **Issue**: Contains text like `"PLAYERNAME. Defeat...by...retaining...and...Mo...Hacker"` 
- **Description**: Appears to be placeholder or corrupted text that should be properly formatted player names or quest dialogue.

### 2. Potential Logical Issues

#### AttackableAI.java Aggression Logic
- **File**: `java/org/l2jmobius/gameserver/ai/AttackableAI.java`
- **Analysis**: In `thinkActive()` method, the aggression check has a potential edge case:
  - When `_globalAggro` is exactly 0, aggressive monsters will not scan for targets
  - The decay mechanism (`_globalAggro++` when negative, `--` when positive) means it oscillates around 0
  - **Potential Impact**: Aggressive monsters might have delayed or periodic target acquisition instead of continuous scanning

#### GeoEngine Path Validation
- **File**: `java/org/l2jmobius/gameserver/geoengine/GeoEngine.java`
- **Analysis**: The `canSeeTarget` and `canMoveToTarget` methods use complex line-of-sight calculations
- **Observation**: No obvious TODOs found, but these are complex systems where edge cases might exist
- **Recommendation**: These should be tested with various terrain configurations

#### Fake Player System Limitations
- **Files**: Multiple in `java/org/l2jmobius/gameserver/` related to FakePlayer*
- **Analysis**: The fake player system (used for ambient population) has significant limitations:
  - Cannot perform actual gameplay (no questing, combat beyond basic aggro, trading, etc.)
  - Limited to chat responses and basic movement
  - **Note**: This is by design, not a bug, but important for AI player implementation context

### 3. Configuration-Related Observations

#### AutoPlay Configuration
- **File**: `dist/game/config/Custom/AutoPlay.ini`
- **Observation**: `EnableAutoPlay = False` by default
- **Note**: This is a configuration choice, not a bug, but affects the baseline player experience

#### Offline Play Configuration
- **File**: `dist/game/config/Custom/OfflinePlay.ini`  
- **Observation**: `EnableOfflinePlayCommand = False` by default
- **Note**: Configuration choice

### 4. Areas Requiring Further Investigation

Based on code review, these areas may benefit from additional scrutiny:
-closed-source analysis:

1. **Quest Item Handling**: Verify proper cleanup and reward distribution in complex quest chains
2. **Clan Warfare Systems**: Check for race conditions in siege/conquerable hall systems
3. **Pet/Summon AI**: Review interaction with player AI and command processing
4. **Event Systems**: Validate timing and cleanup of seasonal/time-based events
5. **Economy Systems**: Check item drop rates, adena circulation, and market stability mechanisms

## Recommended Actions

1. **Address TODO Comments**: 
   - Resolve the `getBaseHpConsumeRate` TODO in Q00336_CoinsOfMagic
   - Review and correct placeholder text in saga quest lines

2. **Testing Focus Areas**:
   - Aggression system with various _globalAggro values
   - GeoEngine pathfinding in complex terrain
   - Quest completion edge cases (item limits, level requirements, etc.)

3. **Monitoring Suggestions**:
   - Implement logging for AI decision points in AttackableAI
   - Track quest completion rates for potentially problematic quest lines
   - Monitor GeoEngine performance in high-player-density areas

## Files Examined During Audit
- Core AI: `AttackableAI.java`, `CreatureAI.java`, `PlayerAI.java`
- Quest Systems: Multiple saga quest files, Q00336_CoinsOfMagic, Q00265_BondsOfSlavery
- GeoEngine: `GeoEngine.java` (movement and LOS methods)
- Configuration: `AutoPlay.ini`, `OfflinePlay.ini`
- Fake Player System: Related data and manager classes

## Next Steps for Audit
As requested, proceeding to audit quest progression systems in detail.

---
*Document generated as part of L2JMobius source code audit. 
All findings based on static analysis of source code in /home/volodro/L2JM/SourceCode/*