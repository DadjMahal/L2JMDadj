package com.aiplayer.examples;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Test;

import com.aiplayer.core.BotInfo;
import com.aiplayer.core.FleetConfig;
import com.aiplayer.web.EventRing;
import com.aiplayer.web.FleetMetrics;
import com.aiplayer.web.HistoryRing;

/**
 * EP-7: locks the virtual-thread fleet — spawnFleet must put every BotSession on a
 * Thread.ofVirtual() carrier (interruptible, daemon-by-default), not a platform thread.
 * Sessions are pointed at a closed port so they enter the reconnect backoff path, which is
 * exactly what a fleet sees when the game server is down.
 */
class VirtualThreadFleetTest
{
    @Test
    void fleetSessionsRunOnVirtualThreads() throws Exception
    {
        FleetConfig cfg = FleetConfig.parse("2 127.0.0.1 7777 2106 8080".split(" "));
        ConcurrentHashMap<String, BotInfo> bots = new ConcurrentHashMap<>();
        Thread[] threads = FleetPlay.spawnFleet(cfg, bots, new EventRing(), new HistoryRing(),
            new FleetMetrics(System.currentTimeMillis()));

        assertTrue(threads.length == 2);
        assertTrue(bots.size() == 2, "every session registers its BotInfo row before spawning");
        for (Thread t : threads)
        {
            assertTrue(t.isVirtual(), "bot session must run on a virtual thread: " + t);
            assertTrue(t.isAlive(), "session thread should be alive (reconnect loop vs closed port)");
            assertTrue(t.getName().startsWith("bot-"), "thread name carries the account: " + t.getName());
        }

        for (Thread t : threads)
        {
            t.interrupt(); // reconnect backoff sleeps must observe interruption and end the loop
        }
        for (Thread t : threads)
        {
            t.join(10_000);
            assertTrue(!t.isAlive(), "session exits on interrupt: " + t.getName());
        }
    }
}
