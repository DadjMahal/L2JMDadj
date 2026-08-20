package com.aiplayer.knowledge;

/**
 * MODE: COMPLETE. New guide-map foundation for AI players (source-grounded).
 *
 * <p>Player races of this server (from {@code ServerBuild/game/data/zones/custom_town.xml} and
 * {@code stats/players/classList.xml}; Interlude = Human, Elf, Dark Elf, Orc, Dwarf).
 *
 * <p>Every coordinate in this enum is the CENTROID of the corresponding town zone polygon
 * computed from the datapack's own {@code custom_town.xml}, i.e. a real, reachable in-world
 * point for AI navigation/idling — never a void coordinate.
 */
public enum PlayerRace
{
    HUMAN("Talking Island", "TalkingIsland", -84327, 242833, -3750, "Grand Master Roien", 30008, -84058, 243239, -3730),
    ELF("Elven Village", "ElvenTerritory", 45365, 50045, -3175, "Newbie Helper", 30370, 45491, 48359, -3086),
    DARK_ELF("Dark Elven Village", "DarkElfTerritory", 11247, 17086, -4150, "Newbie Helper", 30129, 12116, 16666, -4610),
    ORC("Orc Village", "OrcTerritory", -45586, -113739, -300, "Newbie Helper", 30573, -45067, -113549, -235),
    DWARF("Dwarven Village", "DwarvenTerritory", 116440, -180511, -1200, "Newbie Helper", 30528, 115642, -178046, -941);

    private final String _startTown;
    private final String _startZone;
    private final int _townX;
    private final int _townY;
    private final int _townZ;
    private final String _helperNpcName;
    private final int _helperNpcId;
    private final int _helperX;
    private final int _helperY;
    private final int _helperZ;

    PlayerRace(String startTown, String startZone, int townX, int townY, int townZ, String helperNpcName, int helperNpcId, int helperX, int helperY, int helperZ)
    {
        _startTown = startTown;
        _startZone = startZone;
        _townX = townX;
        _townY = townY;
        _townZ = townZ;
        _helperNpcName = helperNpcName;
        _helperNpcId = helperNpcId;
        _helperX = helperX;
        _helperY = helperY;
        _helperZ = helperZ;
    }

    public String startTown() { return _startTown; }
    public String startZone() { return _startZone; }
    public int townX() { return _townX; }
    public int townY() { return _townY; }
    public int townZ() { return _townZ; }
    public String helperNpcName() { return _helperNpcName; }
    public int helperNpcId() { return _helperNpcId; }
    public int helperX() { return _helperX; }
    public int helperY() { return _helperY; }
    public int helperZ() { return _helperZ; }

    /** Resolve race from a server PlayerClass classId (0-57 base/1st/2nd, 88-118 3rd). */
    public static PlayerRace ofClassId(int classId)
    {
        // Base + 1st + 2nd profession ids (see classList.xml)
        if (classId <= 17) return PlayerRace.HUMAN;      // 0-17
        if (classId <= 30) return PlayerRace.ELF;        // 18-30
        if (classId <= 43) return PlayerRace.DARK_ELF;   // 31-43
        if (classId <= 52) return PlayerRace.ORC;        // 44-52
        if (classId <= 57) return PlayerRace.DWARF;      // 53-57
        // 3rd profession ids
        if (classId <= 98) return PlayerRace.HUMAN;      // 88-98
        if (classId <= 105) return PlayerRace.ELF;       // 99-105
        if (classId <= 112) return PlayerRace.DARK_ELF;  // 106-112
        if (classId <= 116) return PlayerRace.ORC;       // 113-116
        return PlayerRace.DWARF;                         // 117-118
    }
}
