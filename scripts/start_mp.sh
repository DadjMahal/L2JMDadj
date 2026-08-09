#!/bin/bash
# Launch the MultiPlayerSession long-run. Usage: start_mp.sh <runMinutes> <sliceSeconds> <logfile>
cd /home/volodro/L2JM/AIPlayerEngine
exec java -Xmx384m -cp target/classes com.aiplayer.examples.MultiPlayerSession "$1" "$2" >> "$3" 2>&1