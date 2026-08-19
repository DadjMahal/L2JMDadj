package com.aiplayer.behavior;
import java.util.Arrays;
import java.util.logging.Logger;

public class EventCalendarAI {
    private static final Logger LOGGER = Logger.getLogger(EventCalendarAI.class.getName());

    public static class CalendarEvent {
        public final String name;
        public final int day;
        public final int priority;

        public CalendarEvent(String n, int d, int p) { name = n; day = d; priority = p; }
    }

    public static CalendarEvent[] EVENTS = {
        new CalendarEvent("Weekly Raid Reset", 7, 10),
        new CalendarEvent("Boss Spawn Window", 14, 8),
        new CalendarEvent("Siege Day", 1, 10),
        new CalendarEvent("Olympiad", 15, 9)
    };

    public static boolean shouldParticipate(CalendarEvent event, int day) {
        return event.day == day;
    }

    public static CalendarEvent getHighestPriorityEvent(int day) {
        Arrays.sort(EVENTS, (a,b) -> Integer.compare(b.priority, a.priority));
        return EVENTS[0];
    }
}
