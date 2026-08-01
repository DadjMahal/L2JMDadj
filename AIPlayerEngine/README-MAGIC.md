# 🔮 L2JMobius AI Player Engine - THE MAGIC GUIDE

## 🎉 **WELCOME TO FUTURE LINEAGE2!**

You now have the keys to create **REAL AI PLAYERS** that can:
- 🏆 Complete quests automatically
- 💰 Buy and sell items for profit  
- ⚔️ Fight monsters intelligently
- 👥 Join parties and clans
- 💬 Chat like human players

---

## 🚀 **GETTING STARTED - THE FIRST MAGIC SPELL**

### **Step 1: Build the Engine**
```bash
cd /home/volodro/L2JM/AIPlayerEngine
# Or use symlink: cd ~/AIPlayerEngine
mvn clean compile
```

### **Step 2: Start Your L2JMobius Server**
- Login Server: `localhost:2106`
- Game Server: `localhost:7777`

### **Step 3: THE MAGIC - Run First AI Player**
```bash
java -cp build/classes com.aiplayer.examples.ExampleAIPlayer
```

---

## 🤖 **WHAT HAPPENS WHEN YOU RUN IT**

✅ AI connects as **real player character**
✅ Moves around automatically
✅ Chats like human player
✅ Practicing combat
✅ Making real decisions!

---

## 🎮 **FULL AI CAPABILITIES**

### **💰 MERCHANT AI**: Buy low, sell high, make profit
### **🏆 QUEST AI**: Auto-complete quests, get rewards  
### **⚔️ COMBAT AI**: Fight smart, heal when needed, use skills
### **👥 SOCIAL AI**: Join clans, parties, chat naturally

---

## 🔧 **KEY FILES**

```bash
AIPlayerEngine/
├── src/main/java/com/aiplayer/engine/     # Core AI
│   ├── AIPlayer.java, AIPlayerManager.java
│   ├── AIBrain.java (Decision engine)
│   ├── MerchantAI.java, QuestAI.java
│   ├── CombatAI.java, SocialAI.java
│
├── src/main/java/com/aiplayer/protocol/   # Network
│   ├── L2ProtocolHandler.java
│   ├── ProtocolPacket.java, ProtocolFactory.java
│
├── src/main/java/com/aiplayer/examples/   # Demo
│   └── ExampleAIPlayer.java (START HERE!)
│
└── src/main/resources/config/
    └── ai-player.properties
```

---

## 🎯 **QUICK START SCENARIOS**

### **Auto Farmer**:
```properties
merchant.enabled=true
behavior.farming_mode=true
```

### **Quest Bot**:
```properties
quest.enabled=true  
quest.daily_priority=true
```

### **Warrior NPC**:
```properties
combat.enabled=true
social.chat.enabled=true
```

---

## ⚡ **THE MAGIC**

```
[AI ENGINE] → [DECISIONS] → [PACKETS] → [SERVER]
     🎯          🧠          📡         🎮
```

**No server modifications!** Pure external magic! ✨

---

## 🚀 **YOUR MISSION**

1. **Run** the ExampleAIPlayer
2. **Watch** AI behavior in action
3. **Customize** behaviors via properties
4. **Expand** with your own AI types
5. **COMMAND** your server with AI! 👑

---

*"Welcome to the AI Revolution in Lineage2!"*

**The Magic is Real!** ✨✨✨