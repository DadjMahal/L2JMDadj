package com.aiplayer.behavior;

/**
 * MODE: COMPLETE. EB-01 extraction — the per-session quest-dialog DRIVING decision machine that
 * used to live inline in {@code core/BotSession} (questDialogOpen / questLastHtml /
 * lastQuestClickMs / questSentLinks + the click→read→send→re-click stale ladder). Pure logic: no
 * IO, no packets, no threads. Every tick the caller feeds the CURRENT live signals (is the giver's
 * dialog on screen, did the server send a NEW NpcHtmlMessage, its bypass links, nowMs) and this
 * class states what the SESSION must next DO (open the dialog / click the giver / send this exact
 * bypass / pause / re-click a stale dialog). {@link QuestDialogDriver} stays the single authority
 * on WHICH link to send; this class owns WHEN — including the S3-T02 stale-dialog re-click rule
 * and the never-re-send set. */
import java.util.HashSet;
import java.util.Set;

import com.aiplayer.behavior.QuestDialogDriver.QuestDialog;

public final class QuestDialogSession
{
    /** One next-step decision the session loop must execute (or WAIT for the server). */
    public enum Action
    {
        /** Giver's dialog is on screen but not yet tracked as open: mark it open, nothing to send. */
        OPEN,
        /** Dialog not on screen: click the giver to open it. */
        CLICK_GIVER,
        /** Send the returned {@link #bypass} command via the normal dialog bypass path. */
        SEND_BYPASS,
        /** No action this tick: wait for the server's next dialog message. */
        WAIT
    }

    /** Immutable result of one {@link #step} call. */
    public static final class Result
    {
        public final Action action;
        /** Valid only when {@code action == SEND_BYPASS}: the exact command to send. */
        public final String bypass;
        /** True when this bypass completes the accept/turn-in (caller may reset cooldowns/log). */
        public final boolean done;

        private Result(Action action, String bypass, boolean done)
        {
            this.action = action;
            this.bypass = bypass;
            this.done = done;
        }
    }

    private final long reclickMs;
    private boolean open = false;
    private String lastHtml = null;
    private long lastClickMs = 0;
    private final Set<String> sent = new HashSet<>();

    public QuestDialogSession(long reclickMs)
    {
        this.reclickMs = reclickMs;
    }

    /**
     * S3-T02: after the dialog goes stale, re-click the giver to surface the quest's next step. */
    private boolean stale(long nowMs)
    {
        return nowMs - lastClickMs > reclickMs;
    }

    /** Is a quest dialog currently open/tracked for this session (drives the loop's quest mode)? */
    public boolean isOpen()
    {
        return open;
    }

    /**
     * One driving step. Inputs mirror exactly what the fleet loop could read from the live
     * PacketLogger: {@code giverTracked} (the NPC was seen in the world), {@code giverOnScreen}
     * (the last NpcHtmlMessage belongs to the giver), {@code html} (the latest raw dialog, or null
     * if none arrived), {@code links} (bypass links extracted from THAT html by the caller), and the
     * {@code dialogDef} built by the caller from the live config (quest name + accept/turn-in
     * objective) — the same object the original loop constructed before stepping.
     *
     * @return the next-step decision; null if the giver is not yet tracked (caller waits).
     */
    public Result step(boolean giverTracked, boolean giverOnScreen, String html, String[] links,
                       QuestDialog dialogDef, long nowMs)
    {
        if (!giverTracked)
        {
            return null;
        }

        if (!open)
        {
            if (giverOnScreen)
            {
                open = true;
                lastHtml = null;
                return new Result(Action.OPEN, null, false);
            }
            lastClickMs = nowMs;
            return new Result(Action.CLICK_GIVER, null, false);
        }

        // Dialog open: consume a NEW NpcHtmlMessage from this session, if the server sent one.
        if (html == null || html.equals(lastHtml))
        {
            if (stale(nowMs))
            {
                open = false;
                lastHtml = null;
            }
            return new Result(Action.WAIT, null, false);
        }
        lastHtml = html;
        String next = QuestDialogDriver.next(links, dialogDef, sent);
        if (next.isEmpty())
        {
            if (stale(nowMs))
            {
                open = false;
                lastHtml = null;
            }
            return new Result(Action.WAIT, null, false);
        }
        sent.add(next);
        boolean done = QuestDialogDriver.completes(dialogDef, next);
        if (done)
        {
            open = false;
            sent.clear();
            lastHtml = null;
        }
        return new Result(Action.SEND_BYPASS, next, done);
    }
}