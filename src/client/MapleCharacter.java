/*
This file is part of the OdinMS Maple Story Server
Copyright (C) 2008 Patrick Huy <patrick.huy@frz.cc>
Matthias Butz <matze@odinms.de>
Jan Christian Meyer <vimes@odinms.de>

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as
published by the Free Software Foundation version 3 as published by
the Free Software Foundation. You may not use, modify or distribute
this program under any otheer version of the GNU Affero General Public
License.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; witout even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.


You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/
package client;

import client.autoban.AutobanManager;
import client.creator.CharacterFactoryRecipe;
import client.inventory.*;
import client.inventory.manipulator.MapleInventoryManipulator;
import client.newyear.NewYearCardRecord;
import constants.ExpTable;
import constants.GameConstants;
import constants.ItemConstants;
import constants.ServerConstants;
import constants.skills.*;
import java.awt.*;
import java.lang.ref.WeakReference;
import java.sql.*;
import java.util.*;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.regex.Pattern;
import net.server.PlayerBuffValueHolder;
import net.server.PlayerCoolDownValueHolder;
import net.server.Server;
import net.server.audit.locks.MonitoredLockType;
import net.server.audit.locks.factory.MonitoredReentrantLockFactory;
import net.server.channel.handlers.PartyOperationHandler;
import net.server.guild.MapleAlliance;
import net.server.guild.MapleGuild;
import net.server.guild.MapleGuildCharacter;
import net.server.world.*;
import scripting.event.EventInstanceManager;
import scripting.item.ItemScriptManager;
import server.*;
import server.events.MapleEvents;
import server.events.RescueGaga;
import server.events.gm.MapleFitness;
import server.events.gm.MapleOla;
import server.life.*;
import server.loot.MapleLootManager;
import server.maps.*;
import server.maps.MapleMiniGame.MiniGameResult;
import server.partyquest.MonsterCarnival;
import server.partyquest.MonsterCarnivalParty;
import server.partyquest.PartyQuest;
import server.quest.MapleQuest;
import tools.*;
import tools.packets.Wedding;

public class MapleCharacter extends AbstractMapleCharacterObject {
    private static final MapleItemInformationProvider ii =
            MapleItemInformationProvider.getInstance();
    private static final String LEVEL_200 =
            "[Congrats] %s has reached Level 200! Congratulate %s on such an amazing achievement!";
    private static final String[] BLOCKED_NAMES = {
        "admin",
        "owner",
        "moderator",
        "intern",
        "donor",
        "administrator",
        "help",
        "helper",
        "alert",
        "notice",
        "maplestory",
        "fuck",
        "wizet",
        "fucking",
        "negro",
        "fuk",
        "fuc",
        "penis",
        "pussy",
        "asshole",
        "gay",
        "nigger",
        "homo",
        "suck",
        "cum",
        "shit",
        "shitty",
        "condom",
        "security",
        "official",
        "rape",
        "nigga",
        "sex",
        "tit",
        "boner",
        "orgy",
        "clit",
        "asshole",
        "fatass",
        "bitch",
        "support",
        "gamemaster",
        "cock",
        "gaay",
        "gm",
        "operate",
        "master",
        "sysop",
        "party",
        "GameMaster",
        "community",
        "message",
        "event",
        "test",
        "meso",
        "Scania",
        "yata",
        "AsiaSoft",
        "henesys"
    };

    private int world;
    private int accountid, id, level;
    private int rank, rankMove, jobRank, jobRankMove;
    private int gender, hair, face;
    private int fame, quest_fame;
    private int initialSpawnPoint;
    private int mapid;
    private int currentPage, currentType = 0, currentTab = 1;
    private int itemEffect;
    private int guildid, guildRank, allianceRank;
    private int messengerposition = 4;
    private int slots = 0;
    private int energybar;
    private int gmLevel;
    private int ci = 0;
    private MapleFamily family;
    private int familyId;
    private int bookCover;
    private int markedMonster = 0;
    private int battleshipHp = 0;
    private int mesosTraded = 0;
    private int possibleReports = 10;
    private int dojoPoints, vanquisherStage, dojoStage, dojoEnergy, vanquisherKills;
    private int warpToId;
    private int expRate = 1,
            mesoRate = 1,
            dropRate = 1,
            expCoupon = 1,
            mesoCoupon = 1,
            dropCoupon = 1;
    private int omokwins, omokties, omoklosses, matchcardwins, matchcardties, matchcardlosses;
    private int owlSearch;
    private long lastfametime,
            lastUsedCashItem,
            lastExpression = 0,
            lastHealed,
            lastBuyback = 0,
            lastDeathtime,
            lastMesoDrop = -1,
            jailExpiration = -1;
    private transient int localstr, localdex, localluk, localint_, localmagic, localwatk;
    private transient int equipmaxhp,
            equipmaxmp,
            equipstr,
            equipdex,
            equipluk,
            equipint_,
            equipmagic,
            equipwatk,
            localchairhp,
            localchairmp;
    private int localchairrate;
    private boolean hidden,
            equipchanged = true,
            canDoor = true,
            berserk,
            hasMerchant,
            hasSandboxItem = false,
            whiteChat = false;
    private boolean equippedMesoMagnet = false,
            equippedItemPouch = false,
            equippedPetItemIgnore = false;
    private int linkedLevel = 0;
    private String linkedName = null;
    private boolean finishedDojoTutorial;
    private boolean usedStorage = false;
    private String name;
    private String chalktext;
    private String dataString;
    private String search = null;
    private final AtomicBoolean mapTransitioning =
            new AtomicBoolean(
                    true); // player client is currently trying to change maps or log in the game
    // map
    private final AtomicBoolean awayFromWorld =
            new AtomicBoolean(true); // player is online, but on cash shop or mts
    private final AtomicInteger exp = new AtomicInteger();
    private final AtomicInteger gachaexp = new AtomicInteger();
    private final AtomicInteger meso = new AtomicInteger();
    private final AtomicInteger chair = new AtomicInteger();
    private int merchantmeso;
    private BuddyList buddylist;
    private EventInstanceManager eventInstance = null;
    private MapleHiredMerchant hiredMerchant = null;
    private MapleClient client;
    private MapleGuildCharacter mgc = null;
    private MaplePartyCharacter mpc = null;
    private final MapleInventory[] inventory;
    private MapleJob job = MapleJob.BEGINNER;
    private MapleMap map;
    private MapleMessenger messenger = null;
    private MapleMiniGame miniGame;
    private MapleMount maplemount;
    private MapleParty party;
    private final MaplePet[] pets = new MaplePet[3];
    private MaplePlayerShop playerShop = null;
    private MapleShop shop = null;
    private MapleSkinColor skinColor = MapleSkinColor.NORMAL;
    private MapleStorage storage = null;
    private MapleTrade trade = null;
    private MonsterBook monsterbook;
    private CashShop cashshop;
    private final Set<NewYearCardRecord> newyears = new LinkedHashSet<>();
    private final SavedLocation[] savedLocations;
    private final SkillMacro[] skillMacros = new SkillMacro[5];
    private List<Integer> lastmonthfameids;
    private final List<WeakReference<MapleMap>> lastVisitedMaps = new ArrayList<>();
    private final Map<Short, MapleQuestStatus> quests;
    private final Set<MapleMonster> controlled = new LinkedHashSet<>();
    private final Map<Integer, String> entered = new LinkedHashMap<>();
    private final Set<MapleMapObject> visibleMapObjects = new LinkedHashSet<>();
    private final Map<Skill, SkillEntry> skills = new LinkedHashMap<>();
    private final Map<Integer, Integer> activeCoupons = new LinkedHashMap<>();
    private final Map<Integer, Integer> activeCouponRates = new LinkedHashMap<>();
    private final EnumMap<MapleBuffStat, MapleBuffStatValueHolder> effects =
            new EnumMap<>(MapleBuffStat.class);
    private final Map<MapleBuffStat, Byte> buffEffectsCount = new LinkedHashMap<>();
    private final Map<MapleDisease, Long> diseaseExpires = new LinkedHashMap<>();
    private final Map<Integer, Map<MapleBuffStat, MapleBuffStatValueHolder>> buffEffects =
            new LinkedHashMap<>(); // non-overriding buffs thanks to Ronan
    private final Map<Integer, Long> buffExpires = new LinkedHashMap<>();
    private final Map<Integer, MapleKeyBinding> keymap = new LinkedHashMap<>();
    private final Map<Integer, MapleSummon> summons = new LinkedHashMap<>();
    private final Map<Integer, MapleCoolDownValueHolder> coolDowns = new LinkedHashMap<>();
    private final EnumMap<MapleDisease, Pair<MapleDiseaseValueHolder, MobSkill>> diseases =
            new EnumMap<>(MapleDisease.class);
    private MapleDoor pdoor = null;
    private Map<MapleQuest, Long> questExpirations = new LinkedHashMap<>();
    private ScheduledFuture<?> dragonBloodSchedule;
    private ScheduledFuture<?> hpDecreaseTask;
    private ScheduledFuture<?> beholderHealingSchedule, beholderBuffSchedule, berserkSchedule;
    private ScheduledFuture<?> skillCooldownTask = null;
    private ScheduledFuture<?> buffExpireTask = null;
    private ScheduledFuture<?> itemExpireTask = null;
    private ScheduledFuture<?> diseaseExpireTask = null;
    private ScheduledFuture<?> questExpireTask = null;
    private ScheduledFuture<?> recoveryTask = null;
    private ScheduledFuture<?> extraRecoveryTask = null;
    private ScheduledFuture<?> chairRecoveryTask = null;
    private ScheduledFuture<?> pendantOfSpirit = null; // 1122017
    private final Lock chrLock =
            MonitoredReentrantLockFactory.createLock(MonitoredLockType.CHARACTER_CHR, true);
    private final Lock evtLock =
            MonitoredReentrantLockFactory.createLock(MonitoredLockType.CHARACTER_EVT, true);
    private final Lock petLock =
            MonitoredReentrantLockFactory.createLock(
                    MonitoredLockType.CHARACTER_PET, true); // for meso & quest tasks as well
    private final Lock prtLock =
            MonitoredReentrantLockFactory.createLock(MonitoredLockType.CHARACTER_PRT);
    private final Map<Integer, Set<Integer>> excluded = new LinkedHashMap<>();
    private final Set<Integer> excludedItems = new LinkedHashSet<>();
    private static final String[] ariantroomleader = new String[3];
    private static final int[] ariantroomslot = new int[3];
    private long portaldelay = 0, lastcombo = 0;
    private short combocounter = 0;
    private final List<String> blockedPortals = new ArrayList<>();
    private final Map<Short, String> area_info = new LinkedHashMap<>();
    private AutobanManager autoban;
    private boolean isbanned = false;
    private boolean blockCashShop = false;
    private byte pendantExp = 0, lastmobcount = 0, doorSlot = -1;
    private final List<Integer> trockmaps = new ArrayList<>();
    private final List<Integer> viptrockmaps = new ArrayList<>();
    private Map<String, MapleEvents> events = new LinkedHashMap<>();
    private PartyQuest partyQuest = null;
    private MapleDragon dragon = null;
    private MapleRing marriageRing;
    private int marriageItemid = -1;
    private int partnerId = -1;
    private final List<MapleRing> crushRings = new ArrayList<>();
    private final List<MapleRing> friendshipRings = new ArrayList<>();
    private boolean loggedIn = false;
    private boolean useCS; // chaos scroll upon crafting item.
    private long npcCd;
    private long petLootCd;
    private long lastHpDec = 0;
    private int newWarpMap = -1;
    private boolean canWarpMap =
            true; // only one "warp" must be used per call, and this will define the right one.
    private int canWarpCounter = 0; // counts how many times "inner warps" have been called.
    private byte extraHpRec = 0, extraMpRec = 0;
    private short extraRecInterval;
    private int targetHpBarHash = 0;
    private long targetHpBarTime = 0;
    private long nextUnderlevelTime = 0;
    private int banishMap = -1;
    private int banishSp = -1;
    private long banishTime = 0;

    private MapleCharacter() {
        super.setListener(
                new AbstractCharacterListener() {
                    @Override
                    public void onHpChanged(int oldHp) {
                        hpChangeAction(oldHp);
                    }

                    @Override
                    public void onHpmpPoolUpdate() {
                        List<Pair<MapleStat, Integer>> hpmpupdate = recalcLocalStats();
                        for (Pair<MapleStat, Integer> p : hpmpupdate) {
                            statUpdates.put(p.getLeft(), p.getRight());
                        }

                        if (hp > localmaxhp) {
                            setHp(localmaxhp);
                            statUpdates.put(MapleStat.HP, hp);
                        }

                        if (mp > localmaxmp) {
                            setMp(localmaxmp);
                            statUpdates.put(MapleStat.MP, mp);
                        }
                    }

                    @Override
                    public void onAnnounceStatPoolUpdate() {
                        List<Pair<MapleStat, Integer>> statup = new ArrayList<>(8);
                        for (Map.Entry<MapleStat, Integer> s : statUpdates.entrySet()) {
                            statup.add(new Pair<>(s.getKey(), s.getValue()));
                        }

                        announce(
                                MaplePacketCreator.updatePlayerStats(
                                        statup, true, MapleCharacter.this));
                    }
                });

        useCS = false;

        setStance(0);
        inventory = new MapleInventory[MapleInventoryType.values().length];
        savedLocations = new SavedLocation[SavedLocationType.values().length];

        for (MapleInventoryType type : MapleInventoryType.values()) {
            byte b = 24;
            if (type == MapleInventoryType.CASH) {
                b = 96;
            }
            inventory[type.ordinal()] = new MapleInventory(this, type, b);
        }
        inventory[MapleInventoryType.CANHOLD.ordinal()] = new MapleInventoryProof(this);

        for (int i = 0; i < SavedLocationType.values().length; i++) {
            savedLocations[i] = null;
        }
        quests = new LinkedHashMap<>();
        setPosition(new Point(0, 0));

        petLootCd = Server.getInstance().getCurrentTime();
    }

    private static MapleJob getJobStyleInternal(int jobid, byte opt) {
        int jobtype = jobid / 100;

        if (jobtype == MapleJob.WARRIOR.getId() / 100
                || jobtype == MapleJob.DAWNWARRIOR1.getId() / 100
                || jobtype == MapleJob.ARAN1.getId() / 100) {
            return (MapleJob.WARRIOR);
        }
        if (jobtype == MapleJob.MAGICIAN.getId() / 100
                || jobtype == MapleJob.BLAZEWIZARD1.getId() / 100
                || jobtype == MapleJob.EVAN1.getId() / 100) {
            return (MapleJob.MAGICIAN);
        }
        if (jobtype == MapleJob.BOWMAN.getId() / 100
                || jobtype == MapleJob.WINDARCHER1.getId() / 100) {
            if (jobid / 10 == MapleJob.CROSSBOWMAN.getId() / 10) return (MapleJob.CROSSBOWMAN);
            return (MapleJob.BOWMAN);
        }
        if (jobtype == MapleJob.THIEF.getId() / 100
                || jobtype == MapleJob.NIGHTWALKER1.getId() / 100) {
            return (MapleJob.THIEF);
        }
        if (jobtype == MapleJob.PIRATE.getId() / 100
                || jobtype == MapleJob.THUNDERBREAKER1.getId() / 100) {
            if (opt == (byte) 0x80) return (MapleJob.BRAWLER);
            return (MapleJob.GUNSLINGER);
        }

        return (MapleJob.BEGINNER);
    }

    public MapleJob getJobStyle(byte opt) {
        return getJobStyleInternal(job.getId(), opt);
    }

    public MapleJob getJobStyle() {
        return getJobStyle((byte) ((this.getStr() > this.getDex()) ? 0x80 : 0x40));
    }

    public static MapleCharacter getDefault(MapleClient c) {
        MapleCharacter ret = new MapleCharacter();
        ret.client = c;
        ret.gmLevel = 0;
        ret.hp = 50;
        ret.setMaxHp(50);
        ret.mp = 5;
        ret.setMaxMp(5);
        ret.str = 12;
        ret.dex = 5;
        ret.int_ = 4;
        ret.luk = 4;
        ret.map = null;
        ret.job = MapleJob.BEGINNER;
        ret.level = 1;
        ret.accountid = c.getAccID();
        ret.buddylist = new BuddyList(20);
        ret.maplemount = null;
        ret.getInventory(MapleInventoryType.EQUIP).setSlotLimit(24);
        ret.getInventory(MapleInventoryType.USE).setSlotLimit(24);
        ret.getInventory(MapleInventoryType.SETUP).setSlotLimit(24);
        ret.getInventory(MapleInventoryType.ETC).setSlotLimit(24);

        // Select a keybinding method
        int[] selectedKey;
        int[] selectedType;
        int[] selectedAction;

        if (ServerConstants.USE_CUSTOM_KEYSET) {
            selectedKey = GameConstants.getCustomKey(true);
            selectedType = GameConstants.getCustomType(true);
            selectedAction = GameConstants.getCustomAction(true);
        } else {
            selectedKey = GameConstants.getCustomKey(false);
            selectedType = GameConstants.getCustomType(false);
            selectedAction = GameConstants.getCustomAction(false);
        }

        for (int i = 0; i < selectedKey.length; i++) {
            ret.keymap.put(selectedKey[i], new MapleKeyBinding(selectedType[i], selectedAction[i]));
        }

        // to fix the map 0 lol
        for (int i = 0; i < 5; i++) {
            ret.trockmaps.add(999999999);
        }
        for (int i = 0; i < 10; i++) {
            ret.viptrockmaps.add(999999999);
        }

        return ret;
    }

    public boolean isLoggedinWorld() {
        return loggedIn && !this.isAwayFromWorld();
    }

    public boolean isAwayFromWorld() {
        return awayFromWorld.get();
    }

    public void setEnteredChannelWorld() {
        awayFromWorld.set(false);
        client.getChannelServer().removePlayerAway(id);
    }

    public void setAwayFromChannelWorld() {
        setAwayFromChannelWorld(false);
    }

    public void setDisconnectedFromChannelWorld() {
        setAwayFromChannelWorld(true);
    }

    private void setAwayFromChannelWorld(boolean disconnect) {
        awayFromWorld.set(true);

        if (!disconnect) {
            client.getChannelServer().insertPlayerAway(id);
        } else {
            client.getChannelServer().removePlayerAway(id);
        }
    }

    public long getPetLootCd() {
        return petLootCd;
    }

    public void setPetLootCd(long cd) {
        petLootCd = cd;
    }

    public boolean getCS() {
        return useCS;
    }

    public void setCS(boolean cs) {
        useCS = cs;
    }

    public long getNpcCooldown() {
        return npcCd;
    }

    public void setNpcCooldown(long d) {
        npcCd = d;
    }

    public void setOwlSearch(int id) {
        owlSearch = id;
    }

    public int getOwlSearch() {
        return owlSearch;
    }

    public void addCooldown(int skillId, long startTime, long length) {
        effLock.lock();
        chrLock.lock();
        try {
            this.coolDowns.put(skillId, new MapleCoolDownValueHolder(skillId, startTime, length));
        } finally {
            chrLock.unlock();
            effLock.unlock();
        }
    }

    public void addCrushRing(MapleRing r) {
        crushRings.add(r);
    }

    public MapleRing getRingById(int id) {
        for (MapleRing ring : getCrushRings()) {
            if (ring.getRingId() == id) {
                return ring;
            }
        }
        for (MapleRing ring : getFriendshipRings()) {
            if (ring.getRingId() == id) {
                return ring;
            }
        }

        if (marriageRing != null) {
            if (marriageRing.getRingId() == id) {
                return marriageRing;
            }
        }

        return null;
    }

    public int getMarriageItemId() {
        return marriageItemid;
    }

    public void setMarriageItemId(int itemid) {
        marriageItemid = itemid;
    }

    public int getPartnerId() {
        return partnerId;
    }

    public void setPartnerId(int partnerid) {
        partnerId = partnerid;
    }

    public int getRelationshipId() {
        return getWorldServer().getRelationshipId(id);
    }

    public boolean isMarried() {
        return marriageRing != null && partnerId > 0;
    }

    public boolean hasJustMarried() {
        EventInstanceManager eim = getEventInstance();
        if (eim != null) {
            String prop = eim.getProperty("groomId");

            if (prop != null) {
                return (Integer.parseInt(prop) == id || eim.getIntProperty("brideId") == id)
                        && (mapid == 680000110 || mapid == 680000210);
            }
        }

        return false;
    }

    public int addDojoPointsByMap(int mapid) {
        int pts = 0;
        if (dojoPoints < 17000) {
            pts = 1 + ((mapid - 1) / 100 % 100) / 6;
            if (!getDojoParty()) {
                pts++;
            }
            this.dojoPoints += pts;
        }
        return pts;
    }

    public void addFame(int famechange) {
        this.fame += famechange;
    }

    public void addFriendshipRing(MapleRing r) {
        friendshipRings.add(r);
    }

    public void addMarriageRing(MapleRing r) {
        marriageRing = r;
    }

    public void addMesosTraded(int gain) {
        this.mesosTraded += gain;
    }

    public void addPet(MaplePet pet) {
        petLock.lock();
        try {
            for (int i = 0; i < 3; i++) {
                if (pets[i] == null) {
                    pets[i] = pet;
                    return;
                }
            }
        } finally {
            petLock.unlock();
        }
    }

    public void addSummon(int id, MapleSummon summon) {
        summons.put(id, summon);
    }

    public void addVisibleMapObject(MapleMapObject mo) {
        visibleMapObjects.add(mo);
    }

    public void ban(String reason) {
        this.isbanned = true;
        try {
            Connection con = DatabaseConnection.getConnection();
            try (PreparedStatement ps =
                    con.prepareStatement(
                            "UPDATE accounts SET banned = 1, banreason = ? WHERE id = ?")) {
                ps.setString(1, reason);
                ps.setInt(2, accountid);
                ps.executeUpdate();
            }
            con.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static boolean ban(String id, String reason, boolean accountId) {
        PreparedStatement ps = null;
        ResultSet rs = null;
        Connection con = null;
        try {
            con = DatabaseConnection.getConnection();

            if (id.matches("/[0-9]{1,3}\\..*")) {
                ps = con.prepareStatement("INSERT INTO ipbans VALUES (DEFAULT, ?)");
                ps.setString(1, id);
                ps.executeUpdate();
                ps.close();
                return true;
            }
            ps =
                    accountId
                            ? con.prepareStatement("SELECT id FROM accounts WHERE name = ?")
                            : con.prepareStatement(
                                    "SELECT accountid FROM characters WHERE name = ?");

            boolean ret = false;
            ps.setString(1, id);
            rs = ps.executeQuery();
            if (rs.next()) {

                try (Connection con2 = DatabaseConnection.getConnection();
                        PreparedStatement psb =
                                con2.prepareStatement(
                                        "UPDATE accounts SET banned = 1, banreason = ? WHERE id = ?")) {
                    psb.setString(1, reason);
                    psb.setInt(2, rs.getInt(1));
                    psb.executeUpdate();
                }
                ret = true;
            }

            rs.close();
            ps.close();
            con.close();
            return ret;
        } catch (SQLException ex) {
            ex.printStackTrace();
        } finally {
            try {
                if (ps != null && !ps.isClosed()) {
                    ps.close();
                }
                if (rs != null && !rs.isClosed()) {
                    rs.close();
                }
                if (con != null && !con.isClosed()) {
                    con.close();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    public int calculateMaxBaseDamage(int watk) {
        int maxbasedamage;
        Item weapon_item = getInventory(MapleInventoryType.EQUIPPED).getItem((short) -11);
        if (weapon_item != null) {
            MapleWeaponType weapon = ii.getWeaponType(weapon_item.getItemId());
            int mainstat, secondarystat;
            if (job.isA(MapleJob.THIEF) && weapon == MapleWeaponType.DAGGER_OTHER) {
                weapon = MapleWeaponType.DAGGER_THIEVES;
            }

            if (weapon == MapleWeaponType.BOW
                    || weapon == MapleWeaponType.CROSSBOW
                    || weapon == MapleWeaponType.GUN) {
                mainstat = localdex;
                secondarystat = localstr;
            } else if (weapon == MapleWeaponType.CLAW || weapon == MapleWeaponType.DAGGER_THIEVES) {
                mainstat = localluk;
                secondarystat = localdex + localstr;
            } else {
                mainstat = localstr;
                secondarystat = localdex;
            }
            maxbasedamage =
                    (int)
                            (((weapon.getMaxDamageMultiplier() * mainstat + secondarystat) / 100.0)
                                    * watk);
        } else {
            if (job.isA(MapleJob.PIRATE) || job.isA(MapleJob.THUNDERBREAKER1)) {
                double weapMulti = 3;
                if (job.getId() % 100 != 0) {
                    weapMulti = 4.2;
                }

                int attack = Math.min((2 * level + 31) / 3, 31);
                maxbasedamage = (int) (localstr * weapMulti + localdex) * attack / 100;
            } else {
                maxbasedamage = 1;
            }
        }
        return maxbasedamage;
    }

    public void setCombo(short count) {
        if (count < combocounter) {
            cancelEffectFromBuffStat(MapleBuffStat.ARAN_COMBO);
        }
        combocounter = (short) Math.min(30000, count);
        if (count > 0) {
            announce(MaplePacketCreator.showCombo(combocounter));
        }
    }

    public void setLastCombo(long time) {
        lastcombo = time;
    }

    public short getCombo() {
        return combocounter;
    }

    public long getLastCombo() {
        return lastcombo;
    }

    public int getLastMobCount() { // Used for skills that have mobCount at 1. (a/b)
        return lastmobcount;
    }

    public void setLastMobCount(byte count) {
        lastmobcount = count;
    }

    public boolean cannotEnterCashShop() {
        return blockCashShop;
    }

    public void toggleBlockCashShop() {
        blockCashShop = !blockCashShop;
    }

    public void newClient(MapleClient c) {
        this.loggedIn = true;
        c.setAccountName(this.client.getAccountName()); // No null's for accountName
        this.client = c;
        this.map = c.getChannelServer().getMapFactory().getMap(getMapId());
        MaplePortal portal = map.findClosestPlayerSpawnpoint(getPosition());
        if (portal == null) {
            portal = map.getPortal(0);
        }
        this.setPosition(portal.getPosition());
        this.initialSpawnPoint = portal.getId();
    }

    public String getMedalText() {
        String medal = "";
        final Item medalItem = getInventory(MapleInventoryType.EQUIPPED).getItem((short) -49);
        if (medalItem != null) {
            medal = '<' + ii.getName(medalItem.getItemId()) + "> ";
        }
        return medal;
    }

    public void Hide(boolean hide, boolean login) {
        if (isGM() && hide != this.hidden) {
            if (!hide) {
                this.hidden = false;
                announce(MaplePacketCreator.getGMEffect(0x10, (byte) 0));
                List<MapleBuffStat> dsstat = Collections.singletonList(MapleBuffStat.DARKSIGHT);
                map.broadcastGMMessage(
                        this, MaplePacketCreator.cancelForeignBuff(id, dsstat), false);
                map.broadcastMessage(this, MaplePacketCreator.spawnPlayerMapObject(this), false);

                for (MapleSummon ms : this.getSummonsValues()) {
                    map.broadcastNONGMMessage(
                            this, MaplePacketCreator.spawnSummon(ms, false), false);
                }
            } else {
                this.hidden = true;
                announce(MaplePacketCreator.getGMEffect(0x10, (byte) 1));
                if (!login) {
                    map.broadcastMessage(this, MaplePacketCreator.removePlayerFromMap(id), false);
                }
                map.broadcastGMMessage(this, MaplePacketCreator.spawnPlayerMapObject(this), false);
                List<Pair<MapleBuffStat, Integer>> ldsstat =
                        Collections.singletonList(new Pair<>(MapleBuffStat.DARKSIGHT, 0));
                map.broadcastGMMessage(
                        this, MaplePacketCreator.giveForeignBuff(id, ldsstat), false);
                for (MapleMonster mon : this.getControlledMonsters()) {
                    mon.setController(null);
                    mon.setControllerHasAggro(false);
                    mon.setControllerKnowsAboutAggro(false);
                    mon.getMap().updateMonsterController(mon);
                }
            }
            announce(MaplePacketCreator.enableActions());
        }
    }

    public void Hide(boolean hide) {
        Hide(hide, false);
    }

    public void toggleHide(boolean login) {
        Hide(!hidden);
    }

    public void cancelMagicDoor() {
        List<MapleBuffStatValueHolder> mbsvhList = getAllStatups();
        for (MapleBuffStatValueHolder mbsvh : mbsvhList) {
            if (mbsvh.effect.isMagicDoor()) {
                cancelEffect(mbsvh.effect, false, mbsvh.startTime);
                break;
            }
        }
    }

    private void cancelPlayerBuffs(List<MapleBuffStat> buffstats) {
        if (client.getChannelServer().getPlayerStorage().getCharacterById(id) != null) {
            updateLocalStats();
            client.announce(MaplePacketCreator.cancelBuff(buffstats));
            if (!buffstats.isEmpty()) {
                map.broadcastMessage(
                        this, MaplePacketCreator.cancelForeignBuff(id, buffstats), false);
            }
        }
    }

    public static boolean canCreateChar(String name) {
        String lname = name.toLowerCase();
        for (String nameTest : BLOCKED_NAMES) {
            if (lname.contains(nameTest)) {
                return false;
            }
        }
        return getIdByName(name) < 0
                && Pattern.compile("[a-zA-Z0-9]{3,12}").matcher(name).matches();
    }

    public boolean canDoor() {
        return canDoor;
    }

    public void setHasSandboxItem() {
        hasSandboxItem = true;
    }

    public void removeSandboxItems() { // sandbox idea thanks to Morty
        if (!hasSandboxItem) return;

        MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
        for (MapleInventoryType invType : MapleInventoryType.values()) {
            MapleInventory inv = this.getInventory(invType);

            inv.lockInventory();
            try {
                for (Item item : new ArrayList<>(inv.list())) {
                    if (MapleInventoryManipulator.isSandboxItem(item)) {
                        MapleInventoryManipulator.removeFromSlot(
                                client, invType, item.getPosition(), item.getQuantity(), false);
                        dropMessage(
                                5,
                                '['
                                        + ii.getName(item.getItemId())
                                        + "] has passed its trial conditions and will be removed from your inventory.");
                    }
                }
            } finally {
                inv.unlockInventory();
            }
        }

        hasSandboxItem = false;
    }

    public FameStatus canGiveFame(MapleCharacter from) {
        if (this.isGM()) {
            return FameStatus.OK;
        }
        if (lastfametime >= System.currentTimeMillis() - 3600000 * 24) {
            return FameStatus.NOT_TODAY;
        }
        if (lastmonthfameids.contains(from.id)) {
            return FameStatus.NOT_THIS_MONTH;
        }
        return FameStatus.OK;
    }

    public void changeCI(int type) {
        this.ci = type;
    }

    public void setMasteries(int jobId) {
        int[] skills = new int[4];
        for (int i = 0; i > skills.length; i++) {
            skills[i] = 0; // that initialization meng
        }
        if (jobId == 112) {
            skills[0] = Hero.ACHILLES;
            skills[1] = Hero.MONSTER_MAGNET;
            skills[2] = Hero.BRANDISH;
        } else if (jobId == 122) {
            skills[0] = Paladin.ACHILLES;
            skills[1] = Paladin.MONSTER_MAGNET;
            skills[2] = Paladin.BLAST;
        } else if (jobId == 132) {
            skills[0] = DarkKnight.BEHOLDER;
            skills[1] = DarkKnight.ACHILLES;
            skills[2] = DarkKnight.MONSTER_MAGNET;
        } else if (jobId == 212) {
            skills[0] = FPArchMage.BIG_BANG;
            skills[1] = FPArchMage.MANA_REFLECTION;
            skills[2] = FPArchMage.PARALYZE;
        } else if (jobId == 222) {
            skills[0] = ILArchMage.BIG_BANG;
            skills[1] = ILArchMage.MANA_REFLECTION;
            skills[2] = ILArchMage.CHAIN_LIGHTNING;
        } else if (jobId == 232) {
            skills[0] = Bishop.BIG_BANG;
            skills[1] = Bishop.MANA_REFLECTION;
            skills[2] = Bishop.HOLY_SHIELD;
        } else if (jobId == 312) {
            skills[0] = Bowmaster.BOW_EXPERT;
            skills[1] = Bowmaster.HAMSTRING;
            skills[2] = Bowmaster.SHARP_EYES;
        } else if (jobId == 322) {
            skills[0] = Marksman.MARKSMAN_BOOST;
            skills[1] = Marksman.BLIND;
            skills[2] = Marksman.SHARP_EYES;
        } else if (jobId == 412) {
            skills[0] = NightLord.SHADOW_STARS;
            skills[1] = NightLord.SHADOW_SHIFTER;
            skills[2] = NightLord.VENOMOUS_STAR;
        } else if (jobId == 422) {
            skills[0] = Shadower.SHADOW_SHIFTER;
            skills[1] = Shadower.VENOMOUS_STAB;
            skills[2] = Shadower.BOOMERANG_STEP;
        } else if (jobId == 512) {
            skills[0] = Buccaneer.BARRAGE;
            skills[1] = Buccaneer.ENERGY_ORB;
            skills[2] = Buccaneer.SPEED_INFUSION;
            skills[3] = Buccaneer.DRAGON_STRIKE;
        } else if (jobId == 522) {
            skills[0] = Corsair.ELEMENTAL_BOOST;
            skills[1] = Corsair.BULLSEYE;
            skills[2] = Corsair.WRATH_OF_THE_OCTOPI;
            skills[3] = Corsair.RAPID_FIRE;
        } else if (jobId == 2112) {
            skills[0] = Aran.OVER_SWING;
            skills[1] = Aran.HIGH_MASTERY;
            skills[2] = Aran.FREEZE_STANDING;
        } else if (jobId == 2217) {
            skills[0] = Evan.MAPLE_WARRIOR;
            skills[1] = Evan.ILLUSION;
        } else if (jobId == 2218) {
            skills[0] = Evan.BLESSING_OF_THE_ONYX;
            skills[1] = Evan.BLAZE;
        }
        for (Integer skillId : skills) {
            if (skillId != 0) {
                Skill skill = SkillFactory.getSkill(skillId);
                final int skilllevel = getSkillLevel(skill);
                if (skilllevel > 0) continue;

                changeSkillLevel(skill, (byte) 0, 10, -1);
            }
        }
    }

    public synchronized void changeJob(MapleJob newJob) {
        if (newJob == null) {
            return; // the fuck you doing idiot!
        }

        this.job = newJob;

        int spGain = 1;
        if (GameConstants.hasSPTable(newJob)) {
            spGain += 2;
        } else {
            if (newJob.getId() % 10 == 2) {
                spGain += 2;
            }
        }
        gainSp(spGain, GameConstants.getSkillBook(newJob.getId()), true);

        if (newJob.getId() % 10 > 1) {
            gainAp(5, true);
        }

        if (!isGM()) {
            for (byte i = 1; i < 5; i++) {
                gainSlots(i, 4, true);
            }
        }

        int addhp = 0, addmp = 0;
        int job_ = job.getId() % 1000; // lame temp "fix"
        if (job_ == 100) { // 1st warrior
            addhp += Randomizer.rand(200, 250);
        } else if (job_ == 200) { // 1st mage
            addmp += Randomizer.rand(100, 150);
        } else if (job_ % 100 == 0) { // 1st others
            addhp += Randomizer.rand(100, 150);
            addhp += Randomizer.rand(25, 50);
        } else if (job_ > 0 && job_ < 200) { // 2nd~4th warrior
            addhp += Randomizer.rand(300, 350);
        } else if (job_ < 300) { // 2nd~4th mage
            addmp += Randomizer.rand(450, 500);
        } else if (job_ > 0) { // 2nd~4th others
            addhp += Randomizer.rand(300, 350);
            addmp += Randomizer.rand(150, 200);
        }

        /*
        //aran perks?
        int newJobId = newJob.getId();
        if(newJobId == 2100) {          // become aran1
            addhp += 275;
            addmp += 15;
        } else if(newJobId == 2110) {   // become aran2
            addmp += 275;
        } else if(newJobId == 2111) {   // become aran3
            addhp += 275;
            addmp += 275;
        }
        */

        effLock.lock();
        statWlock.lock();
        try {
            addMaxMPMaxHP(addhp, addmp, true);
            recalcLocalStats();

            List<Pair<MapleStat, Integer>> statup = new ArrayList<>(7);
            statup.add(new Pair<>(MapleStat.HP, hp));
            statup.add(new Pair<>(MapleStat.MP, mp));
            statup.add(new Pair<>(MapleStat.MAXHP, clientmaxhp));
            statup.add(new Pair<>(MapleStat.MAXMP, clientmaxmp));
            statup.add(new Pair<>(MapleStat.AVAILABLEAP, remainingAp));
            statup.add(
                    new Pair<>(
                            MapleStat.AVAILABLESP,
                            remainingSp[GameConstants.getSkillBook(job.getId())]));
            statup.add(new Pair<>(MapleStat.JOB, job.getId()));
            client.announce(MaplePacketCreator.updatePlayerStats(statup, true, this));
        } finally {
            statWlock.unlock();
            effLock.unlock();
        }

        silentPartyUpdate();

        if (dragon != null) {
            map.broadcastMessage(MaplePacketCreator.removeDragon(dragon.getObjectId()));
            dragon = null;
        }

        if (this.guildid > 0) {
            getGuild().broadcast(MaplePacketCreator.jobMessage(0, job.getId(), name), id);
        }
        setMasteries(this.job.getId());
        guildUpdate();

        map.broadcastMessage(this, MaplePacketCreator.showForeignEffect(id, 8), false);

        if (GameConstants.hasSPTable(newJob) && newJob.getId() != 2001) {
            if (getBuffedValue(MapleBuffStat.MONSTER_RIDING) != null) {
                cancelBuffStats(MapleBuffStat.MONSTER_RIDING);
            }
            createDragon();
        }

        if (ServerConstants.USE_ANNOUNCE_CHANGEJOB) {
            if (!this.isGM()) {
                broadcastAcquaintances(
                        6,
                        '['
                                + GameConstants.ordinal(GameConstants.getJobBranch(newJob))
                                + " Job] "
                                + name
                                + " has just become a "
                                + newJob.name()
                                + '.');
            }
        }
    }

    public void broadcastAcquaintances(int type, String message) {
        broadcastAcquaintances(MaplePacketCreator.serverNotice(type, message));
    }

    public void broadcastAcquaintances(byte[] packet) {
        buddylist.broadcast(packet, getWorldServer().getPlayerStorage());

        if (family != null) {
            // family.broadcast(packet, id); not yet implemented
        }

        MapleGuild guild = getGuild();
        if (guild != null) {
            guild.broadcast(packet, id);
        }

        /*
        if(partnerid > 0) {
            partner.announce(packet); not yet implemented
        }
        */
        announce(packet);
    }

    public void changeKeybinding(int key, MapleKeyBinding keybinding) {
        if (keybinding.getType() != 0) {
            keymap.put(key, keybinding);
        } else {
            keymap.remove(key);
        }
    }

    public MapleMap getWarpMap(int map) {
        MapleMap target;
        EventInstanceManager eim = getEventInstance();
        target =
                eim == null
                        ? client.getChannelServer().getMapFactory().getMap(map)
                        : eim.getMapInstance(map);
        return target;
    }

    // for use ONLY inside OnUserEnter map scripts that requires a player to change map while still
    // moving between maps.
    public void warpAhead(int map) {
        newWarpMap = map;
    }

    private void eventChangedMap(int map) {
        EventInstanceManager eim = getEventInstance();
        if (eim != null) eim.changedMap(this, map);
    }

    private void eventAfterChangedMap(int map) {
        EventInstanceManager eim = getEventInstance();
        if (eim != null) eim.afterChangedMap(this, map);
    }

    public boolean canRecoverLastBanish() {
        return System.currentTimeMillis() - this.banishTime < 5 * 60 * 1000;
    }

    public Pair<Integer, Integer> getLastBanishData() {
        return new Pair<>(this.banishMap, this.banishSp);
    }

    public void clearBanishPlayerData() {
        this.banishMap = -1;
        this.banishSp = -1;
        this.banishTime = 0;
    }

    public void setBanishPlayerData(int banishMap, int banishSp, long banishTime) {
        this.banishMap = banishMap;
        this.banishSp = banishSp;
        this.banishTime = banishTime;
    }

    public void changeMapBanish(int mapid, String portal, String msg) {
        if (ServerConstants.USE_SPIKES_AVOID_BANISH) {
            for (Item it : this.getInventory(MapleInventoryType.EQUIPPED).list()) {
                if ((it.getFlag() & ItemConstants.SPIKES) == ItemConstants.SPIKES) return;
            }
        }

        int banMap = this.getMapId();
        int banSp = map.findClosestPlayerSpawnpoint(this.getPosition()).getId();
        long banTime = System.currentTimeMillis();

        dropMessage(5, msg);
        MapleMap map_ = getWarpMap(mapid);
        changeMap(map_, map_.getPortal(portal));

        setBanishPlayerData(banMap, banSp, banTime);
    }

    public void changeMap(int map) {
        MapleMap warpMap;
        EventInstanceManager eim = getEventInstance();

        warpMap =
                eim != null
                        ? eim.getMapInstance(map)
                        : client.getChannelServer().getMapFactory().getMap(map);

        changeMap(warpMap, warpMap.getRandomPlayerSpawnpoint());
    }

    public void changeMap(int map, int portal) {
        MapleMap warpMap;
        EventInstanceManager eim = getEventInstance();

        warpMap =
                eim != null
                        ? eim.getMapInstance(map)
                        : client.getChannelServer().getMapFactory().getMap(map);

        changeMap(warpMap, warpMap.getPortal(portal));
    }

    public void changeMap(int map, String portal) {
        MapleMap warpMap;
        EventInstanceManager eim = getEventInstance();

        warpMap =
                eim != null
                        ? eim.getMapInstance(map)
                        : client.getChannelServer().getMapFactory().getMap(map);

        changeMap(warpMap, warpMap.getPortal(portal));
    }

    public void changeMap(int map, MaplePortal portal) {
        MapleMap warpMap;
        EventInstanceManager eim = getEventInstance();

        warpMap =
                eim != null
                        ? eim.getMapInstance(map)
                        : client.getChannelServer().getMapFactory().getMap(map);

        changeMap(warpMap, portal);
    }

    public void changeMap(MapleMap to) {
        changeMap(to, 0);
    }

    public void changeMap(MapleMap to, int portal) {
        changeMap(to, to.getPortal(portal));
    }

    public void changeMap(final MapleMap target, final MaplePortal pto) {
        canWarpCounter++;

        eventChangedMap(target.getId()); // player can be dropped from an event here, hence the new
        // warping target.
        MapleMap to = getWarpMap(target.getId());
        changeMapInternal(
                to, pto.getPosition(), MaplePacketCreator.getWarpToMap(to, pto.getId(), this));
        canWarpMap = false;

        canWarpCounter--;
        if (canWarpCounter == 0) canWarpMap = true;

        eventAfterChangedMap(this.getMapId());
    }

    public void changeMap(final MapleMap target, final Point pos) {
        canWarpCounter++;

        eventChangedMap(target.getId());
        MapleMap to = getWarpMap(target.getId());
        changeMapInternal(to, pos, MaplePacketCreator.getWarpToMap(to, 0x80, pos, this));
        canWarpMap = false;

        canWarpCounter--;
        if (canWarpCounter == 0) canWarpMap = true;

        eventAfterChangedMap(this.getMapId());
    }

    public void forceChangeMap(final MapleMap target, final MaplePortal pto) {
        // will actually enter the map given as parameter, regardless of being an eventmap or
        // whatnot

        canWarpCounter++;
        eventChangedMap(999999999);

        EventInstanceManager mapEim = target.getEventInstance();
        if (mapEim != null) {
            EventInstanceManager playerEim = this.getEventInstance();
            if (playerEim != null) {
                playerEim.exitPlayer(this);
                if (playerEim.getPlayerCount() == 0) {
                    playerEim.dispose();
                }
            }

            mapEim.registerPlayer(this);
        }

        MapleMap to = getWarpMap(target.getId());
        changeMapInternal(
                to, pto.getPosition(), MaplePacketCreator.getWarpToMap(to, pto.getId(), this));
        canWarpMap = false;

        canWarpCounter--;
        if (canWarpCounter == 0) canWarpMap = true;

        eventAfterChangedMap(this.getMapId());
    }

    private boolean buffMapProtection() {
        effLock.lock();
        chrLock.lock();

        int thisMapid = mapid;
        int returnMapid =
                client.getChannelServer().getMapFactory().getMap(thisMapid).getReturnMapId();

        try {
            for (Entry<MapleBuffStat, MapleBuffStatValueHolder> mbs : effects.entrySet()) {
                if (mbs.getKey() == MapleBuffStat.MAP_PROTECTION) {
                    byte value = (byte) mbs.getValue().value;

                    return value == 1
                                    && ((returnMapid == 211000000 && thisMapid != 200082300)
                                            || returnMapid == 193000000)
                            || value == 2
                                    && (returnMapid == 230000000
                                            || thisMapid == 200082300); // protection from cold
                    // breathing underwater
                }
            }
        } finally {
            chrLock.unlock();
            effLock.unlock();
        }

        for (Item it : this.getInventory(MapleInventoryType.EQUIPPED).list()) {
            if ((it.getFlag() & ItemConstants.COLD) == ItemConstants.COLD
                    && ((returnMapid == 211000000 && thisMapid != 200082300)
                            || returnMapid == 193000000)) {
                return true; // protection from cold
            }
        }

        return false;
    }

    public List<Integer> getLastVisitedMapids() {
        List<Integer> lastVisited = new ArrayList<>(5);

        petLock.lock();
        try {
            for (WeakReference<MapleMap> lv : lastVisitedMaps) {
                MapleMap lvm = lv.get();

                if (lvm != null) {
                    lastVisited.add(lvm.getId());
                }
            }
        } finally {
            petLock.unlock();
        }

        return lastVisited;
    }

    public void partyOperationUpdate(MapleParty party, List<MapleCharacter> exPartyMembers) {
        List<WeakReference<MapleMap>> mapids;

        petLock.lock();
        try {
            mapids = new ArrayList<>(lastVisitedMaps);
        } finally {
            petLock.unlock();
        }

        List<MapleCharacter> partyMembers = new ArrayList<>();
        for (MapleCharacter mc :
                (exPartyMembers != null) ? exPartyMembers : this.getPartyMembers()) {
            if (mc.isLoggedinWorld()) {
                partyMembers.add(mc);
            }
        }

        MapleCharacter partyLeaver = null;
        if (exPartyMembers != null) {
            partyMembers.remove(this);
            partyLeaver = this;
        }

        int partyId = exPartyMembers != null ? 0 : this.getPartyId();
        for (WeakReference<MapleMap> mapRef : mapids) {
            MapleMap mapObj = mapRef.get();

            if (mapObj != null) {
                mapObj.updatePlayerItemDrops(partyId, id, partyMembers, partyLeaver);
            }
        }

        updatePartyTownDoors(party, this, partyLeaver, partyMembers);
    }

    private static void addPartyPlayerDoor(MapleCharacter target) {
        MapleDoor targetDoor = target.getPlayerDoor();
        if (targetDoor != null) {
            target.applyPartyDoor(targetDoor, true);
        }
    }

    private static void removePartyPlayerDoor(MapleParty party, MapleCharacter target) {
        target.removePartyDoor(party);
    }

    private static void updatePartyTownDoors(
            MapleParty party,
            MapleCharacter target,
            MapleCharacter partyLeaver,
            List<MapleCharacter> partyMembers) {
        if (partyLeaver != null) {
            removePartyPlayerDoor(party, target);
        } else {
            addPartyPlayerDoor(target);
        }

        Map<Integer, MapleDoor> partyDoors = null;
        if (!partyMembers.isEmpty()) {
            partyDoors = party.getDoors();

            for (MapleCharacter pchr : partyMembers) {
                MapleDoor door = partyDoors.get(pchr.id);
                if (door != null) {
                    door.updateDoorPortal(pchr);
                }
            }

            for (MapleDoor door : partyDoors.values()) {
                for (MapleCharacter pchar : partyMembers) {
                    door.getTownDoor().sendDestroyData(pchar.client, true);
                }
            }

            if (partyLeaver != null) {
                Collection<MapleDoor> leaverDoors = partyLeaver.getDoors();
                for (MapleDoor door : leaverDoors) {
                    for (MapleCharacter pchar : partyMembers) {
                        door.getTownDoor().sendDestroyData(pchar.client, true);
                    }
                }
            }

            List<Integer> histMembers = party.getMembersSortedByHistory();
            for (Integer chrid : histMembers) {
                MapleDoor door = partyDoors.get(chrid);

                if (door != null) {
                    for (MapleCharacter pchar : partyMembers) {
                        door.getTownDoor().sendSpawnData(pchar.client);
                    }
                }
            }
        }

        if (partyLeaver != null) {
            Collection<MapleDoor> leaverDoors = partyLeaver.getDoors();

            if (partyDoors != null) {
                for (MapleDoor door : partyDoors.values()) {
                    door.getTownDoor().sendDestroyData(partyLeaver.client, true);
                }
            }

            for (MapleDoor door : leaverDoors) {
                door.getTownDoor().sendDestroyData(partyLeaver.client, true);
            }

            for (MapleDoor door : leaverDoors) {
                door.updateDoorPortal(partyLeaver);
                door.getTownDoor().sendSpawnData(partyLeaver.client);
            }
        }
    }

    private Integer getVisitedMapIndex(MapleMap map) {
        int idx = 0;

        for (WeakReference<MapleMap> mapRef : lastVisitedMaps) {
            if (map.equals(mapRef.get())) {
                return idx;
            }

            idx++;
        }

        return -1;
    }

    public void visitMap(MapleMap map) {
        petLock.lock();
        try {
            int idx = getVisitedMapIndex(map);

            if (idx == -1) {
                if (lastVisitedMaps.size() == ServerConstants.MAP_VISITED_SIZE) {
                    lastVisitedMaps.remove(0);
                }
            } else {
                WeakReference<MapleMap> mapRef = lastVisitedMaps.remove(idx);
                lastVisitedMaps.add(mapRef);
                return;
            }

            lastVisitedMaps.add(new WeakReference<>(map));
        } finally {
            petLock.unlock();
        }
    }

    public void notifyMapTransferToPartner(int mapid) {
        if (partnerId > 0) {
            final MapleCharacter partner =
                    getWorldServer().getPlayerStorage().getCharacterById(partnerId);
            if (partner != null && !partner.isAwayFromWorld()) {
                partner.announce(Wedding.OnNotifyWeddingPartnerTransfer(id, mapid));
            }
        }
    }

    private void changeMapInternal(final MapleMap to, final Point pos, final byte[] warpPacket) {
        if (!canWarpMap) return;

        this.mapTransitioning.set(true);

        this.unregisterChairBuff();
        this.clearBanishPlayerData();
        this.closePlayerInteractions();
        this.resetPlayerAggro();

        client.announce(warpPacket);
        map.removePlayer(this);
        if (client.getChannelServer().getPlayerStorage().getCharacterById(id) != null) {
            map = to;
            setPosition(pos);
            map.addPlayer(this);
            visitMap(map);

            prtLock.lock();
            try {
                if (party != null) {
                    mpc.setMapId(to.getId());
                    silentPartyUpdateInternal();
                    client.announce(
                            MaplePacketCreator.updateParty(
                                    client.getChannel(),
                                    party,
                                    PartyOperation.SILENT_UPDATE,
                                    null));
                    updatePartyMemberHPInternal();
                }
            } finally {
                prtLock.unlock();
            }

            if (map.getHPDec() > 0) resetHpDecreaseTask();
        } else {
            FilePrinter.printError(
                    FilePrinter.MAPLE_MAP,
                    "Character " + name + " got stuck when moving to map " + map.getId() + '.');
        }

        notifyMapTransferToPartner(map.getId());

        // alas, new map has been specified when a warping was being processed...
        if (newWarpMap != -1) {
            canWarpMap = true;

            int temp = newWarpMap;
            newWarpMap = -1;
            changeMap(temp);
        } else {
            // if this event map has a gate already opened, render it
            EventInstanceManager eim = getEventInstance();
            if (eim != null) {
                eim.recoverOpenedGate(this, map.getId());
            }

            // if this map has obstacle components moving, make it do so for this client
            announce(MaplePacketCreator.environmentMoveList(map.getEnvironment().entrySet()));
        }
    }

    public boolean isChangingMaps() {
        return this.mapTransitioning.get();
    }

    public void setMapTransitionComplete() {
        this.mapTransitioning.set(false);
    }

    public void changePage(int page) {
        this.currentPage = page;
    }

    public void changeSkillLevel(Skill skill, byte newLevel, int newMasterlevel, long expiration) {
        if (newLevel > -1) {
            skills.put(skill, new SkillEntry(newLevel, newMasterlevel, expiration));
            if (!GameConstants.isHiddenSkills(skill.getId())) {
                this.client.announce(
                        MaplePacketCreator.updateSkill(
                                skill.getId(), newLevel, newMasterlevel, expiration));
            }
        } else {
            skills.remove(skill);
            this.client.announce(
                    MaplePacketCreator.updateSkill(
                            skill.getId(),
                            newLevel,
                            newMasterlevel,
                            -1)); // Shouldn't use expiration anymore :)
            try {
                try (Connection con = DatabaseConnection.getConnection();
                        PreparedStatement ps =
                                con.prepareStatement(
                                        "DELETE FROM skills WHERE skillid = ? AND characterid = ?")) {
                    ps.setInt(1, skill.getId());
                    ps.setInt(2, id);
                    ps.execute();
                }
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
        }
    }

    public void changeTab(int tab) {
        this.currentTab = tab;
    }

    public void changeType(int type) {
        this.currentType = type;
    }

    public void checkBerserk(final boolean isHidden) {
        if (berserkSchedule != null) {
            berserkSchedule.cancel(false);
        }
        final MapleCharacter chr = this;
        if (job.equals(MapleJob.DARKKNIGHT)) {
            var BerserkX = SkillFactory.getSkill(DarkKnight.BERSERK);
            assert BerserkX != null;
            final int skilllevel = getSkillLevel(BerserkX);
            if (skilllevel > 0) {
                berserk =
                        chr.getHp() * 100 / chr.getCurrentMaxHp()
                                < BerserkX.getEffect(skilllevel).getX();
                berserkSchedule =
                        TimerManager.getInstance()
                                .register(
                                        () -> {
                                            if (awayFromWorld.get()) return;

                                            client.announce(
                                                    MaplePacketCreator.showOwnBerserk(
                                                            skilllevel, berserk));
                                            if (!isHidden)
                                                getMap().broadcastMessage(
                                                                MapleCharacter.this,
                                                                MaplePacketCreator.showBerserk(
                                                                        getId(),
                                                                        skilllevel,
                                                                        berserk),
                                                                false);
                                            else
                                                getMap().broadcastGMMessage(
                                                                MapleCharacter.this,
                                                                MaplePacketCreator.showBerserk(
                                                                        getId(),
                                                                        skilllevel,
                                                                        berserk),
                                                                false);
                                        },
                                        5000,
                                        3000);
            }
        }
    }

    public void checkMessenger() {
        if (messenger != null && messengerposition < 4 && messengerposition > -1) {
            World worldz = getWorldServer();
            worldz.silentJoinMessenger(
                    messenger.getId(),
                    new MapleMessengerCharacter(this, messengerposition),
                    messengerposition);
            worldz.updateMessenger(messenger.getId(), name, client.getChannel());
        }
    }

    public void checkMonsterAggro(MapleMonster monster) {
        if (!monster.isControllerHasAggro()) {
            if (monster.getController() == this) {
                monster.setControllerHasAggro(true);
            } else {
                monster.switchController(this, true);
            }
        }
    }

    public void controlMonster(MapleMonster monster, boolean aggro) {
        monster.setController(this);
        controlled.add(monster);
        client.announce(MaplePacketCreator.controlMonster(monster, false, aggro));
    }

    private boolean useItem(final int id) {
        if (id / 1000000 == 2) {
            if (ii.isConsumeOnPickup(id)) {
                if (ItemConstants.isPartyItem(id)) {
                    List<MapleCharacter> pchr = this.getPartyMembersOnSameMap();

                    if (!ItemConstants.isPartyAllcure(id)) {
                        MapleStatEffect mse = ii.getItemEffect(id);

                        if (!pchr.isEmpty()) {
                            for (MapleCharacter mc : pchr) {
                                mse.applyTo(mc);
                            }
                        } else {
                            mse.applyTo(this);
                        }
                    } else {
                        if (!pchr.isEmpty()) {
                            for (MapleCharacter mc : pchr) {
                                mc.dispelDebuffs();
                            }
                        } else {
                            this.dispelDebuffs();
                        }
                    }
                } else {
                    ii.getItemEffect(id).applyTo(this);
                }
                return true;
            }
        }
        return false;
    }

    public final void pickupItem(MapleMapObject ob) {
        pickupItem(ob, -1);
    }

    public final void pickupItem(
            MapleMapObject ob,
            int petIndex) { // yes, one picks the MapleMapObject, not the MapleMapItem
        if (ob == null) { // pet index refers to the one picking up the item
            return;
        }

        if (ob instanceof MapleMapItem) {
            MapleMapItem mapitem = (MapleMapItem) ob;
            if (Server.getInstance().getCurrentTime() - mapitem.getDropTime() < 900
                    || !mapitem.canBePickedBy(this)) {
                client.announce(MaplePacketCreator.enableActions());
                return;
            }

            List<MapleCharacter> mpcs = new ArrayList<>();
            if (mapitem.getMeso() > 0 && !mapitem.isPickedUp()) {
                mpcs = getPartyMembersOnSameMap();
            }

            mapitem.lockItem();
            try {
                if (mapitem.isPickedUp()) {
                    client.announce(MaplePacketCreator.showItemUnavailable());
                    client.announce(MaplePacketCreator.enableActions());
                    return;
                }

                boolean isPet = petIndex > -1;
                final byte[] pickupPacket =
                        MaplePacketCreator.removeItemFromMap(
                                mapitem.getObjectId(), (isPet) ? 5 : 2, id, isPet, petIndex);

                Item mItem = mapitem.getItem();
                boolean hasSpaceInventory = true;
                if (mapitem.getItemId() == 4031865
                        || mapitem.getItemId() == 4031866
                        || mapitem.getMeso() > 0
                        || ii.isConsumeOnPickup(mapitem.getItemId())
                        || (hasSpaceInventory =
                                MapleInventoryManipulator.checkSpace(
                                        client,
                                        mapitem.getItemId(),
                                        mItem.getQuantity(),
                                        mItem.getOwner()))) {
                    int mapId = this.getMapId();

                    if ((mapId > 209000000 && mapId < 209000016)
                            || (mapId >= 990000500
                                    && mapId <= 990000502)) { // happyville trees and guild PQ
                        if (!mapitem.isPlayerDrop()
                                || mapitem.getDropper().getObjectId()
                                        == client.getPlayer().getObjectId()) {
                            if (mapitem.getMeso() > 0) {
                                if (!mpcs.isEmpty()) {
                                    int mesosamm = mapitem.getMeso() / mpcs.size();
                                    for (MapleCharacter partymem : mpcs) {
                                        if (partymem.isLoggedinWorld()) {
                                            partymem.gainMeso(mesosamm, true, true, false);
                                        }
                                    }
                                } else {
                                    this.gainMeso(mapitem.getMeso(), true, true, false);
                                }

                                map.pickItemDrop(pickupPacket, mapitem);
                            } else if (mapitem.getItemId() == 4031865
                                    || mapitem.getItemId() == 4031866) {
                                // Add NX to account, show effect and make item disappear
                                int nxGain = mapitem.getItemId() == 4031865 ? 100 : 250;
                                cashshop.gainCash(1, nxGain);

                                showHint(
                                        "You have earned #e#b"
                                                + nxGain
                                                + " NX#k#n. ("
                                                + cashshop.getCash(1)
                                                + " NX)",
                                        300);

                                map.pickItemDrop(pickupPacket, mapitem);
                            } else if (MapleInventoryManipulator.addFromDrop(client, mItem, true)) {
                                map.pickItemDrop(pickupPacket, mapitem);
                            } else {
                                client.announce(MaplePacketCreator.enableActions());
                                return;
                            }
                        } else {
                            client.announce(MaplePacketCreator.showItemUnavailable());
                            client.announce(MaplePacketCreator.enableActions());
                            return;
                        }
                        client.announce(MaplePacketCreator.enableActions());
                        return;
                    }

                    if (!this.needQuestItem(mapitem.getQuest(), mapitem.getItemId())) {
                        client.announce(MaplePacketCreator.showItemUnavailable());
                        client.announce(MaplePacketCreator.enableActions());
                        return;
                    }

                    if (mapitem.getMeso() > 0) {
                        if (!mpcs.isEmpty()) {
                            int mesosamm = mapitem.getMeso() / mpcs.size();
                            for (MapleCharacter partymem : mpcs) {
                                if (partymem.isLoggedinWorld()) {
                                    partymem.gainMeso(mesosamm, true, true, false);
                                }
                            }
                        } else {
                            this.gainMeso(mapitem.getMeso(), true, true, false);
                        }
                    } else if (mItem.getItemId() / 10000 == 243) {
                        MapleItemInformationProvider.scriptedItem info =
                                ii.getScriptedItemInfo(mItem.getItemId());
                        if (info.runOnPickup()) {
                            ItemScriptManager ism = ItemScriptManager.getInstance();
                            String scriptName = info.getScript();
                            if (ItemScriptManager.scriptExists(scriptName)) {
                                ism.getItemScript(client, scriptName);
                            }

                        } else {
                            if (!MapleInventoryManipulator.addFromDrop(client, mItem, true)) {
                                client.announce(MaplePacketCreator.enableActions());
                                return;
                            }
                        }
                    } else if (mapitem.getItemId() == 4031865 || mapitem.getItemId() == 4031866) {
                        // Add NX to account, show effect and make item disappear
                        int nxGain = mapitem.getItemId() == 4031865 ? 100 : 250;
                        cashshop.gainCash(1, nxGain);

                        showHint(
                                "You have earned #e#b"
                                        + nxGain
                                        + " NX#k#n. ("
                                        + cashshop.getCash(1)
                                        + " NX)",
                                300);
                    } else if (useItem(mItem.getItemId())) {
                        if (mItem.getItemId() / 10000 == 238) {
                            monsterbook.addCard(client, mItem.getItemId());
                        }
                    } else if (MapleInventoryManipulator.addFromDrop(client, mItem, true)) {
                    } else if (mItem.getItemId() == 4031868) {
                        map.broadcastMessage(
                                MaplePacketCreator.updateAriantPQRanking(
                                        name, this.getItemQuantity(4031868, false), false));
                    } else {
                        client.announce(MaplePacketCreator.enableActions());
                        return;
                    }

                    map.pickItemDrop(pickupPacket, mapitem);
                } else if (!hasSpaceInventory) {
                    client.announce(MaplePacketCreator.getInventoryFull());
                    client.announce(MaplePacketCreator.getShowInventoryFull());
                }
            } finally {
                mapitem.unlockItem();
            }
        }
        client.announce(MaplePacketCreator.enableActions());
    }

    public int countItem(int itemid) {
        return inventory[ItemConstants.getInventoryType(itemid).ordinal()].countById(itemid);
    }

    public boolean canHold(int itemid) {
        return canHold(itemid, 1);
    }

    public boolean canHold(int itemid, int quantity) {
        int hold = getCleanItemQuantity(itemid, false);

        if (hold > 0) {
            if (hold + quantity <= ii.getSlotMax(client, itemid)) return true;
        }

        return getInventory(ItemConstants.getInventoryType(itemid)).getNextFreeSlot() > -1;
    }

    public boolean isRidingBattleship() {
        Integer bv = getBuffedValue(MapleBuffStat.MONSTER_RIDING);
        return Integer.valueOf(Corsair.BATTLE_SHIP).equals(bv);
    }

    public void announceBattleshipHp() {
        announce(MaplePacketCreator.skillCooldown(5221999, battleshipHp));
    }

    public void decreaseBattleshipHp(int decrease) {
        this.battleshipHp -= decrease;
        if (battleshipHp <= 0) {
            Skill battleship = SkillFactory.getSkill(Corsair.BATTLE_SHIP);
            int cooldown = battleship.getEffect(getSkillLevel(battleship)).getCooldown();
            announce(MaplePacketCreator.skillCooldown(Corsair.BATTLE_SHIP, cooldown));
            addCooldown(Corsair.BATTLE_SHIP, System.currentTimeMillis(), (cooldown * 1000));
            removeCooldown(5221999);
            cancelEffectFromBuffStat(MapleBuffStat.MONSTER_RIDING);
        } else {
            announceBattleshipHp();
            addCooldown(5221999, 0, Long.MAX_VALUE);
        }
    }

    public void decreaseReports() {
        this.possibleReports--;
    }

    public void deleteGuild(int guildId) {
        try {
            try (Connection con = DatabaseConnection.getConnection()) {
                try (PreparedStatement ps =
                        con.prepareStatement(
                                "UPDATE characters SET guildid = 0, guildrank = 5 WHERE guildid = ?")) {
                    ps.setInt(1, guildId);
                    ps.execute();
                }
                try (PreparedStatement ps =
                        con.prepareStatement("DELETE FROM guilds WHERE guildid = ?")) {
                    ps.setInt(1, id);
                    ps.execute();
                }
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    private static void nextPendingRequest(MapleClient c) {
        CharacterNameAndId pendingBuddyRequest = c.getPlayer().buddylist.pollPendingRequest();
        if (pendingBuddyRequest != null) {
            c.announce(
                    MaplePacketCreator.requestBuddylistAdd(
                            pendingBuddyRequest.getId(),
                            c.getPlayer().id,
                            pendingBuddyRequest.getName()));
        }
    }

    private static void notifyRemoteChannel(
            MapleClient c, int remoteChannel, int otherCid, BuddyList.BuddyOperation operation) {
        MapleCharacter player = c.getPlayer();
        if (remoteChannel != -1) {
            c.getWorldServer()
                    .buddyChanged(otherCid, player.id, player.name, c.getChannel(), operation);
        }
    }

    public void deleteBuddy(int otherCid) {
        BuddyList bl = buddylist;

        if (bl.containsVisible(otherCid)) {
            notifyRemoteChannel(
                    client,
                    getWorldServer().find(otherCid),
                    otherCid,
                    BuddyList.BuddyOperation.DELETED);
        }
        bl.remove(otherCid);
        client.announce(MaplePacketCreator.updateBuddylist(buddylist.getBuddies()));
        nextPendingRequest(client);
    }

    public static boolean deleteCharFromDB(MapleCharacter player, int senderAccId) {
        int cid = player.id;
        if (!Server.getInstance().haveCharacterEntry(senderAccId, cid)) {
            return false;
        }

        int accId = senderAccId, world = 0;
        Connection con = null;
        try {
            con = DatabaseConnection.getConnection();

            try (PreparedStatement ps =
                    con.prepareStatement("SELECT world FROM characters WHERE id = ?")) {
                ps.setInt(1, cid);

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        world = rs.getInt("world");
                    }
                }
            }

            try (PreparedStatement ps =
                    con.prepareStatement("SELECT buddyid FROM buddies WHERE characterid = ?")) {
                ps.setInt(1, cid);

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int buddyid = rs.getInt("buddyid");
                        MapleCharacter buddy =
                                Server.getInstance()
                                        .getWorld(world)
                                        .getPlayerStorage()
                                        .getCharacterById(buddyid);

                        if (buddy != null) {
                            buddy.deleteBuddy(cid);
                        }
                    }
                }
            }
            try (PreparedStatement ps =
                    con.prepareStatement("DELETE FROM buddies WHERE characterid = ?")) {
                ps.setInt(1, cid);
                ps.executeUpdate();
            }

            try (PreparedStatement ps =
                    con.prepareStatement("SELECT threadid FROM bbs_threads WHERE postercid = ?")) {
                ps.setInt(1, cid);

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int tid = rs.getInt("threadid");

                        try (PreparedStatement ps2 =
                                con.prepareStatement(
                                        "DELETE FROM bbs_replies WHERE threadid = ?")) {
                            ps2.setInt(1, tid);
                            ps2.executeUpdate();
                        }
                    }
                }
            }
            try (PreparedStatement ps =
                    con.prepareStatement("DELETE FROM bbs_threads WHERE postercid = ?")) {
                ps.setInt(1, cid);
                ps.executeUpdate();
            }

            try (PreparedStatement ps =
                    con.prepareStatement(
                            "SELECT id, guildid, guildrank, name, allianceRank FROM characters WHERE id = ? AND accountid = ?")) {
                ps.setInt(1, cid);
                ps.setInt(2, accId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next() && rs.getInt("guildid") > 0) {
                        Server.getInstance()
                                .deleteGuildCharacter(
                                        new MapleGuildCharacter(
                                                player,
                                                cid,
                                                0,
                                                rs.getString("name"),
                                                -1,
                                                -1,
                                                0,
                                                rs.getInt("guildrank"),
                                                rs.getInt("guildid"),
                                                false,
                                                rs.getInt("allianceRank")));
                    }
                }
            }

            if (con.isClosed()) con = DatabaseConnection.getConnection(); // wtf tho

            try (PreparedStatement ps =
                    con.prepareStatement("DELETE FROM wishlists WHERE charid = ?")) {
                ps.setInt(1, cid);
                ps.executeUpdate();
            }
            try (PreparedStatement ps =
                    con.prepareStatement("DELETE FROM cooldowns WHERE charid = ?")) {
                ps.setInt(1, cid);
                ps.executeUpdate();
            }
            try (PreparedStatement ps =
                    con.prepareStatement("DELETE FROM playerdiseases WHERE charid = ?")) {
                ps.setInt(1, cid);
                ps.executeUpdate();
            }
            try (PreparedStatement ps =
                    con.prepareStatement("DELETE FROM area_info WHERE charid = ?")) {
                ps.setInt(1, cid);
                ps.executeUpdate();
            }
            try (PreparedStatement ps =
                    con.prepareStatement("DELETE FROM monsterbook WHERE charid = ?")) {
                ps.setInt(1, cid);
                ps.executeUpdate();
            }
            try (PreparedStatement ps =
                    con.prepareStatement("DELETE FROM characters WHERE id = ?")) {
                ps.setInt(1, cid);
                ps.executeUpdate();
            }
            try (PreparedStatement ps =
                    con.prepareStatement("DELETE FROM famelog WHERE characterid_to = ?")) {
                ps.setInt(1, cid);
                ps.executeUpdate();
            }

            try (PreparedStatement ps =
                    con.prepareStatement(
                            "SELECT inventoryitemid, petid FROM inventoryitems WHERE characterid = ?")) {
                ps.setInt(1, cid);

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int inventoryitemid = rs.getInt("inventoryitemid");

                        try (PreparedStatement ps2 =
                                con.prepareStatement(
                                        "SELECT ringid FROM inventoryequipment WHERE inventoryitemid = ?")) {
                            ps2.setInt(1, inventoryitemid);

                            try (ResultSet rs2 = ps2.executeQuery()) {
                                while (rs2.next()) {
                                    int ringid = rs2.getInt("ringid");

                                    if (ringid > -1) {
                                        try (PreparedStatement ps3 =
                                                con.prepareStatement(
                                                        "DELETE FROM rings WHERE id = ?")) {
                                            ps3.setInt(1, ringid);
                                            ps3.executeUpdate();
                                        }
                                    }
                                }
                            }
                        }

                        try (PreparedStatement ps2 =
                                con.prepareStatement(
                                        "DELETE FROM inventoryequipment WHERE inventoryitemid = ?")) {
                            ps2.setInt(1, inventoryitemid);
                            ps2.executeUpdate();
                        }

                        if (rs.getInt("petid") > -1) {
                            try (PreparedStatement ps2 =
                                    con.prepareStatement("DELETE FROM pets WHERE petid = ?")) {
                                ps2.setInt(1, rs.getInt("petid"));
                                ps2.executeUpdate();
                            }
                        }
                    }
                }
            }

            deleteQuestProgressWhereCharacterId(con, cid);

            try (PreparedStatement ps =
                    con.prepareStatement("SELECT id FROM mts_cart WHERE cid = ?")) {
                ps.setInt(1, cid);

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int mtsid = rs.getInt("id");

                        try (PreparedStatement ps2 =
                                con.prepareStatement("DELETE FROM mts_items WHERE id = ?")) {
                            ps2.setInt(1, mtsid);
                            ps2.executeUpdate();
                        }
                    }
                }
            }
            try (PreparedStatement ps =
                    con.prepareStatement("DELETE FROM mts_cart WHERE cid = ?")) {
                ps.setInt(1, cid);
                ps.executeUpdate();
            }

            String[] toDel = {
                "famelog",
                "inventoryitems",
                "keymap",
                "queststatus",
                "savedlocations",
                "trocklocations",
                "skillmacros",
                "skills",
                "eventstats",
                "server_queue"
            };
            for (String s : toDel) {
                MapleCharacter.deleteWhereCharacterId(
                        con, "DELETE FROM `" + s + "` WHERE characterid = ?", cid);
            }

            con.close();
            Server.getInstance().deleteCharacterEntry(accId, cid);
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    private static void deleteQuestProgressWhereCharacterId(Connection con, int cid)
            throws SQLException {
        try (PreparedStatement ps =
                con.prepareStatement("DELETE FROM medalmaps WHERE characterid = ?")) {
            ps.setInt(1, cid);
            ps.executeUpdate();
        }

        try (PreparedStatement ps =
                con.prepareStatement("DELETE FROM questprogress WHERE characterid = ?")) {
            ps.setInt(1, cid);
            ps.executeUpdate();
        }

        try (PreparedStatement ps =
                con.prepareStatement("DELETE FROM queststatus WHERE characterid = ?")) {
            ps.setInt(1, cid);
            ps.executeUpdate();
        }
    }

    private void deleteWhereCharacterId(Connection con, String sql) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    public static void deleteWhereCharacterId(Connection con, String sql, int cid)
            throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, cid);
            ps.executeUpdate();
        }
    }

    private void stopChairTask() {
        chrLock.lock();
        try {
            if (chairRecoveryTask != null) {
                chairRecoveryTask.cancel(false);
                chairRecoveryTask = null;
            }
        } finally {
            chrLock.unlock();
        }
    }

    private static Pair<Integer, Pair<Integer, Integer>> getChairTaskIntervalRate(
            int maxhp, int maxmp) {
        float toHeal = Math.max(maxhp, maxmp);
        float maxDuration = ServerConstants.CHAIR_EXTRA_HEAL_MAX_DELAY * 1000;

        int rate = 0;
        int minRegen = 1,
                maxRegen = (256 * ServerConstants.CHAIR_EXTRA_HEAL_MULTIPLIER) - 1,
                midRegen = 1;
        while (minRegen < maxRegen) {
            midRegen = (int) ((minRegen + maxRegen) * 0.94);

            float procs = toHeal / midRegen;
            float newRate = maxDuration / procs;
            rate = (int) newRate;

            if (newRate < 420) {
                minRegen = (int) (1.2 * midRegen);
            } else if (newRate > 5000) {
                maxRegen = (int) (0.8 * midRegen);
            } else {
                break;
            }
        }

        float procs = maxDuration / rate;
        int hpRegen, mpRegen;
        if (maxhp > maxmp) {
            hpRegen = midRegen;
            mpRegen = (int) Math.ceil(maxmp / procs);
        } else {
            hpRegen = (int) Math.ceil(maxhp / procs);
            mpRegen = midRegen;
        }

        return new Pair<>(rate, new Pair<>(hpRegen, mpRegen));
    }

    private void updateChairHealStats() {
        statRlock.lock();
        try {
            if (localchairrate != -1) {
                return;
            }
        } finally {
            statRlock.unlock();
        }

        effLock.lock();
        statWlock.lock();
        try {
            Pair<Integer, Pair<Integer, Integer>> p =
                    getChairTaskIntervalRate(localmaxhp, localmaxmp);

            localchairrate = p.getLeft();
            localchairhp = p.getRight().getLeft();
            localchairmp = p.getRight().getRight();
        } finally {
            statWlock.unlock();
            effLock.unlock();
        }
    }

    private void startChairTask() {
        if (chair.get() == 0) return;

        int healInterval;
        effLock.lock();
        try {
            updateChairHealStats();
            healInterval = localchairrate;
        } finally {
            effLock.unlock();
        }

        chrLock.lock();
        try {
            if (chairRecoveryTask != null) {
                stopChairTask();
            }

            chairRecoveryTask =
                    TimerManager.getInstance()
                            .register(
                                    () -> {
                                        updateChairHealStats();
                                        final int healHP = localchairhp;
                                        final int healMP = localchairmp;

                                        if (MapleCharacter.this.getHp() < localmaxhp) {
                                            byte recHP =
                                                    (byte)
                                                            (healHP
                                                                    / ServerConstants
                                                                            .CHAIR_EXTRA_HEAL_MULTIPLIER);

                                            client.announce(
                                                    MaplePacketCreator.showOwnRecovery(recHP));
                                            getMap().broadcastMessage(
                                                            MapleCharacter.this,
                                                            MaplePacketCreator.showRecovery(
                                                                    id, recHP),
                                                            false);
                                        } else if (MapleCharacter.this.getMp() >= localmaxmp) {
                                            stopChairTask(); // optimizing schedule management
                                            // when player is already with full
                                            // pool.
                                        }

                                        addMPHP(healHP, healMP);
                                    },
                                    healInterval,
                                    healInterval);
        } finally {
            chrLock.unlock();
        }
    }

    private void stopExtraTask() {
        chrLock.lock();
        try {
            if (extraRecoveryTask != null) {
                extraRecoveryTask.cancel(false);
                extraRecoveryTask = null;
            }
        } finally {
            chrLock.unlock();
        }
    }

    private void startExtraTask(final byte healHP, final byte healMP, final short healInterval) {
        chrLock.lock();
        try {
            startExtraTaskInternal(healHP, healMP, healInterval);
        } finally {
            chrLock.unlock();
        }
    }

    private void startExtraTaskInternal(
            final byte healHP, final byte healMP, final short healInterval) {
        extraRecInterval = healInterval;

        extraRecoveryTask =
                TimerManager.getInstance()
                        .register(
                                () -> {
                                    if (getBuffSource(MapleBuffStat.HPREC) == -1
                                            && getBuffSource(MapleBuffStat.MPREC) == -1) {
                                        stopExtraTask();
                                        return;
                                    }

                                    if (MapleCharacter.this.getHp() < localmaxhp) {
                                        if (healHP > 0) {
                                            client.announce(
                                                    MaplePacketCreator.showOwnRecovery(healHP));
                                            getMap().broadcastMessage(
                                                            MapleCharacter.this,
                                                            MaplePacketCreator.showRecovery(
                                                                    id, healHP),
                                                            false);
                                        }
                                    }

                                    addMPHP(healHP, healMP);
                                },
                                healInterval,
                                healInterval);
    }

    public void disableDoorSpawn() {
        canDoor = false;

        Runnable r = () -> canDoor = true;

        client.getChannelServer().registerOverallAction(mapid, r, 5000);
    }

    public void disbandGuild() {
        if (guildid < 1 || guildRank != 1) {
            return;
        }
        try {
            Server.getInstance().disbandGuild(guildid);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void dispel() {
        if (!(ServerConstants.USE_UNDISPEL_HOLY_SHIELD
                && this.isActiveBuffedValue(Bishop.HOLY_SHIELD))) {
            List<MapleBuffStatValueHolder> mbsvhList = getAllStatups();
            for (MapleBuffStatValueHolder mbsvh : mbsvhList) {
                if (mbsvh.effect.isSkill()) {
                    if (mbsvh.effect.getBuffSourceId()
                            != Aran.COMBO_ABILITY) { // check discovered thanks to Croosade dev team
                        cancelEffect(mbsvh.effect, false, mbsvh.startTime);
                    }
                }
            }
        }
    }

    public final boolean hasDisease(final MapleDisease dis) {
        chrLock.lock();
        try {
            return diseases.containsKey(dis);
        } finally {
            chrLock.unlock();
        }
    }

    public final int getDiseasesSize() {
        chrLock.lock();
        try {
            return diseases.size();
        } finally {
            chrLock.unlock();
        }
    }

    public Map<MapleDisease, Pair<Long, MobSkill>> getAllDiseases() {
        chrLock.lock();
        try {
            long curtime = Server.getInstance().getCurrentTime();
            Map<MapleDisease, Pair<Long, MobSkill>> ret = new LinkedHashMap<>();

            for (Entry<MapleDisease, Long> de : diseaseExpires.entrySet()) {
                Pair<MapleDiseaseValueHolder, MobSkill> dee = diseases.get(de.getKey());
                MapleDiseaseValueHolder mdvh = dee.getLeft();

                ret.put(
                        de.getKey(),
                        new Pair<>(mdvh.length - (curtime - mdvh.startTime), dee.getRight()));
            }

            return ret;
        } finally {
            chrLock.unlock();
        }
    }

    public void silentApplyDiseases(Map<MapleDisease, Pair<Long, MobSkill>> diseaseMap) {
        chrLock.lock();
        try {
            long curTime = Server.getInstance().getCurrentTime();

            for (Entry<MapleDisease, Pair<Long, MobSkill>> di : diseaseMap.entrySet()) {
                long expTime = curTime + di.getValue().getLeft();

                diseaseExpires.put(di.getKey(), expTime);
                diseases.put(
                        di.getKey(),
                        new Pair<>(
                                new MapleDiseaseValueHolder(curTime, di.getValue().getLeft()),
                                di.getValue().getRight()));
            }
        } finally {
            chrLock.unlock();
        }
    }

    public void announceDiseases() {
        Set<Entry<MapleDisease, Pair<MapleDiseaseValueHolder, MobSkill>>> chrDiseases;

        chrLock.lock();
        try {
            // Poison damage visibility and diseases status visibility, extended through map
            // transitions thanks to Ronan
            if (!this.isLoggedinWorld()) return;

            chrDiseases = new LinkedHashSet<>(diseases.entrySet());
        } finally {
            chrLock.unlock();
        }

        for (Entry<MapleDisease, Pair<MapleDiseaseValueHolder, MobSkill>> di : chrDiseases) {
            MapleDisease disease = di.getKey();
            MobSkill skill = di.getValue().getRight();
            final List<Pair<MapleDisease, Integer>> debuff =
                    Collections.singletonList(new Pair<>(disease, skill.getX()));

            if (disease != MapleDisease.SLOW)
                map.broadcastMessage(MaplePacketCreator.giveForeignDebuff(id, debuff, skill));
            else map.broadcastMessage(MaplePacketCreator.giveForeignSlowDebuff(id, debuff, skill));
        }
    }

    public void giveDebuff(final MapleDisease disease, MobSkill skill) {
        if (!hasDisease(disease) && getDiseasesSize() < 2) {
            if (!(disease == MapleDisease.SEDUCE || disease == MapleDisease.STUN)) {
                if (isActiveBuffedValue(Bishop.HOLY_SHIELD)) {
                    return;
                }
            }

            chrLock.lock();
            try {
                long curTime = Server.getInstance().getCurrentTime();
                diseaseExpires.put(disease, curTime + skill.getDuration());
                diseases.put(
                        disease,
                        new Pair<>(
                                new MapleDiseaseValueHolder(curTime, skill.getDuration()), skill));
            } finally {
                chrLock.unlock();
            }

            final List<Pair<MapleDisease, Integer>> debuff =
                    Collections.singletonList(new Pair<>(disease, skill.getX()));
            client.announce(MaplePacketCreator.giveDebuff(debuff, skill));

            if (disease != MapleDisease.SLOW)
                map.broadcastMessage(
                        this, MaplePacketCreator.giveForeignDebuff(id, debuff, skill), false);
            else
                map.broadcastMessage(
                        this, MaplePacketCreator.giveForeignSlowDebuff(id, debuff, skill), false);
        }
    }

    public void dispelDebuff(MapleDisease debuff) {
        if (hasDisease(debuff)) {
            long mask = debuff.getValue();
            announce(MaplePacketCreator.cancelDebuff(mask));

            if (debuff != MapleDisease.SLOW)
                map.broadcastMessage(this, MaplePacketCreator.cancelForeignDebuff(id, mask), false);
            else map.broadcastMessage(this, MaplePacketCreator.cancelForeignSlowDebuff(id), false);

            chrLock.lock();
            try {
                diseases.remove(debuff);
                diseaseExpires.remove(debuff);
            } finally {
                chrLock.unlock();
            }
        }
    }

    public void dispelDebuffs() {
        dispelDebuff(MapleDisease.CURSE);
        dispelDebuff(MapleDisease.DARKNESS);
        dispelDebuff(MapleDisease.POISON);
        dispelDebuff(MapleDisease.SEAL);
        dispelDebuff(MapleDisease.WEAKEN);
        dispelDebuff(MapleDisease.SLOW);
    }

    public void cancelAllDebuffs() {
        chrLock.lock();
        try {
            diseases.clear();
            diseaseExpires.clear();
        } finally {
            chrLock.unlock();
        }
    }

    public void dispelSkill(int skillid) {
        List<MapleBuffStatValueHolder> allBuffs = getAllStatups();
        for (MapleBuffStatValueHolder mbsvh : allBuffs) {
            if (skillid == 0) {
                if (mbsvh.effect.isSkill()
                        && (mbsvh.effect.getSourceId() % 10000000 == 1004
                                || dispelSkills(mbsvh.effect.getSourceId()))) {
                    cancelEffect(mbsvh.effect, false, mbsvh.startTime);
                }
            } else if (mbsvh.effect.isSkill() && mbsvh.effect.getSourceId() == skillid) {
                cancelEffect(mbsvh.effect, false, mbsvh.startTime);
            }
        }
    }

    private static boolean dispelSkills(int skillid) {
        switch (skillid) {
            case DarkKnight.BEHOLDER:
            case FPArchMage.ELQUINES:
            case ILArchMage.IFRIT:
            case Priest.SUMMON_DRAGON:
            case Bishop.BAHAMUT:
            case Ranger.PUPPET:
            case Ranger.SILVER_HAWK:
            case Sniper.PUPPET:
            case Sniper.GOLDEN_EAGLE:
            case Hermit.SHADOW_PARTNER:
                return true;
            default:
                return false;
        }
    }

    public void changeFaceExpression(int emote) {
        long timeNow = Server.getInstance().getCurrentTime();
        if (timeNow - lastExpression > 2000) {
            lastExpression = timeNow;
            client.getChannelServer().registerFaceExpression(map, this, emote);
        }
    }

    private void doHurtHp() {
        if (!(this.getInventory(MapleInventoryType.EQUIPPED).findById(map.getHPDecProtect()) != null
                || buffMapProtection())) {
            addHP(-map.getHPDec());
            lastHpDec = Server.getInstance().getCurrentTime();
        }
    }

    private void startHpDecreaseTask(long lastHpTask) {
        hpDecreaseTask =
                TimerManager.getInstance().register(this::doHurtHp, 10000, 10000 - lastHpTask);
    }

    public void resetHpDecreaseTask() {
        if (hpDecreaseTask != null) {
            hpDecreaseTask.cancel(false);
        }

        long lastHpTask = Server.getInstance().getCurrentTime() - lastHpDec;
        startHpDecreaseTask((lastHpTask > 10000) ? 10000 : lastHpTask);
    }

    public void dropMessage(String message) {
        dropMessage(0, message);
    }

    public void dropMessage(int type, String message) {
        client.announce(MaplePacketCreator.serverNotice(type, message));
    }

    public void enteredScript(String script, int mapid) {
        if (!entered.containsKey(mapid)) {
            entered.put(mapid, script);
        }
    }

    public void equipChanged() {
        map.broadcastMessage(this, MaplePacketCreator.updateCharLook(this), false);
        equipchanged = true;
        updateLocalStats();
        if (messenger != null) {
            getWorldServer().updateMessenger(messenger, name, world, client.getChannel());
        }
    }

    public void cancelDiseaseExpireTask() {
        if (diseaseExpireTask != null) {
            diseaseExpireTask.cancel(false);
            diseaseExpireTask = null;
        }
    }

    public void diseaseExpireTask() {
        if (diseaseExpireTask == null) {
            diseaseExpireTask =
                    TimerManager.getInstance()
                            .register(
                                    () -> {
                                        Set<MapleDisease> toExpire = new LinkedHashSet<>();

                                        chrLock.lock();
                                        try {
                                            long curTime = Server.getInstance().getCurrentTime();

                                            for (Entry<MapleDisease, Long> de :
                                                    diseaseExpires.entrySet()) {
                                                if (de.getValue() < curTime) {
                                                    toExpire.add(de.getKey());
                                                }
                                            }
                                        } finally {
                                            chrLock.unlock();
                                        }

                                        for (MapleDisease d : toExpire) {
                                            dispelDebuff(d);
                                        }
                                    },
                                    1500);
        }
    }

    public void cancelBuffExpireTask() {
        if (buffExpireTask != null) {
            buffExpireTask.cancel(false);
            buffExpireTask = null;
        }
    }

    public void buffExpireTask() {
        if (buffExpireTask == null) {
            buffExpireTask =
                    TimerManager.getInstance()
                            .register(
                                    () -> {
                                        Set<Entry<Integer, Long>> es;
                                        List<MapleBuffStatValueHolder> toCancel = new ArrayList<>();

                                        effLock.lock();
                                        chrLock.lock();
                                        try {
                                            es = new LinkedHashSet<>(buffExpires.entrySet());

                                            long curTime = Server.getInstance().getCurrentTime();
                                            for (Entry<Integer, Long> bel : es) {
                                                if (curTime >= bel.getValue()) {
                                                    toCancel.add(
                                                            buffEffects
                                                                    .get(bel.getKey())
                                                                    .entrySet()
                                                                    .iterator()
                                                                    .next()
                                                                    .getValue()); // rofl
                                                }
                                            }
                                        } finally {
                                            chrLock.unlock();
                                            effLock.unlock();
                                        }

                                        for (MapleBuffStatValueHolder mbsvh : toCancel) {
                                            cancelEffect(mbsvh.effect, false, mbsvh.startTime);
                                        }
                                    },
                                    1500);
        }
    }

    public void cancelSkillCooldownTask() {
        if (skillCooldownTask != null) {
            skillCooldownTask.cancel(false);
            skillCooldownTask = null;
        }
    }

    public void skillCooldownTask() {
        if (skillCooldownTask == null) {
            skillCooldownTask =
                    TimerManager.getInstance()
                            .register(
                                    () -> {
                                        Set<Entry<Integer, MapleCoolDownValueHolder>> es;

                                        effLock.lock();
                                        chrLock.lock();
                                        try {
                                            es = new LinkedHashSet<>(coolDowns.entrySet());
                                        } finally {
                                            chrLock.unlock();
                                            effLock.unlock();
                                        }

                                        long curTime = System.currentTimeMillis();
                                        for (Entry<Integer, MapleCoolDownValueHolder> bel : es) {
                                            MapleCoolDownValueHolder mcdvh = bel.getValue();
                                            if (curTime >= mcdvh.startTime + mcdvh.length) {
                                                removeCooldown(mcdvh.skillId);
                                                client.announce(
                                                        MaplePacketCreator.skillCooldown(
                                                                mcdvh.skillId, 0));
                                            }
                                        }
                                    },
                                    1500);
        }
    }

    public void cancelExpirationTask() {
        if (itemExpireTask != null) {
            itemExpireTask.cancel(false);
            itemExpireTask = null;
        }
    }

    public void expirationTask() {
        if (itemExpireTask == null) {
            itemExpireTask =
                    TimerManager.getInstance()
                            .register(
                                    () -> {
                                        boolean deletedCoupon = false;

                                        long expiration, currenttime = System.currentTimeMillis();
                                        Set<Skill> keys = getSkills().keySet();
                                        for (Skill key : keys) {
                                            SkillEntry skill = getSkills().get(key);
                                            if (skill.expiration != -1
                                                    && skill.expiration < currenttime) {
                                                changeSkillLevel(key, (byte) -1, 0, -1);
                                            }
                                        }

                                        List<Item> toberemove = new ArrayList<>();
                                        for (MapleInventory inv : inventory) {
                                            for (Item item : inv.list()) {
                                                expiration = item.getExpiration();

                                                if (expiration != -1
                                                        && (expiration < currenttime)
                                                        && ((item.getFlag() & ItemConstants.LOCK)
                                                                == ItemConstants.LOCK)) {
                                                    byte aids = item.getFlag();
                                                    aids &= ~(ItemConstants.LOCK);
                                                    item.setFlag(aids); // Probably need a check,
                                                    // else people can make
                                                    // expiring items into
                                                    // permanent items...
                                                    item.setExpiration(-1);
                                                    forceUpdateItem(item); // TEST :3
                                                } else if (expiration != -1
                                                        && expiration < currenttime) {
                                                    if (!ItemConstants.isPet(item.getItemId())) {
                                                        client.announce(
                                                                MaplePacketCreator.itemExpired(
                                                                        item.getItemId()));
                                                        toberemove.add(item);
                                                        if (ItemConstants.isRateCoupon(
                                                                item.getItemId())) {
                                                            deletedCoupon = true;
                                                        }
                                                    } else {
                                                        if (item.getPetId() > -1) {
                                                            int petIdx =
                                                                    getPetIndex(item.getPetId());
                                                            if (petIdx > -1)
                                                                unequipPet(getPet(petIdx), true);
                                                        }

                                                        if (ItemConstants.isExpirablePet(
                                                                item.getItemId())) {
                                                            client.announce(
                                                                    MaplePacketCreator.itemExpired(
                                                                            item.getItemId()));
                                                            toberemove.add(item);
                                                        } else {
                                                            item.setExpiration(-1);
                                                            forceUpdateItem(item);
                                                        }
                                                    }
                                                }
                                            }

                                            if (!toberemove.isEmpty()) {
                                                for (Item item : toberemove) {
                                                    MapleInventoryManipulator.removeFromSlot(
                                                            client,
                                                            inv.getType(),
                                                            item.getPosition(),
                                                            item.getQuantity(),
                                                            true);
                                                }

                                                MapleItemInformationProvider ii =
                                                        MapleItemInformationProvider.getInstance();
                                                for (Item item : toberemove) {
                                                    List<Integer> toadd = new ArrayList<>();
                                                    Pair<Integer, String> replace =
                                                            ii.getReplaceOnExpire(item.getItemId());
                                                    if (replace.left > 0) {
                                                        toadd.add(replace.left);
                                                        if (!replace.right.isEmpty()) {
                                                            dropMessage(replace.right);
                                                        }
                                                    }
                                                    for (Integer itemid : toadd) {
                                                        MapleInventoryManipulator.addById(
                                                                client, itemid, (short) 1);
                                                    }
                                                }

                                                toberemove.clear();
                                            }

                                            if (deletedCoupon) {
                                                updateCouponRates();
                                            }
                                        }
                                    },
                                    60000);
        }
    }

    public enum FameStatus {
        OK,
        NOT_TODAY,
        NOT_THIS_MONTH
    }

    public void forceUpdateItem(Item item) {
        final List<ModifyInventory> mods = new ArrayList<>();
        mods.add(new ModifyInventory(3, item));
        mods.add(new ModifyInventory(0, item));
        client.announce(MaplePacketCreator.modifyInventory(true, mods));
    }

    public void gainGachaExp() {
        int expgain = 0;
        int currentgexp = gachaexp.get();
        if ((currentgexp + exp.get()) >= ExpTable.getExpNeededForLevel(level)) {
            expgain += ExpTable.getExpNeededForLevel(level) - exp.get();
            int nextneed = ExpTable.getExpNeededForLevel(level + 1);
            if ((currentgexp - expgain) >= nextneed) {
                expgain += nextneed;
            }
            this.gachaexp.set(currentgexp - expgain);
        } else {
            expgain = this.gachaexp.getAndSet(0);
        }
        gainExp(expgain, false, false);
        updateSingleStat(MapleStat.GACHAEXP, this.gachaexp.get());
    }

    public void gainGachaExp(int gain) {
        updateSingleStat(MapleStat.GACHAEXP, gachaexp.addAndGet(gain));
    }

    public void gainExp(int gain) {
        gainExp(gain, true, true);
    }

    public void gainExp(int gain, boolean show, boolean inChat) {
        gainExp(gain, show, inChat, true);
    }

    public void gainExp(int gain, boolean show, boolean inChat, boolean white) {
        gainExp(gain, 0, show, inChat, white);
    }

    public void gainExp(int gain, int party, boolean show, boolean inChat, boolean white) {
        if (hasDisease(MapleDisease.CURSE)) {
            gain *= 0.5;
            party *= 0.5;
        }

        if (gain < 0) gain = Integer.MAX_VALUE; // integer overflow, heh.
        if (party < 0) party = Integer.MAX_VALUE; // integer overflow, heh.
        int equip = (int) Math.min((long) (gain / 10) * pendantExp, Integer.MAX_VALUE);

        long total = (long) gain + equip + party;
        gainExpInternal(total, equip, party, show, inChat, white);
    }

    public void loseExp(int loss, boolean show, boolean inChat) {
        loseExp(loss, show, inChat, true);
    }

    public void loseExp(int loss, boolean show, boolean inChat, boolean white) {
        gainExpInternal(-loss, 0, 0, show, inChat, white);
    }

    private void gainExpInternal(
            long gain, int equip, int party, boolean show, boolean inChat, boolean white) {
        long total = Math.max(gain, -exp.get());

        if (level < getMaxLevel()) {
            long leftover = 0;
            long nextExp = exp.get() + total;

            if (nextExp > Integer.MAX_VALUE) {
                total = Integer.MAX_VALUE - exp.get();
                leftover = nextExp - Integer.MAX_VALUE;
            }
            updateSingleStat(MapleStat.EXP, exp.addAndGet((int) total));
            if (show && gain != 0) {
                client.announce(
                        MaplePacketCreator.getShowExpGain(
                                (int) Math.min(gain, Integer.MAX_VALUE),
                                equip,
                                party,
                                inChat,
                                white));
            }
            while (exp.get() >= ExpTable.getExpNeededForLevel(level)) {
                levelUp(true);
                if (level == getMaxLevel()) {
                    setExp(0);
                    updateSingleStat(MapleStat.EXP, 0);
                    break;
                }
            }

            if (leftover > 0) gainExpInternal(leftover, equip, party, false, inChat, white);
        }
    }

    private synchronized int applyFame(int delta) {
        int newFame = fame + delta;
        if (newFame < -30000) {
            delta = -(30000 + fame);
        } else if (newFame > 30000) {
            delta = 30000 - fame;
        }

        fame += delta;
        return delta;
    }

    public void gainFame(int delta) {
        gainFame(delta, null, 0);
    }

    public boolean gainFame(int delta, MapleCharacter fromPlayer, int mode) {
        delta = applyFame(delta);
        if (delta != 0) {
            int thisFame = fame;
            updateSingleStat(MapleStat.FAME, thisFame);

            if (fromPlayer != null) {
                fromPlayer.announce(MaplePacketCreator.giveFameResponse(mode, name, thisFame));
                announce(MaplePacketCreator.receiveFame(mode, fromPlayer.name));
            } else {
                announce(MaplePacketCreator.getShowFameGain(delta));
            }

            return true;
        }
        return false;
    }

    public void gainMeso(int gain) {
        gainMeso(gain, true, false, true);
    }

    public void gainMeso(int gain, boolean show) {
        gainMeso(gain, show, false, false);
    }

    public void gainMeso(int gain, boolean show, boolean enableActions, boolean inChat) {
        long nextMeso;
        petLock.lock();
        try {
            nextMeso = meso.get() + gain;
            if (nextMeso > Integer.MAX_VALUE) {
                gain -= (nextMeso - Integer.MAX_VALUE);
            } else if (nextMeso < 0) {
                gain = -meso.get();
            }
            nextMeso = meso.addAndGet(gain);
        } finally {
            petLock.unlock();
        }

        if (gain != 0) {
            updateSingleStat(MapleStat.MESO, (int) nextMeso, enableActions);
            if (show) {
                client.announce(MaplePacketCreator.getShowMesoGain(gain, inChat));
            }
        } else {
            client.announce(MaplePacketCreator.enableActions());
        }
    }

    public void genericGuildMessage(int code) {
        this.client.announce(MaplePacketCreator.genericGuildMessage((byte) code));
    }

    public int getAccountID() {
        return accountid;
    }

    public List<PlayerCoolDownValueHolder> getAllCooldowns() {
        List<PlayerCoolDownValueHolder> ret = new ArrayList<>();

        effLock.lock();
        chrLock.lock();
        try {
            for (MapleCoolDownValueHolder mcdvh : coolDowns.values()) {
                ret.add(
                        new PlayerCoolDownValueHolder(
                                mcdvh.skillId, mcdvh.startTime, mcdvh.length));
            }
        } finally {
            chrLock.unlock();
            effLock.unlock();
        }

        return ret;
    }

    public int getAllianceRank() {
        return allianceRank;
    }

    public int getAllowWarpToId() {
        return warpToId;
    }

    public static String getAriantRoomLeaderName(int room) {
        return ariantroomleader[room];
    }

    public static int getAriantSlotsRoom(int room) {
        return ariantroomslot[room];
    }

    public int getBattleshipHp() {
        return battleshipHp;
    }

    public BuddyList getBuddylist() {
        return buddylist;
    }

    public static Map<String, String> getCharacterFromDatabase(String name) {
        Map<String, String> character = new LinkedHashMap<>();

        try {

            try (Connection con = DatabaseConnection.getConnection();
                    PreparedStatement ps =
                            con.prepareStatement(
                                    "SELECT `id`, `accountid`, `name` FROM `characters` WHERE `name` = ?")) {
                ps.setString(1, name);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        rs.close();
                        ps.close();
                        return null;
                    }

                    for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
                        character.put(rs.getMetaData().getColumnLabel(i), rs.getString(i));
                    }
                }
            }
        } catch (SQLException sqle) {
            sqle.printStackTrace();
        }

        return character;
    }

    public Long getBuffedStarttime(MapleBuffStat effect) {
        effLock.lock();
        chrLock.lock();
        try {
            MapleBuffStatValueHolder mbsvh = effects.get(effect);
            if (mbsvh == null) {
                return null;
            }
            return mbsvh.startTime;
        } finally {
            chrLock.unlock();
            effLock.unlock();
        }
    }

    public Integer getBuffedValue(MapleBuffStat effect) {
        effLock.lock();
        chrLock.lock();
        try {
            MapleBuffStatValueHolder mbsvh = effects.get(effect);
            if (mbsvh == null) {
                return null;
            }
            return mbsvh.value;
        } finally {
            chrLock.unlock();
            effLock.unlock();
        }
    }

    public int getBuffSource(MapleBuffStat stat) {
        effLock.lock();
        chrLock.lock();
        try {
            MapleBuffStatValueHolder mbsvh = effects.get(stat);
            if (mbsvh == null) {
                return -1;
            }
            return mbsvh.effect.getSourceId();
        } finally {
            chrLock.unlock();
            effLock.unlock();
        }
    }

    public MapleStatEffect getBuffEffect(MapleBuffStat stat) {
        effLock.lock();
        chrLock.lock();
        try {
            MapleBuffStatValueHolder mbsvh = effects.get(stat);
            if (mbsvh == null) {
                return null;
            }
            return mbsvh.effect;
        } finally {
            chrLock.unlock();
            effLock.unlock();
        }
    }

    public Set<Integer> getAvailableBuffs() {
        effLock.lock();
        chrLock.lock();
        try {
            return new LinkedHashSet<>(buffEffects.keySet());
        } finally {
            chrLock.unlock();
            effLock.unlock();
        }
    }

    private List<MapleBuffStatValueHolder> getAllStatups() {
        effLock.lock();
        chrLock.lock();
        try {
            List<MapleBuffStatValueHolder> ret = new ArrayList<>();
            for (Map<MapleBuffStat, MapleBuffStatValueHolder> bel : buffEffects.values()) {
                ret.addAll(bel.values());
            }
            return ret;
        } finally {
            chrLock.unlock();
            effLock.unlock();
        }
    }

    public List<PlayerBuffValueHolder>
            getAllBuffs() { // buff values will be stored in an arbitrary order
        effLock.lock();
        chrLock.lock();
        try {
            long curtime = Server.getInstance().getCurrentTime();

            Map<Integer, PlayerBuffValueHolder> ret = new LinkedHashMap<>();
            for (Map<MapleBuffStat, MapleBuffStatValueHolder> bel : buffEffects.values()) {
                for (MapleBuffStatValueHolder mbsvh : bel.values()) {
                    int srcid = mbsvh.effect.getBuffSourceId();
                    if (!ret.containsKey(srcid)) {
                        ret.put(
                                srcid,
                                new PlayerBuffValueHolder(
                                        (int) (curtime - mbsvh.startTime), mbsvh.effect));
                    }
                }
            }
            return new ArrayList<>(ret.values());
        } finally {
            chrLock.unlock();
            effLock.unlock();
        }
    }

    public List<Pair<MapleBuffStat, Integer>> getAllActiveStatups() {
        effLock.lock();
        chrLock.lock();
        try {
            List<Pair<MapleBuffStat, Integer>> ret = new ArrayList<>();
            for (Entry<MapleBuffStat, MapleBuffStatValueHolder>
                    mapleBuffStatMapleBuffStatValueHolderEntry : effects.entrySet()) {
                MapleBuffStatValueHolder mbsvh =
                        mapleBuffStatMapleBuffStatValueHolderEntry.getValue();
                ret.add(
                        new Pair<>(
                                mapleBuffStatMapleBuffStatValueHolderEntry.getKey(), mbsvh.value));
            }
            return ret;
        } finally {
            chrLock.unlock();
            effLock.unlock();
        }
    }

    public boolean hasBuffFromSourceid(int sourceid) {
        effLock.lock();
        chrLock.lock();
        try {
            return buffEffects.containsKey(sourceid);
        } finally {
            chrLock.unlock();
            effLock.unlock();
        }
    }

    private List<Pair<MapleBuffStat, Integer>> getActiveStatupsFromSourceid(
            int sourceid) { // already under effLock & chrLock
        List<Pair<MapleBuffStat, Integer>> ret = new ArrayList<>();

        for (Entry<MapleBuffStat, MapleBuffStatValueHolder> bel :
                buffEffects.get(sourceid).entrySet()) {
            MapleBuffStat mbs = bel.getKey();
            MapleBuffStatValueHolder mbsvh = effects.get(bel.getKey());

            if (mbsvh != null) {
                ret.add(new Pair<>(mbs, mbsvh.value));
            } else {
                ret.add(new Pair<>(mbs, 0));
            }
        }

        ret.sort(Comparator.comparing(Pair::getLeft));

        return ret;
    }

    private void addItemEffectHolder(
            Integer sourceid,
            long expirationtime,
            Map<MapleBuffStat, MapleBuffStatValueHolder> statups) {
        buffEffects.put(sourceid, statups);
        buffExpires.put(sourceid, expirationtime);
    }

    private boolean removeEffectFromItemEffectHolder(Integer sourceid, MapleBuffStat buffStat) {
        Map<MapleBuffStat, MapleBuffStatValueHolder> lbe = buffEffects.get(sourceid);

        if (lbe.remove(buffStat) != null) {
            buffEffectsCount.put(buffStat, (byte) (buffEffectsCount.get(buffStat) - 1));

            if (lbe.isEmpty()) {
                buffEffects.remove(sourceid);
                buffExpires.remove(sourceid);
            }

            return true;
        }

        return false;
    }

    private void removeItemEffectHolder(Integer sourceid) {
        Map<MapleBuffStat, MapleBuffStatValueHolder> be = buffEffects.remove(sourceid);
        if (be != null) {
            for (Entry<MapleBuffStat, MapleBuffStatValueHolder> bei : be.entrySet()) {
                buffEffectsCount.put(bei.getKey(), (byte) (buffEffectsCount.get(bei.getKey()) - 1));
            }
        }

        buffExpires.remove(sourceid);
    }

    private void dropWorstEffectFromItemEffectHolder(MapleBuffStat mbs) {
        int min = Integer.MAX_VALUE;
        Integer srcid = -1;
        for (Entry<Integer, Map<MapleBuffStat, MapleBuffStatValueHolder>> bpl :
                buffEffects.entrySet()) {
            MapleBuffStatValueHolder mbsvh = bpl.getValue().get(mbs);
            if (mbsvh != null) {
                if (mbsvh.value < min) {
                    min = mbsvh.value;
                    srcid = bpl.getKey();
                }
            }
        }

        removeEffectFromItemEffectHolder(srcid, mbs);
    }

    private MapleBuffStatValueHolder fetchBestEffectFromItemEffectHolder(MapleBuffStat mbs) {
        Pair<Integer, Integer> max = new Pair<>(Integer.MIN_VALUE, 0);
        MapleBuffStatValueHolder mbsvh = null;
        for (Entry<Integer, Map<MapleBuffStat, MapleBuffStatValueHolder>> bpl :
                buffEffects.entrySet()) {
            MapleBuffStatValueHolder mbsvhi = bpl.getValue().get(mbs);
            if (mbsvhi != null) {
                if (mbsvhi.value > max.left) {
                    max = new Pair<>(mbsvhi.value, mbsvhi.effect.getStatups().size());
                    mbsvh = mbsvhi;
                } else if (mbsvhi.value == max.left
                        && mbsvhi.effect.getStatups().size() > max.right) {
                    max = new Pair<>(mbsvhi.value, mbsvhi.effect.getStatups().size());
                    mbsvh = mbsvhi;
                }
            }
        }

        if (mbsvh != null) effects.put(mbs, mbsvh);
        return mbsvh;
    }

    private void extractBuffValue(int sourceid, MapleBuffStat stat) {
        chrLock.lock();
        try {
            removeEffectFromItemEffectHolder(sourceid, stat);
        } finally {
            chrLock.unlock();
        }
    }

    public void debugListAllBuffs() {
        effLock.lock();
        chrLock.lock();
        try {
            System.out.println("-------------------");
            System.out.println("CACHED BUFFS: ");
            for (Entry<Integer, Map<MapleBuffStat, MapleBuffStatValueHolder>> bpl :
                    buffEffects.entrySet()) {
                System.out.print(bpl.getKey() + ": ");
                for (Entry<MapleBuffStat, MapleBuffStatValueHolder> pble :
                        bpl.getValue().entrySet()) {
                    System.out.print(pble.getKey().name() + pble.getValue().value + ", ");
                }
                System.out.println();
            }
            System.out.println("-------------------");

            System.out.println("IN ACTION:");
            for (Entry<MapleBuffStat, MapleBuffStatValueHolder> bpl : effects.entrySet()) {
                System.out.println(
                        bpl.getKey().name()
                                + " -> "
                                + MapleItemInformationProvider.getInstance()
                                        .getName(bpl.getValue().effect.getSourceId()));
            }
        } finally {
            chrLock.unlock();
            effLock.unlock();
        }
    }

    public void debugListAllBuffsCount() {
        effLock.lock();
        chrLock.lock();
        try {
            for (Entry<MapleBuffStat, Byte> mbsl : buffEffectsCount.entrySet()) {
                System.out.println(mbsl.getKey().name() + " -> " + mbsl.getValue());
            }
        } finally {
            chrLock.unlock();
            effLock.unlock();
        }
    }

    public void cancelAllBuffs(boolean softcancel) {
        if (softcancel) {
            effLock.lock();
            chrLock.lock();
            try {
                cancelEffectFromBuffStat(MapleBuffStat.SUMMON);
                cancelEffectFromBuffStat(MapleBuffStat.PUPPET);
                cancelEffectFromBuffStat(MapleBuffStat.COMBO);

                effects.clear();

                for (Integer srcid : new ArrayList<>(buffEffects.keySet())) {
                    removeItemEffectHolder(srcid);
                }
            } finally {
                chrLock.unlock();
                effLock.unlock();
            }
        } else {
            Map<MapleStatEffect, Long> mseBuffs = new LinkedHashMap<>();

            effLock.lock();
            chrLock.lock();
            try {
                for (Entry<Integer, Map<MapleBuffStat, MapleBuffStatValueHolder>> bpl :
                        buffEffects.entrySet()) {
                    for (Entry<MapleBuffStat, MapleBuffStatValueHolder> mbse :
                            bpl.getValue().entrySet()) {
                        mseBuffs.put(mbse.getValue().effect, mbse.getValue().startTime);
                    }
                }
            } finally {
                chrLock.unlock();
                effLock.unlock();
            }

            for (Entry<MapleStatEffect, Long> mse : mseBuffs.entrySet()) {
                cancelEffect(mse.getKey(), false, mse.getValue());
            }
        }
    }

    private void dropBuffStats(
            List<Pair<MapleBuffStat, MapleBuffStatValueHolder>> effectsToCancel) {
        for (Pair<MapleBuffStat, MapleBuffStatValueHolder> cancelEffectCancelTasks :
                effectsToCancel) {
            // boolean nestedCancel = false;

            chrLock.lock();
            try {
                /*
                if (buffExpires.get(cancelEffectCancelTasks.getRight().effect.getBuffSourceId()) != null) {
                    nestedCancel = true;
                }*/

                if (cancelEffectCancelTasks.getRight().bestApplied) {
                    fetchBestEffectFromItemEffectHolder(cancelEffectCancelTasks.getLeft());
                }
            } finally {
                chrLock.unlock();
            }

            /*
            if (nestedCancel) {
                this.cancelEffect(cancelEffectCancelTasks.getRight().effect, false, -1, false);
            }*/
        }
    }

    private List<Pair<MapleBuffStat, MapleBuffStatValueHolder>> deregisterBuffStats(
            Map<MapleBuffStat, MapleBuffStatValueHolder> stats) {
        chrLock.lock();
        try {
            List<Pair<MapleBuffStat, MapleBuffStatValueHolder>> effectsToCancel =
                    new ArrayList<>(stats.size());
            for (Entry<MapleBuffStat, MapleBuffStatValueHolder> stat : stats.entrySet()) {
                int sourceid = stat.getValue().effect.getBuffSourceId();

                if (!buffEffects.containsKey(sourceid)) {
                    buffExpires.remove(sourceid);
                }

                MapleBuffStat mbs = stat.getKey();
                effectsToCancel.add(new Pair<>(mbs, stat.getValue()));

                MapleBuffStatValueHolder mbsvh = effects.get(mbs);
                if (mbsvh != null && mbsvh.effect.getBuffSourceId() == sourceid) {
                    mbsvh.bestApplied = true;
                    effects.remove(mbs);

                    if (mbs == MapleBuffStat.RECOVERY) {
                        if (recoveryTask != null) {
                            recoveryTask.cancel(false);
                            recoveryTask = null;
                        }
                    } else if (mbs == MapleBuffStat.SUMMON || mbs == MapleBuffStat.PUPPET) {
                        int summonId = mbsvh.effect.getSourceId();

                        MapleSummon summon = summons.get(summonId);
                        if (summon != null) {
                            map.broadcastMessage(
                                    MaplePacketCreator.removeSummon(summon, true),
                                    summon.getPosition());
                            map.removeMapObject(summon);
                            removeVisibleMapObject(summon);
                            summons.remove(summonId);

                            if (summon.getSkill() == DarkKnight.BEHOLDER) {
                                if (beholderHealingSchedule != null) {
                                    beholderHealingSchedule.cancel(false);
                                    beholderHealingSchedule = null;
                                }
                                if (beholderBuffSchedule != null) {
                                    beholderBuffSchedule.cancel(false);
                                    beholderBuffSchedule = null;
                                }
                            }
                        }
                    } else if (mbs == MapleBuffStat.DRAGONBLOOD) {
                        dragonBloodSchedule.cancel(false);
                        dragonBloodSchedule = null;
                    } else if (mbs == MapleBuffStat.HPREC || mbs == MapleBuffStat.MPREC) {
                        if (mbs == MapleBuffStat.HPREC) {
                            extraHpRec = 0;
                        } else {
                            extraMpRec = 0;
                        }

                        if (extraRecoveryTask != null) {
                            extraRecoveryTask.cancel(false);
                            extraRecoveryTask = null;
                        }

                        if (extraHpRec != 0 || extraMpRec != 0) {
                            startExtraTaskInternal(extraHpRec, extraMpRec, extraRecInterval);
                        }
                    }
                }
            }

            return effectsToCancel;
        } finally {
            chrLock.unlock();
        }
    }

    public void cancelEffect(int itemId) {
        cancelEffect(ii.getItemEffect(itemId), false, -1);
    }

    public boolean cancelEffect(MapleStatEffect effect, boolean overwrite, long startTime) {
        effLock.lock();
        try {
            return cancelEffect(effect, overwrite, startTime, true);
        } finally {
            effLock.unlock();
        }
    }

    private void updateEffects(Set<MapleBuffStat> removedStats) {
        chrLock.lock();
        try {
            Map<Integer, Pair<MapleStatEffect, Long>> retrievedEffects = new LinkedHashMap<>();
            Map<MapleBuffStat, Pair<Integer, Integer>> maxStatups = new LinkedHashMap<>();

            for (Entry<Integer, Map<MapleBuffStat, MapleBuffStatValueHolder>> bel :
                    buffEffects.entrySet()) {
                for (Entry<MapleBuffStat, MapleBuffStatValueHolder> belv :
                        bel.getValue().entrySet()) {
                    if (removedStats.contains(belv.getKey())) {
                        if (!retrievedEffects.containsKey(bel.getKey())) {
                            retrievedEffects.put(
                                    bel.getKey(),
                                    new Pair<>(belv.getValue().effect, belv.getValue().startTime));
                        }

                        Pair<Integer, Integer> thisStat = maxStatups.get(belv.getKey());
                        if (thisStat == null || belv.getValue().value > thisStat.getRight()) {
                            maxStatups.put(
                                    belv.getKey(), new Pair<>(bel.getKey(), belv.getValue().value));
                        }
                    }
                }
            }

            Map<Integer, Pair<MapleStatEffect, Long>> bestEffects = new LinkedHashMap<>();
            Set<MapleBuffStat> retrievedStats = new LinkedHashSet<>();
            for (Entry<MapleBuffStat, Pair<Integer, Integer>> lmsee : maxStatups.entrySet()) {
                if (isSingletonStatup(lmsee.getKey())) continue;

                Integer srcid = lmsee.getValue().getLeft();
                if (!bestEffects.containsKey(srcid)) {
                    Pair<MapleStatEffect, Long> msel = retrievedEffects.get(srcid);

                    bestEffects.put(srcid, msel);
                    for (Pair<MapleBuffStat, Integer> mbsi : msel.getLeft().getStatups()) {
                        retrievedStats.add(mbsi.getLeft());
                    }
                }
            }

            propagateBuffEffectUpdates(bestEffects, retrievedStats);
        } finally {
            chrLock.unlock();
        }
    }

    private boolean cancelEffect(
            MapleStatEffect effect, boolean overwrite, long startTime, boolean firstCancel) {
        Set<MapleBuffStat> removedStats = new LinkedHashSet<>();
        dropBuffStats(cancelEffectInternal(effect, overwrite, startTime, removedStats));
        updateLocalStats();
        updateEffects(removedStats);

        return !removedStats.isEmpty();
    }

    private List<Pair<MapleBuffStat, MapleBuffStatValueHolder>> cancelEffectInternal(
            MapleStatEffect effect,
            boolean overwrite,
            long startTime,
            Set<MapleBuffStat> removedStats) {
        Map<MapleBuffStat, MapleBuffStatValueHolder> buffstats = null;
        MapleBuffStat ombs;
        if (!overwrite) { // is removing the source effect, meaning every effect from this srcid is
            // being purged
            buffstats = extractCurrentBuffStats(effect);
        } else if ((ombs = getSingletonStatupFromEffect(effect))
                != null) { // removing all effects of a buff having non-shareable buff stat.
            MapleBuffStatValueHolder mbsvh = effects.get(ombs);
            if (mbsvh != null) {
                buffstats = extractCurrentBuffStats(mbsvh.effect);
            }
        }

        if (buffstats
                == null) { // all else, is dropping ALL current statups that uses same stats as the
            // given effect
            buffstats = extractLeastRelevantStatEffectsIfFull(effect);
        }

        if (effect.isMagicDoor()) {
            MapleDoor destroyDoor = removePartyDoor(false);

            if (destroyDoor != null) {
                destroyDoor.getTarget().removeMapObject(destroyDoor.getAreaDoor());
                destroyDoor.getTown().removeMapObject(destroyDoor.getTownDoor());

                for (MapleCharacter chr : destroyDoor.getTarget().getCharacters()) {
                    destroyDoor.getAreaDoor().sendDestroyData(chr.client);
                }

                Collection<MapleCharacter> townChars = destroyDoor.getTown().getCharacters();
                for (MapleCharacter chr : townChars) {
                    destroyDoor.getTownDoor().sendDestroyData(chr.client);
                }
                if (destroyDoor.getTownPortal().getId() == 0x80) {
                    for (MapleCharacter chr : townChars) {
                        MapleDoor door = chr.getMainTownDoor();
                        if (door != null) {
                            destroyDoor.getTownDoor().sendSpawnData(chr.client);
                        }
                    }
                }
            }
        } else if (effect.isMapChair()) {
            stopChairTask();
        }

        List<Pair<MapleBuffStat, MapleBuffStatValueHolder>> toCancel =
                deregisterBuffStats(buffstats);
        if (effect.isMonsterRiding()) {
            if (effect.getSourceId() != Corsair.BATTLE_SHIP) {
                client.getWorldServer().unregisterMountHunger(this);
                maplemount.setActive(false);
            }
        }

        if (!overwrite) {
            List<MapleBuffStat> cancelStats = new ArrayList<>();

            chrLock.lock();
            try {
                for (Entry<MapleBuffStat, MapleBuffStatValueHolder> mbsl : buffstats.entrySet()) {
                    cancelStats.add(mbsl.getKey());
                }
            } finally {
                chrLock.unlock();
            }

            removedStats.addAll(cancelStats);
            cancelPlayerBuffs(cancelStats);
        }

        return toCancel;
    }

    public void cancelEffectFromBuffStat(MapleBuffStat stat) {
        MapleBuffStatValueHolder effect;

        effLock.lock();
        chrLock.lock();
        try {
            effect = effects.get(stat);
        } finally {
            chrLock.unlock();
            effLock.unlock();
        }
        if (effect != null) {
            cancelEffect(effect.effect, false, -1);
        }
    }

    public void cancelBuffStats(MapleBuffStat stat) {
        effLock.lock();
        try {
            List<Pair<Integer, MapleBuffStatValueHolder>> cancelList = new ArrayList<>();

            chrLock.lock();
            try {
                for (Entry<Integer, Map<MapleBuffStat, MapleBuffStatValueHolder>> bel :
                        this.buffEffects.entrySet()) {
                    MapleBuffStatValueHolder beli = bel.getValue().get(stat);
                    if (beli != null) {
                        cancelList.add(new Pair<>(bel.getKey(), beli));
                    }
                }
            } finally {
                chrLock.unlock();
            }

            Map<MapleBuffStat, MapleBuffStatValueHolder> buffStatList = new LinkedHashMap<>();
            for (Pair<Integer, MapleBuffStatValueHolder> p : cancelList) {
                buffStatList.put(stat, p.getRight());
                extractBuffValue(p.getLeft(), stat);
                dropBuffStats(deregisterBuffStats(buffStatList));
            }
        } finally {
            effLock.unlock();
        }

        cancelPlayerBuffs(Collections.singletonList(stat));
    }

    private Map<MapleBuffStat, MapleBuffStatValueHolder> extractCurrentBuffStats(
            MapleStatEffect effect) {
        chrLock.lock();
        try {
            Map<MapleBuffStat, MapleBuffStatValueHolder> stats = new LinkedHashMap<>();
            Map<MapleBuffStat, MapleBuffStatValueHolder> buffList =
                    buffEffects.remove(effect.getBuffSourceId());

            if (buffList != null) {
                for (Entry<MapleBuffStat, MapleBuffStatValueHolder> stateffect :
                        buffList.entrySet()) {
                    stats.put(stateffect.getKey(), stateffect.getValue());
                    buffEffectsCount.put(
                            stateffect.getKey(),
                            (byte) (buffEffectsCount.get(stateffect.getKey()) - 1));
                }
            }

            return stats;
        } finally {
            chrLock.unlock();
        }
    }

    private Map<MapleBuffStat, MapleBuffStatValueHolder> extractLeastRelevantStatEffectsIfFull(
            MapleStatEffect effect) {
        Map<MapleBuffStat, MapleBuffStatValueHolder> extractedStatBuffs = new LinkedHashMap<>();

        chrLock.lock();
        try {
            Map<MapleBuffStat, Byte> stats = new LinkedHashMap<>();
            Map<MapleBuffStat, MapleBuffStatValueHolder> minStatBuffs = new LinkedHashMap<>();

            for (Entry<Integer, Map<MapleBuffStat, MapleBuffStatValueHolder>> mbsvhi :
                    buffEffects.entrySet()) {
                for (Entry<MapleBuffStat, MapleBuffStatValueHolder> mbsvhe :
                        mbsvhi.getValue().entrySet()) {
                    MapleBuffStat mbs = mbsvhe.getKey();
                    Byte b = stats.get(mbs);

                    if (b != null) {
                        stats.put(mbs, (byte) (b + 1));
                        if (mbsvhe.getValue().value < minStatBuffs.get(mbs).value)
                            minStatBuffs.put(mbs, mbsvhe.getValue());
                    } else {
                        stats.put(mbs, (byte) 1);
                        minStatBuffs.put(mbs, mbsvhe.getValue());
                    }
                }
            }

            Set<MapleBuffStat> effectStatups = new LinkedHashSet<>();
            for (Pair<MapleBuffStat, Integer> efstat : effect.getStatups()) {
                effectStatups.add(efstat.getLeft());
            }

            for (Entry<MapleBuffStat, Byte> it : stats.entrySet()) {
                boolean uniqueBuff = isSingletonStatup(it.getKey());

                if (it.getValue() >= (!uniqueBuff ? ServerConstants.MAX_MONITORED_BUFFSTATS : 1)
                        && effectStatups.contains(it.getKey())) {
                    MapleBuffStatValueHolder mbsvh = minStatBuffs.get(it.getKey());

                    Map<MapleBuffStat, MapleBuffStatValueHolder> lpbe =
                            buffEffects.get(mbsvh.effect.getBuffSourceId());
                    lpbe.remove(it.getKey());
                    buffEffectsCount.put(
                            it.getKey(), (byte) (buffEffectsCount.get(it.getKey()) - 1));

                    if (lpbe.isEmpty()) buffEffects.remove(mbsvh.effect.getBuffSourceId());
                    extractedStatBuffs.put(it.getKey(), mbsvh);
                }
            }
        } finally {
            chrLock.unlock();
        }

        return extractedStatBuffs;
    }

    private void propagateBuffEffectUpdates(
            Map<Integer, Pair<MapleStatEffect, Long>> retrievedEffects,
            Set<MapleBuffStat> retrievedStats) {
        if (retrievedStats.isEmpty()) return;

        Map<MapleBuffStat, Pair<Integer, MapleStatEffect>> maxBuffValue = new LinkedHashMap<>();
        for (MapleBuffStat mbs : retrievedStats) {
            MapleBuffStatValueHolder mbsvh = effects.get(mbs);
            if (mbsvh != null) {
                retrievedEffects.put(
                        mbsvh.effect.getBuffSourceId(), new Pair<>(mbsvh.effect, mbsvh.startTime));
            }

            maxBuffValue.put(mbs, new Pair<>(Integer.MIN_VALUE, null));
        }

        Map<MapleStatEffect, Pair<Integer, Integer>> updateEffects = new LinkedHashMap<>();

        List<MapleStatEffect> recalcMseList = new ArrayList<>();
        for (Entry<Integer, Pair<MapleStatEffect, Long>> re : retrievedEffects.entrySet()) {
            recalcMseList.add(re.getValue().getLeft());
        }

        boolean mageJob = this.getJobStyle() == MapleJob.MAGICIAN;
        do {
            List<MapleStatEffect> mseList = recalcMseList;
            recalcMseList = new ArrayList<>();

            for (MapleStatEffect mse : mseList) {
                int mseAmount = 0;
                int maxEffectiveStatup = Integer.MIN_VALUE;
                for (Pair<MapleBuffStat, Integer> st : mse.getStatups()) {
                    MapleBuffStat mbs = st.getLeft();

                    boolean relevantStatup = true;
                    if (mbs == MapleBuffStat.WATK) { // not relevant for mages
                        if (mageJob) relevantStatup = false;
                    } else if (mbs == MapleBuffStat.MATK) { // not relevant for non-mages
                        if (!mageJob) relevantStatup = false;
                    }

                    Pair<Integer, MapleStatEffect> mbv = maxBuffValue.get(mbs);
                    if (mbv == null) {
                        continue;
                    }

                    if (mbv.getLeft() < st.getRight()) {
                        MapleStatEffect msbe = mbv.getRight();
                        if (msbe != null) {
                            recalcMseList.add(msbe);
                        }

                        maxBuffValue.put(mbs, new Pair<>(st.getRight(), mse));

                        if (relevantStatup) {
                            if (maxEffectiveStatup < st.getRight()) {
                                maxEffectiveStatup = st.getRight();
                            }
                        }
                    }

                    if (relevantStatup) {
                        mseAmount += st.getRight();
                    }
                }

                updateEffects.put(mse, new Pair<>(maxEffectiveStatup, mseAmount));
            }
        } while (!recalcMseList.isEmpty());

        List<Pair<MapleStatEffect, Pair<Integer, Integer>>> updateEffectsList = new ArrayList<>();
        for (Entry<MapleStatEffect, Pair<Integer, Integer>> ue : updateEffects.entrySet()) {
            updateEffectsList.add(new Pair<>(ue.getKey(), ue.getValue()));
        }

        updateEffectsList.sort(
                (o1, o2) -> {
                    if (o1.getRight().getLeft().equals(o2.getRight().getLeft())) {
                        return o1.getRight().getRight().compareTo(o2.getRight().getRight());
                    }
                    return o1.getRight().getLeft().compareTo(o2.getRight().getLeft());
                });

        List<Pair<Integer, Pair<MapleStatEffect, Long>>> toUpdateEffects = new ArrayList<>();
        for (Pair<MapleStatEffect, Pair<Integer, Integer>> msep : updateEffectsList) {
            MapleStatEffect mse = msep.getLeft();
            toUpdateEffects.add(
                    new Pair<>(mse.getBuffSourceId(), retrievedEffects.get(mse.getBuffSourceId())));
        }

        List<Pair<MapleBuffStat, Integer>> activeStatups = new ArrayList<>();
        for (Pair<Integer, Pair<MapleStatEffect, Long>> lmse : toUpdateEffects) {
            Pair<MapleStatEffect, Long> msel = lmse.getRight();

            for (Pair<MapleBuffStat, Integer> statup :
                    getActiveStatupsFromSourceid(lmse.getLeft())) {
                if (!isSingletonStatup(statup.getLeft())) {
                    activeStatups.add(statup);
                }
            }

            msel.getLeft().updateBuffEffect(this, activeStatups, msel.getRight());
            activeStatups.clear();
        }

        if (this.isRidingBattleship()) {
            List<Pair<MapleBuffStat, Integer>> statups = new ArrayList<>(1);
            statups.add(new Pair<>(MapleBuffStat.MONSTER_RIDING, 0));
            this.announce(MaplePacketCreator.giveBuff(1932000, 5221006, statups));
            this.announceBattleshipHp();
        }
    }

    private static MapleBuffStat getSingletonStatupFromEffect(MapleStatEffect mse) {
        for (Pair<MapleBuffStat, Integer> mbs : mse.getStatups()) {
            if (isSingletonStatup(mbs.getLeft())) {
                return mbs.getLeft();
            }
        }

        return null;
    }

    private static boolean isSingletonStatup(MapleBuffStat mbs) {
        switch (mbs) { // HPREC and MPREC are supposed to be singleton
            case COUPON_EXP1:
            case COUPON_EXP2:
            case COUPON_EXP3:
            case COUPON_EXP4:
            case COUPON_DRP1:
            case COUPON_DRP2:
            case COUPON_DRP3:
            case WATK:
            case WDEF:
            case MATK:
            case MDEF:
            case ACC:
            case AVOID:
            case SPEED:
            case JUMP:
                return false;

            default:
                return true;
        }
    }

    public void registerEffect(
            MapleStatEffect effect, long starttime, long expirationtime, boolean isSilent) {
        if (effect.isDragonBlood()) {
            prepareDragonBlood(effect);
        } else if (effect.isBerserk()) {
            checkBerserk(hidden);
        } else if (effect.isBeholder()) {
            final int beholder = DarkKnight.BEHOLDER;
            if (beholderHealingSchedule != null) {
                beholderHealingSchedule.cancel(false);
            }
            if (beholderBuffSchedule != null) {
                beholderBuffSchedule.cancel(false);
            }
            Skill bHealing = SkillFactory.getSkill(DarkKnight.AURA_OF_BEHOLDER);
            int bHealingLvl = getSkillLevel(bHealing);
            if (bHealingLvl > 0) {
                final MapleStatEffect healEffect = bHealing.getEffect(bHealingLvl);
                int healInterval = healEffect.getX() * 1000;
                beholderHealingSchedule =
                        TimerManager.getInstance()
                                .register(
                                        () -> {
                                            if (awayFromWorld.get()) return;

                                            addHP(healEffect.getHp());
                                            client.announce(
                                                    MaplePacketCreator.showOwnBuffEffect(
                                                            beholder, 2));
                                            getMap().broadcastMessage(
                                                            MapleCharacter.this,
                                                            MaplePacketCreator.summonSkill(
                                                                    getId(), beholder, 5),
                                                            true);
                                            getMap().broadcastMessage(
                                                            MapleCharacter.this,
                                                            MaplePacketCreator.showOwnBuffEffect(
                                                                    beholder, 2),
                                                            false);
                                        },
                                        healInterval,
                                        healInterval);
            }
            Skill bBuff = SkillFactory.getSkill(DarkKnight.HEX_OF_BEHOLDER);
            if (getSkillLevel(bBuff) > 0) {
                final MapleStatEffect buffEffect = bBuff.getEffect(getSkillLevel(bBuff));
                int buffInterval = buffEffect.getX() * 1000;
                beholderBuffSchedule =
                        TimerManager.getInstance()
                                .register(
                                        () -> {
                                            if (awayFromWorld.get()) return;

                                            buffEffect.applyTo(MapleCharacter.this);
                                            client.announce(
                                                    MaplePacketCreator.showOwnBuffEffect(
                                                            beholder, 2));
                                            getMap().broadcastMessage(
                                                            MapleCharacter.this,
                                                            MaplePacketCreator.summonSkill(
                                                                    getId(),
                                                                    beholder,
                                                                    (int) (Math.random() * 3) + 6),
                                                            true);
                                            getMap().broadcastMessage(
                                                            MapleCharacter.this,
                                                            MaplePacketCreator.showBuffeffect(
                                                                    getId(), beholder, 2),
                                                            false);
                                        },
                                        buffInterval,
                                        buffInterval);
            }
        } else if (effect.isRecovery()) {
            int healInterval = (ServerConstants.USE_ULTRA_RECOVERY) ? 2000 : 5000;
            final byte heal = (byte) effect.getX();

            chrLock.lock();
            try {
                if (recoveryTask != null) {
                    recoveryTask.cancel(false);
                }

                recoveryTask =
                        TimerManager.getInstance()
                                .register(
                                        () -> {
                                            if (getBuffSource(MapleBuffStat.RECOVERY) == -1) {
                                                chrLock.lock();
                                                try {
                                                    if (recoveryTask != null) {
                                                        recoveryTask.cancel(false);
                                                        recoveryTask = null;
                                                    }
                                                } finally {
                                                    chrLock.unlock();
                                                }

                                                return;
                                            }

                                            addHP(heal);
                                            client.announce(
                                                    MaplePacketCreator.showOwnRecovery(heal));
                                            getMap().broadcastMessage(
                                                            MapleCharacter.this,
                                                            MaplePacketCreator.showRecovery(
                                                                    id, heal),
                                                            false);
                                        },
                                        healInterval,
                                        healInterval);
            } finally {
                chrLock.unlock();
            }
        } else if (effect.getHpRRate() > 0 || effect.getMpRRate() > 0) {
            if (effect.getHpRRate() > 0) {
                extraHpRec = effect.getHpR();
                extraRecInterval = effect.getHpRRate();
            }

            if (effect.getMpRRate() > 0) {
                extraMpRec = effect.getMpR();
                extraRecInterval = effect.getMpRRate();
            }

            stopExtraTask();
            startExtraTask(
                    extraHpRec,
                    extraMpRec,
                    extraRecInterval); // HP & MP sharing the same task holder
        } else if (effect.isMapChair()) {
            startChairTask();
        }

        effLock.lock();
        chrLock.lock();
        try {
            Integer sourceid = effect.getBuffSourceId();
            Map<MapleBuffStat, MapleBuffStatValueHolder> toDeploy;
            Map<MapleBuffStat, MapleBuffStatValueHolder> appliedStatups = new LinkedHashMap<>();

            for (Pair<MapleBuffStat, Integer> ps : effect.getStatups()) {
                appliedStatups.put(
                        ps.getLeft(),
                        new MapleBuffStatValueHolder(effect, starttime, ps.getRight()));
            }

            if (ServerConstants.USE_BUFF_MOST_SIGNIFICANT) {
                toDeploy = new LinkedHashMap<>();
                Map<Integer, Pair<MapleStatEffect, Long>> retrievedEffects = new LinkedHashMap<>();
                Set<MapleBuffStat> retrievedStats = new LinkedHashSet<>();

                for (Entry<MapleBuffStat, MapleBuffStatValueHolder> statup :
                        appliedStatups.entrySet()) {
                    MapleBuffStatValueHolder mbsvh = effects.get(statup.getKey());
                    MapleBuffStatValueHolder statMbsvh = statup.getValue();

                    if (mbsvh == null
                            || mbsvh.value < statMbsvh.value
                            || (mbsvh.value == statMbsvh.value
                                    && mbsvh.effect.getStatups().size()
                                            <= statMbsvh.effect.getStatups().size())) {
                        toDeploy.put(statup.getKey(), statMbsvh);
                    } else {
                        if (!isSingletonStatup(statup.getKey())) {
                            retrievedEffects.put(
                                    mbsvh.effect.getBuffSourceId(),
                                    new Pair<>(mbsvh.effect, mbsvh.startTime));
                            for (Pair<MapleBuffStat, Integer> mbs : mbsvh.effect.getStatups()) {
                                retrievedStats.add(mbs.getLeft());
                            }
                        }
                    }

                    Byte val = buffEffectsCount.get(statup.getKey());
                    val = val != null ? (byte) (val + 1) : (byte) 1;

                    buffEffectsCount.put(statup.getKey(), val);
                }

                if (!isSilent) {
                    addItemEffectHolder(sourceid, expirationtime, appliedStatups);
                    for (Entry<MapleBuffStat, MapleBuffStatValueHolder> statup :
                            toDeploy.entrySet()) {
                        effects.put(statup.getKey(), statup.getValue());
                    }

                    retrievedEffects.put(sourceid, new Pair<>(effect, starttime));
                    propagateBuffEffectUpdates(retrievedEffects, retrievedStats);
                }
            } else {
                for (Entry<MapleBuffStat, MapleBuffStatValueHolder> statup :
                        appliedStatups.entrySet()) {
                    Byte val = buffEffectsCount.get(statup.getKey());
                    val = val != null ? (byte) (val + 1) : (byte) 1;

                    buffEffectsCount.put(statup.getKey(), val);
                }

                toDeploy = appliedStatups;
            }

            addItemEffectHolder(sourceid, expirationtime, appliedStatups);
            for (Entry<MapleBuffStat, MapleBuffStatValueHolder> statup : toDeploy.entrySet()) {
                effects.put(statup.getKey(), statup.getValue());
            }
        } finally {
            chrLock.unlock();
            effLock.unlock();
        }

        updateLocalStats();
    }

    private static int getJobMapChair(MapleJob job) {
        switch (job.getId() / 1000) {
            case 0:
                return Beginner.MAP_CHAIR;
            case 1:
                return Noblesse.MAP_CHAIR;
            default:
                return Legend.MAP_CHAIR;
        }
    }

    public boolean unregisterChairBuff() {
        if (!ServerConstants.USE_CHAIR_EXTRAHEAL) return false;

        int skillId = getJobMapChair(job);
        int skillLv = getSkillLevel(skillId);
        if (skillLv > 0) {
            MapleStatEffect mapChairSkill = SkillFactory.getSkill(skillId).getEffect(skillLv);
            return cancelEffect(mapChairSkill, false, -1);
        }

        return false;
    }

    public boolean registerChairBuff() {
        if (!ServerConstants.USE_CHAIR_EXTRAHEAL) return false;

        int skillId = getJobMapChair(job);
        int skillLv = getSkillLevel(skillId);
        if (skillLv > 0) {
            MapleStatEffect mapChairSkill = SkillFactory.getSkill(skillId).getEffect(skillLv);
            mapChairSkill.applyTo(this);
            return true;
        }

        return false;
    }

    public int getChair() {
        return chair.get();
    }

    public String getChalkboard() {
        return this.chalktext;
    }

    public MapleClient getClient() {
        return client;
    }

    public final List<MapleQuestStatus> getCompletedQuests() {
        synchronized (quests) {
            List<MapleQuestStatus> ret = new ArrayList<>();
            for (MapleQuestStatus q : quests.values()) {
                if (q.getStatus().equals(MapleQuestStatus.Status.COMPLETED)) {
                    ret.add(q);
                }
            }

            return Collections.unmodifiableList(ret);
        }
    }

    public Collection<MapleMonster> getControlledMonsters() {
        return Collections.unmodifiableCollection(controlled);
    }

    public List<MapleRing> getCrushRings() {
        Collections.sort(crushRings);
        return crushRings;
    }

    public int getCurrentCI() {
        return ci;
    }

    public int getCurrentPage() {
        return currentPage;
    }

    public int getCurrentTab() {
        return currentTab;
    }

    public int getCurrentType() {
        return currentType;
    }

    public int getDojoEnergy() {
        return dojoEnergy;
    }

    public boolean getDojoParty() {
        return mapid >= 925030100 && mapid < 925040000;
    }

    public int getDojoPoints() {
        return dojoPoints;
    }

    public int getDojoStage() {
        return dojoStage;
    }

    public Collection<MapleDoor> getDoors() {
        prtLock.lock();
        try {
            return (party != null
                    ? Collections.unmodifiableCollection(party.getDoors().values())
                    : (pdoor != null ? Collections.singleton(pdoor) : new LinkedHashSet<>()));
        } finally {
            prtLock.unlock();
        }
    }

    public MapleDoor getPlayerDoor() {
        prtLock.lock();
        try {
            return pdoor;
        } finally {
            prtLock.unlock();
        }
    }

    public MapleDoor getMainTownDoor() {
        for (MapleDoor door : getDoors()) {
            if (door.getTownPortal().getId() == 0x80) {
                return door;
            }
        }

        return null;
    }

    public void applyPartyDoor(MapleDoor door, boolean partyUpdate) {
        prtLock.lock();
        try {
            if (!partyUpdate) {
                pdoor = door;
            }

            if (party != null) {
                party.addDoor(id, door);
                silentPartyUpdateInternal();
            }
        } finally {
            prtLock.unlock();
        }
    }

    private MapleDoor removePartyDoor(boolean partyUpdate) {
        MapleDoor ret = null;

        prtLock.lock();
        try {
            if (party != null) {
                party.removeDoor(id);
                silentPartyUpdateInternal();
            }

            if (!partyUpdate) {
                ret = pdoor;
                pdoor = null;
            }
        } finally {
            prtLock.unlock();
        }

        return ret;
    }

    private void removePartyDoor(
            MapleParty formerParty) { // player is no longer registered at this party
        formerParty.removeDoor(id);
    }

    public int getEnergyBar() {
        return energybar;
    }

    public EventInstanceManager getEventInstance() {
        evtLock.lock();
        try {
            return eventInstance;
        } finally {
            evtLock.unlock();
        }
    }

    public void resetExcluded(int petId) {
        chrLock.lock();
        try {
            Set<Integer> petExclude = excluded.get(petId);

            if (petExclude != null) petExclude.clear();
            else excluded.put(petId, new LinkedHashSet<>());
        } finally {
            chrLock.unlock();
        }
    }

    public void addExcluded(int petId, int x) {
        chrLock.lock();
        try {
            excluded.get(petId).add(x);
        } finally {
            chrLock.unlock();
        }
    }

    public void commitExcludedItems() {
        Map<Integer, Set<Integer>> petExcluded = this.getExcluded();

        chrLock.lock();
        try {
            excludedItems.clear();
        } finally {
            chrLock.unlock();
        }

        for (Map.Entry<Integer, Set<Integer>> pe : petExcluded.entrySet()) {
            byte petIndex = this.getPetIndex(pe.getKey());
            if (petIndex < 0) continue;

            Set<Integer> exclItems = pe.getValue();
            if (!exclItems.isEmpty()) {
                client.announce(
                        MaplePacketCreator.loadExceptionList(
                                id, pe.getKey(), petIndex, new ArrayList<>(exclItems)));

                chrLock.lock();
                try {
                    excludedItems.addAll(exclItems);
                } finally {
                    chrLock.unlock();
                }
            }
        }
    }

    public void exportExcludedItems(MapleClient c) {
        Map<Integer, Set<Integer>> petExcluded = this.getExcluded();
        for (Map.Entry<Integer, Set<Integer>> pe : petExcluded.entrySet()) {
            byte petIndex = this.getPetIndex(pe.getKey());
            if (petIndex < 0) continue;

            Set<Integer> exclItems = pe.getValue();
            if (!exclItems.isEmpty()) {
                c.announce(
                        MaplePacketCreator.loadExceptionList(
                                id, pe.getKey(), petIndex, new ArrayList<>(exclItems)));
            }
        }
    }

    public Map<Integer, Set<Integer>> getExcluded() {
        chrLock.lock();
        try {
            return Collections.unmodifiableMap(excluded);
        } finally {
            chrLock.unlock();
        }
    }

    public Set<Integer> getExcludedItems() {
        chrLock.lock();
        try {
            return Collections.unmodifiableSet(excludedItems);
        } finally {
            chrLock.unlock();
        }
    }

    public int getExp() {
        return exp.get();
    }

    public int getGachaExp() {
        return gachaexp.get();
    }

    public int getExpRate() {
        return expRate;
    }

    public int getCouponExpRate() {
        return expCoupon;
    }

    public int getRawExpRate() {
        return expRate / (expCoupon * getWorldServer().getExpRate());
    }

    public int getDropRate() {
        return dropRate;
    }

    public int getCouponDropRate() {
        return dropCoupon;
    }

    public int getRawDropRate() {
        return dropRate / (dropCoupon * getWorldServer().getDropRate());
    }

    public int getMesoRate() {
        return mesoRate;
    }

    public int getCouponMesoRate() {
        return mesoCoupon;
    }

    public int getRawMesoRate() {
        return mesoRate / (mesoCoupon * getWorldServer().getMesoRate());
    }

    public int getFace() {
        return face;
    }

    public int getFame() {
        return fame;
    }

    public MapleFamily getFamily() {
        return family;
    }

    public void setFamily(MapleFamily f) {
        this.family = f;
    }

    public int getFamilyId() {
        return familyId;
    }

    public boolean getFinishedDojoTutorial() {
        return finishedDojoTutorial;
    }

    public void setUsedStorage() {
        usedStorage = true;
    }

    public List<MapleRing> getFriendshipRings() {
        Collections.sort(friendshipRings);
        return friendshipRings;
    }

    public int getGender() {
        return gender;
    }

    public boolean isMale() {
        return gender == 0;
    }

    public MapleGuild getGuild() {
        try {
            return Server.getInstance().getGuild(guildid, world, this);
        } catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }
    }

    public MapleAlliance getAlliance() {
        if (mgc != null) {
            try {
                return Server.getInstance().getAlliance(getGuild().getAllianceId());
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        return null;
    }

    public int getGuildId() {
        return guildid;
    }

    public int getGuildRank() {
        return guildRank;
    }

    public int getHair() {
        return hair;
    }

    public MapleHiredMerchant getHiredMerchant() {
        return hiredMerchant;
    }

    public int getId() {
        return id;
    }

    public static int getAccountIdByName(String name) {
        try {
            int id;

            try (Connection con = DatabaseConnection.getConnection();
                    PreparedStatement ps =
                            con.prepareStatement(
                                    "SELECT accountid FROM characters WHERE name = ?")) {
                ps.setString(1, name);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        rs.close();
                        ps.close();
                        return -1;
                    }
                    id = rs.getInt("accountid");
                }
            }
            return id;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return -1;
    }

    public static int getIdByName(String name) {
        try {
            int id;
            try (Connection con = DatabaseConnection.getConnection();
                    PreparedStatement ps =
                            con.prepareStatement("SELECT id FROM characters WHERE name = ?")) {
                ps.setString(1, name);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        rs.close();
                        ps.close();
                        return -1;
                    }
                    id = rs.getInt("id");
                }
            }
            return id;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return -1;
    }

    public static String getNameById(int id) {
        try {
            String name;
            try (Connection con = DatabaseConnection.getConnection();
                    PreparedStatement ps =
                            con.prepareStatement("SELECT name FROM characters WHERE id = ?")) {
                ps.setInt(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        rs.close();
                        ps.close();
                        return null;
                    }
                    name = rs.getString("name");
                }
            }
            return name;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public int getInitialSpawnpoint() {
        return initialSpawnPoint;
    }

    public MapleInventory getInventory(MapleInventoryType type) {
        return inventory[type.ordinal()];
    }

    public int getItemEffect() {
        return itemEffect;
    }

    public boolean haveItemWithId(int itemid, boolean checkEquipped) {
        return (inventory[ItemConstants.getInventoryType(itemid).ordinal()].findById(itemid)
                        != null)
                || (checkEquipped
                        && inventory[MapleInventoryType.EQUIPPED.ordinal()].findById(itemid)
                                != null);
    }

    public boolean haveItemEquipped(int itemid) {
        return (inventory[MapleInventoryType.EQUIPPED.ordinal()].findById(itemid) != null);
    }

    public int getItemQuantity(int itemid, boolean checkEquipped) {
        int possesed =
                inventory[ItemConstants.getInventoryType(itemid).ordinal()].countById(itemid);
        if (checkEquipped) {
            possesed += inventory[MapleInventoryType.EQUIPPED.ordinal()].countById(itemid);
        }
        return possesed;
    }

    public int getCleanItemQuantity(int itemid, boolean checkEquipped) {
        int possesed =
                inventory[ItemConstants.getInventoryType(itemid).ordinal()].countNotOwnedById(
                        itemid);
        if (checkEquipped) {
            possesed += inventory[MapleInventoryType.EQUIPPED.ordinal()].countNotOwnedById(itemid);
        }
        return possesed;
    }

    public MapleJob getJob() {
        return job;
    }

    public int getJobRank() {
        return jobRank;
    }

    public int getJobRankMove() {
        return jobRankMove;
    }

    public int getJobType() {
        return job.getId() / 1000;
    }

    public Map<Integer, MapleKeyBinding> getKeymap() {
        return keymap;
    }

    public long getLastHealed() {
        return lastHealed;
    }

    public long getLastUsedCashItem() {
        return lastUsedCashItem;
    }

    public int getLevel() {
        return level;
    }

    public int getFh() {
        Point pos = this.getPosition();
        pos.y -= 6;

        if (map.getFootholds().findBelow(pos) == null) {
            return 0;
        }
        return map.getFootholds().findBelow(pos).getY1();
    }

    public MapleMap getMap() {
        return map;
    }

    public int getMapId() {
        if (map != null) {
            return map.getId();
        }
        return mapid;
    }

    public int getMarkedMonster() {
        return markedMonster;
    }

    public MapleRing getMarriageRing() {
        return partnerId > 0 ? marriageRing : null;
    }

    public int getMasterLevel(Skill skill) {
        if (skills.get(skill) == null) {
            return 0;
        }
        return skills.get(skill).masterlevel;
    }

    public int getTotalStr() {
        return localstr;
    }

    public int getTotalDex() {
        return localdex;
    }

    public int getTotalInt() {
        return localint_;
    }

    public int getTotalLuk() {
        return localluk;
    }

    public int getTotalMagic() {
        return localmagic;
    }

    public int getTotalWatk() {
        return localwatk;
    }

    public int getMaxClassLevel() {
        return isCygnus() ? 120 : 200;
    }

    public int getMaxLevel() {
        if (!ServerConstants.USE_ENFORCE_JOB_LEVEL_RANGE || isGmJob()) {
            return getMaxClassLevel();
        }

        return GameConstants.getJobMaxLevel(job);
    }

    public int getMeso() {
        return meso.get();
    }

    public int getMerchantMeso() {
        return merchantmeso;
    }

    public int getMesosTraded() {
        return mesosTraded;
    }

    public int getMessengerPosition() {
        return messengerposition;
    }

    public MapleGuildCharacter getMGC() {
        return mgc;
    }

    public void setMGC(MapleGuildCharacter mgc) {
        this.mgc = mgc;
    }

    public MaplePartyCharacter getMPC() {
        if (mpc == null) {
            mpc = new MaplePartyCharacter(this);
        }
        return mpc;
    }

    public void setMPC(MaplePartyCharacter mpc) {
        this.mpc = mpc;
    }

    public int getTargetHpBarHash() {
        return this.targetHpBarHash;
    }

    public void setTargetHpBarHash(int mobHash) {
        this.targetHpBarHash = mobHash;
    }

    public long getTargetHpBarTime() {
        return this.targetHpBarTime;
    }

    public void setTargetHpBarTime(long timeNow) {
        this.targetHpBarTime = timeNow;
    }

    public void setPlayerAggro(int mobHash) {
        this.targetHpBarHash = mobHash;
        this.targetHpBarTime = System.currentTimeMillis();
    }

    public void resetPlayerAggro() {
        if (getWorldServer().unregisterDisabledServerMessage(id)) {
            client.announceServerMessage();
        }

        this.targetHpBarHash = 0;
        this.targetHpBarTime = 0;
    }

    public MapleMiniGame getMiniGame() {
        return miniGame;
    }

    public int getMiniGamePoints(MiniGameResult type, boolean omok) {
        if (omok) {
            switch (type) {
                case WIN:
                    return omokwins;
                case LOSS:
                    return omoklosses;
                default:
                    return omokties;
            }
        }
        switch (type) {
            case WIN:
                return matchcardwins;
            case LOSS:
                return matchcardlosses;
            default:
                return matchcardties;
        }
    }

    public MonsterBook getMonsterBook() {
        return monsterbook;
    }

    public int getMonsterBookCover() {
        return bookCover;
    }

    public MapleMount getMount() {
        return maplemount;
    }

    public MapleMessenger getMessenger() {
        return messenger;
    }

    public String getName() {
        return name;
    }

    public int getNextEmptyPetIndex() {
        petLock.lock();
        try {
            for (int i = 0; i < 3; i++) {
                if (pets[i] == null) {
                    return i;
                }
            }
            return 3;
        } finally {
            petLock.unlock();
        }
    }

    public int getNoPets() {
        petLock.lock();
        try {
            int ret = 0;
            for (int i = 0; i < 3; i++) {
                if (pets[i] != null) {
                    ret++;
                }
            }
            return ret;
        } finally {
            petLock.unlock();
        }
    }

    public int getNumControlledMonsters() {
        return controlled.size();
    }

    public MapleParty getParty() {
        prtLock.lock();
        try {
            return party;
        } finally {
            prtLock.unlock();
        }
    }

    public int getPartyId() {
        prtLock.lock();
        try {
            return (party != null ? party.getId() : -1);
        } finally {
            prtLock.unlock();
        }
    }

    public List<MapleCharacter> getPartyMembers() {
        List<MapleCharacter> list = new ArrayList<>();

        prtLock.lock();
        try {
            if (party != null) {
                for (MaplePartyCharacter partyMembers : party.getMembers()) {
                    list.add(partyMembers.getPlayer());
                }
            }
        } finally {
            prtLock.unlock();
        }

        return list;
    }

    public List<MapleCharacter> getPartyMembersOnSameMap() {
        List<MapleCharacter> list = new ArrayList<>();
        int thisMapHash = map.hashCode();

        prtLock.lock();
        try {
            if (party != null) {
                for (MaplePartyCharacter partyMembers : party.getMembers()) {
                    MapleCharacter chr = partyMembers.getPlayer();
                    MapleMap chrMap = chr.map;
                    if (chrMap != null
                            && chrMap.hashCode() == thisMapHash
                            && chr.isLoggedinWorld()) {
                        list.add(chr);
                    }
                }
            }
        } finally {
            prtLock.unlock();
        }

        return list;
    }

    public boolean isPartyMember(MapleCharacter chr) {
        return isPartyMember(chr.id);
    }

    public boolean isPartyMember(int cid) {
        for (MapleCharacter mpcu : getPartyMembers()) {
            if (mpcu.id == cid) {
                return true;
            }
        }

        return false;
    }

    public List<MonsterDropEntry> retrieveRelevantDrops(int monsterId) {
        List<MapleCharacter> pchars = new ArrayList<>();
        for (MapleCharacter chr : getPartyMembers()) {
            if (chr.isLoggedinWorld()) {
                pchars.add(chr);
            }
        }

        if (pchars.isEmpty()) pchars.add(this);
        return MapleLootManager.retrieveRelevantDrops(monsterId, pchars);
    }

    public MaplePlayerShop getPlayerShop() {
        return playerShop;
    }

    public void setGMLevel(int level) {
        this.gmLevel = Math.min(level, 6);
        this.gmLevel = Math.max(level, 0);
    }

    public void closePlayerInteractions() {
        closeNpcShop();
        closeTrade();
        closePlayerShop();
        closeMiniGame();
        closeHiredMerchant(false);
        closePlayerMessenger();

        client.closePlayerScriptInteractions();
    }

    public void closeNpcShop() {
        this.shop = null;
    }

    public void closeTrade() {
        MapleTrade.cancelTrade(this);
    }

    public void closePlayerShop() {
        MaplePlayerShop mps = playerShop;
        if (mps == null) return;

        if (mps.isOwner(this)) {
            mps.setOpen(false);
            getWorldServer().unregisterPlayerShop(mps);

            for (MaplePlayerShopItem mpsi : mps.getItems()) {
                if (mpsi.getBundles() >= 2) {
                    Item iItem = mpsi.getItem().copy();
                    iItem.setQuantity((short) (mpsi.getBundles() * iItem.getQuantity()));
                    MapleInventoryManipulator.addFromDrop(client, iItem, false);
                } else if (mpsi.isExist()) {
                    MapleInventoryManipulator.addFromDrop(client, mpsi.getItem(), true);
                }
            }
            mps.closeShop();
        } else {
            mps.removeVisitor(this);
        }
        this.playerShop = null;
    }

    public void closeMiniGame() {
        MapleMiniGame game = miniGame;
        if (game == null) return;

        this.miniGame = null;
        if (game.isOwner(this)) {
            map.broadcastMessage(MaplePacketCreator.removeMinigameBox(this));
            game.broadcastToVisitor(MaplePacketCreator.getMiniGameClose(3));
        } else {
            game.removeVisitor(this);
        }
    }

    public void closeHiredMerchant(boolean closeMerchant) {
        MapleHiredMerchant merchant = hiredMerchant;
        if (merchant == null) return;

        if (closeMerchant) {
            merchant.removeVisitor(this);
            this.hiredMerchant = null;
        } else {
            if (merchant.isOwner(this)) {
                merchant.setOpen(true);
            } else {
                merchant.removeVisitor(this);
            }
            try {
                merchant.saveItems(false);
            } catch (SQLException ex) {
                ex.printStackTrace();
                FilePrinter.printError(
                        FilePrinter.EXCEPTION_CAUGHT,
                        "Error while saving " + name + "'s Hired Merchant items.");
            }
        }
    }

    public void closePlayerMessenger() {
        MapleMessenger m = messenger;
        if (m == null) return;

        World w = getWorldServer();
        MapleMessengerCharacter messengerplayer =
                new MapleMessengerCharacter(this, messengerposition);

        w.leaveMessenger(m.getId(), messengerplayer);
        this.messenger = null;
        this.messengerposition = 4;
    }

    public MaplePet[] getPets() {
        petLock.lock();
        try {
            return Arrays.copyOf(pets, pets.length);
        } finally {
            petLock.unlock();
        }
    }

    public MaplePet getPet(int index) {
        if (index < 0) return null;

        petLock.lock();
        try {
            return pets[index];
        } finally {
            petLock.unlock();
        }
    }

    public byte getPetIndex(int petId) {
        petLock.lock();
        try {
            for (byte i = 0; i < 3; i++) {
                if (pets[i] != null) {
                    if (pets[i].getUniqueId() == petId) {
                        return i;
                    }
                }
            }
            return -1;
        } finally {
            petLock.unlock();
        }
    }

    public byte getPetIndex(MaplePet pet) {
        petLock.lock();
        try {
            for (byte i = 0; i < 3; i++) {
                if (pets[i] != null) {
                    if (pets[i].getUniqueId() == pet.getUniqueId()) {
                        return i;
                    }
                }
            }
            return -1;
        } finally {
            petLock.unlock();
        }
    }

    public int getPossibleReports() {
        return possibleReports;
    }

    public final byte getQuestStatus(final int quest) {
        synchronized (quests) {
            for (final MapleQuestStatus q : quests.values()) {
                if (q.getQuest().getId() == quest) {
                    return (byte) q.getStatus().getId();
                }
            }
            return 0;
        }
    }

    public final MapleQuestStatus getMapleQuestStatus(final int quest) {
        synchronized (quests) {
            for (final MapleQuestStatus q : quests.values()) {
                if (q.getQuest().getId() == quest) {
                    return q;
                }
            }
            return null;
        }
    }

    public MapleQuestStatus getQuest(MapleQuest quest) {
        synchronized (quests) {
            if (!quests.containsKey(quest.getId())) {
                return new MapleQuestStatus(quest, MapleQuestStatus.Status.NOT_STARTED);
            }
            return quests.get(quest.getId());
        }
    }

    // ---- \/ \/ \/ \/ \/ \/ \/  NOT TESTED  \/ \/ \/ \/ \/ \/ \/ \/ \/ ----

    public final void setQuestAdd(
            final MapleQuest quest, final byte status, final String customData) {
        synchronized (quests) {
            if (!quests.containsKey(quest.getId())) {
                final MapleQuestStatus stat =
                        new MapleQuestStatus(quest, MapleQuestStatus.Status.getById(status));
                stat.setCustomData(customData);
                quests.put(quest.getId(), stat);
            }
        }
    }

    public final MapleQuestStatus getQuestNAdd(final MapleQuest quest) {
        synchronized (quests) {
            if (!quests.containsKey(quest.getId())) {
                final MapleQuestStatus status =
                        new MapleQuestStatus(quest, MapleQuestStatus.Status.NOT_STARTED);
                quests.put(quest.getId(), status);
                return status;
            }
            return quests.get(quest.getId());
        }
    }

    public final MapleQuestStatus getQuestNoAdd(final MapleQuest quest) {
        synchronized (quests) {
            return quests.get(quest.getId());
        }
    }

    public final MapleQuestStatus getQuestRemove(final MapleQuest quest) {
        synchronized (quests) {
            return quests.remove(quest.getId());
        }
    }

    // ---- /\ /\ /\ /\ /\ /\ /\  NOT TESTED  /\ /\ /\ /\ /\ /\ /\ /\ /\ ----

    public boolean needQuestItem(int questid, int itemid) {
        if (questid <= 0) return true; // For non quest items :3
        if (this.getQuestStatus(questid) != 1) return false;

        MapleQuest quest = MapleQuest.getInstance(questid);
        return getInventory(ItemConstants.getInventoryType(itemid)).countById(itemid)
                < quest.getItemAmountNeeded(itemid);
    }

    public int getRank() {
        return rank;
    }

    public int getRankMove() {
        return rankMove;
    }

    private void clearSavedLocation(SavedLocationType type) {
        savedLocations[type.ordinal()] = null;
    }

    public int peekSavedLocation(String type) {
        SavedLocation sl = savedLocations[SavedLocationType.fromString(type).ordinal()];
        if (sl == null) {
            return -1;
        }
        return sl.getMapId();
    }

    public int getSavedLocation(String type) {
        int m = peekSavedLocation(type);
        clearSavedLocation(SavedLocationType.fromString(type));

        return m;
    }

    public String getSearch() {
        return search;
    }

    public MapleShop getShop() {
        return shop;
    }

    public Map<Skill, SkillEntry> getSkills() {
        return Collections.unmodifiableMap(skills);
    }

    public int getSkillLevel(int skill) {
        SkillEntry ret = skills.get(SkillFactory.getSkill(skill));
        if (ret == null) {
            return 0;
        }
        return ret.skillevel;
    }

    public byte getSkillLevel(Skill skill) {
        if (skills.get(skill) == null) {
            return 0;
        }
        return skills.get(skill).skillevel;
    }

    public long getSkillExpiration(int skill) {
        SkillEntry ret = skills.get(SkillFactory.getSkill(skill));
        if (ret == null) {
            return -1;
        }
        return ret.expiration;
    }

    public long getSkillExpiration(Skill skill) {
        if (skills.get(skill) == null) {
            return -1;
        }
        return skills.get(skill).expiration;
    }

    public MapleSkinColor getSkinColor() {
        return skinColor;
    }

    public int getSlot() {
        return slots;
    }

    public final List<MapleQuestStatus> getStartedQuests() {
        synchronized (quests) {
            List<MapleQuestStatus> ret = new ArrayList<>();
            for (MapleQuestStatus q : quests.values()) {
                if (q.getStatus().equals(MapleQuestStatus.Status.STARTED)) {
                    ret.add(q);
                }
            }
            return Collections.unmodifiableList(ret);
        }
    }

    public final int getStartedQuestsSize() {
        synchronized (quests) {
            int i = 0;
            for (MapleQuestStatus q : quests.values()) {
                if (q.getStatus().equals(MapleQuestStatus.Status.STARTED)) {
                    if (q.getQuest().getInfoNumber() > 0) {
                        i++;
                    }
                    i++;
                }
            }
            return i;
        }
    }

    public MapleStatEffect getStatForBuff(MapleBuffStat effect) {
        effLock.lock();
        chrLock.lock();
        try {
            MapleBuffStatValueHolder mbsvh = effects.get(effect);
            if (mbsvh == null) {
                return null;
            }
            return mbsvh.effect;
        } finally {
            chrLock.unlock();
            effLock.unlock();
        }
    }

    public MapleStorage getStorage() {
        return storage;
    }

    public Collection<MapleSummon> getSummonsValues() {
        return summons.values();
    }

    public void clearSummons() {
        summons.clear();
    }

    public MapleSummon getSummonByKey(int id) {
        return summons.get(id);
    }

    public boolean isSummonsEmpty() {
        return summons.isEmpty();
    }

    public boolean containsSummon(MapleSummon summon) {
        return summons.containsValue(summon);
    }

    public MapleTrade getTrade() {
        return trade;
    }

    public int getVanquisherKills() {
        return vanquisherKills;
    }

    public int getVanquisherStage() {
        return vanquisherStage;
    }

    public Collection<MapleMapObject> getVisibleMapObjects() {
        return Collections.unmodifiableCollection(visibleMapObjects);
    }

    public int getWorld() {
        return world;
    }

    public World getWorldServer() {
        return Server.getInstance().getWorld(world);
    }

    public void giveCoolDowns(final int skillid, long starttime, long length) {
        if (skillid == 5221999) {
            this.battleshipHp = (int) length;
            addCooldown(skillid, 0, length);
        } else {
            int time = (int) ((length + starttime) - System.currentTimeMillis());
            addCooldown(skillid, System.currentTimeMillis(), time);
        }
    }

    public int gmLevel() {
        return gmLevel;
    }

    private void guildUpdate() {
        mgc.setLevel(level);
        mgc.setJobId(job.getId());

        if (this.guildid < 1) {
            return;
        }

        try {
            Server.getInstance().memberLevelJobUpdate(this.mgc);
            // Server.getInstance().getGuild(guildid, world, mgc).gainGP(40);
            int allianceId = getGuild().getAllianceId();
            if (allianceId > 0) {
                Server.getInstance()
                        .allianceMessage(
                                allianceId,
                                MaplePacketCreator.updateAllianceJobLevel(this),
                                id,
                                -1);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void handleEnergyChargeGain() { // to get here energychargelevel has to be > 0
        Skill energycharge =
                isCygnus()
                        ? SkillFactory.getSkill(ThunderBreaker.ENERGY_CHARGE)
                        : SkillFactory.getSkill(Marauder.ENERGY_CHARGE);
        MapleStatEffect ceffect;
        ceffect = energycharge.getEffect(getSkillLevel(energycharge));
        TimerManager tMan = TimerManager.getInstance();
        if (energybar < 10000) {
            energybar += 102;
            if (energybar > 10000) {
                energybar = 10000;
            }
            List<Pair<MapleBuffStat, Integer>> stat =
                    Collections.singletonList(new Pair<>(MapleBuffStat.ENERGY_CHARGE, energybar));
            setBuffedValue(MapleBuffStat.ENERGY_CHARGE, energybar);
            client.announce(MaplePacketCreator.giveBuff(energybar, 0, stat));
            client.announce(MaplePacketCreator.showOwnBuffEffect(energycharge.getId(), 2));
            map.broadcastMessage(
                    this, MaplePacketCreator.showBuffeffect(id, energycharge.getId(), 2));
            map.broadcastMessage(this, MaplePacketCreator.giveForeignBuff(energybar, stat));
        }
        if (energybar >= 10000 && energybar < 11000) {
            energybar = 15000;
            final MapleCharacter chr = this;
            tMan.schedule(
                    () -> {
                        energybar = 0;
                        List<Pair<MapleBuffStat, Integer>> stat =
                                Collections.singletonList(
                                        new Pair<>(MapleBuffStat.ENERGY_CHARGE, energybar));
                        setBuffedValue(MapleBuffStat.ENERGY_CHARGE, energybar);
                        client.announce(MaplePacketCreator.giveBuff(energybar, 0, stat));
                        getMap().broadcastMessage(
                                        chr, MaplePacketCreator.giveForeignBuff(energybar, stat));
                    },
                    ceffect.getDuration());
        }
    }

    public void handleOrbconsume() {
        int skillid = isCygnus() ? DawnWarrior.COMBO : Crusader.COMBO;
        Skill combo = SkillFactory.getSkill(skillid);
        List<Pair<MapleBuffStat, Integer>> stat =
                Collections.singletonList(new Pair<>(MapleBuffStat.COMBO, 1));
        setBuffedValue(MapleBuffStat.COMBO, 1);
        client.announce(
                MaplePacketCreator.giveBuff(
                        skillid,
                        combo.getEffect(getSkillLevel(combo)).getDuration()
                                + (int)
                                        ((getBuffedStarttime(MapleBuffStat.COMBO)
                                                - System.currentTimeMillis())),
                        stat));
        map.broadcastMessage(this, MaplePacketCreator.giveForeignBuff(id, stat), false);
    }

    public boolean hasEntered(String script) {
        for (Entry<Integer, String> integerStringEntry : entered.entrySet()) {
            if (integerStringEntry.getValue().equals(script)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasEntered(String script, int mapId) {
        return entered.containsKey(mapId) && entered.get(mapId).equals(script);
    }

    public void hasGivenFame(MapleCharacter to) {
        lastfametime = System.currentTimeMillis();
        lastmonthfameids.add(to.id);
        try {
            try (Connection con = DatabaseConnection.getConnection();
                    PreparedStatement ps =
                            con.prepareStatement(
                                    "INSERT INTO famelog (characterid, characterid_to) VALUES (?, ?)")) {
                ps.setInt(1, id);
                ps.setInt(2, to.id);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public boolean hasMerchant() {
        return hasMerchant;
    }

    public boolean haveItem(int itemid) {
        return getItemQuantity(itemid, false) > 0;
    }

    public boolean haveCleanItem(int itemid) {
        return getCleanItemQuantity(itemid, false) > 0;
    }

    public boolean hasEmptySlot(int itemId) {
        return getInventory(ItemConstants.getInventoryType(itemId)).getNextFreeSlot() > -1;
    }

    public boolean hasEmptySlot(byte invType) {
        return getInventory(MapleInventoryType.getByType(invType)).getNextFreeSlot() > -1;
    }

    public void increaseGuildCapacity() {
        int cost = MapleGuild.getIncreaseGuildCost(getGuild().getCapacity());

        if (getMeso() < cost) {
            dropMessage(1, "You don't have enough mesos.");
            return;
        }

        if (Server.getInstance().increaseGuildCapacity(guildid)) {
            gainMeso(-cost, true, false, true);
        } else {
            dropMessage(1, "Your guild already reached the maximum capacity of players.");
        }
    }

    public boolean isActiveBuffedValue(int skillid) {
        ArrayList<MapleBuffStatValueHolder> allBuffs;

        effLock.lock();
        chrLock.lock();
        try {
            allBuffs = new ArrayList<>(effects.values());
        } finally {
            chrLock.unlock();
            effLock.unlock();
        }

        for (MapleBuffStatValueHolder mbsvh : allBuffs) {
            if (mbsvh.effect.isSkill() && mbsvh.effect.getSourceId() == skillid) {
                return true;
            }
        }
        return false;
    }

    private boolean canBuyback(int fee, boolean usingMesos) {
        return (usingMesos ? this.getMeso() : cashshop.getCash(1)) >= fee;
    }

    private void applyBuybackFee(int fee, boolean usingMesos) {
        if (usingMesos) {
            this.gainMeso(-fee);
        } else {
            cashshop.gainCash(1, -fee);
        }
    }

    private long getNextBuybackTime() {
        return lastBuyback + ServerConstants.BUYBACK_COOLDOWN_MINUTES * 60 * 1000;
    }

    private boolean isBuybackInvincible() {
        return Server.getInstance().getCurrentTime() - lastBuyback < 4200;
    }

    private int getBuybackFee() {
        float fee = ServerConstants.BUYBACK_FEE;
        int grade = Math.min(Math.max(level, 30), 120) - 30;

        fee += (grade * ServerConstants.BUYBACK_LEVEL_STACK_FEE);
        return (int) Math.floor(fee);
    }

    public boolean couldBuyback() { // Ronan's buyback system
        long timeNow = Server.getInstance().getCurrentTime();

        if (timeNow - lastDeathtime > ServerConstants.BUYBACK_RETURN_MINUTES * 60 * 1000) {
            this.dropMessage(
                    5,
                    "The time available to decide has expired, therefore you are unable to buyback.");
            return false;
        }

        long nextBuybacktime = getNextBuybackTime();
        if (timeNow < nextBuybacktime) {
            long timeLeft = nextBuybacktime - timeNow;
            int seconds = (int) (timeLeft / 1000L % 60L);
            int minutes = (int) (timeLeft / (1000L * 60L) % 60L);

            this.dropMessage(
                    5,
                    "Next buyback available in "
                            + (minutes > 0 ? (String.format("%02d", minutes) + " minutes, ") : "")
                            + String.format("%02d", seconds)
                            + " seconds.");
            return false;
        }

        boolean usingMesos = ServerConstants.USE_BUYBACK_WITH_MESOS;
        int fee = getBuybackFee();
        if (usingMesos) fee *= ServerConstants.BUYBACK_MESO_MULTIPLIER;

        if (!canBuyback(fee, usingMesos)) {
            this.dropMessage(
                    5,
                    "You don't have " + fee + ' ' + (usingMesos ? "mesos" : "NX") + " to buyback.");
            return false;
        }

        lastBuyback = timeNow;
        applyBuybackFee(fee, usingMesos);
        return true;
    }

    public boolean isBuffFrom(MapleBuffStat stat, Skill skill) {
        effLock.lock();
        chrLock.lock();
        try {
            MapleBuffStatValueHolder mbsvh = effects.get(stat);
            return mbsvh != null
                    && mbsvh.effect.isSkill()
                    && mbsvh.effect.getSourceId() == skill.getId();
        } finally {
            chrLock.unlock();
            effLock.unlock();
        }
    }

    public boolean isGmJob() {
        int jn = job.getJobNiche();
        return jn >= 8 && jn <= 9;
    }

    public boolean isCygnus() {
        return getJobType() == 1;
    }

    public boolean isAran() {
        return job.getId() >= 2000 && job.getId() <= 2112;
    }

    public boolean isBeginnerJob() {
        return (job.getId() == 0 || job.getId() == 1000 || job.getId() == 2000);
    }

    public boolean isGM() {
        return gmLevel > 1;
    }

    public boolean isHidden() {
        return hidden;
    }

    public boolean isMapObjectVisible(MapleMapObject mo) {
        return visibleMapObjects.contains(mo);
    }

    public boolean isPartyLeader() {
        prtLock.lock();
        try {
            return party.getLeaderId() == id;
        } finally {
            prtLock.unlock();
        }
    }

    public boolean isGuildLeader() { // true on guild master or jr. master
        return guildid > 0 && guildRank < 3;
    }

    public void leaveMap() {
        controlled.clear();
        visibleMapObjects.clear();
        chair.set(0);
        if (hpDecreaseTask != null) {
            hpDecreaseTask.cancel(false);
        }
    }

    public synchronized void levelUp(boolean takeexp) {
        Skill improvingMaxHP = null;
        Skill improvingMaxMP = null;
        int improvingMaxHPLevel = 0;
        int improvingMaxMPLevel = 0;

        boolean isBeginner = isBeginnerJob();
        if (ServerConstants.USE_AUTOASSIGN_STARTERS_AP && isBeginner && level <= 10) {
            effLock.lock();
            statWlock.lock();
            try {
                gainAp(5, true);

                int str = 0, dex = 0;
                if (level < 6) {
                    str += 5;
                } else {
                    str += 4;
                    dex += 1;
                }

                assignStrDexIntLuk(str, dex, 0, 0);
            } finally {
                statWlock.unlock();
                effLock.unlock();
            }
        } else {
            int remainingAp = 5;
            if (isCygnus() && level > 10 && level < 70) {
                remainingAp++;
            }

            gainAp(remainingAp, true);
        }

        int addhp = 0, addmp = 0;
        if (isBeginner) {
            addhp += Randomizer.rand(12, 16);
            addmp += Randomizer.rand(10, 12);
        } else if (job.isA(MapleJob.WARRIOR) || job.isA(MapleJob.DAWNWARRIOR1)) {
            improvingMaxHP =
                    isCygnus()
                            ? SkillFactory.getSkill(DawnWarrior.MAX_HP_INCREASE)
                            : SkillFactory.getSkill(Swordsman.IMPROVED_MAX_HP_INCREASE);
            if (job.isA(MapleJob.CRUSADER)) {
                improvingMaxMP = SkillFactory.getSkill(1210000);
            } else if (job.isA(MapleJob.DAWNWARRIOR2)) {
                improvingMaxMP = SkillFactory.getSkill(11110000);
            }
            improvingMaxHPLevel = getSkillLevel(improvingMaxHP);
            addhp += Randomizer.rand(24, 28);
            addmp += Randomizer.rand(4, 6);
        } else if (job.isA(MapleJob.MAGICIAN) || job.isA(MapleJob.BLAZEWIZARD1)) {
            improvingMaxMP =
                    isCygnus()
                            ? SkillFactory.getSkill(BlazeWizard.INCREASING_MAX_MP)
                            : SkillFactory.getSkill(Magician.IMPROVED_MAX_MP_INCREASE);
            improvingMaxMPLevel = getSkillLevel(improvingMaxMP);
            addhp += Randomizer.rand(10, 14);
            addmp += Randomizer.rand(22, 24);
        } else if (job.isA(MapleJob.BOWMAN)
                || job.isA(MapleJob.THIEF)
                || (job.getId() > 1299 && job.getId() < 1500)) {
            addhp += Randomizer.rand(20, 24);
            addmp += Randomizer.rand(14, 16);
        } else if (job.isA(MapleJob.GM)) {
            addhp += 30000;
            addmp += 30000;
        } else if (job.isA(MapleJob.PIRATE) || job.isA(MapleJob.THUNDERBREAKER1)) {
            improvingMaxHP =
                    isCygnus()
                            ? SkillFactory.getSkill(ThunderBreaker.IMPROVE_MAX_HP)
                            : SkillFactory.getSkill(5100000);
            improvingMaxHPLevel = getSkillLevel(improvingMaxHP);
            addhp += Randomizer.rand(22, 28);
            addmp += Randomizer.rand(18, 23);
        } else if (job.isA(MapleJob.ARAN1)) {
            addhp += Randomizer.rand(44, 48);
            int aids = Randomizer.rand(4, 8);
            addmp += aids + Math.floor(aids * 0.1);
        }
        if (improvingMaxHPLevel > 0
                && (job.isA(MapleJob.WARRIOR)
                        || job.isA(MapleJob.PIRATE)
                        || job.isA(MapleJob.DAWNWARRIOR1))) {
            addhp += improvingMaxHP.getEffect(improvingMaxHPLevel).getX();
        }
        if (improvingMaxMPLevel > 0
                && (job.isA(MapleJob.MAGICIAN)
                        || job.isA(MapleJob.CRUSADER)
                        || job.isA(MapleJob.BLAZEWIZARD1))) {
            addmp += improvingMaxMP.getEffect(improvingMaxMPLevel).getX();
        }

        if (ServerConstants.USE_RANDOMIZE_HPMP_GAIN) {
            addmp += getJobStyle() == MapleJob.MAGICIAN ? localint_ / 20 : localint_ / 10;
        }

        addMaxMPMaxHP(addhp, addmp, true);

        if (takeexp) {
            exp.addAndGet(-ExpTable.getExpNeededForLevel(level));
            if (exp.get() < 0) {
                exp.set(0);
            }
        }

        level++;
        if (level >= getMaxClassLevel()) {
            exp.set(0);

            int maxClassLevel = getMaxClassLevel();
            if (level == maxClassLevel) {
                if (!this.isGM()) {
                    if (ServerConstants.PLAYERNPC_AUTODEPLOY) {
                        Thread t =
                                new Thread(
                                        () ->
                                                MaplePlayerNPC.spawnPlayerNPC(
                                                        GameConstants.getHallOfFameMapid(job),
                                                        MapleCharacter.this));

                        t.start();
                    }

                    final String names = (getMedalText() + name);
                    getWorldServer()
                            .broadcastPacket(
                                    MaplePacketCreator.serverNotice(
                                            6, String.format(LEVEL_200, names, names)));
                }
            }

            level = maxClassLevel; // To prevent levels past the maximum
        }

        if (job.getId() % 1000 > 0) {
            gainSp(3, GameConstants.getSkillBook(job.getId()), true);
        } else if (job == MapleJob.BEGINNER) {
            gainSp(1, 0, true);
        }

        effLock.lock();
        statWlock.lock();
        try {
            recalcLocalStats();
            changeHpMp(localmaxhp, localmaxmp, true);

            List<Pair<MapleStat, Integer>> statup = new ArrayList<>(10);
            statup.add(new Pair<>(MapleStat.AVAILABLEAP, remainingAp));
            statup.add(
                    new Pair<>(
                            MapleStat.AVAILABLESP,
                            remainingSp[GameConstants.getSkillBook(job.getId())]));
            statup.add(new Pair<>(MapleStat.HP, hp));
            statup.add(new Pair<>(MapleStat.MP, mp));
            statup.add(new Pair<>(MapleStat.EXP, exp.get()));
            statup.add(new Pair<>(MapleStat.LEVEL, level));
            statup.add(new Pair<>(MapleStat.MAXHP, clientmaxhp));
            statup.add(new Pair<>(MapleStat.MAXMP, clientmaxmp));
            statup.add(new Pair<>(MapleStat.STR, str));
            statup.add(new Pair<>(MapleStat.DEX, dex));

            client.announce(MaplePacketCreator.updatePlayerStats(statup, true, this));
        } finally {
            statWlock.unlock();
            effLock.unlock();
        }

        map.broadcastMessage(this, MaplePacketCreator.showForeignEffect(id, 0), false);
        this.mpc = new MaplePartyCharacter(this);
        silentPartyUpdate();

        if (this.guildid > 0) {
            getGuild().broadcast(MaplePacketCreator.levelUpMessage(2, level, name), id);
        }

        if (level % 20 == 0) {
            if (ServerConstants.USE_ADD_SLOTS_BY_LEVEL == true) {
                if (!isGM()) {
                    for (byte i = 1; i < 5; i++) {
                        gainSlots(i, 4, true);
                    }

                    this.yellowMessage(
                            "You reached level "
                                    + level
                                    + ". Congratulations! As a token of your success, your inventory has been expanded a little bit.");
                }
            }
            if (ServerConstants.USE_ADD_RATES_BY_LEVEL == true) { // For the rate upgrade
                revertLastPlayerRates();
                setPlayerRates();
                this.yellowMessage(
                        "You managed to get level "
                                + level
                                + "! Getting experience and items seems a little easier now, huh?");
            }
        }

        if (ServerConstants.USE_PERFECT_PITCH && level >= 30) {
            // milestones?
            if (MapleInventoryManipulator.checkSpace(client, 4310000, 1, "")) {
                MapleInventoryManipulator.addById(client, 4310000, (short) 1);
            }
        } else if (level == 10) {
            Runnable r =
                    () -> {
                        prtLock.lock();
                        try {
                            if (party != null) {
                                if (isPartyLeader()) party.assignNewLeader(client);
                                PartyOperationHandler.leaveParty(party, mpc, client);

                                showHint(
                                        "You have reached #blevel 10#k, therefore you must leave your #rstarter party#k.");
                            }
                        } finally {
                            prtLock.unlock();
                        }
                    };

            Thread t = new Thread(r);
            t.start();
        }

        levelUpMessages();
        guildUpdate();
    }

    private void levelUpMessages() {
        if (level % 5 != 0) { // Performance FTW?
            return;
        }
        if (level == 5) {
            yellowMessage("Aww, you're level 5, how cute!");
        } else if (level == 10) {
            yellowMessage(
                    "Henesys Party Quest is now open to you! Head over to Henesys, find some friends, and try it out!");
        } else if (level == 15) {
            yellowMessage("Half-way to your 2nd job advancement, nice work!");
        } else if (level == 20) {
            yellowMessage("You can almost Kerning Party Quest!");
        } else if (level == 25) {
            yellowMessage(
                    "You seem to be improving, but you are still not ready to move on to the next step.");
        } else if (level == 30) {
            yellowMessage(
                    "You have finally reached level 30! Try job advancing, after that try the Mushroom Castle!");
        } else if (level == 35) {
            yellowMessage(
                    "Hey did you hear about this mall that opened in Kerning? Try visiting the Kerning Mall.");
        } else if (level == 40) {
            yellowMessage("Do @rates to see what all your rates are!");
        } else if (level == 45) {
            yellowMessage(
                    "I heard that a rock and roll artist died during the grand opening of the Kerning Mall. People are naming him the Spirit of Rock.");
        } else if (level == 50) {
            yellowMessage(
                    "You seem to be growing very fast, would you like to test your new found strength with the mighty Zakum?");
        } else if (level == 55) {
            yellowMessage("You can now try out the Ludibrium Maze Party Quest!");
        } else if (level == 60) {
            yellowMessage("Feels good to be near the end of 2nd job, doesn't it?");
        } else if (level == 65) {
            yellowMessage("You're only 5 more levels away from 3rd job, not bad!");
        } else if (level == 70) {
            yellowMessage(
                    "I see many people wearing a teddy bear helmet. I should ask someone where they got it from.");
        } else if (level == 75) {
            yellowMessage("You have reached level 3 quarters!");
        } else if (level == 80) {
            yellowMessage("You think you are powerful enough? Try facing horntail!");
        } else if (level == 85) {
            yellowMessage(
                    "Did you know? The majority of people who hit level 85 in HeavenMS don't live to be 85 years old?");
        } else if (level == 90) {
            yellowMessage(
                    "Hey do you like the amusement park? I heard Spooky World is the best theme park around. I heard they sell cute teddy-bears.");
        } else if (level == 95) {
            yellowMessage(
                    "100% of people who hit level 95 in HeavenMS don't live to be 95 years old.");
        } else if (level == 100) {
            yellowMessage(
                    "Mid-journey so far... You just reached level 100! Now THAT's such a feat, however to manage the 200 you will need even more passion and determination than ever! Good hunting!");
        } else if (level == 105) {
            yellowMessage("Have you ever been to leafre? I heard they have dragons!");
        } else if (level == 110) {
            yellowMessage(
                    "I see many people wearing a teddy bear helmet. I should ask someone where they got it from.");
        } else if (level == 115) {
            yellowMessage("I bet all you can think of is level 120, huh? Level 115 gets no love.");
        } else if (level == 120) {
            yellowMessage(
                    "Are you ready to learn from the masters? Head over to your job instructor!");
        } else if (level == 125) {
            yellowMessage("The struggle for mastery books has begun, huh?");
        } else if (level == 130) {
            yellowMessage("You should try Temple of Time. It should be pretty decent EXP.");
        } else if (level == 135) {
            yellowMessage("I hope you're still not struggling for mastery books!");
        } else if (level == 140) {
            yellowMessage("You're well into 4th job at this point, great work!");
        } else if (level == 145) {
            yellowMessage("Level 145 is serious business!");
        } else if (level == 150) {
            yellowMessage("You have becomed quite strong, but the journey is not yet over.");
        } else if (level == 155) {
            yellowMessage("At level 155, Zakum should be a joke to you. Nice job!");
        } else if (level == 160) {
            yellowMessage(
                    "Level 160 is pretty impressive. Try taking a picture and putting it on Instagram.");
        } else if (level == 165) {
            yellowMessage("At this level, you should start looking into doing some boss runs.");
        } else if (level == 170) {
            yellowMessage("Level 170, huh? You have the heart of a champion.");
        } else if (level == 175) {
            yellowMessage("You came a long way from level 1. Amazing job so far.");
        } else if (level == 180) {
            yellowMessage(
                    "Have you ever tried taking a boss on by yourself? It is quite difficult.");
        } else if (level == 185) {
            yellowMessage("Legend has it that you're a legend.");
        } else if (level == 190) {
            yellowMessage("You only have 10 more levels to go until you hit 200!");
        } else if (level == 195) {
            yellowMessage("Nothing is stopping you at this point, level 195!");
        } else if (level == 200) {
            yellowMessage(
                    "Very nicely done! You have reached the so-long dreamed LEVEL 200!!! You are truly a hero among men, cheers upon you!");
        }
    }

    public void setPlayerRates() {
        this.expRate *= GameConstants.getPlayerBonusExpRate(this.level / 20);
        this.mesoRate *= GameConstants.getPlayerBonusMesoRate(this.level / 20);
        this.dropRate *= GameConstants.getPlayerBonusDropRate(this.level / 20);
    }

    public void revertLastPlayerRates() {
        this.expRate /= GameConstants.getPlayerBonusExpRate((this.level - 1) / 20);
        this.mesoRate /= GameConstants.getPlayerBonusMesoRate((this.level - 1) / 20);
        this.dropRate /= GameConstants.getPlayerBonusDropRate((this.level - 1) / 20);
    }

    public void revertPlayerRates() {
        this.expRate /= GameConstants.getPlayerBonusExpRate(this.level / 20);
        this.mesoRate /= GameConstants.getPlayerBonusMesoRate(this.level / 20);
        this.dropRate /= GameConstants.getPlayerBonusDropRate(this.level / 20);
    }

    public void setWorldRates() {
        World worldz = getWorldServer();
        this.expRate *= worldz.getExpRate();
        this.mesoRate *= worldz.getMesoRate();
        this.dropRate *= worldz.getDropRate();
    }

    public void revertWorldRates() {
        World worldz = getWorldServer();
        this.expRate /= worldz.getExpRate();
        this.mesoRate /= worldz.getMesoRate();
        this.dropRate /= worldz.getDropRate();
    }

    private void setCouponRates() {
        List<Integer> couponEffects;

        Collection<Item> cashItems = this.getInventory(MapleInventoryType.CASH).list();
        chrLock.lock();
        try {
            setActiveCoupons(cashItems);
            couponEffects = activateCouponsEffects();
        } finally {
            chrLock.unlock();
        }

        for (Integer couponId : couponEffects) {
            commitBuffCoupon(couponId);
        }
    }

    private void revertCouponRates() {
        revertCouponsEffects();
    }

    public void updateCouponRates() {
        revertCouponRates();
        setCouponRates();
    }

    public void resetPlayerRates() {
        expRate = 1;
        mesoRate = 1;
        dropRate = 1;

        expCoupon = 1;
        mesoCoupon = 1;
        dropCoupon = 1;
    }

    private int getCouponMultiplier(int couponId) {
        return activeCouponRates.get(couponId);
    }

    private void setExpCouponRate(int couponId, int couponQty) {
        this.expCoupon *= (getCouponMultiplier(couponId) * couponQty);
    }

    private void setDropCouponRate(int couponId, int couponQty) {
        this.dropCoupon *= (getCouponMultiplier(couponId) * couponQty);
        this.mesoCoupon *= (getCouponMultiplier(couponId) * couponQty);
    }

    private void revertCouponsEffects() {
        dispelBuffCoupons();

        this.expRate /= this.expCoupon;
        this.dropRate /= this.dropCoupon;
        this.mesoRate /= this.mesoCoupon;

        this.expCoupon = 1;
        this.dropCoupon = 1;
        this.mesoCoupon = 1;
    }

    private List<Integer> activateCouponsEffects() {
        List<Integer> toCommitEffect = new ArrayList<>();

        if (ServerConstants.USE_STACK_COUPON_RATES) {
            for (Entry<Integer, Integer> coupon : activeCoupons.entrySet()) {
                int couponId = coupon.getKey();
                int couponQty = coupon.getValue();

                toCommitEffect.add(couponId);

                if (ItemConstants.isExpCoupon(couponId)) setExpCouponRate(couponId, couponQty);
                else setDropCouponRate(couponId, couponQty);
            }
        } else {
            int maxExpRate = 1, maxDropRate = 1, maxExpCouponId = -1, maxDropCouponId = -1;

            for (Entry<Integer, Integer> coupon : activeCoupons.entrySet()) {
                int couponId = coupon.getKey();

                if (ItemConstants.isExpCoupon(couponId)) {
                    if (maxExpRate < getCouponMultiplier(couponId)) {
                        maxExpCouponId = couponId;
                        maxExpRate = getCouponMultiplier(couponId);
                    }
                } else {
                    if (maxDropRate < getCouponMultiplier(couponId)) {
                        maxDropCouponId = couponId;
                        maxDropRate = getCouponMultiplier(couponId);
                    }
                }
            }

            if (maxExpCouponId > -1) toCommitEffect.add(maxExpCouponId);
            if (maxDropCouponId > -1) toCommitEffect.add(maxDropCouponId);

            this.expCoupon = maxExpRate;
            this.dropCoupon = maxDropRate;
            this.mesoCoupon = maxDropRate;
        }

        this.expRate *= this.expCoupon;
        this.dropRate *= this.dropCoupon;
        this.mesoRate *= this.mesoCoupon;

        return toCommitEffect;
    }

    private void setActiveCoupons(Collection<Item> cashItems) {
        activeCoupons.clear();
        activeCouponRates.clear();

        Map<Integer, Integer> coupons = Server.getCouponRates();
        List<Integer> active = Server.getActiveCoupons();

        for (Item it : cashItems) {
            if (ItemConstants.isRateCoupon(it.getItemId()) && active.contains(it.getItemId())) {
                Integer count = activeCoupons.get(it.getItemId());

                if (count != null) activeCoupons.put(it.getItemId(), count + 1);
                else {
                    activeCoupons.put(it.getItemId(), 1);
                    activeCouponRates.put(it.getItemId(), coupons.get(it.getItemId()));
                }
            }
        }
    }

    private void commitBuffCoupon(int couponid) {
        if (!loggedIn || cashshop.isOpened()) return;

        MapleStatEffect mse = ii.getItemEffect(couponid);
        mse.applyTo(this);
    }

    public void dispelBuffCoupons() {
        List<MapleBuffStatValueHolder> allBuffs = getAllStatups();

        for (MapleBuffStatValueHolder mbsvh : allBuffs) {
            if (ItemConstants.isRateCoupon(mbsvh.effect.getSourceId())) {
                cancelEffect(mbsvh.effect, false, mbsvh.startTime);
            }
        }
    }

    public Set<Integer> getActiveCoupons() {
        chrLock.lock();
        try {
            return Collections.unmodifiableSet(activeCoupons.keySet());
        } finally {
            chrLock.unlock();
        }
    }

    public void addPlayerRing(MapleRing ring) {
        int ringItemId = ring.getItemId();
        if (ItemConstants.isWeddingRing(ringItemId)) {
            this.addMarriageRing(ring);
        } else if (ring.getItemId() > 1112012) {
            this.addFriendshipRing(ring);
        } else {
            this.addCrushRing(ring);
        }
    }

    public static MapleCharacter loadCharacterEntryFromDB(ResultSet rs, List<Item> equipped) {
        MapleCharacter ret = new MapleCharacter();

        try {
            ret.accountid = rs.getInt("accountid");
            ret.id = rs.getInt("id");
            ret.name = rs.getString("name");
            ret.gender = rs.getInt("gender");
            ret.skinColor = MapleSkinColor.getById(rs.getInt("skincolor"));
            ret.face = rs.getInt("face");
            ret.hair = rs.getInt("hair");

            // skipping pets, probably unneeded here

            ret.level = rs.getInt("level");
            ret.job = MapleJob.getById(rs.getInt("job"));
            ret.str = rs.getInt("str");
            ret.dex = rs.getInt("dex");
            ret.int_ = rs.getInt("int");
            ret.luk = rs.getInt("luk");
            ret.hp = rs.getInt("hp");
            ret.setMaxHp(rs.getInt("maxhp"));
            ret.mp = rs.getInt("mp");
            ret.setMaxMp(rs.getInt("maxmp"));
            ret.remainingAp = rs.getInt("ap");
            ret.loadCharSkillPoints(rs.getString("sp").split(","));
            ret.exp.set(rs.getInt("exp"));
            ret.fame = rs.getInt("fame");
            ret.gachaexp.set(rs.getInt("gachaexp"));
            ret.mapid = rs.getInt("map");
            ret.initialSpawnPoint = rs.getInt("spawnpoint");

            ret.gmLevel = rs.getInt("gm");
            ret.world = rs.getByte("world");
            ret.rank = rs.getInt("rank");
            ret.rankMove = rs.getInt("rankMove");
            ret.jobRank = rs.getInt("jobRank");
            ret.jobRankMove = rs.getInt("jobRankMove");

            if (equipped != null) { // players can have no equipped items at all, ofc
                MapleInventory inv = ret.inventory[MapleInventoryType.EQUIPPED.ordinal()];
                for (Item item : equipped) {
                    inv.addItemFromDB(item);
                }
            }
        } catch (SQLException sqle) {
            sqle.printStackTrace();
        }

        return ret;
    }

    public MapleCharacter generateCharacterEntry() {
        MapleCharacter ret = new MapleCharacter();

        ret.accountid = accountid;
        ret.id = id;
        ret.name = name;
        ret.gender = gender;
        ret.skinColor = skinColor;
        ret.face = face;
        ret.hair = hair;

        // skipping pets, probably unneeded here

        ret.level = level;
        ret.job = job;
        ret.str = this.getStr();
        ret.dex = this.getDex();
        ret.int_ = this.getInt();
        ret.luk = this.getLuk();
        ret.hp = this.getHp();
        ret.setMaxHp(this.getMaxHp());
        ret.mp = this.getMp();
        ret.setMaxMp(this.getMaxMp());
        ret.remainingAp = this.getRemainingAp();
        ret.setRemainingSp(this.getRemainingSps());
        ret.exp.set(this.getExp());
        ret.fame = fame;
        ret.gachaexp.set(this.getGachaExp());
        ret.mapid = this.getMapId();
        ret.initialSpawnPoint = initialSpawnPoint;

        ret.inventory[MapleInventoryType.EQUIPPED.ordinal()] =
                this.getInventory(MapleInventoryType.EQUIPPED);

        ret.gmLevel = this.gmLevel();
        ret.world = world;
        ret.rank = rank;
        ret.rankMove = rankMove;
        ret.jobRank = jobRank;
        ret.jobRankMove = jobRankMove;

        return ret;
    }

    private void loadCharSkillPoints(String[] skillPoints) {
        int[] sps = new int[skillPoints.length];
        for (int i = 0; i < skillPoints.length; i++) {
            sps[i] = Integer.parseInt(skillPoints[i]);
        }

        setRemainingSp(sps);
    }

    public int getRemainingSp() {
        return getRemainingSp(job.getId()); // default
    }

    public void updateRemainingSp(int remainingSp) {
        updateRemainingSp(remainingSp, GameConstants.getSkillBook(job.getId()));
    }

    public static MapleCharacter loadCharFromDB(
            int charid, MapleClient client, boolean channelserver) throws SQLException {
        try {
            MapleCharacter ret = new MapleCharacter();
            ret.client = client;
            ret.id = charid;

            Connection con = DatabaseConnection.getConnection();
            PreparedStatement ps = con.prepareStatement("SELECT * FROM characters WHERE id = ?");
            ps.setInt(1, charid);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) {
                rs.close();
                ps.close();
                throw new RuntimeException("Loading char failed (not found)");
            }
            ret.name = rs.getString("name");
            ret.level = rs.getInt("level");
            ret.fame = rs.getInt("fame");
            ret.quest_fame = rs.getInt("fquest");
            ret.str = rs.getInt("str");
            ret.dex = rs.getInt("dex");
            ret.int_ = rs.getInt("int");
            ret.luk = rs.getInt("luk");
            ret.exp.set(rs.getInt("exp"));
            ret.gachaexp.set(rs.getInt("gachaexp"));
            ret.hp = rs.getInt("hp");
            ret.setMaxHp(rs.getInt("maxhp"));
            ret.mp = rs.getInt("mp");
            ret.setMaxMp(rs.getInt("maxmp"));
            ret.hpMpApUsed = rs.getInt("hpMpUsed");
            ret.hasMerchant = rs.getInt("HasMerchant") == 1;
            ret.remainingAp = rs.getInt("ap");
            ret.loadCharSkillPoints(rs.getString("sp").split(","));
            ret.meso.set(rs.getInt("meso"));
            ret.merchantmeso = rs.getInt("MerchantMesos");
            ret.gmLevel = rs.getInt("gm");
            ret.skinColor = MapleSkinColor.getById(rs.getInt("skincolor"));
            ret.gender = rs.getInt("gender");
            ret.job = MapleJob.getById(rs.getInt("job"));
            ret.finishedDojoTutorial = rs.getInt("finishedDojoTutorial") == 1;
            ret.vanquisherKills = rs.getInt("vanquisherKills");
            ret.omokwins = rs.getInt("omokwins");
            ret.omoklosses = rs.getInt("omoklosses");
            ret.omokties = rs.getInt("omokties");
            ret.matchcardwins = rs.getInt("matchcardwins");
            ret.matchcardlosses = rs.getInt("matchcardlosses");
            ret.matchcardties = rs.getInt("matchcardties");
            ret.hair = rs.getInt("hair");
            ret.face = rs.getInt("face");
            ret.accountid = rs.getInt("accountid");
            ret.mapid = rs.getInt("map");
            ret.jailExpiration = rs.getLong("jailexpire");
            ret.initialSpawnPoint = rs.getInt("spawnpoint");
            ret.world = rs.getByte("world");
            ret.rank = rs.getInt("rank");
            ret.rankMove = rs.getInt("rankMove");
            ret.jobRank = rs.getInt("jobRank");
            ret.jobRankMove = rs.getInt("jobRankMove");
            int mountexp = rs.getInt("mountexp");
            int mountlevel = rs.getInt("mountlevel");
            int mounttiredness = rs.getInt("mounttiredness");
            ret.guildid = rs.getInt("guildid");
            ret.guildRank = rs.getInt("guildrank");
            ret.allianceRank = rs.getInt("allianceRank");
            ret.familyId = rs.getInt("familyId");
            ret.bookCover = rs.getInt("monsterbookcover");
            ret.monsterbook = new MonsterBook();
            ret.monsterbook.loadCards(charid);
            ret.vanquisherStage = rs.getInt("vanquisherStage");
            ret.dojoPoints = rs.getInt("dojoPoints");
            ret.dojoStage = rs.getInt("lastDojoStage");
            ret.dataString = rs.getString("dataString");
            ret.mgc = new MapleGuildCharacter(ret);
            int buddyCapacity = rs.getInt("buddyCapacity");
            ret.buddylist = new BuddyList(buddyCapacity);

            ret.getInventory(MapleInventoryType.EQUIP).setSlotLimit(rs.getByte("equipslots"));
            ret.getInventory(MapleInventoryType.USE).setSlotLimit(rs.getByte("useslots"));
            ret.getInventory(MapleInventoryType.SETUP).setSlotLimit(rs.getByte("setupslots"));
            ret.getInventory(MapleInventoryType.ETC).setSlotLimit(rs.getByte("etcslots"));

            byte sandboxCheck = 0x0;
            for (Pair<Item, MapleInventoryType> item :
                    ItemFactory.INVENTORY.loadItems(ret.id, !channelserver)) {
                sandboxCheck |= item.getLeft().getFlag();

                ret.getInventory(item.getRight()).addItemFromDB(item.getLeft());
                Item itemz = item.getLeft();
                if (itemz.getPetId() > -1) {
                    MaplePet pet = itemz.getPet();
                    if (pet != null && pet.isSummoned()) {
                        ret.addPet(pet);
                    }
                    continue;
                }

                MapleInventoryType mit = item.getRight();
                if (mit.equals(MapleInventoryType.EQUIP)
                        || mit.equals(MapleInventoryType.EQUIPPED)) {
                    Equip equip = (Equip) item.getLeft();
                    if (equip.getRingId() > -1) {
                        MapleRing ring = MapleRing.loadFromDb(equip.getRingId());
                        if (item.getRight().equals(MapleInventoryType.EQUIPPED)) {
                            ring.equip();
                        }

                        ret.addPlayerRing(ring);
                    }
                }
            }
            if ((sandboxCheck & ItemConstants.SANDBOX) == ItemConstants.SANDBOX)
                ret.setHasSandboxItem();

            World wserv = Server.getInstance().getWorld(ret.world);

            ret.partnerId = rs.getInt("partnerId");
            ret.marriageItemid = rs.getInt("marriageItemId");
            if (ret.marriageItemid > 0 && ret.partnerId <= 0) {
                ret.marriageItemid = -1;
            } else if (ret.partnerId > 0 && wserv.getRelationshipId(ret.id) <= 0) {
                ret.marriageItemid = -1;
                ret.partnerId = -1;
            }

            NewYearCardRecord.loadPlayerNewYearCards(ret);

            PreparedStatement ps2, ps3;
            ResultSet rs2, rs3;

            ps3 =
                    con.prepareStatement(
                            "SELECT petid FROM inventoryitems WHERE characterid = ? AND petid > -1");
            ps3.setInt(1, charid);
            rs3 = ps3.executeQuery();
            while (rs3.next()) {
                int petId = rs3.getInt("petid");

                ps2 = con.prepareStatement("SELECT itemid FROM petignores WHERE petid = ?");
                ps2.setInt(1, petId);

                ret.resetExcluded(petId);

                rs2 = ps2.executeQuery();
                while (rs2.next()) {
                    ret.addExcluded(petId, rs2.getInt("itemid"));
                }

                ps2.close();
                rs2.close();
            }
            ps3.close();
            rs3.close();

            ret.commitExcludedItems();

            if (channelserver) {
                MapleMapFactory mapFactory = client.getChannelServer().getMapFactory();
                ret.map = mapFactory.getMap(ret.mapid);

                if (ret.map == null) {
                    ret.map = mapFactory.getMap(100000000);
                }
                MaplePortal portal = ret.map.getPortal(ret.initialSpawnPoint);
                if (portal == null) {
                    portal = ret.map.getPortal(0);
                    ret.initialSpawnPoint = 0;
                }
                ret.setPosition(portal.getPosition());
                int partyid = rs.getInt("party");
                MapleParty party = wserv.getParty(partyid);
                if (party != null) {
                    ret.mpc = party.getMemberById(ret.id);
                    if (ret.mpc != null) {
                        ret.mpc = new MaplePartyCharacter(ret);
                        ret.party = party;
                    }
                }
                int messengerid = rs.getInt("messengerid");
                int position = rs.getInt("messengerposition");
                if (messengerid > 0 && position < 4 && position > -1) {
                    MapleMessenger messenger = wserv.getMessenger(messengerid);
                    if (messenger != null) {
                        ret.messenger = messenger;
                        ret.messengerposition = position;
                    }
                }
                ret.loggedIn = true;
            }
            rs.close();
            ps.close();
            ps =
                    con.prepareStatement(
                            "SELECT mapid,vip FROM trocklocations WHERE characterid = ? LIMIT 15");
            ps.setInt(1, charid);
            rs = ps.executeQuery();
            byte v = 0;
            byte r = 0;
            while (rs.next()) {
                if (rs.getInt("vip") == 1) {
                    ret.viptrockmaps.add(rs.getInt("mapid"));
                    v++;
                } else {
                    ret.trockmaps.add(rs.getInt("mapid"));
                    r++;
                }
            }
            while (v < 10) {
                ret.viptrockmaps.add(999999999);
                v++;
            }
            while (r < 5) {
                ret.trockmaps.add(999999999);
                r++;
            }
            rs.close();
            ps.close();
            ps =
                    con.prepareStatement(
                            "SELECT name, characterslots FROM accounts WHERE id = ?",
                            Statement.RETURN_GENERATED_KEYS);
            ps.setInt(1, ret.accountid);
            rs = ps.executeQuery();
            if (rs.next()) {
                MapleClient retClient = ret.client;

                retClient.setAccountName(rs.getString("name"));
                retClient.setCharacterSlots(rs.getByte("characterslots"));
            }
            rs.close();
            ps.close();
            ps = con.prepareStatement("SELECT `area`,`info` FROM area_info WHERE charid = ?");
            ps.setInt(1, ret.id);
            rs = ps.executeQuery();
            while (rs.next()) {
                ret.area_info.put(rs.getShort("area"), rs.getString("info"));
            }
            rs.close();
            ps.close();
            ps = con.prepareStatement("SELECT `name`,`info` FROM eventstats WHERE characterid = ?");
            ps.setInt(1, ret.id);
            rs = ps.executeQuery();
            while (rs.next()) {
                String name = rs.getString("name");
                if (rs.getString("name").equals("rescueGaga")) {
                    ret.events.put(name, new RescueGaga(rs.getInt("info")));
                }
                // ret.events = new MapleEvents(new RescueGaga(rs.getInt("rescuegaga")), new
                // ArtifactHunt(rs.getInt("artifacthunt")));
            }
            rs.close();
            ps.close();
            ret.cashshop = new CashShop(ret.accountid, ret.id, ret.getJobType());
            ret.autoban = new AutobanManager(ret);
            ps =
                    con.prepareStatement(
                            "SELECT name, level FROM characters WHERE accountid = ? AND id != ? ORDER BY level DESC limit 1");
            ps.setInt(1, ret.accountid);
            ps.setInt(2, charid);
            rs = ps.executeQuery();
            if (rs.next()) {
                ret.linkedName = rs.getString("name");
                ret.linkedLevel = rs.getInt("level");
            }
            rs.close();
            ps.close();
            if (channelserver) {
                ps = con.prepareStatement("SELECT * FROM queststatus WHERE characterid = ?");
                ps.setInt(1, charid);
                rs = ps.executeQuery();

                Map<Integer, MapleQuestStatus> loadedQuestStatus = new LinkedHashMap<>();
                while (rs.next()) {
                    MapleQuest q = MapleQuest.getInstance(rs.getShort("quest"));
                    MapleQuestStatus status =
                            new MapleQuestStatus(
                                    q, MapleQuestStatus.Status.getById(rs.getInt("status")));
                    long cTime = rs.getLong("time");
                    if (cTime > -1) {
                        status.setCompletionTime(cTime * 1000);
                    }

                    long eTime = rs.getLong("expires");
                    if (eTime > 0) {
                        status.setExpirationTime(eTime);
                    }

                    status.setForfeited(rs.getInt("forfeited"));
                    ret.quests.put(q.getId(), status);
                    loadedQuestStatus.put(rs.getInt("queststatusid"), status);
                }
                rs.close();
                ps.close();

                // opportunity for improvement on questprogress/medalmaps calls to DB
                try (PreparedStatement pse =
                        con.prepareStatement("SELECT * FROM questprogress WHERE characterid = ?")) {
                    pse.setInt(1, charid);
                    try (ResultSet rsProgress = pse.executeQuery()) {
                        while (rsProgress.next()) {
                            MapleQuestStatus status =
                                    loadedQuestStatus.get(rsProgress.getInt("queststatusid"));
                            if (status != null) {
                                status.setProgress(
                                        rsProgress.getInt("progressid"),
                                        rsProgress.getString("progress"));
                            }
                        }
                    }
                }

                try (PreparedStatement pse =
                        con.prepareStatement("SELECT * FROM medalmaps WHERE characterid = ?")) {
                    pse.setInt(1, charid);
                    try (ResultSet rsMedalMaps = pse.executeQuery()) {
                        while (rsMedalMaps.next()) {
                            MapleQuestStatus status =
                                    loadedQuestStatus.get(rsMedalMaps.getInt("queststatusid"));
                            if (status != null) {
                                status.addMedalMap(rsMedalMaps.getInt("mapid"));
                            }
                        }
                    }
                }

                loadedQuestStatus.clear();

                ps =
                        con.prepareStatement(
                                "SELECT skillid,skilllevel,masterlevel,expiration FROM skills WHERE characterid = ?");
                ps.setInt(1, charid);
                rs = ps.executeQuery();
                while (rs.next()) {
                    ret.skills.put(
                            SkillFactory.getSkill(rs.getInt("skillid")),
                            new SkillEntry(
                                    rs.getByte("skilllevel"),
                                    rs.getInt("masterlevel"),
                                    rs.getLong("expiration")));
                }
                rs.close();
                ps.close();
                ps =
                        con.prepareStatement(
                                "SELECT SkillID,StartTime,length FROM cooldowns WHERE charid = ?");
                ps.setInt(1, ret.id);
                rs = ps.executeQuery();
                long curTime = System.currentTimeMillis();
                while (rs.next()) {
                    final int skillid = rs.getInt("SkillID");
                    final long length = rs.getLong("length"), startTime = rs.getLong("StartTime");
                    if (skillid != 5221999 && (length + startTime < curTime)) {
                        continue;
                    }
                    ret.giveCoolDowns(skillid, startTime, length);
                }
                rs.close();
                ps.close();
                ps = con.prepareStatement("DELETE FROM cooldowns WHERE charid = ?");
                ps.setInt(1, ret.id);
                ps.executeUpdate();
                ps.close();
                Map<MapleDisease, Pair<Long, MobSkill>> loadedDiseases = new LinkedHashMap<>();
                ps = con.prepareStatement("SELECT * FROM playerdiseases WHERE charid = ?");
                ps.setInt(1, ret.id);
                rs = ps.executeQuery();
                while (rs.next()) {
                    final MapleDisease disease = MapleDisease.ordinal(rs.getInt("disease"));
                    if (disease == MapleDisease.NULL) continue;

                    final int skillid = rs.getInt("mobskillid"), skilllv = rs.getInt("mobskilllv");
                    final long length = rs.getInt("length");

                    MobSkill ms = MobSkillFactory.getMobSkill(skillid, skilllv);
                    if (ms != null) {
                        loadedDiseases.put(disease, new Pair<>(length, ms));
                    }
                }
                rs.close();
                ps.close();
                ps = con.prepareStatement("DELETE FROM playerdiseases WHERE charid = ?");
                ps.setInt(1, ret.id);
                ps.executeUpdate();
                ps.close();
                if (!loadedDiseases.isEmpty())
                    Server.getInstance()
                            .getPlayerBuffStorage()
                            .addDiseasesToStorage(ret.id, loadedDiseases);
                ps = con.prepareStatement("SELECT * FROM skillmacros WHERE characterid = ?");
                ps.setInt(1, charid);
                rs = ps.executeQuery();
                while (rs.next()) {
                    int position = rs.getInt("position");
                    SkillMacro macro =
                            new SkillMacro(
                                    rs.getInt("skill1"),
                                    rs.getInt("skill2"),
                                    rs.getInt("skill3"),
                                    rs.getString("name"),
                                    rs.getInt("shout"),
                                    position);
                    ret.skillMacros[position] = macro;
                }
                rs.close();
                ps.close();
                ps =
                        con.prepareStatement(
                                "SELECT `key`,`type`,`action` FROM keymap WHERE characterid = ?");
                ps.setInt(1, charid);
                rs = ps.executeQuery();
                while (rs.next()) {
                    int key = rs.getInt("key");
                    int type = rs.getInt("type");
                    int action = rs.getInt("action");
                    ret.keymap.put(key, new MapleKeyBinding(type, action));
                }
                rs.close();
                ps.close();
                ps =
                        con.prepareStatement(
                                "SELECT `locationtype`,`map`,`portal` FROM savedlocations WHERE characterid = ?");
                ps.setInt(1, charid);
                rs = ps.executeQuery();
                while (rs.next()) {
                    ret.savedLocations[
                                    SavedLocationType.valueOf(rs.getString("locationtype"))
                                            .ordinal()] =
                            new SavedLocation(rs.getInt("map"), rs.getInt("portal"));
                }
                rs.close();
                ps.close();
                ps =
                        con.prepareStatement(
                                "SELECT `characterid_to`,`when` FROM famelog WHERE characterid = ? AND DATEDIFF(NOW(),`when`) < 30");
                ps.setInt(1, charid);
                rs = ps.executeQuery();
                ret.lastfametime = 0;
                ret.lastmonthfameids = new ArrayList<>(31);
                while (rs.next()) {
                    ret.lastfametime =
                            Math.max(ret.lastfametime, rs.getTimestamp("when").getTime());
                    ret.lastmonthfameids.add(rs.getInt("characterid_to"));
                }
                rs.close();
                ps.close();
                ret.buddylist.loadFromDb(charid);
                ret.storage = MapleStorage.loadOrCreateFromDB(ret.accountid, ret.world);

                int startHp = ret.hp, startMp = ret.mp;
                ret.recalcLocalStats();
                ret.changeHpMp(startHp, startMp, true);
                // ret.resetBattleshipHp();
            }
            int mountid = ret.getJobType() * 10000000 + 1004;
            ret.maplemount =
                    ret.getInventory(MapleInventoryType.EQUIPPED).getItem((short) -18) != null
                            ? new MapleMount(
                                    ret,
                                    ret.getInventory(MapleInventoryType.EQUIPPED)
                                            .getItem((short) -18)
                                            .getItemId(),
                                    mountid)
                            : new MapleMount(ret, 0, mountid);
            ret.maplemount.setExp(mountexp);
            ret.maplemount.setLevel(mountlevel);
            ret.maplemount.setTiredness(mounttiredness);
            ret.maplemount.setActive(false);

            con.close();
            return ret;
        } catch (SQLException | RuntimeException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void reloadQuestExpirations() {
        for (MapleQuestStatus mqs : quests.values()) {
            if (mqs.getExpirationTime() > 0) {
                questTimeLimit2(mqs.getQuest(), mqs.getExpirationTime());
            }
        }
    }

    public static String makeMapleReadable(String in) {
        String i = in.replace('I', 'i');
        i = i.replace('l', 'L');
        i = i.replace("rn", "Rn");
        i = i.replace("vv", "Vv");
        i = i.replace("VV", "Vv");

        return i;
    }

    private static class MapleBuffStatValueHolder {

        public MapleStatEffect effect;
        public long startTime;
        public int value;
        public boolean bestApplied;

        public MapleBuffStatValueHolder(MapleStatEffect effect, long startTime, int value) {
            this.effect = effect;
            this.startTime = startTime;
            this.value = value;
            this.bestApplied = false;
        }
    }

    public static class MapleCoolDownValueHolder {
        public int skillId;
        public long startTime, length;

        public MapleCoolDownValueHolder(int skillId, long startTime, long length) {
            this.skillId = skillId;
            this.startTime = startTime;
            this.length = length;
        }
    }

    public void message(String m) {
        dropMessage(5, m);
    }

    public void yellowMessage(String m) {
        announce(MaplePacketCreator.sendYellowTip(m));
    }

    public void updateQuestMobCount(int id) {
        // It seems nexon uses monsters that don't exist in the WZ (except string) to merge multiple
        // mobs together for these 3 monsters.
        // We also want to run mobKilled for both since there are some quest that don't use the
        // updated ID...
        if (id == 1110100 || id == 1110130) {
            updateQuestMobCount(9101000);
        } else if (id == 2230101 || id == 2230131) {
            updateQuestMobCount(9101001);
        } else if (id == 1140100 || id == 1140130) {
            updateQuestMobCount(9101002);
        }

        int lastQuestProcessed = 0;
        try {
            synchronized (quests) {
                for (MapleQuestStatus q : quests.values()) {
                    lastQuestProcessed = q.getQuest().getId();
                    if (q.getStatus() == MapleQuestStatus.Status.COMPLETED
                            || q.getQuest().canComplete(this, null)) {
                        continue;
                    }
                    String progress = q.getProgress(id);
                    if (!progress.isEmpty()
                            && Integer.parseInt(progress) >= q.getQuest().getMobAmountNeeded(id)) {
                        continue;
                    }
                    if (q.progress(id)) {
                        client.announce(MaplePacketCreator.updateQuest(q, false));
                    }
                }
            }
        } catch (Exception e) {
            FilePrinter.printError(
                    FilePrinter.EXCEPTION_CAUGHT,
                    e,
                    "MapleCharacter.mobKilled. CID: "
                            + this.id
                            + " last Quest Processed: "
                            + lastQuestProcessed);
        }
    }

    public void mount(int id, int skillid) {
        maplemount = new MapleMount(this, id, skillid);
    }

    private void playerDead() {
        cancelAllBuffs(false);
        dispelDebuffs();
        lastDeathtime = System.currentTimeMillis();

        EventInstanceManager eim = getEventInstance();
        if (eim != null) {
            eim.playerKilled(this);
        }
        int[] charmID = {5130000, 4031283, 4140903};
        int possesed = 0;
        int i;
        for (i = 0; i < charmID.length; i++) {
            int quantity = getItemQuantity(charmID[i], false);
            if (possesed == 0 && quantity > 0) {
                possesed = quantity;
                break;
            }
        }
        if (possesed > 0) {
            message("You have used a safety charm, so your EXP points have not been decreased.");
            MapleInventoryManipulator.removeById(
                    client, ItemConstants.getInventoryType(charmID[i]), charmID[i], 1, true, false);
        } else if (mapid > 925020000 && mapid < 925030000) {
            this.dojoStage = 0;
        } else if (mapid > 980000100 && mapid < 980000700) {
            map.broadcastMessage(this, MaplePacketCreator.CPQDied(this));
        } else if (job != MapleJob.BEGINNER) { // Hmm...
            int XPdummy = ExpTable.getExpNeededForLevel(level);
            if (map.isTown()) {
                XPdummy /= 100;
            }
            if (XPdummy == ExpTable.getExpNeededForLevel(level)) {
                if (getLuk() <= 100 && getLuk() > 8) {
                    XPdummy *= (200 - getLuk()) / 2000;
                } else if (getLuk() < 8) {
                    XPdummy /= 10;
                } else {
                    XPdummy /= 20;
                }
            }
            if (getExp() > XPdummy) {
                loseExp(XPdummy, false, false);
            } else {
                loseExp(getExp(), false, false);
            }
        }
        if (getBuffedValue(MapleBuffStat.MORPH) != null) {
            cancelEffectFromBuffStat(MapleBuffStat.MORPH);
        }

        if (getBuffedValue(MapleBuffStat.MONSTER_RIDING) != null) {
            cancelEffectFromBuffStat(MapleBuffStat.MONSTER_RIDING);
        }

        if (getChair() != 0) {
            setChair(0);
            client.announce(MaplePacketCreator.cancelChair(-1));
            map.broadcastMessage(this, MaplePacketCreator.showChair(id, 0), false);
        }
        client.announce(MaplePacketCreator.enableActions());
    }

    public void respawn(int returnMap) {
        respawn(null, returnMap); // unspecified EIM, don't force EIM unregister in this case
    }

    public void respawn(EventInstanceManager eim, int returnMap) {
        cancelAllBuffs(false);

        updateHp(50);
        setStance(0);

        if (eim != null) eim.unregisterPlayer(this); // some event scripts uses this...
        changeMap(returnMap);
    }

    private void prepareDragonBlood(final MapleStatEffect bloodEffect) {
        if (dragonBloodSchedule != null) {
            dragonBloodSchedule.cancel(false);
        }
        dragonBloodSchedule =
                TimerManager.getInstance()
                        .register(
                                () -> {
                                    if (awayFromWorld.get()) return;

                                    addHP(-bloodEffect.getX());
                                    announce(
                                            MaplePacketCreator.showOwnBuffEffect(
                                                    bloodEffect.getSourceId(), 5));
                                    getMap().broadcastMessage(
                                                    MapleCharacter.this,
                                                    MaplePacketCreator.showBuffeffect(
                                                            getId(), bloodEffect.getSourceId(), 5),
                                                    false);
                                },
                                4000,
                                4000);
    }

    private void recalcEquipStats() {
        if (equipchanged) {
            equipmaxhp = 0;
            equipmaxmp = 0;
            equipdex = 0;
            equipint_ = 0;
            equipstr = 0;
            equipluk = 0;
            equipmagic = 0;
            equipwatk = 0;
            // equipspeed = 0;
            // equipjump = 0;

            for (Item item : getInventory(MapleInventoryType.EQUIPPED)) {
                Equip equip = (Equip) item;
                equipmaxhp += equip.getHp();
                equipmaxmp += equip.getMp();
                equipdex += equip.getDex();
                equipint_ += equip.getInt();
                equipstr += equip.getStr();
                equipluk += equip.getLuk();
                equipmagic += equip.getMatk() + equip.getInt();
                equipwatk += equip.getWatk();
                // equipspeed += equip.getSpeed();
                // equipjump += equip.getJump();
            }

            equipchanged = false;
        }

        localmaxhp += equipmaxhp;
        localmaxmp += equipmaxmp;
        localdex += equipdex;
        localint_ += equipint_;
        localstr += equipstr;
        localluk += equipluk;
        localmagic += equipmagic;
        localwatk += equipwatk;
    }

    private List<Pair<MapleStat, Integer>> recalcLocalStats() {
        effLock.lock();
        statWlock.lock();
        try {
            List<Pair<MapleStat, Integer>> hpmpupdate = new ArrayList<>(2);

            int oldlocalmaxhp = localmaxhp;
            int oldlocalmaxmp = localmaxmp;

            localmaxhp = getMaxHp();
            localmaxmp = getMaxMp();
            localdex = getDex();
            localint_ = getInt();
            localstr = getStr();
            localluk = getLuk();
            localmagic = localint_;
            localwatk = 0;
            localchairrate = -1;

            recalcEquipStats();

            localmagic = Math.min(localmagic, 2000);

            Integer hbhp = getBuffedValue(MapleBuffStat.HYPERBODYHP);
            if (hbhp != null) {
                localmaxhp += (hbhp.doubleValue() / 100) * localmaxhp;
            }
            Integer hbmp = getBuffedValue(MapleBuffStat.HYPERBODYMP);
            if (hbmp != null) {
                localmaxmp += (hbmp.doubleValue() / 100) * localmaxmp;
            }

            localmaxhp = Math.min(30000, localmaxhp);
            localmaxmp = Math.min(30000, localmaxmp);

            if (ServerConstants.USE_FIXED_RATIO_HPMP_UPDATE) {
                if (localmaxhp != oldlocalmaxhp)
                    hpmpupdate.add(calcHpRatioUpdate(localmaxhp, oldlocalmaxhp));
                if (localmaxmp != oldlocalmaxmp)
                    hpmpupdate.add(calcMpRatioUpdate(localmaxmp, oldlocalmaxmp));
            }

            MapleStatEffect combo = getBuffEffect(MapleBuffStat.ARAN_COMBO);
            if (combo != null) {
                localwatk += combo.getX();
            }

            if (energybar == 15000) {
                Skill energycharge =
                        isCygnus()
                                ? SkillFactory.getSkill(ThunderBreaker.ENERGY_CHARGE)
                                : SkillFactory.getSkill(Marauder.ENERGY_CHARGE);
                MapleStatEffect ceffect = energycharge.getEffect(getSkillLevel(energycharge));
                localwatk += ceffect.getWatk();
            }

            Integer mwarr = getBuffedValue(MapleBuffStat.MAPLE_WARRIOR);
            if (mwarr != null) {
                localstr += getStr() * mwarr / 100;
                localdex += getDex() * mwarr / 100;
                localint_ += getInt() * mwarr / 100;
                localluk += getLuk() * mwarr / 100;
            }
            if (job.isA(MapleJob.BOWMAN)) {
                Skill expert = null;
                if (job.isA(MapleJob.MARKSMAN)) {
                    expert = SkillFactory.getSkill(3220004);
                } else if (job.isA(MapleJob.BOWMASTER)) {
                    expert = SkillFactory.getSkill(3120005);
                }
                if (expert != null) {
                    int boostLevel = getSkillLevel(expert);
                    if (boostLevel > 0) {
                        localwatk += expert.getEffect(boostLevel).getX();
                    }
                }
            }

            Integer watkbuff = getBuffedValue(MapleBuffStat.WATK);
            if (watkbuff != null) {
                localwatk += watkbuff.intValue();
            }
            Integer matkbuff = getBuffedValue(MapleBuffStat.MATK);
            if (matkbuff != null) {
                localmagic += matkbuff.intValue();
            }

            /*
            Integer speedbuff = getBuffedValue(MapleBuffStat.SPEED);
            if (speedbuff != null) {
                localspeed += speedbuff.intValue();
            }
            Integer jumpbuff = getBuffedValue(MapleBuffStat.JUMP);
            if (jumpbuff != null) {
                localjump += jumpbuff.intValue();
            }
            */

            Integer blessing = getSkillLevel(10000000 * getJobType() + 12);
            if (blessing > 0) {
                localwatk += blessing;
                localmagic += blessing * 2;
            }

            if (job.isA(MapleJob.THIEF)
                    || job.isA(MapleJob.BOWMAN)
                    || job.isA(MapleJob.PIRATE)
                    || job.isA(MapleJob.NIGHTWALKER1)
                    || job.isA(MapleJob.WINDARCHER1)) {
                Item weapon_item = getInventory(MapleInventoryType.EQUIPPED).getItem((short) -11);
                if (weapon_item != null) {
                    MapleWeaponType weapon = ii.getWeaponType(weapon_item.getItemId());
                    boolean bow = weapon == MapleWeaponType.BOW;
                    boolean crossbow = weapon == MapleWeaponType.CROSSBOW;
                    boolean claw = weapon == MapleWeaponType.CLAW;
                    boolean gun = weapon == MapleWeaponType.GUN;
                    if (bow || crossbow || claw || gun) {
                        // Also calc stars into this.
                        MapleInventory inv = getInventory(MapleInventoryType.USE);
                        for (short i = 1; i <= inv.getSlotLimit(); i++) {
                            Item item = inv.getItem(i);
                            if (item != null) {
                                if ((claw && ItemConstants.isThrowingStar(item.getItemId()))
                                        || (gun && ItemConstants.isBullet(item.getItemId()))
                                        || (bow && ItemConstants.isArrowForBow(item.getItemId()))
                                        || (crossbow
                                                && ItemConstants.isArrowForCrossBow(
                                                        item.getItemId()))) {
                                    if (item.getQuantity() > 0) {
                                        // Finally there!
                                        localwatk += ii.getWatkForProjectile(item.getItemId());
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
                // Add throwing stars to dmg.
            }

            return hpmpupdate;
        } finally {
            statWlock.unlock();
            effLock.unlock();
        }
    }

    private void updateLocalStats() {
        effLock.lock();
        statWlock.lock();
        try {
            int oldmaxhp = localmaxhp;
            List<Pair<MapleStat, Integer>> hpmpupdate = recalcLocalStats();
            enforceMaxHpMp();

            if (!hpmpupdate.isEmpty()) {
                client.announce(MaplePacketCreator.updatePlayerStats(hpmpupdate, true, this));
            }

            if (oldmaxhp != localmaxhp) {
                updatePartyMemberHP();
            }
        } finally {
            statWlock.unlock();
            effLock.unlock();
        }
    }

    public void receivePartyMemberHP() {
        prtLock.lock();
        try {
            if (party != null) {
                for (MapleCharacter partychar : this.getPartyMembersOnSameMap()) {
                    announce(
                            MaplePacketCreator.updatePartyMemberHP(
                                    partychar.id, partychar.getHp(), partychar.getCurrentMaxHp()));
                }
            }
        } finally {
            prtLock.unlock();
        }
    }

    public void removeAllCooldownsExcept(int id, boolean packet) {
        effLock.lock();
        chrLock.lock();
        try {
            ArrayList<MapleCoolDownValueHolder> list = new ArrayList<>(coolDowns.values());
            for (MapleCoolDownValueHolder mcvh : list) {
                if (mcvh.skillId != id) {
                    coolDowns.remove(mcvh.skillId);
                    if (packet) {
                        client.announce(MaplePacketCreator.skillCooldown(mcvh.skillId, 0));
                    }
                }
            }
        } finally {
            chrLock.unlock();
            effLock.unlock();
        }
    }

    public static void removeAriantRoom(int room) {
        ariantroomleader[room] = "";
        ariantroomslot[room] = 0;
    }

    public void removeCooldown(int skillId) {
        effLock.lock();
        chrLock.lock();
        try {
            if (this.coolDowns.containsKey(skillId)) {
                this.coolDowns.remove(skillId);
            }
        } finally {
            chrLock.unlock();
            effLock.unlock();
        }
    }

    public void removePet(MaplePet pet, boolean shift_left) {
        petLock.lock();
        try {
            int slot = -1;
            for (int i = 0; i < 3; i++) {
                if (pets[i] != null) {
                    if (pets[i].getUniqueId() == pet.getUniqueId()) {
                        pets[i] = null;
                        slot = i;
                        break;
                    }
                }
            }
            if (shift_left) {
                if (slot > -1) {
                    for (int i = slot; i < 3; i++) {
                        pets[i] = i != 2 ? pets[i + 1] : null;
                    }
                }
            }
        } finally {
            petLock.unlock();
        }
    }

    public void removeVisibleMapObject(MapleMapObject mo) {
        visibleMapObjects.remove(mo);
    }

    public synchronized void resetStats() {
        if (!ServerConstants.USE_AUTOASSIGN_STARTERS_AP) {
            return;
        }

        effLock.lock();
        statWlock.lock();
        try {
            int tap = remainingAp + str + dex + int_ + luk, tsp = 1;
            int tstr = 4, tdex = 4, tint = 4, tluk = 4;

            switch (job.getId()) {
                case 100:
                case 1100:
                case 2100:
                    tstr = 35;
                    tsp += ((level - 10) * 3);
                    break;
                case 200:
                case 1200:
                    tint = 20;
                    tsp += ((level - 8) * 3);
                    break;
                case 300:
                case 1300:
                case 400:
                case 1400:
                    tdex = 25;
                    tsp += ((level - 10) * 3);
                    break;
                case 500:
                case 1500:
                    tdex = 20;
                    tsp += ((level - 10) * 3);
                    break;
            }

            tap -= tstr;
            tap -= tdex;
            tap -= tint;
            tap -= tluk;

            if (tap >= 0) {
                updateStrDexIntLukSp(
                        tstr, tdex, tint, tluk, tap, tsp, GameConstants.getSkillBook(job.getId()));
            } else {
                FilePrinter.print(
                        FilePrinter.EXCEPTION_CAUGHT,
                        name
                                + " tried to get their stats reseted, without having enough AP available.");
            }
        } finally {
            statWlock.unlock();
            effLock.unlock();
        }
    }

    public void resetBattleshipHp() {
        this.battleshipHp =
                400 * getSkillLevel(SkillFactory.getSkill(Corsair.BATTLE_SHIP))
                        + ((level - 120) * 200);
    }

    public void resetEnteredScript() {
        if (entered.containsKey(map.getId())) {
            entered.remove(map.getId());
        }
    }

    public void resetEnteredScript(int mapId) {
        if (entered.containsKey(mapId)) {
            entered.remove(mapId);
        }
    }

    public void resetEnteredScript(String script) {
        for (Entry<Integer, String> integerStringEntry : entered.entrySet()) {
            if (integerStringEntry.getValue().equals(script)) {
                entered.remove(integerStringEntry.getKey());
            }
        }
    }

    public synchronized void saveCooldowns() {
        List<PlayerCoolDownValueHolder> listcd = getAllCooldowns();

        if (!listcd.isEmpty()) {
            try {
                Connection con = DatabaseConnection.getConnection();
                deleteWhereCharacterId(con, "DELETE FROM cooldowns WHERE charid = ?");
                try (PreparedStatement ps =
                        con.prepareStatement(
                                "INSERT INTO cooldowns (charid, SkillID, StartTime, length) VALUES (?, ?, ?, ?)")) {
                    ps.setInt(1, id);
                    for (PlayerCoolDownValueHolder cooling : listcd) {
                        ps.setInt(2, cooling.skillId);
                        ps.setLong(3, cooling.startTime);
                        ps.setLong(4, cooling.length);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }

                con.close();
            } catch (SQLException se) {
                se.printStackTrace();
            }
        }

        Map<MapleDisease, Pair<Long, MobSkill>> listds = getAllDiseases();
        if (!listds.isEmpty()) {
            try {
                Connection con = DatabaseConnection.getConnection();
                deleteWhereCharacterId(con, "DELETE FROM playerdiseases WHERE charid = ?");
                try (PreparedStatement ps =
                        con.prepareStatement(
                                "INSERT INTO playerdiseases (charid, disease, mobskillid, mobskilllv, length) VALUES (?, ?, ?, ?, ?)")) {
                    ps.setInt(1, id);

                    for (Entry<MapleDisease, Pair<Long, MobSkill>> e : listds.entrySet()) {
                        ps.setInt(2, e.getKey().ordinal());

                        MobSkill ms = e.getValue().getRight();
                        ps.setInt(3, ms.getSkillId());
                        ps.setInt(4, ms.getSkillLevel());
                        ps.setInt(5, e.getValue().getLeft().intValue());
                        ps.addBatch();
                    }

                    ps.executeBatch();
                }

                con.close();
            } catch (SQLException se) {
                se.printStackTrace();
            }
        }
    }

    public void saveGuildStatus() {
        try {
            Connection con = DatabaseConnection.getConnection();
            try (PreparedStatement ps =
                    con.prepareStatement(
                            "UPDATE characters SET guildid = ?, guildrank = ?, allianceRank = ? WHERE id = ?")) {
                ps.setInt(1, guildid);
                ps.setInt(2, guildRank);
                ps.setInt(3, allianceRank);
                ps.setInt(4, id);
                ps.executeUpdate();
            }

            con.close();
        } catch (SQLException se) {
            se.printStackTrace();
        }
    }

    public void saveLocation(String type) {
        MaplePortal closest = map.findClosestPortal(getPosition());
        savedLocations[SavedLocationType.fromString(type).ordinal()] =
                new SavedLocation(getMapId(), closest != null ? closest.getId() : 0);
    }

    public final boolean insertNewChar(CharacterFactoryRecipe recipe) {
        str = recipe.getStr();
        dex = recipe.getDex();
        int_ = recipe.getInt();
        luk = recipe.getLuk();
        setMaxHp(recipe.getMaxHp());
        setMaxMp(recipe.getMaxMp());
        hp = maxhp;
        mp = maxmp;
        level = recipe.getLevel();
        remainingAp = recipe.getRemainingAp();
        remainingSp[GameConstants.getSkillBook(job.getId())] = recipe.getRemainingSp();
        mapid = recipe.getMap();
        meso.set(recipe.getMeso());

        List<Pair<Skill, Integer>> startingSkills = recipe.getStartingSkillLevel();
        for (final Pair<Skill, Integer> skEntry : startingSkills) {
            Skill skill = skEntry.getLeft();
            this.changeSkillLevel(skill, skEntry.getRight().byteValue(), skill.getMaxLevel(), -1);
        }

        List<Pair<Item, MapleInventoryType>> itemsWithType = recipe.getStartingItems();
        for (final Pair<Item, MapleInventoryType> itEntry : itemsWithType) {
            this.getInventory(itEntry.getRight()).addItem(itEntry.getLeft());
        }

        Connection con = null;
        PreparedStatement ps = null;

        try {
            con = DatabaseConnection.getConnection();

            con.setTransactionIsolation(Connection.TRANSACTION_READ_UNCOMMITTED);
            con.setAutoCommit(false);
            ps =
                    con.prepareStatement(
                            "INSERT INTO characters (str, dex, luk, `int`, gm, skincolor, gender, job, hair, face, map, meso, spawnpoint, accountid, name, world, hp, mp, maxhp, maxmp, level, ap, sp) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                            Statement.RETURN_GENERATED_KEYS);
            ps.setInt(1, str);
            ps.setInt(2, dex);
            ps.setInt(3, luk);
            ps.setInt(4, int_);
            ps.setInt(5, gmLevel);
            ps.setInt(6, skinColor.getId());
            ps.setInt(7, gender);
            ps.setInt(8, job.getId());
            ps.setInt(9, hair);
            ps.setInt(10, face);
            ps.setInt(11, mapid);
            ps.setInt(12, Math.abs(meso.get()));
            ps.setInt(13, 0);
            ps.setInt(14, accountid);
            ps.setString(15, name);
            ps.setInt(16, world);
            ps.setInt(17, hp);
            ps.setInt(18, mp);
            ps.setInt(19, maxhp);
            ps.setInt(20, maxmp);
            ps.setInt(21, level);
            ps.setInt(22, remainingAp);

            StringBuilder sps = new StringBuilder();
            for (final int aRemainingSp : remainingSp) {
                sps.append(aRemainingSp);
                sps.append(',');
            }
            String sp = sps.toString();
            ps.setString(23, sp.substring(0, sp.length() - 1));

            int updateRows = ps.executeUpdate();
            if (updateRows < 1) {
                ps.close();
                FilePrinter.printError(FilePrinter.INSERT_CHAR, "Error trying to insert " + name);
                return false;
            }
            ResultSet rs = ps.getGeneratedKeys();
            if (rs.next()) {
                this.id = rs.getInt(1);
                rs.close();
                ps.close();
            } else {
                rs.close();
                ps.close();
                FilePrinter.printError(FilePrinter.INSERT_CHAR, "Inserting char failed " + name);
                return false;
            }

            // Select a keybinding method
            int[] selectedKey;
            int[] selectedType;
            int[] selectedAction;

            if (ServerConstants.USE_CUSTOM_KEYSET) {
                selectedKey = GameConstants.getCustomKey(true);
                selectedType = GameConstants.getCustomType(true);
                selectedAction = GameConstants.getCustomAction(true);
            } else {
                selectedKey = GameConstants.getCustomKey(false);
                selectedType = GameConstants.getCustomType(false);
                selectedAction = GameConstants.getCustomAction(false);
            }

            ps =
                    con.prepareStatement(
                            "INSERT INTO keymap (characterid, `key`, `type`, `action`) VALUES (?, ?, ?, ?)");
            ps.setInt(1, id);
            for (int i = 0; i < selectedKey.length; i++) {
                ps.setInt(2, selectedKey[i]);
                ps.setInt(3, selectedType[i]);
                ps.setInt(4, selectedAction[i]);
                ps.execute();
            }
            ps.close();

            itemsWithType = new ArrayList<>();
            for (MapleInventory iv : inventory) {
                for (Item item : iv.list()) {
                    itemsWithType.add(new Pair<>(item, iv.getType()));
                }
            }

            ItemFactory.INVENTORY.saveItems(itemsWithType, id, con);

            if (!skills.isEmpty()) {
                ps =
                        con.prepareStatement(
                                "INSERT INTO skills (characterid, skillid, skilllevel, masterlevel, expiration) VALUES (?, ?, ?, ?, ?)");
                ps.setInt(1, id);
                for (Entry<Skill, SkillEntry> skill : skills.entrySet()) {
                    ps.setInt(2, skill.getKey().getId());
                    ps.setInt(3, skill.getValue().skillevel);
                    ps.setInt(4, skill.getValue().masterlevel);
                    ps.setLong(5, skill.getValue().expiration);
                    ps.addBatch();
                }
                ps.executeBatch();
                ps.close();
            }

            con.commit();
            return true;
        } catch (Throwable t) {
            FilePrinter.printError(
                    FilePrinter.INSERT_CHAR,
                    t,
                    "Error creating " + name + " Level: " + level + " Job: " + job.getId());
            try {
                con.rollback();
            } catch (SQLException se) {
                FilePrinter.printError(
                        FilePrinter.INSERT_CHAR, se, "Error trying to rollback " + name);
            }
            return false;
        } finally {
            try {
                if (ps != null && !ps.isClosed()) {
                    ps.close();
                }
                con.setAutoCommit(true);
                con.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
                con.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    public void saveCharToDB() {
        if (ServerConstants.USE_AUTOSAVE) {
            Runnable r = () -> saveCharToDB(true);

            Thread t = new Thread(r); // spawns a new thread to deal with this
            t.start();
        } else {
            saveCharToDB(true);
        }
    }

    // ItemFactory saveItems and monsterbook.saveCards are the most time consuming here.
    public synchronized void saveCharToDB(boolean notAutosave) {
        if (!loggedIn) return;

        Calendar c = Calendar.getInstance();

        if (notAutosave)
            FilePrinter.print(
                    FilePrinter.SAVING_CHARACTER,
                    "Attempting to save " + name + " at " + c.getTime());
        else
            FilePrinter.print(
                    FilePrinter.AUTOSAVING_CHARACTER,
                    "Attempting to autosave " + name + " at " + c.getTime());

        Server.getInstance().updateCharacterEntry(this);

        Connection con = null;
        try {
            con = DatabaseConnection.getConnection();
            con.setTransactionIsolation(Connection.TRANSACTION_READ_UNCOMMITTED);
            con.setAutoCommit(false);
            PreparedStatement ps;
            ps =
                    con.prepareStatement(
                            "UPDATE characters SET level = ?, fame = ?, str = ?, dex = ?, luk = ?, `int` = ?, exp = ?, gachaexp = ?, hp = ?, mp = ?, maxhp = ?, maxmp = ?, sp = ?, ap = ?, gm = ?, skincolor = ?, gender = ?, job = ?, hair = ?, face = ?, map = ?, meso = ?, hpMpUsed = ?, spawnpoint = ?, party = ?, buddyCapacity = ?, messengerid = ?, messengerposition = ?, mountlevel = ?, mountexp = ?, mounttiredness= ?, equipslots = ?, useslots = ?, setupslots = ?, etcslots = ?,  monsterbookcover = ?, vanquisherStage = ?, dojoPoints = ?, lastDojoStage = ?, finishedDojoTutorial = ?, vanquisherKills = ?, matchcardwins = ?, matchcardlosses = ?, matchcardties = ?, omokwins = ?, omoklosses = ?, omokties = ?, dataString = ?, fquest = ?, jailexpire = ?, partnerId = ?, marriageItemId = ? WHERE id = ?",
                            Statement.RETURN_GENERATED_KEYS);
            if (gmLevel < 1 && level > 199) {
                ps.setInt(1, isCygnus() ? 120 : 200);
            } else {
                ps.setInt(1, level);
            }
            ps.setInt(2, fame);

            effLock.lock();
            statWlock.lock();
            try {
                ps.setInt(3, str);
                ps.setInt(4, dex);
                ps.setInt(5, luk);
                ps.setInt(6, int_);
                ps.setInt(7, Math.abs(exp.get()));
                ps.setInt(8, Math.abs(gachaexp.get()));
                ps.setInt(9, hp);
                ps.setInt(10, mp);
                ps.setInt(11, maxhp);
                ps.setInt(12, maxmp);
                StringBuilder sps = new StringBuilder();
                for (int aRemainingSp : remainingSp) {
                    sps.append(aRemainingSp);
                    sps.append(',');
                }
                String sp = sps.toString();
                ps.setString(13, sp.substring(0, sp.length() - 1));
                ps.setInt(14, remainingAp);
            } finally {
                statWlock.unlock();
                effLock.unlock();
            }

            ps.setInt(15, gmLevel);
            ps.setInt(16, skinColor.getId());
            ps.setInt(17, gender);
            ps.setInt(18, job.getId());
            ps.setInt(19, hair);
            ps.setInt(20, face);
            if (map == null || (cashshop != null && cashshop.isOpened())) {
                ps.setInt(21, mapid);
            } else {
                if (map.getForcedReturnId() != 999999999) {
                    ps.setInt(21, map.getForcedReturnId());
                } else {
                    ps.setInt(21, getHp() < 1 ? map.getReturnMapId() : map.getId());
                }
            }
            ps.setInt(22, meso.get());
            ps.setInt(23, hpMpApUsed);
            if (map == null
                    || map.getId() == 610020000
                    || map.getId() == 610020001) { // reset to first spawnpoint on those maps
                ps.setInt(24, 0);
            } else {
                MaplePortal closest = map.findClosestPlayerSpawnpoint(getPosition());
                if (closest != null) {
                    ps.setInt(24, closest.getId());
                } else {
                    ps.setInt(24, 0);
                }
            }

            prtLock.lock();
            try {
                if (party != null) {
                    ps.setInt(25, party.getId());
                } else {
                    ps.setInt(25, -1);
                }
            } finally {
                prtLock.unlock();
            }

            ps.setInt(26, buddylist.getCapacity());
            if (messenger != null) {
                ps.setInt(27, messenger.getId());
                ps.setInt(28, messengerposition);
            } else {
                ps.setInt(27, 0);
                ps.setInt(28, 4);
            }
            if (maplemount != null) {
                ps.setInt(29, maplemount.getLevel());
                ps.setInt(30, maplemount.getExp());
                ps.setInt(31, maplemount.getTiredness());
            } else {
                ps.setInt(29, 1);
                ps.setInt(30, 0);
                ps.setInt(31, 0);
            }
            for (int i = 1; i < 5; i++) {
                ps.setInt(i + 31, getSlots(i));
            }

            monsterbook.saveCards(id);

            ps.setInt(36, bookCover);
            ps.setInt(37, vanquisherStage);
            ps.setInt(38, dojoPoints);
            ps.setInt(39, dojoStage);
            ps.setInt(40, finishedDojoTutorial ? 1 : 0);
            ps.setInt(41, vanquisherKills);
            ps.setInt(42, matchcardwins);
            ps.setInt(43, matchcardlosses);
            ps.setInt(44, matchcardties);
            ps.setInt(45, omokwins);
            ps.setInt(46, omoklosses);
            ps.setInt(47, omokties);
            ps.setString(48, dataString);
            ps.setInt(49, quest_fame);
            ps.setLong(50, jailExpiration);
            ps.setInt(51, partnerId);
            ps.setInt(52, marriageItemid);
            ps.setInt(53, id);

            int updateRows = ps.executeUpdate();
            ps.close();

            assert updateRows >= 1 : "Character not in database (" + id + ')';

            petLock.lock();
            try {
                for (int i = 0; i < 3; i++) {
                    if (pets[i] != null) {
                        pets[i].saveToDb();
                    }
                }
            } finally {
                petLock.unlock();
            }

            for (Entry<Integer, Set<Integer>> es :
                    getExcluded().entrySet()) { // this set is already protected
                try (PreparedStatement ps2 =
                        con.prepareStatement("DELETE FROM petignores WHERE petid=?")) {
                    ps2.setInt(1, es.getKey());
                    ps2.executeUpdate();
                }

                try (PreparedStatement ps2 =
                        con.prepareStatement(
                                "INSERT INTO petignores (petid, itemid) VALUES (?, ?)")) {
                    ps2.setInt(1, es.getKey());
                    for (Integer x : es.getValue()) {
                        ps2.setInt(2, x);
                        ps2.addBatch();
                    }
                    ps2.executeBatch();
                }
            }

            deleteWhereCharacterId(con, "DELETE FROM keymap WHERE characterid = ?");
            ps =
                    con.prepareStatement(
                            "INSERT INTO keymap (characterid, `key`, `type`, `action`) VALUES (?, ?, ?, ?)");
            ps.setInt(1, id);

            Set<Entry<Integer, MapleKeyBinding>> keybindingItems =
                    Collections.unmodifiableSet(keymap.entrySet());
            for (Entry<Integer, MapleKeyBinding> keybinding : keybindingItems) {
                ps.setInt(2, keybinding.getKey());
                ps.setInt(3, keybinding.getValue().getType());
                ps.setInt(4, keybinding.getValue().getAction());
                ps.addBatch();
            }
            ps.executeBatch();
            ps.close();

            deleteWhereCharacterId(con, "DELETE FROM skillmacros WHERE characterid = ?");
            ps =
                    con.prepareStatement(
                            "INSERT INTO skillmacros (characterid, skill1, skill2, skill3, name, shout, position) VALUES (?, ?, ?, ?, ?, ?, ?)");
            ps.setInt(1, id);
            for (int i = 0; i < 5; i++) {
                SkillMacro macro = skillMacros[i];
                if (macro != null) {
                    ps.setInt(2, macro.getSkill1());
                    ps.setInt(3, macro.getSkill2());
                    ps.setInt(4, macro.getSkill3());
                    ps.setString(5, macro.getName());
                    ps.setInt(6, macro.getShout());
                    ps.setInt(7, i);
                    ps.addBatch();
                }
            }
            ps.executeBatch();
            ps.close();

            List<Pair<Item, MapleInventoryType>> itemsWithType = new ArrayList<>();
            for (MapleInventory iv : inventory) {
                for (Item item : iv.list()) {
                    itemsWithType.add(new Pair<>(item, iv.getType()));
                }
            }

            ItemFactory.INVENTORY.saveItems(itemsWithType, id, con);

            deleteWhereCharacterId(con, "DELETE FROM skills WHERE characterid = ?");
            ps =
                    con.prepareStatement(
                            "INSERT INTO skills (characterid, skillid, skilllevel, masterlevel, expiration) VALUES (?, ?, ?, ?, ?)");
            ps.setInt(1, id);
            for (Entry<Skill, SkillEntry> skill : skills.entrySet()) {
                ps.setInt(2, skill.getKey().getId());
                ps.setInt(3, skill.getValue().skillevel);
                ps.setInt(4, skill.getValue().masterlevel);
                ps.setLong(5, skill.getValue().expiration);
                ps.addBatch();
            }
            ps.executeBatch();
            ps.close();

            deleteWhereCharacterId(con, "DELETE FROM savedlocations WHERE characterid = ?");
            ps =
                    con.prepareStatement(
                            "INSERT INTO savedlocations (characterid, `locationtype`, `map`, `portal`) VALUES (?, ?, ?, ?)");
            ps.setInt(1, id);
            for (SavedLocationType savedLocationType : SavedLocationType.values()) {
                if (savedLocations[savedLocationType.ordinal()] != null) {
                    ps.setString(2, savedLocationType.name());
                    ps.setInt(3, savedLocations[savedLocationType.ordinal()].getMapId());
                    ps.setInt(4, savedLocations[savedLocationType.ordinal()].getPortal());
                    ps.addBatch();
                }
            }
            ps.executeBatch();
            ps.close();

            deleteWhereCharacterId(con, "DELETE FROM trocklocations WHERE characterid = ?");
            ps =
                    con.prepareStatement(
                            "INSERT INTO trocklocations(characterid, mapid, vip) VALUES (?, ?, 0)");
            for (int i = 0; i < getTrockSize(); i++) {
                if (trockmaps.get(i) != 999999999) {
                    ps.setInt(1, id);
                    ps.setInt(2, trockmaps.get(i));
                    ps.addBatch();
                }
            }
            ps.executeBatch();
            ps.close();

            ps =
                    con.prepareStatement(
                            "INSERT INTO trocklocations(characterid, mapid, vip) VALUES (?, ?, 1)");
            for (int i = 0; i < getVipTrockSize(); i++) {
                if (viptrockmaps.get(i) != 999999999) {
                    ps.setInt(1, id);
                    ps.setInt(2, viptrockmaps.get(i));
                    ps.addBatch();
                }
            }
            ps.executeBatch();
            ps.close();

            deleteWhereCharacterId(
                    con, "DELETE FROM buddies WHERE characterid = ? AND pending = 0");
            ps =
                    con.prepareStatement(
                            "INSERT INTO buddies (characterid, `buddyid`, `pending`, `group`) VALUES (?, ?, 0, ?)");
            ps.setInt(1, id);
            for (BuddylistEntry entry : buddylist.getBuddies()) {
                if (entry.isVisible()) {
                    ps.setInt(2, entry.getCharacterId());
                    ps.setString(3, entry.getGroup());
                    ps.addBatch();
                }
            }
            ps.executeBatch();
            ps.close();

            deleteWhereCharacterId(con, "DELETE FROM area_info WHERE charid = ?");
            ps =
                    con.prepareStatement(
                            "INSERT INTO area_info (id, charid, area, info) VALUES (DEFAULT, ?, ?, ?)");
            ps.setInt(1, id);
            for (Entry<Short, String> area : area_info.entrySet()) {
                ps.setInt(2, area.getKey());
                ps.setString(3, area.getValue());
                ps.addBatch();
            }
            ps.executeBatch();
            ps.close();

            deleteWhereCharacterId(con, "DELETE FROM eventstats WHERE characterid = ?");

            deleteQuestProgressWhereCharacterId(con, id);

            ps =
                    con.prepareStatement(
                            "INSERT INTO queststatus (`queststatusid`, `characterid`, `quest`, `status`, `time`, `expires`, `forfeited`) VALUES (DEFAULT, ?, ?, ?, ?, ?, ?)",
                            Statement.RETURN_GENERATED_KEYS);
            PreparedStatement psf;
            try (PreparedStatement pse =
                    con.prepareStatement(
                            "INSERT INTO questprogress VALUES (DEFAULT, ?, ?, ?, ?)")) {
                psf = con.prepareStatement("INSERT INTO medalmaps VALUES (DEFAULT, ?, ?, ?)");
                ps.setInt(1, id);

                synchronized (quests) {
                    for (MapleQuestStatus q : quests.values()) {
                        ps.setInt(2, q.getQuest().getId());
                        ps.setInt(3, q.getStatus().getId());
                        ps.setInt(4, (int) (q.getCompletionTime() / 1000));
                        ps.setLong(5, q.getExpirationTime());
                        ps.setInt(6, q.getForfeited());
                        ps.executeUpdate();
                        try (ResultSet rs = ps.getGeneratedKeys()) {
                            rs.next();
                            for (int mob : q.getProgress().keySet()) {
                                pse.setInt(1, id);
                                pse.setInt(2, rs.getInt(1));
                                pse.setInt(3, mob);
                                pse.setString(4, q.getProgress(mob));
                                pse.addBatch();
                            }
                            for (int i = 0; i < q.getMedalMaps().size(); i++) {
                                psf.setInt(1, id);
                                psf.setInt(2, rs.getInt(1));
                                psf.setInt(3, q.getMedalMaps().get(i));
                                psf.addBatch();
                            }
                            pse.executeBatch();
                            psf.executeBatch();
                        }
                    }
                }
            }
            psf.close();
            ps.close();

            con.commit();
            con.setAutoCommit(true);

            if (cashshop != null) {
                cashshop.save(con);
            }

            if (storage != null && usedStorage) {
                storage.saveToDB(con);
                usedStorage = false;
            }

        } catch (SQLException | RuntimeException t) {
            FilePrinter.printError(
                    FilePrinter.SAVE_CHAR,
                    t,
                    "Error saving " + name + " Level: " + level + " Job: " + job.getId());
            try {
                con.rollback();
            } catch (SQLException se) {
                FilePrinter.printError(
                        FilePrinter.SAVE_CHAR, se, "Error trying to rollback " + name);
            }
        } catch (Exception e) {
            FilePrinter.printError(
                    FilePrinter.SAVE_CHAR,
                    e,
                    "Error saving " + name + " Level: " + level + " Job: " + job.getId());
        } finally {
            try {
                con.setAutoCommit(true);
                con.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
                con.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void sendPolice(int greason, String reason, int duration) {
        announce(
                MaplePacketCreator.sendPolice(
                        String.format(
                                "You have been blocked by the#b %s Police for %s.#k",
                                "HeavenMS", reason)));
        this.isbanned = true;
        TimerManager.getInstance().schedule(() -> client.disconnect(false, false), duration);
    }

    public void sendPolice(String text) {
        String message = name + " received this - " + text;
        if (Server.getInstance().isGmOnline(world)) { // Alert and log if a GM is online
            Server.getInstance()
                    .broadcastGMMessage(world, MaplePacketCreator.sendYellowTip(message));
            FilePrinter.printError("autobanwarning.txt", message + System.lineSeparator());
        } else { // Auto DC and log if no GM is online
            client.disconnect(false, false);
            FilePrinter.printError("autobandced.txt", message + System.lineSeparator());
        }
        // Server.getInstance().broadcastGMMessage(0, MaplePacketCreator.serverNotice(1, getName() +
        // " received this - " + text));
        // announce(MaplePacketCreator.sendPolice(text));
        // this.isbanned = true;
        // TimerManager.getInstance().schedule(new Runnable() {
        //    @Override
        //    public void run() {
        //        client.disconnect(false, false);
        //    }
        // }, 6000);
    }

    public void sendKeymap() {
        client.announce(MaplePacketCreator.getKeymap(keymap));
    }

    public void sendMacros() {
        // Always send the macro packet to fix a client side bug when switching characters.
        client.announce(MaplePacketCreator.getMacros(skillMacros));
    }

    public void sendNote(String to, String msg, byte fame) throws SQLException {
        try (Connection con = DatabaseConnection.getConnection();
                PreparedStatement ps =
                        con.prepareStatement(
                                "INSERT INTO notes (`to`, `from`, `message`, `timestamp`, `fame`) VALUES (?, ?, ?, ?, ?)",
                                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, to);
            ps.setString(2, name);
            ps.setString(3, msg);
            ps.setLong(4, Server.getInstance().getCurrentTime());
            ps.setByte(5, fame);
            ps.executeUpdate();
        }
    }

    public void setAllowWarpToId(int id) {
        this.warpToId = id;
    }

    public static void setAriantRoomLeader(int room, String charname) {
        ariantroomleader[room] = charname;
    }

    public static void setAriantSlotRoom(int room, int slot) {
        ariantroomslot[room] = slot;
    }

    public void setBattleshipHp(int battleshipHp) {
        this.battleshipHp = battleshipHp;
    }

    public void setBuddyCapacity(int capacity) {
        buddylist.setCapacity(capacity);
        client.announce(MaplePacketCreator.updateBuddyCapacity(capacity));
    }

    public void setBuffedValue(MapleBuffStat effect, int value) {
        effLock.lock();
        chrLock.lock();
        try {
            MapleBuffStatValueHolder mbsvh = effects.get(effect);
            if (mbsvh == null) {
                return;
            }
            mbsvh.value = value;
        } finally {
            chrLock.unlock();
            effLock.unlock();
        }
    }

    public void setChair(int chair) {
        this.chair.set(chair);
    }

    public void setChalkboard(String text) {
        this.chalktext = text;
    }

    public void setDojoEnergy(int x) {
        this.dojoEnergy = Math.min(x, 10000);
    }

    public void setDojoPoints(int x) {
        this.dojoPoints = x;
    }

    public void setDojoStage(int x) {
        this.dojoStage = x;
    }

    public void setEnergyBar(int set) {
        energybar = set;
    }

    public void setEventInstance(EventInstanceManager eventInstance) {
        evtLock.lock();
        try {
            this.eventInstance = eventInstance;
        } finally {
            evtLock.unlock();
        }
    }

    public void setExp(int amount) {
        this.exp.set(amount);
    }

    public void setGachaExp(int amount) {
        this.gachaexp.set(amount);
    }

    public void setFace(int face) {
        this.face = face;
    }

    public void setFame(int fame) {
        this.fame = fame;
    }

    public void setFamilyId(int familyId) {
        this.familyId = familyId;
    }

    public void setFinishedDojoTutorial() {
        this.finishedDojoTutorial = true;
    }

    public void setGender(int gender) {
        this.gender = gender;
    }

    public void setGM(int level) {
        this.gmLevel = level;
    }

    public void setGuildId(int _id) {
        guildid = _id;
    }

    public void setGuildRank(int _rank) {
        guildRank = _rank;
    }

    public void setAllianceRank(int _rank) {
        allianceRank = _rank;
    }

    public void setHair(int hair) {
        this.hair = hair;
    }

    public void setHasMerchant(boolean set) {
        try {
            Connection con = DatabaseConnection.getConnection();

            try (PreparedStatement ps =
                    con.prepareStatement("UPDATE characters SET HasMerchant = ? WHERE id = ?")) {
                ps.setInt(1, set ? 1 : 0);
                ps.setInt(2, id);
                ps.executeUpdate();
            }

            con.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        hasMerchant = set;
    }

    public void addMerchantMesos(int add) {
        int newAmount;

        try {
            newAmount = (int) Math.min((long) merchantmeso + add, Integer.MAX_VALUE);

            Connection con = DatabaseConnection.getConnection();
            try (PreparedStatement ps =
                    con.prepareStatement(
                            "UPDATE characters SET MerchantMesos = ? WHERE id = ?",
                            Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, newAmount);
                ps.setInt(2, id);
                ps.executeUpdate();
            }

            con.close();
        } catch (SQLException e) {
            e.printStackTrace();
            return;
        }
        merchantmeso = newAmount;
    }

    public void setMerchantMeso(int set) {
        try {
            Connection con = DatabaseConnection.getConnection();
            try (PreparedStatement ps =
                    con.prepareStatement(
                            "UPDATE characters SET MerchantMesos = ? WHERE id = ?",
                            Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, set);
                ps.setInt(2, id);
                ps.executeUpdate();
            }
            con.close();
        } catch (SQLException e) {
            e.printStackTrace();
            return;
        }
        merchantmeso = set;
    }

    public synchronized void withdrawMerchantMesos() {
        int merchantMeso = merchantmeso;
        if (merchantMeso > 0) {
            int possible = Integer.MAX_VALUE - merchantMeso;

            if (possible > 0) {
                if (possible < merchantMeso) {
                    this.gainMeso(possible, false);
                    this.setMerchantMeso(merchantMeso - possible);
                } else {
                    this.gainMeso(merchantMeso, false);
                    this.setMerchantMeso(0);
                }
            }
        }
    }

    public void setHiredMerchant(MapleHiredMerchant merchant) {
        this.hiredMerchant = merchant;
    }

    private void hpChangeAction(int oldHp) {
        boolean playerDied = false;
        if (hp <= 0) {
            if (oldHp > hp) {
                if (!isBuybackInvincible()) {
                    playerDied = true;
                } else {
                    hp = 1;
                }
            }
        }

        updatePartyMemberHP();

        if (playerDied) {
            playerDead();
        } else {
            checkBerserk(hidden);
        }
    }

    private Pair<MapleStat, Integer> calcHpRatioUpdate(int newHp, int oldHp) {
        int delta = newHp - oldHp;
        this.hp = calcHpMpRatioUpdate(hp, oldHp, delta);

        hpChangeAction(Short.MIN_VALUE);
        return new Pair<>(MapleStat.HP, hp);
    }

    private Pair<MapleStat, Integer> calcMpRatioUpdate(int newMp, int oldMp) {
        int delta = newMp - oldMp;
        this.mp = calcHpMpRatioUpdate(mp, oldMp, delta);
        return new Pair<>(MapleStat.MP, mp);
    }

    private static int calcHpMpRatioUpdate(int curpoint, int maxpoint, int diffpoint) {
        int curMax = maxpoint;
        int nextMax = Math.min(30000, maxpoint + diffpoint);

        float temp = curpoint * nextMax;
        int ret = Math.round(temp / curMax);

        // System.out.println("cur: " + curpoint + " next: " + ret + " max: " + curMax + " nextmax:"
        // + nextMax + " diff: " + diffpoint);
        return !(ret <= 0 && curpoint > 0) ? ret : 1;
    }

    public boolean applyHpMpChange(int hpCon, int hpchange, int mpchange) {
        boolean zombify = hasDisease(MapleDisease.ZOMBIFY);

        effLock.lock();
        statWlock.lock();
        try {
            int nextHp = hp + hpchange, nextMp = mp + mpchange;
            boolean cannotApplyHp = hpchange != 0 && nextHp <= 0 && (!zombify || hpCon > 0);
            boolean cannotApplyMp = mpchange != 0 && nextMp < 0;

            if (cannotApplyHp || cannotApplyMp) {
                if (!isGM()) {
                    return false;
                }

                if (cannotApplyHp) nextHp = 1;
            }

            updateHpMp(nextHp, nextMp);
            return true;
        } finally {
            statWlock.unlock();
            effLock.unlock();
        }
    }

    public void setInventory(MapleInventoryType type, MapleInventory inv) {
        inventory[type.ordinal()] = inv;
    }

    public void setItemEffect(int itemEffect) {
        this.itemEffect = itemEffect;
    }

    public void setJob(MapleJob job) {
        this.job = job;
    }

    public void setLastHealed(long time) {
        this.lastHealed = time;
    }

    public void setLastUsedCashItem(long time) {
        this.lastUsedCashItem = time;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public void setMap(int PmapId) {
        this.mapid = PmapId;
    }

    public void setMap(MapleMap newmap) {
        this.map = newmap;
    }

    public void setMarkedMonster(int markedMonster) {
        this.markedMonster = markedMonster;
    }

    public void setMessenger(MapleMessenger messenger) {
        this.messenger = messenger;
    }

    public void setMessengerPosition(int position) {
        this.messengerposition = position;
    }

    public void setMiniGame(MapleMiniGame miniGame) {
        this.miniGame = miniGame;
    }

    public void setMiniGamePoints(MapleCharacter visitor, int winnerslot, boolean omok) {
        if (omok) {
            if (winnerslot == 1) {
                this.omokwins++;
                visitor.omoklosses++;
            } else if (winnerslot == 2) {
                visitor.omokwins++;
                this.omoklosses++;
            } else {
                this.omokties++;
                visitor.omokties++;
            }
        } else {
            if (winnerslot == 1) {
                this.matchcardwins++;
                visitor.matchcardlosses++;
            } else if (winnerslot == 2) {
                visitor.matchcardwins++;
                this.matchcardlosses++;
            } else {
                this.matchcardties++;
                visitor.matchcardties++;
            }
        }
    }

    public void setMonsterBookCover(int bookCover) {
        this.bookCover = bookCover;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void changeName(String name) {
        this.name = name;
        try {
            Connection con = DatabaseConnection.getConnection();
            PreparedStatement ps =
                    con.prepareStatement("UPDATE `characters` SET `name` = ? WHERE `id` = ?");
            ps.setString(1, name);
            ps.setInt(2, id);
            ps.executeUpdate();
            ps.close();
            con.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public int getDoorSlot() {
        if (doorSlot != -1) return doorSlot;
        return fetchDoorSlot();
    }

    public int fetchDoorSlot() {
        prtLock.lock();
        try {
            doorSlot = (party == null) ? 0 : party.getPartyDoor(id);
            return doorSlot;
        } finally {
            prtLock.unlock();
        }
    }

    public void setParty(MapleParty p) {
        prtLock.lock();
        try {
            if (p == null) {
                this.mpc = null;
                doorSlot = -1;

                party = null;
                // cancelMagicDoor();  // cancel magic doors if kicked out / quitted from party.
            } else {
                party = p;
            }
        } finally {
            prtLock.unlock();
        }
    }

    public void setPlayerShop(MaplePlayerShop playerShop) {
        this.playerShop = playerShop;
    }

    public void setSearch(String find) {
        search = find;
    }

    public void setSkinColor(MapleSkinColor skinColor) {
        this.skinColor = skinColor;
    }

    public byte getSlots(int type) {
        return type == MapleInventoryType.CASH.getType() ? 96 : inventory[type].getSlotLimit();
    }

    public boolean gainSlots(int type, int slots) {
        return gainSlots(type, slots, true);
    }

    public boolean gainSlots(int type, int slots, boolean update) {
        slots += inventory[type].getSlotLimit();
        if (slots <= 96) {
            inventory[type].setSlotLimit(slots);

            this.saveCharToDB();
            if (update) {
                client.announce(MaplePacketCreator.updateInventorySlotLimit(type, slots));
            }

            return true;
        }

        return false;
    }

    public int sellAllItemsFromName(byte invTypeId, String name) {
        // player decides from which inventory items should be sold.

        MapleInventoryType type = MapleInventoryType.getByType(invTypeId);

        Item it = getInventory(type).findByName(name);
        if (it == null) {
            return (-1);
        }

        return (sellAllItemsFromPosition(ii, type, it.getPosition()));
    }

    public int sellAllItemsFromPosition(
            MapleItemInformationProvider ii, MapleInventoryType type, short pos) {
        int mesoGain = 0;

        for (short i = pos; i <= getInventory(type).getSlotLimit(); i++) {
            if (getInventory(type).getItem(i) == null) continue;
            mesoGain +=
                    standaloneSell(
                            client, ii, type, i, getInventory(type).getItem(i).getQuantity());
        }

        return (mesoGain);
    }

    private int standaloneSell(
            MapleClient c,
            MapleItemInformationProvider ii,
            MapleInventoryType type,
            short slot,
            short quantity) {
        if (quantity == 0xFFFF || quantity == 0) {
            quantity = 1;
        }
        Item item = getInventory(type).getItem(slot);
        if (item == null) { // Basic check
            return (0);
        }

        int itemid = item.getItemId();
        if (ItemConstants.isRechargeable(itemid)) {
            quantity = item.getQuantity();
        } else if (ItemConstants.isWeddingToken(itemid) || ItemConstants.isWeddingRing(itemid)) {
            return (0);
        }

        if (quantity < 0) {
            return (0);
        }
        short iQuant = item.getQuantity();
        if (iQuant == 0xFFFF) {
            iQuant = 1;
        }

        if (quantity <= iQuant && iQuant > 0) {
            MapleInventoryManipulator.removeFromSlot(c, type, (byte) slot, quantity, false);
            int recvMesos = ii.getPrice(itemid, quantity);
            if (recvMesos > 0) {
                gainMeso(recvMesos, false);
                return (recvMesos);
            }
        }

        return (0);
    }

    public void setShop(MapleShop shop) {
        this.shop = shop;
    }

    public void setSlot(int slotid) {
        slots = slotid;
    }

    public void setTrade(MapleTrade trade) {
        this.trade = trade;
    }

    public void setVanquisherKills(int x) {
        this.vanquisherKills = x;
    }

    public void setVanquisherStage(int x) {
        this.vanquisherStage = x;
    }

    public void setWorld(int world) {
        this.world = world;
    }

    public void shiftPetsRight() {
        petLock.lock();
        try {
            if (pets[2] == null) {
                pets[2] = pets[1];
                pets[1] = pets[0];
                pets[0] = null;
            }
        } finally {
            petLock.unlock();
        }
    }

    private long getDojoTimeLeft() {
        return client.getChannelServer().getDojoFinishTime(map.getId())
                - Server.getInstance().getCurrentTime();
    }

    public void showDojoClock() {
        if (map.isDojoFightMap()) {
            client.announce(MaplePacketCreator.getClock((int) (getDojoTimeLeft() / 1000)));
        }
    }

    public void timeoutFromDojo() {
        if (map.isDojoMap()) {
            client.getPlayer()
                    .changeMap(client.getChannelServer().getMapFactory().getMap(925020002));
        }
    }

    public void showUnderleveledInfo(MapleMonster mob) {
        chrLock.lock();
        try {
            long curTime = Server.getInstance().getCurrentTime();
            if (nextUnderlevelTime < curTime) {
                nextUnderlevelTime =
                        curTime + (60 * 1000); // show underlevel info again after 1 minute

                showHint(
                        "You have gained #rno experience#k from defeating #e#b"
                                + mob.getName()
                                + "#k#n (lv. #b"
                                + mob.getLevel()
                                + "#k)! Take note you must have around the same level as the mob to start earning EXP from it.");
            }
        } finally {
            chrLock.unlock();
        }
    }

    public void showHint(String msg) {
        showHint(msg, 500);
    }

    public void showHint(String msg, int length) {
        client.announceHint(msg, length);
    }

    public void showNote() {
        try {
            Connection con = DatabaseConnection.getConnection();
            try (PreparedStatement ps =
                    con.prepareStatement(
                            "SELECT * FROM notes WHERE `to` = ? AND `deleted` = 0",
                            ResultSet.TYPE_SCROLL_SENSITIVE,
                            ResultSet.CONCUR_UPDATABLE)) {
                ps.setString(1, name);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.last();
                    int count = rs.getRow();
                    rs.first();
                    client.announce(MaplePacketCreator.showNotes(rs, count));
                }
            }

            con.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void silentGiveBuffs(List<Pair<Long, PlayerBuffValueHolder>> buffs) {
        for (Pair<Long, PlayerBuffValueHolder> mbsv : buffs) {
            PlayerBuffValueHolder mbsvh = mbsv.getRight();
            mbsvh.effect.silentApplyBuff(this, mbsv.getLeft());
        }
    }

    public void silentPartyUpdate() {
        prtLock.lock();
        try {
            silentPartyUpdateInternal();
        } finally {
            prtLock.unlock();
        }
    }

    private void silentPartyUpdateInternal() {
        if (party != null) {
            getWorldServer().updateParty(party.getId(), PartyOperation.SILENT_UPDATE, getMPC());
        }
    }

    public static class SkillEntry {

        public int masterlevel;
        public byte skillevel;
        public long expiration;

        public SkillEntry(byte skillevel, int masterlevel, long expiration) {
            this.skillevel = skillevel;
            this.masterlevel = masterlevel;
            this.expiration = expiration;
        }

        @Override
        public String toString() {
            return skillevel + ":" + masterlevel;
        }
    }

    public boolean skillIsCooling(int skillId) {
        effLock.lock();
        chrLock.lock();
        try {
            return coolDowns.containsKey(skillId);
        } finally {
            chrLock.unlock();
            effLock.unlock();
        }
    }

    public void runFullnessSchedule(int petSlot) {
        MaplePet pet = getPet(petSlot);
        if (pet == null) return;

        int newFullness = pet.getFullness() - PetDataFactory.getHunger(pet.getItemId());
        if (newFullness <= 5) {
            pet.setFullness(15);
            pet.saveToDb();
            unequipPet(pet, true);
            dropMessage(6, "Your pet grew hungry! Treat it some pet food to keep it healthy!");
        } else {
            pet.setFullness(newFullness);
            pet.saveToDb();
            Item petz = getInventory(MapleInventoryType.CASH).getItem(pet.getPosition());
            if (petz != null) {
                forceUpdateItem(petz);
            }
        }
    }

    public void runTirednessSchedule() {
        if (maplemount != null) {
            int tiredness = maplemount.incrementAndGetTiredness();

            map.broadcastMessage(MaplePacketCreator.updateMount(id, maplemount, false));
            if (tiredness > 99) {
                maplemount.setTiredness(99);
                this.dispelSkill(this.getJobType() * 10000000 + 1004);
                this.dropMessage(
                        6,
                        "Your mount grew tired! Treat it some revitalizer before riding it again!");
            }
        }
    }

    public void startMapEffect(String msg, int itemId) {
        startMapEffect(msg, itemId, 30000);
    }

    public void startMapEffect(String msg, int itemId, int duration) {
        final MapleMapEffect mapEffect = new MapleMapEffect(msg, itemId);
        client.announce(mapEffect.makeStartData());
        TimerManager.getInstance()
                .schedule(() -> getClient().announce(MapleMapEffect.makeDestroyData()), duration);
    }

    public void stopControllingMonster(MapleMonster monster) {
        controlled.remove(monster);
    }

    public void unequipAllPets() {
        for (int i = 0; i < 3; i++) {
            MaplePet pet = getPet(i);
            if (pet != null) {
                unequipPet(pet, true);
            }
        }
    }

    public void unequipPet(MaplePet pet, boolean shift_left) {
        unequipPet(pet, shift_left, false);
    }

    public void unequipPet(MaplePet pet, boolean shift_left, boolean hunger) {
        byte petIdx = this.getPetIndex(pet);
        MaplePet chrPet = this.getPet(petIdx);

        if (chrPet != null) {
            chrPet.setSummoned(false);
            chrPet.saveToDb();
        }

        client.getWorldServer().unregisterPetHunger(this, petIdx);
        map.broadcastMessage(this, MaplePacketCreator.showPet(this, pet, true, hunger), true);

        removePet(pet, shift_left);
        commitExcludedItems();

        client.announce(MaplePacketCreator.petStatUpdate(this));
        client.announce(MaplePacketCreator.enableActions());
    }

    public void updateMacros(int position, SkillMacro updateMacro) {
        skillMacros[position] = updateMacro;
    }

    public void updatePartyMemberHP() {
        prtLock.lock();
        try {
            updatePartyMemberHPInternal();
        } finally {
            prtLock.unlock();
        }
    }

    private void updatePartyMemberHPInternal() {
        if (party != null) {
            int curmaxhp = getCurrentMaxHp();
            int curhp = getHp();
            for (MapleCharacter partychar : this.getPartyMembersOnSameMap()) {
                partychar.announce(MaplePacketCreator.updatePartyMemberHP(id, curhp, curmaxhp));
            }
        }
    }

    public String getQuestInfo(int quest) {
        MapleQuestStatus qs = getQuest(MapleQuest.getInstance(quest));
        return qs.getInfo();
    }

    public void updateQuestInfo(int quest, String info) {
        MapleQuest q = MapleQuest.getInstance(quest);
        MapleQuestStatus qs = getQuest(q);
        qs.setInfo(info);

        synchronized (quests) {
            quests.put(q.getId(), qs);
        }

        announce(MaplePacketCreator.updateQuest(qs, false));
        if (qs.getQuest().getInfoNumber() > 0) {
            announce(MaplePacketCreator.updateQuest(qs, true));
        }
        announce(MaplePacketCreator.updateQuestInfo(qs.getQuest().getId(), qs.getNpc()));
    }

    public void awardQuestPoint(int awardedPoints) {
        if (ServerConstants.QUEST_POINT_REQUIREMENT < 1 || awardedPoints < 1) return;

        int delta;
        synchronized (quests) {
            quest_fame += awardedPoints;

            delta = quest_fame / ServerConstants.QUEST_POINT_REQUIREMENT;
            quest_fame %= ServerConstants.QUEST_POINT_REQUIREMENT;
        }

        if (delta > 0) {
            gainFame(delta);
        }
    }

    public void updateQuest(MapleQuestStatus quest) {
        synchronized (quests) {
            quests.put(quest.getQuestID(), quest);
        }
        if (quest.getStatus().equals(MapleQuestStatus.Status.STARTED)) {
            announce(MaplePacketCreator.updateQuest(quest, false));
            if (quest.getQuest().getInfoNumber() > 0) {
                announce(MaplePacketCreator.updateQuest(quest, true));
            }
            announce(MaplePacketCreator.updateQuestInfo(quest.getQuest().getId(), quest.getNpc()));
        } else if (quest.getStatus().equals(MapleQuestStatus.Status.COMPLETED)) {
            MapleQuest mquest = quest.getQuest();
            short questid = mquest.getId();
            if (!mquest.isSameDayRepeatable() && !MapleQuest.isExploitableQuest(questid)) {
                awardQuestPoint(ServerConstants.QUEST_POINT_PER_QUEST_COMPLETE);
            }

            announce(MaplePacketCreator.completeQuest(questid, quest.getCompletionTime()));
        } else if (quest.getStatus().equals(MapleQuestStatus.Status.NOT_STARTED)) {
            announce(MaplePacketCreator.updateQuest(quest, false));
            if (quest.getQuest().getInfoNumber() > 0) {
                announce(MaplePacketCreator.updateQuest(quest, true));
            }
        }
    }

    private void expireQuest(MapleQuest quest) {
        if (getQuestStatus(quest.getId()) == MapleQuestStatus.Status.COMPLETED.getId()) return;
        if (System.currentTimeMillis() < getMapleQuestStatus(quest.getId()).getExpirationTime())
            return;

        announce(MaplePacketCreator.questExpire(quest.getId()));
        MapleQuestStatus newStatus =
                new MapleQuestStatus(quest, MapleQuestStatus.Status.NOT_STARTED);
        newStatus.setForfeited(getQuest(quest).getForfeited() + 1);
        updateQuest(newStatus);
    }

    public void cancelQuestExpirationTask() {
        petLock.lock();
        try {
            if (questExpireTask != null) {
                questExpireTask.cancel(false);
                questExpireTask = null;
            }
        } finally {
            petLock.unlock();
        }
    }

    public void forfeitExpirableQuests() {
        petLock.lock();
        try {
            for (MapleQuest quest : questExpirations.keySet()) {
                quest.forfeit(this);
            }

            questExpirations.clear();
        } finally {
            petLock.unlock();
        }
    }

    public void questExpirationTask() {
        petLock.lock();
        try {
            if (!questExpirations.isEmpty()) {
                if (questExpireTask == null) {
                    questExpireTask =
                            TimerManager.getInstance()
                                    .register(this::runQuestExpireTask, 10 * 1000);
                }
            }
        } finally {
            petLock.unlock();
        }
    }

    private void runQuestExpireTask() {
        petLock.lock();
        try {
            long timeNow = Server.getInstance().getCurrentTime();
            List<MapleQuest> expireList = new ArrayList<>();

            for (Entry<MapleQuest, Long> qe : questExpirations.entrySet()) {
                if (qe.getValue() <= timeNow) expireList.add(qe.getKey());
            }

            if (!expireList.isEmpty()) {
                for (MapleQuest quest : expireList) {
                    expireQuest(quest);
                    questExpirations.remove(quest);
                }

                if (questExpirations.isEmpty()) {
                    questExpireTask.cancel(false);
                    questExpireTask = null;
                }
            }
        } finally {
            petLock.unlock();
        }
    }

    private void registerQuestExpire(MapleQuest quest, long time) {
        petLock.lock();
        try {
            if (questExpireTask == null) {
                questExpireTask =
                        TimerManager.getInstance().register(this::runQuestExpireTask, 10 * 1000);
            }

            questExpirations.put(quest, Server.getInstance().getCurrentTime() + time);
        } finally {
            petLock.unlock();
        }
    }

    public void questTimeLimit(final MapleQuest quest, int seconds) {
        registerQuestExpire(quest, seconds * 1000);
        announce(MaplePacketCreator.addQuestTimeLimit(quest.getId(), seconds * 1000));
    }

    public void questTimeLimit2(final MapleQuest quest, long expires) {
        long timeLeft = expires - System.currentTimeMillis();

        if (timeLeft <= 0) {
            expireQuest(quest);
        } else {
            registerQuestExpire(quest, timeLeft);
        }
    }

    public void updateSingleStat(MapleStat stat, int newval) {
        updateSingleStat(stat, newval, false);
    }

    private void updateSingleStat(MapleStat stat, int newval, boolean itemReaction) {
        announce(
                MaplePacketCreator.updatePlayerStats(
                        Collections.singletonList(new Pair<>(stat, newval)), itemReaction, this));
    }

    public void announce(final byte[] packet) {
        client.announce(packet);
    }

    @Override
    public int getObjectId() {
        return id;
    }

    @Override
    public MapleMapObjectType getType() {
        return MapleMapObjectType.PLAYER;
    }

    @Override
    public void sendDestroyData(MapleClient client) {
        client.announce(MaplePacketCreator.removePlayerFromMap(this.getObjectId()));
    }

    @Override
    public void sendSpawnData(MapleClient client) {
        if (!hidden || client.getPlayer().gmLevel() > 1) {
            client.announce(MaplePacketCreator.spawnPlayerMapObject(this));

            if (hasBuffFromSourceid(getJobMapChair(job))) {
                client.announce(MaplePacketCreator.giveForeignChairSkillEffect(id));
            }
        }

        if (hidden) {
            List<Pair<MapleBuffStat, Integer>> dsstat =
                    Collections.singletonList(new Pair<>(MapleBuffStat.DARKSIGHT, 0));
            map.broadcastGMMessage(this, MaplePacketCreator.giveForeignBuff(id, dsstat), false);
        }
    }

    @Override
    public void setObjectId(int id) {}

    @Override
    public String toString() {
        return name;
    }

    public int getLinkedLevel() {
        return linkedLevel;
    }

    public String getLinkedName() {
        return linkedName;
    }

    public CashShop getCashShop() {
        return cashshop;
    }

    public Set<NewYearCardRecord> getNewYearRecords() {
        return newyears;
    }

    public Set<NewYearCardRecord> getReceivedNewYearRecords() {
        Set<NewYearCardRecord> received = new LinkedHashSet<>();

        for (NewYearCardRecord nyc : newyears) {
            if (nyc.isReceiverCardReceived()) {
                received.add(nyc);
            }
        }

        return received;
    }

    public NewYearCardRecord getNewYearRecord(int cardid) {
        for (NewYearCardRecord nyc : newyears) {
            if (nyc.getId() == cardid) {
                return nyc;
            }
        }

        return null;
    }

    public void addNewYearRecord(NewYearCardRecord newyear) {
        newyears.add(newyear);
    }

    public void removeNewYearRecord(NewYearCardRecord newyear) {
        newyears.remove(newyear);
    }

    public void portalDelay(long delay) {
        this.portaldelay = System.currentTimeMillis() + delay;
    }

    public long portalDelay() {
        return portaldelay;
    }

    public void blockPortal(String scriptName) {
        if (!blockedPortals.contains(scriptName) && scriptName != null) {
            blockedPortals.add(scriptName);
            client.announce(MaplePacketCreator.enableActions());
        }
    }

    public void unblockPortal(String scriptName) {
        if (blockedPortals.contains(scriptName) && scriptName != null) {
            blockedPortals.remove(scriptName);
        }
    }

    public List<String> getBlockedPortals() {
        return blockedPortals;
    }

    public boolean containsAreaInfo(int area, String info) {
        Short area_ = (short) area;
        return area_info.containsKey(area_) && area_info.get(area_).contains(info);
    }

    public void updateAreaInfo(int area, String info) {
        area_info.put((short) area, info);
        announce(MaplePacketCreator.updateAreaInfo(area, info));
    }

    public String getAreaInfo(int area) {
        return area_info.get((short) area);
    }

    public Map<Short, String> getAreaInfos() {
        return area_info;
    }

    public void autoban(String reason) {
        this.ban(reason);
        announce(
                MaplePacketCreator.sendPolice(
                        String.format(
                                "You have been blocked by the#b %s Police for HACK reason.#k",
                                "HeavenMS")));
        TimerManager.getInstance().schedule(() -> client.disconnect(false, false), 5000);

        Server.getInstance()
                .broadcastGMMessage(
                        world,
                        MaplePacketCreator.serverNotice(
                                6,
                                MapleCharacter.makeMapleReadable(this.name)
                                        + " was autobanned for "
                                        + reason));
    }

    public void block(int reason, int days, String desc) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DATE, days);
        Timestamp TS = new Timestamp(cal.getTimeInMillis());
        try {
            try (Connection con = DatabaseConnection.getConnection();
                    PreparedStatement ps =
                            con.prepareStatement(
                                    "UPDATE accounts SET banreason = ?, tempban = ?, greason = ? WHERE id = ?")) {
                ps.setString(1, desc);
                ps.setTimestamp(2, TS);
                ps.setInt(3, reason);
                ps.setInt(4, accountid);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public boolean isBanned() {
        return isbanned;
    }

    public List<Integer> getTrockMaps() {
        return trockmaps;
    }

    public List<Integer> getVipTrockMaps() {
        return viptrockmaps;
    }

    public int getTrockSize() {
        int ret = trockmaps.indexOf(999999999);
        if (ret == -1) {
            ret = 5;
        }

        return ret;
    }

    public void deleteFromTrocks(int map) {
        trockmaps.remove(Integer.valueOf(map));
        while (trockmaps.size() < 10) {
            trockmaps.add(999999999);
        }
    }

    public void addTrockMap() {
        int index = trockmaps.indexOf(999999999);
        if (index != -1) {
            trockmaps.set(index, getMapId());
        }
    }

    public boolean isTrockMap(int id) {
        int index = trockmaps.indexOf(id);
        return index != -1;
    }

    public int getVipTrockSize() {
        int ret = viptrockmaps.indexOf(999999999);

        if (ret == -1) {
            ret = 10;
        }

        return ret;
    }

    public void deleteFromVipTrocks(int map) {
        viptrockmaps.remove(Integer.valueOf(map));
        while (viptrockmaps.size() < 10) {
            viptrockmaps.add(999999999);
        }
    }

    public void addVipTrockMap() {
        int index = viptrockmaps.indexOf(999999999);
        if (index != -1) {
            viptrockmaps.set(index, getMapId());
        }
    }

    public boolean isVipTrockMap(int id) {
        int index = viptrockmaps.indexOf(id);
        return index != -1;
    }

    // EVENTS
    private byte team = 0;
    private MapleFitness fitness;
    private MapleOla ola;
    private long snowballattack;

    public byte getTeam() {
        return team;
    }

    public void setTeam(int team) {
        this.team = (byte) team;
    }

    public MapleOla getOla() {
        return ola;
    }

    public void setOla(MapleOla ola) {
        this.ola = ola;
    }

    public MapleFitness getFitness() {
        return fitness;
    }

    public void setFitness(MapleFitness fit) {
        this.fitness = fit;
    }

    public long getLastSnowballAttack() {
        return snowballattack;
    }

    public void setLastSnowballAttack(long time) {
        this.snowballattack = time;
    }

    // Monster Carnival
    private int cp = 0;
    private int obtainedcp = 0;
    private MonsterCarnivalParty carnivalparty;
    private MonsterCarnival carnival;

    public MonsterCarnivalParty getCarnivalParty() {
        return carnivalparty;
    }

    public void setCarnivalParty(MonsterCarnivalParty party) {
        this.carnivalparty = party;
    }

    public MonsterCarnival getCarnival() {
        return carnival;
    }

    public void setCarnival(MonsterCarnival car) {
        this.carnival = car;
    }

    public int getCP() {
        return cp;
    }

    public int getObtainedCP() {
        return obtainedcp;
    }

    public void addCP(int cp) {
        this.cp += cp;
        this.obtainedcp += cp;
    }

    public void useCP(int cp) {
        this.cp -= cp;
    }

    public void setObtainedCP(int cp) {
        this.obtainedcp = cp;
    }

    public int getAndRemoveCP() {
        int rCP = 10;
        if (cp < 9) {
            rCP = cp;
            cp = 0;
        } else {
            cp -= 10;
        }

        return rCP;
    }

    public AutobanManager getAutobanManager() {
        return autoban;
    }

    public void equippedItem(Equip equip) {
        int itemid = equip.getItemId();

        if (itemid == 1122017) {
            this.equipPendantOfSpirit();
        } else if (itemid == 1812000) { // meso magnet
            equippedMesoMagnet = true;
        } else if (itemid == 1812001) { // item pouch
            equippedItemPouch = true;
        } else if (itemid == 1812007) { // item ignore pendant
            equippedPetItemIgnore = true;
        }
    }

    public void unequippedItem(Equip equip) {
        int itemid = equip.getItemId();

        if (itemid == 1122017) {
            this.unequipPendantOfSpirit();
        } else if (itemid == 1812000) { // meso magnet
            equippedMesoMagnet = false;
        } else if (itemid == 1812001) { // item pouch
            equippedItemPouch = false;
        } else if (itemid == 1812007) { // item ignore pendant
            equippedPetItemIgnore = false;
        }
    }

    public boolean isEquippedMesoMagnet() {
        return equippedMesoMagnet;
    }

    public boolean isEquippedItemPouch() {
        return equippedItemPouch;
    }

    public boolean isEquippedPetItemIgnore() {
        return equippedPetItemIgnore;
    }

    private void equipPendantOfSpirit() {
        if (pendantOfSpirit == null) {
            pendantOfSpirit =
                    TimerManager.getInstance()
                            .register(
                                    () -> {
                                        if (pendantExp < 3) {
                                            pendantExp++;
                                            message(
                                                    "Pendant of the Spirit has been equipped for "
                                                            + pendantExp
                                                            + " hour(s), you will now receive "
                                                            + pendantExp
                                                            + "0% bonus exp.");
                                        } else {
                                            pendantOfSpirit.cancel(false);
                                        }
                                    },
                                    3600000); // 1 hour
        }
    }

    private void unequipPendantOfSpirit() {
        if (pendantOfSpirit != null) {
            pendantOfSpirit.cancel(false);
            pendantOfSpirit = null;
        }
        pendantExp = 0;
    }

    public void increaseEquipExp(int expGain) {
        if (expGain < 0) expGain = Integer.MAX_VALUE;

        for (Item item : getInventory(MapleInventoryType.EQUIPPED).list()) {
            Equip nEquip = (Equip) item;
            String itemName = ii.getName(nEquip.getItemId());
            if (itemName == null) {
                continue;
            }

            nEquip.gainItemExp(client, expGain);
        }
    }

    public void showAllEquipFeatures() {
        StringBuilder showMsg = new StringBuilder();

        for (Item item : getInventory(MapleInventoryType.EQUIPPED).list()) {
            Equip nEquip = (Equip) item;
            String itemName = ii.getName(nEquip.getItemId());
            if (itemName == null) {
                continue;
            }

            showMsg.append(nEquip.showEquipFeatures(client));
        }

        if (showMsg.length() > 0) {
            this.showHint("#ePLAYER EQUIPMENTS:#n\n\n" + showMsg, 400);
        }
    }

    public void broadcastMarriageMessage() {
        MapleGuild guild = this.getGuild();
        if (guild != null) {
            guild.broadcast(MaplePacketCreator.marriageMessage(0, name));
        }

        MapleFamily family = this.family;
        if (family != null) {
            family.broadcast(MaplePacketCreator.marriageMessage(1, name));
        }
    }

    public Map<String, MapleEvents> getEvents() {
        return events;
    }

    public PartyQuest getPartyQuest() {
        return partyQuest;
    }

    public void setPartyQuest(PartyQuest pq) {
        this.partyQuest = pq;
    }

    public final void empty(final boolean remove) {
        if (dragonBloodSchedule != null) {
            dragonBloodSchedule.cancel(true);
        }
        dragonBloodSchedule = null;

        if (hpDecreaseTask != null) {
            hpDecreaseTask.cancel(true);
        }
        hpDecreaseTask = null;

        if (beholderHealingSchedule != null) {
            beholderHealingSchedule.cancel(true);
        }
        beholderHealingSchedule = null;

        if (beholderBuffSchedule != null) {
            beholderBuffSchedule.cancel(true);
        }
        beholderBuffSchedule = null;

        if (berserkSchedule != null) {
            berserkSchedule.cancel(true);
        }
        berserkSchedule = null;

        unregisterChairBuff();
        cancelBuffExpireTask();
        cancelDiseaseExpireTask();
        cancelSkillCooldownTask();
        cancelExpirationTask();

        if (questExpireTask != null) {
            questExpireTask.cancel(true);
        }
        questExpireTask = null;

        if (recoveryTask != null) {
            recoveryTask.cancel(true);
        }
        recoveryTask = null;

        if (extraRecoveryTask != null) {
            extraRecoveryTask.cancel(true);
        }
        extraRecoveryTask = null;

        // already done on unregisterChairBuff
        /* if (chairRecoveryTask != null) { chairRecoveryTask.cancel(true); }
        chairRecoveryTask = null; */

        if (pendantOfSpirit != null) {
            pendantOfSpirit.cancel(true);
        }
        pendantOfSpirit = null;

        petLock.lock();
        try {
            if (questExpireTask != null) {
                questExpireTask.cancel(false);
                questExpireTask = null;

                questExpirations.clear();
                questExpirations = null;
            }
        } finally {
            petLock.unlock();
        }

        if (maplemount != null) {
            maplemount.empty();
            maplemount = null;
        }
        if (remove) {
            partyQuest = null;
            events = null;
            mpc = null;
            mgc = null;
            events = null;
            party = null;
            family = null;

            getWorldServer()
                    .registerTimedMapObject(
                            () -> {
                                client = null; // clients still triggers handlers a few times
                                // after disconnecting
                                map = null;
                                setListener(null);
                            },
                            5 * 60 * 1000);
        }
    }

    public void logOff() {
        this.loggedIn = false;
    }

    public boolean isLoggedin() {
        return loggedIn;
    }

    public void setMapId(int mapid) {
        this.mapid = mapid;
    }

    public boolean getWhiteChat() {
        return isGM() && whiteChat;
    }

    public void toggleWhiteChat() {
        whiteChat = !whiteChat;
    }

    public boolean canDropMeso() {
        if (System.currentTimeMillis() - lastMesoDrop >= 200
                || lastMesoDrop == -1) { // About 200 meso drops a minute
            lastMesoDrop = System.currentTimeMillis();
            return true;
        }
        return false;
    }

    // These need to be renamed, but I am too lazy right now to go through the scripts and rename
    // them...
    public String getPartyQuestItems() {
        return dataString;
    }

    public boolean gotPartyQuestItem(String partyquestchar) {
        return dataString.contains(partyquestchar);
    }

    public void removePartyQuestItem(String letter) {
        if (gotPartyQuestItem(letter)) {
            dataString =
                    dataString.substring(0, dataString.indexOf(letter))
                            + dataString.substring(dataString.indexOf(letter) + letter.length());
        }
    }

    public void setPartyQuestItemObtained(String partyquestchar) {
        if (!dataString.contains(partyquestchar)) {
            this.dataString += partyquestchar;
        }
    }

    public void createDragon() {
        dragon = new MapleDragon(this);
    }

    public MapleDragon getDragon() {
        return dragon;
    }

    public void setDragon(MapleDragon dragon) {
        this.dragon = dragon;
    }

    public long getJailExpirationTimeLeft() {
        return jailExpiration - System.currentTimeMillis();
    }

    private void setFutureJailExpiration(long time) {
        jailExpiration = System.currentTimeMillis() + time;
    }

    public void addJailExpirationTime(long time) {
        long timeLeft = getJailExpirationTimeLeft();

        if (timeLeft <= 0) setFutureJailExpiration(time);
        else setFutureJailExpiration(timeLeft + time);
    }

    public void removeJailExpirationTime() {
        jailExpiration = 0;
    }
}
