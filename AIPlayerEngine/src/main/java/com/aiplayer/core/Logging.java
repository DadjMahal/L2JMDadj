package com.aiplayer.core;

import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.StreamHandler;

/**
 * EB-12 — the engine's single logging convention: {@code java.util.logging} everywhere.
 *
 * <p>Every class should obtain its logger through {@link #getLogger(Class)} so the whole engine
 * shares one naming scheme ({@code com.aiplayer.<package>.<Class>}) and one simple console format.
 *
 * <p>Deliberate exemption (documented in {@code scripts/check_style.sh}): line-oriented grep-able
 * PROOF MARKERS ({@code [MP]}, {@code [EVIDENCE-H5]}, {@code [FleetPlay]}...) stay on
 * {@code System.out} because live scripts (watchdog/analyze/proofs) consume raw stdout. Everything
 * else must use a JUL logger.
 */
public final class Logging
{
    private Logging()
    {
    }

    /**
     * JUL logger named after {@code clazz} and configured with the engine's compact console
     * format (timestamp + level + source + message — no noisy logger-class prefix per line).
     */
    public static Logger getLogger(Class<?> clazz)
    {
        Logger logger = Logger.getLogger(
            clazz != null ? clazz.getName() : "com.aiplayer.core.Logging");
        if (logger.getHandlers().length == 0)
        {
            StreamHandler handler = new StreamHandler(System.out, new CompactFormatter())
            {
                @Override
                public synchronized void publish(LogRecord record)
                {
                    super.publish(record);
                    flush();
                }
            };
            handler.setLevel(Level.ALL);
            logger.addHandler(handler);
            logger.setUseParentHandlers(false);
            logger.setLevel(Level.INFO);
        }
        return logger;
    }

    /** Compact single-line format: {@code 2026-08-22 12:00:00.123 INFO [Class] message}. */
    static final class CompactFormatter extends Formatter
    {
        @Override
        public String format(LogRecord record)
        {
            return String.format("%1$tF %1$tT.%1$tL %2$s [%3$s] %4$s%n",
                new java.util.Date(record.getMillis()),
                record.getLevel().getName(),
                sourceLabel(record),
                record.getMessage());
        }

        private static String sourceLabel(LogRecord record)
        {
            String cls = record.getSourceClassName();
            if (cls == null)
            {
                return "-";
            }
            int dot = cls.lastIndexOf('.');
            return dot >= 0 ? cls.substring(dot + 1) : cls;
        }
    }
}