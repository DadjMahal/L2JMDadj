#!/usr/bin/env python3
"""provision_fleet.py — generate SQL for N random-race accounts+chars (S9-T03).

Balances the 5 races (10 each over 50). i%5 -> race: 1 ELF, 2 DARK_ELF, 3 ORC, 4 DWARF, 0 HUMAN.
Spawns each char at its race's mob-dense newbie field (parsed from spawns/*Starting.xml). Sets
race+classid correctly. NOTE: in-world race fidelity also needs character_subclasses rows.
Prints SQL to stdout.
Usage: python3 provision_fleet.py [count] [prefix] [charIdBase] [pwhash]
"""
import sys

COUNT = int(sys.argv[1]) if len(sys.argv) > 1 else 50
PREFIX = sys.argv[2] if len(sys.argv) > 2 else "ai_rand_"
CHARID_BASE = int(sys.argv[3]) if len(sys.argv) > 3 else 500000
PWHASH = sys.argv[4] if len(sys.argv) > 4 else "CBaKoSACCN4c8lxxnen4gH2jHh8="

# i%5 -> (raceid, classid, x, y, z) mob-dense newbie field per race
SPOTS = {
    1: (1, 18, 50000, 42000, -3500, "ELF"),
    2: (2, 31, 30000, 15500, -4100, "DARK_ELF"),
    3: (3, 44, -48500, -110000, -270, "ORC"),
    4: (4, 53, 115500, -174500, -1100, "DWARF"),
    0: (0, 0, -91500, 240500, -3500, "HUMAN"),
}

out = []
out.append("USE loginserver;")
out.append("INSERT INTO accounts (login,password,accessLevel) VALUES")
accts = ["('%s%s','%s',0)" % (PREFIX, "%02d" % i, PWHASH) for i in range(1, COUNT + 1)]
out.append(",\n".join(accts) + ";")

out.append("USE gameserver;")
cols = ("account_name,charId,char_name,level,maxHp,curHp,maxCp,curCp,maxMp,curMp,"
        "face,hairStyle,hairColor,sex,heading,x,y,z,exp,expBeforeDeath,sp,karma,fame,pvpkills,"
        "pkkills,clanid,race,classid,base_class,transform_id,deletetime,cancraft,title,title_color,"
        "accesslevel,online,onlinetime,char_slot,newbie,lastAccess,clan_privs,wantspeace,isin7sdungeon,"
        "power_grade,nobless,subpledge,lvl_joined_academy,apprentice,sponsor,clan_join_expiry_time,"
        "clan_create_expiry_time,death_penalty_level,bookmarkslot,vitality_points,createDate,language,"
        "faction,pccafe_points,last_recom_date,rec_have,rec_left")
out.append("INSERT INTO characters (" + cols + ") VALUES")
rows = []
for i in range(1, COUNT + 1):
    raceid, cid, x, y, z, _ = SPOTS[i % 5]
    acc = "%s%02d" % (PREFIX, i)
    charid = CHARID_BASE + i - 1
    charname = "%s%02d" % ("Rand_", i)
    rows.append("('%s',%d,'%s',1,120,120,100,100,120,120,0,0,0,0,0,%d,%d,%d,0,0,0,0,0,0,0,0,"
                "%d,%d,0,0,0,NULL,'',15530402,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,"
                "'2015-01-01',NULL,0,0,0,0,3"
                % (acc, charid, charname, x, y, z, raceid, cid))
out.append(",\n".join(rows) + ";")
# S6-T05: starter consumables for each new char (20x HP potion 1061, 50x soulshot 1835), live inventory loc=1.
out.append("INSERT INTO gameserver.items (owner_id,object_id,item_id,count,enchant_level,loc,loc_data,time_of_use,custom_type1,custom_type2,mana_left) VALUES")
ibase = 4000000 + CHARID_BASE
iro = []
for i in range(1, COUNT + 1):
    charid = CHARID_BASE + i - 1
    iro.append("(%d,%d,1061,20,0,1,0,0,0,0,-1)" % (charid, ibase + (i - 1) * 2))
    iro.append("(%d,%d,1835,50,0,1,0,0,0,0,-1)" % (charid, ibase + (i - 1) * 2 + 1))
out.append(",\n".join(iro) + ";")
print("\n".join(out))
