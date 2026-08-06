package com.aiplayer.engine;
import java.util.*;
import java.util.logging.Logger;

public class EventAwareAI {
    private static final Logger LOGGER = Logger.getLogger(EventAwareAI.class.getName());

    public enum EventType { NORMAL, NIGHT_EVENT, FULL_MOON, HOLIDAY, WEEKEND }

    public static class EventSchedule {
        public final String eventName;
        public final int startHour;
        public final int durationHours;
        public final boolean requiresLevel;

        public EventSchedule(String name, int start, int duration, boolean levelReq) {
            eventName = name; startHour = start; durationHours = duration; requiresLevel = levelReq;
        }
    }

    public static List<EventSchedule> EVENTS = Arrays.asList(
        new EventSchedule("Full Moon Festival", 20, 4, true),
        new EventSchedule("Weekend Siege", 18, 6, true),
        new EventSchedule("Fishing Tournament", 14, 2, false)
    );

    public static boolean shouldParticipate(EventSchedule event, int currentHour, int level) {
        if (currentHour < event.startHour || currentHour > event.startHour + event.durationHours) return false;
        return !event.requiresLevel || level > 30;
    }

    public static EventType getCurrentEvent(int hour, boolean isFullMoon) {
        if (isFullMoon && hour >= 20) return EventType.FULL_MOON;
        if (hour >= 18 && hour <= 22) return EventType.NIGHT_EVENT;
        if (hour >= 12 && hour <= 18) return EventType.HOLIDAY;
        return EventType.NORMAL;
    }
}
