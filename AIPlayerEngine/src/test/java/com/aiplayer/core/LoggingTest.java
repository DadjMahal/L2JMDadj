package com.aiplayer.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;

import com.aiplayer.core.Logging.CompactFormatter;

/**
 * EB-12 — locks the shared java.util.logging convention: every engine class gets a stable,
 * consistently-named JUL logger with the compact console format. Also proves System.out stays
 * reserved for grep-able proof markers (documented in scripts/check_style.sh).
 */
class LoggingTest
{
    @Test
    void returnsJullLoggerNamedAfterClass()
    {
        Logger logger = Logging.getLogger(LoggingTest.class);
        assertNotNull(logger);
        assertEquals(LoggingTest.class.getName(), logger.getName());
        assertEquals(Level.INFO, logger.getLevel());
        assertTrue(logger.getHandlers().length > 0, "engine logger has a console handler");
    }

    @Test
    void isReusableAcrossCalls()
    {
        assertEquals(Logging.getLogger(LoggingTest.class), Logging.getLogger(LoggingTest.class),
            "getLogger must return the same JUL logger instance (JUL caches by name)");
    }

    @Test
    void formatterIsCompactAndMarkerClean()
    {
        CompactFormatter f = new CompactFormatter();
        LogRecord r = new LogRecord(Level.INFO, "no System.out needed for this message");
        r.setSourceClassName(LoggingTest.class.getName());
        String line = f.format(r);

        assertTrue(line.startsWith("20"), "timestamp first: " + line);
        assertTrue(line.contains(" INFO "), "level present: " + line);
        assertTrue(line.contains("[LoggingTest]"), "short source label: " + line);
        assertTrue(line.contains("no System.out needed for this message"));
        assertTrue(line.endsWith("\n"));
    }

    @Test
    void sourceLabelHandlesNullMaybe()
    {
        CompactFormatter f = new CompactFormatter();
        LogRecord r = new LogRecord(Level.WARNING, "x");
        r.setSourceClassName(null);
        assertTrue(f.format(r).contains("[-]"), "unknown source -> -");
    }
}