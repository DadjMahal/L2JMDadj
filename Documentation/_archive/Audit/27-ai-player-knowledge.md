# AI Player Knowledge Base Audit (Iteration 27)

## Purpose
Compilation of observable state, possible actions, packet sequences, and world‑query APIs for AI players.

## Status
Completed – Analysis of existing systems completed. Foundation exists for AI player implementation via AutoPlay/OfflinePlay systems, but true AI players requiring complex behaviors (quests, trading, crafting, social interaction) would require extension.

## Key Findings

### Existing Systems Relevant to AI Players

1. **AutoPlay System** (`AutoPlayTaskManager.java`)
   - Enables automated combat and basic gameplay for online players
   - Features:
     - Target selection (monsters, players, NPCs) with configurable ranges
     - Automatic skill usage based on class type (melee vs mage)
     - Auto-pickup of items (configurable)
     - Auto-potion usage
     - Assist party leader functionality
     - Respectful hunting (avoiding kill-stealing)
     - Target cycling between different types
   - Activated via `.play` voiced command
   - Configurable through `AutoPlay.ini`

2. **Offline Play System** (`OfflinePlay.java`)
   - Allows players to continue auto-playing after logging out
   - Stores player state for persistence
   - Requires AutoPlay to be active before logging out
   - Configurable through `OfflinePlay.ini`

3. **Fake Player System** (`FakePlayerData.java`, `FakePlayerHolder.java`)
   - **NOT** suitable for true AI players
   - Creates NPCs disguised as players with:
     - Static appearance/equipment
     - Limited chat response capabilities
     - No actual gameplay functionality (no movement, combat, questing, etc.)
   - Primarily for ambient population/chat simulation

### Current Limitations for AI Players
The existing systems provide excellent foundations for:
- **Combat automation** (AutoPlay)
- **Persistence** (OfflinePlay)
- **Basic item management** (auto-pickup, auto-potions, auto-equip)

However, they lack capabilities for:
- **Quest progression** (no dialogue acceptance, objective tracking)
- **Economic activities** (no buying/selling, crafting, trading)
- **Social interaction** (no clan management, party formation beyond assist)
- **Complex decision making** (no pathfinding to specific NPCs, no strategy)
- **Skill progression** (no automatic skill learning, no stat allocation)

### Recommended Approach for True AI Players
To create AI players that behave like real humans:

1. **Extend AutoPlay System** with:
   - Quest acceptance/completion handlers
   - NPC interaction systems (buying/selling, skill learning)
   - Pathfinding to specific locations (towns, dungeons, hunting zones)
   - Economic decision making (when to buy/sell, what to craft)
   - Social systems (clan joining, party formation, chat responses)

2. **Create AI Player Manager** that:
   - Spawns and manages AI player characters
   - Assigns behavior patterns (farmer, fighter, crafter, trader, etc.)
   - Tracks progress and goals for each AI
   - Handles life cycle (creation, development, retirement)

3. **Leverage Existing Infrastructure**:
   - Use existing AI framework (PlayerAI, PlayableAI, CreatureAI)
   - Utilize GeoEngine for pathfinding and movement
   - Leverage packet system for all client-server communication
   - Use existing skill, item, and quest systems

### Files Examined During Audit
- Core AI: `PlayerAI.java`, `PlayableAI.java`, `CreatureAI.java`, `AbstractAI.java`
- AutoPlay: `AutoPlayTaskManager.java`, `AutoPlaySettingsHolder.java`, `AutoPlayConfig.java`
- Offline Play: `OfflinePlay.java`, `OfflinePlayConfig.java`
- Fake Players: `FakePlayerData.java`, `FakePlayerHolder.java`, `FakePlayerChatManager.java`
- Player Class: `Player.java` (observed auto-play integration points)
- Voiced Commands: `AutoPlay.java`, `OfflinePlay.java`
- Configuration: `AutoPlay.ini`, `OfflinePlay.ini`, `FakePlayers.ini`

### Recommendations for Implementation
1. Start by extending the AutoPlay system with quest awareness
2. Implement a simple "quest bot" that can accept, complete, and turn in basic quests
3. Gradually add economic capabilities (buying/selling basic supplies)
4. Implement social behaviors (joining novice clans, requesting help)
5. Create AI "personalities" with different goals and playstyles
6. Ensure proper rate limiting to prevent server overload

## Notes (Resume Checkpoint)
- Read files: All core AI, AutoPlay, OfflinePlay, FakePlayer systems examined
- Quest system: Quest.java, QuestState.java, AbstractSagaQuest.java examined
- **ECONOMIC SYSTEMS ADDED**: Trading, Merchant, Auction, Offline Trading systems fully documented
- **SOCIAL SYSTEMS ADDED**: Clan and Party systems fully documented
- Trading validation logic, tax systems, restock mechanisms documented
- Party distribution methods and loot rules documented
- Clan hall auction/bidding protocols documented
- Next step: Design AI player architecture that extends existing systems while maintaining server performance

## Files Modified
Updated this document (27-ai-player-knowledge.md) with comprehensive findings.

## Economic System Details

### Trading System

**Core Classes:**
- `TradeRequest.java`: Client packet for initiating trade between players
  - Validates: distance (150 units), karma state, jail status, store mode, existing transactions
  - Implements: `player.onTransactionRequest(partner)`, sends `SendTradeRequest` packet
  - Restrictions: Olympiad mode, access levels, blocked players, trade refusal mode

- `TradeStart.java`, `TradeDone.java`, `TradeUpdate.java`: Server packets for trade UI
- `AddTradeItem.java`: Client packet for adding items to trade
- `TradeItem.java`, `TradeList.java`: Data holders for trade items and lists
- `AnswerTradeRequest.java`: Client packet for accepting/declining trade

**Trade Process Flow:**
1. Player sends TradeRequest with target ObjectId
2. Server validates conditions (range, karma, jail, store mode, existing trades)
3. Target receives SendTradeRequest packet
4. Both players must confirm with TradeDone
5. Items/items added via AddTradeItem
6. Trade confirmed, items exchanged atomically

### Merchant System (Buy Lists)

**Core Classes:**
- `Merchant.java`: Extends Folk, handles buy list NPCs
  - `showBuyWindow()`: Displays buy list to player
  - Tax calculation: Uses MerchantPriceConfigTable for dynamic tax rates
  - Restock support: Limited stock items restock based on timers
  - Validation: Checks NPC allowed for specific buy lists

- `BuyListHolder.java`: Container for products in a buy list
  - Holds: ListId, Products (item -> price map), Allowed NPCs
  - Products stored in LinkedHashMap for ordered display

- `Product.java`: Represents a single item in a buy list
  - Properties: itemId, price, restockDelay, maxCount
  - Inventory integration: AtomicInteger for stock count
  - Auto-restock: Uses BuyListTaskManager to schedule restocks
  - Persistent storage: Saves to `buylists` table on count changes
  - SQL schema: `INSERT INTO buylists (buylist_id, item_id, count, next_restock_time)`

- `MerchantPriceConfigTable.java`: Dynamic tax management
  - Configuration: XML-based (MerchantPriceConfig.xml)
  - Tax rates: baseTax + castle tax (if owns castle)
  - Zone-based: Different tax rates for different zone types
  - Castle ownership: Castle owners get additional tax revenue

### Auction Systems

**Clan Hall Auction (Auctioneer.java):**
- `ClanHallAuction.java`: Manages bidder information and auction state
  - Bidders: Map<clanId, Bidder> with bid amounts
  - Starting bid, end date tracking
  - Winner determination and payment processing

- `ClanHallAuctionManager.java`: Centralized auction management
  - Listens for: ClanHallAuctionEvent
  - Handles: Bid placement, auction expiration, winner notification
  - Creates: ClanHallAuction instances per clan hall

**Regular Auction (from Auction table):**
- SQL schema: `auction` table with item details, seller info, bid amounts, end dates
- Item types: Armor, Weapons, Consumables, etc.
- Current bid starts at startingBid (minimum)

**Database Tables:**
- `auction`: Active auctions, current bids
- `auction_bid`: Bid history
- `auction_watch`: Watched auctions
- `buylists`: Merchant product stock levels
- `character_quests`: Quest state with variables

## Social System Details

### Clan System (Clan.java)

**Core Features:**
- Clan hierarchy: Leader, officers, members, academy
- Sub-units: Royal Guard, Knights, Academy
- Privileges system: RankPrivs for clan-specific permissions
- Skills: Clan-wide skill bonuses (crest, level-based)
- Warehouse: Clan-wide bank storage (ClanWarehouse)
- Castle/Hall ownership: Tax generation and prestige
- Reputation score: Clan standing in game

**Classes:**
- `Clan.java`: Main clan container with members, skills, warehouses
- `ClanMember.java`: Individual member data (level, class, privileges)
- `ClanTable.java`: Database operations for clan persistence
- `RankPrivs.java`: Permission settings per rank
- `SubPledge.java`: Sub-leader configurations (Royal Guard, etc.)
- `ClanAccess.java`: Access level enumeration

**Database Schema:**
- `clan_data`: Clan information (name, level, leader, crests)
- `clan_members`: Member list with titles
- `clan_privs`: Privilege settings per rank
- `clan_skills`: Clan skill assignments
- `clan_warehouse`: Clan warehouse item storage

### Party System (Party.java)

**Core Features:**
- Max 8 members per party
- Distribution Types:
  - `PartyDistributionType.BALANCE` (0): Equal split
  - `PartyDistributionType.FOLLOWER` (1): Follower gets more
  - `PartyDistributionType.LEADER` (2): Leader gets all
  - `PartyDistributionType.RANDOM` (3): Random distribution
  - `PartyDistributionType.LOAD_BALANCING` (4): Based on load

- Party Experience Bonus:
  - 1 member: 1.0x
  - 2 members: 1.1x
  - 4 members: 1.2x-1.5x (based on level)
  - 8 members: 2.0x-2.2x

**Party Commands:**
- Invitation system with timeout
- Position broadcast for party members
- Item distribution (loot sharing)
- Command Channel integration (larger groups)

**Classes:**
- `Party.java`: Main party container
- `PartyDistributionType.java`: Distribution type enum
- `AbstractPlayerGroup.java`: Base class for Party and CommandChannel
- `PartyMatchRoom.java`: Party finder system
- Party Packets: PartyMemberPosition, PartySmallWindow*, etc.

**Party Invite Process:**
1. Player requests invitation
2. Target receives PartySmallWindow invitation
3. Accept/Decline triggers response
4. On accept: Player added to party, all members receive updated party info

### Command Channel (Party-CommandChannel extension)

**Features:**
- Groups multiple parties together
- Unified command interface
- Castle siege coordination
- Area-wide chat and commands

## Integration Points for AI Players

### Economic AI Opportunities

1. **Trading Agent:**
   - Target merchants with buy lists
   - Calculate profit margins (sell vs buy prices)
   - Automatic item stocking/restocking
   - Market timing (when to buy low, sell high)

2. **Crafting AI:**
   - Identify craftable items from inventory
   - Check component availability
   - Calculate profit from crafted items
   - Execute crafting sequence

3. **Auction/Trade AI:**
   - Monitor market prices
   - Bid on undervalued items
   - Sell items at optimal prices
   - Participate in clan hall auctions

### Social AI Opportunities

1. **Clan AI:**
   - Automatic clan joining (novice clans)
   - Clan hall auction participation
   - Territory management
   - Clan chat responses

2. **Party AI:**
   - Automatic party formation
   - Role-based party joining (tank, healer, DPS)
   - Party loot distribution preferences
   - Command channel coordination

## Recommendations for Implementation

1. **Extend AutoPlay with Economic Logic:**
   - Add MerchantInteractionTask
   - Implement PriceCalculationEngine
   - Add RestockScheduler
   - Create TradeOpportunityDetector

2. **Add Social Intelligence:**
   - PartyFormationManager
   - ClanJoinDecisionEngine
   - SocialInteractionHandler
   - ReputationTracker

3. **Data Integration:**
   - Feed TradeItem/TradeList data to AI
   - Use BuyListHolder for shopping decisions
   - Access Clan/Party data for group strategies
   - Monitor auction tables for opportunities

4. **Performance Considerations:**
   - Cache tax rates and prices
   - Batch trade operations
   - Rate-limit social interactions
   - Prioritize high-value actions

## Files Examined During Audit
- Core AI: `PlayerAI.java`, `PlayableAI.java`, `CreatureAI.java`, `AbstractAI.java`
- AutoPlay: `AutoPlayTaskManager.java`, `AutoPlaySettingsHolder.java`, `AutoPlayConfig.java`
- Offline Play: `OfflinePlay.java`, `OfflinePlayConfig.java`
- Fake Players: `FakePlayerData.java`, `FakePlayerHolder.java`, `FakePlayerChatManager.java`
- Player Class: `Player.java` (observed auto-play integration points)
- Voiced Commands: `AutoPlay.java`, `OfflinePlay.java`
- Configuration: `AutoPlay.ini`, `OfflinePlay.ini`, `FakePlayers.ini`
- Trading: `Trade*.java`, `SendTradeRequest.java`, `AnswerTradeRequest.java`, `AddTradeItem.java`
- Merchant: `Merchant.java`, `BuyListHolder.java`, `Product.java`, `MerchantPriceConfigTable.java`
- Auction: `Auctioneer.java`, `ClanHallAuction.java`, `ClanHallAuctionManager.java`
- Clan: `Clan.java`, `ClanMember.java`, `ClanTable.java`, `RankPrivs.java`, `SubPledge.java`
- Party: `Party.java`, `PartyDistributionType.java`, `PartyMatchRoom.java`
- Quest: `Quest.java`, `QuestState.java`, `AbstractSagaQuest.java`
- Database: `character_quests.sql`, `auction.sql`, `buylists.sql`

## Related Audit References
- See 16-ai.md (AI controllers) for core AI architecture
- See 24-scripts-ai-vehicles-events.md for area-specific AI behaviors
- See 20-ai-player-knowledge.md (initial audit) for baseline observable state concepts
- See 30-quest-progression.md for detailed quest system analysis