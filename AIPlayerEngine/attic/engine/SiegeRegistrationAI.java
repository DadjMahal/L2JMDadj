// package com.aiplayer.engine;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * Task 133: Siege registration AI - register clan for scheduled siege
 */
public class SiegeRegistrationAI {
    private static final Logger LOGGER = Logger.getLogger(SiegeRegistrationAI.class.getName());
    private final AtomicLong totalRegistrations = new AtomicLong(0);
    private final Map<String, SiegeInfo> registrations = new HashMap<>();

    public enum SiegeStatus { NOT_REGISTERED, REGISTERED, IN_PROGRESS, COMPLETED, FAILED }

    public static class SiegeInfo {
        public final String castleName;
        public final int castleId;
        public final String clanName;
        public final long registrationTime;
        public SiegeStatus status;
        public String confirmationCode;

        public SiegeInfo(String castleName, int castleId, String clanName) {
            this.castleName = castleName;
            this.castleId = castleId;
            this.clanName = clanName;
            this.registrationTime = System.currentTimeMillis();
            this.status = SiegeStatus.REGISTERED;
            this.confirmationCode = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        }
    }

    public static class CastleInfo {
        public final String name;
        public final int id;
        public SiegeStatus siegeStatus;

        public CastleInfo(String name, int id) {
            this.name = name; this.id = id;
            this.siegeStatus = SiegeStatus.NOT_REGISTERED;
        }
    }

    public boolean shouldRegister(String clanName, CastleInfo castle, int castleLevel, long scheduledTime) {
        if (castle.siegeStatus != SiegeStatus.NOT_REGISTERED) return false;
        if (System.currentTimeMillis() > scheduledTime - 3600000) return false;
        if (castleLevel < 1) return false;
        return true;
    }

    public SiegeInfo registerForSiege(String clanName, String castleName, int castleId) {
        SiegeInfo info = new SiegeInfo(castleName, castleId, clanName);
        registrations.put(castleName, info);
        totalRegistrations.incrementAndGet();
        LOGGER.info("SiegeRegistrationAI: " + clanName + " registered for " + castleName);
        return info;
    }

    public boolean confirmRegistration(String castleName, String code) {
        SiegeInfo info = registrations.get(castleName);
        if (info != null && info.confirmationCode.equals(code)) {
            info.status = SiegeStatus.IN_PROGRESS;
            LOGGER.info("SiegeRegistrationAI: Siege of " + castleName + " confirmed");
            return true;
        }
        return false;
    }

    public long getTotalRegistrations() { return totalRegistrations.get(); }
    public Collection<SiegeInfo> getActiveRegistrations() { return registrations.values(); }
}
