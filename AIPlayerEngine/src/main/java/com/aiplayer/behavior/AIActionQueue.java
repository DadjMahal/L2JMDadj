package com.aiplayer.behavior;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import com.aiplayer.behavior.combat.CombatDecision.Action;

/**
 * AI Action Queue
 * Thread-safe queue for AI actions
 */
public class AIActionQueue {
    private static final Logger LOGGER = Logger.getLogger(AIActionQueue.class.getName());

    private final ConcurrentLinkedQueue<AIAction> queue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger queueSize = new AtomicInteger(0);
    private static final int MAX_QUEUE_SIZE = 100;

    /**
     * Add action to queue
     */
    public boolean add(AIAction action) {
        if (queueSize.get() >= MAX_QUEUE_SIZE) {
            LOGGER.warning("Action queue full! Dropping action: " + action);
            return false;
        }

        boolean added = queue.offer(action);
        if (added) {
            queueSize.incrementAndGet();
        }
        return added;
    }

    /**
     * Poll next action
     */
    public AIAction poll() {
        AIAction action = queue.poll();
        if (action != null) {
            queueSize.decrementAndGet();
        }
        return action;
    }

    /**
     * Check if queue is empty
     */
    public boolean isEmpty() {
        return queue.isEmpty();
    }

    /**
     * Get current queue size
     */
    public int size() {
        return queueSize.get();
    }

    /**
     * Clear the queue
     */
    public void clear() {
        queue.clear();
        queueSize.set(0);
    }
}
