#!/bin/bash
# Epic Night Session - 15 AI Players (5 existing + 10 new)

echo "
╔═══════════════════════════════════════════════════════════════════╗
║         🌙 EPIC NIGHT SESSION - 15 AI PLAYERS STARTING! 🌙         ║
╚═══════════════════════════════════════════════════════════════════╝

This will run ALL 15 AI players for the entire night.
They will:
• Gain levels through combat
• Complete quests
• Trade in the marketplace  
• Chat and socialize
• Explore the world
• Farm resources

Tomorrow morning, check the server logs and run:
java -cp target/classes:lib/* com.aiplayer.examples.NightlyProgressReport

═════════════════════════════════════════════════════════════════════
"

cd /home/volodro/AIPlayerEngine

# Compile the project
echo "🔧 Compiling project..."
mvn compile -q

# Run all 15 AI players
echo "🚀 Starting all 15 AI players..."

# Start original 5 players
java -cp target/classes:$(mvn dependency:build-classpath -q -DincludeScope=runtime -Dmdep.outputFile=/dev/stdout 2>/dev/null) com.aiplayer.examples.FivePlayerMagicShow &

# Start new 10 players
java -cp target/classes:$(mvn dependency:build-classpath -q -DincludeScope=runtime -Dmdep.outputFile=/dev/stdout 2>/dev/null) com.aiplayer.examples.TenMorePlayersDemo &

echo ""
echo "✅ All 15 AI players are now playing!"
echo "   They will play all night long!"
echo "   Check server logs in the morning for their progress!"
echo ""
