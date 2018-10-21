/*
    This file is part of the HeavenMS MapleStory Server
    Copyleft (L) 2016 - 2018 RonanLana

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as
    published by the Free Software Foundation version 3 as published by
    the Free Software Foundation. You may not use, modify or distribute
    this program under any other version of the GNU Affero General Public
    License.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/
package maplemesofetcher;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import life.MapleLifeFactory;
import life.MapleMonsterStats;
import tools.DatabaseConnection;
import tools.Pair;

/**
 * @author RonanLana
 *     <p>This application traces missing meso drop data on the underlying DB (that must be defined
 *     on the DatabaseConnection file of this project) and generates a SQL file that proposes
 *     missing drop entries for the drop_data table.
 *     <p>The meso range is calculated accordingly with the target mob stats, such as level and if
 *     it's a boss or not, similarly as how it has been done for the actual meso drops.
 */
public class MapleMesoFetcher {

    private static PrintWriter printWriter;
    private static final String newFile = "lib/meso_drop_data.sql";

    private static final boolean permitMesosOnDojoBosses = false;

    private static final int minItems = 4;

    private static final int mesoid = 0;
    private static final int chance = 400000;

    private static Map<Integer, MapleMonsterStats> mobStats;
    private static final Map<Integer, Pair<Integer, Integer>> mobRange = new HashMap<>();

    private static Pair<Integer, Integer> calcMesoRange90(int level, boolean boss) {
        int minRange, maxRange;

        // MIN range
        minRange = (int) (72.70814714 * Math.exp(0.02284640619 * level));

        // MAX range
        maxRange = (int) (133.8194881 * Math.exp(0.02059225059 * level));

        // boss perks
        if (boss) {
            minRange *= 3;
            maxRange *= 10;
        }

        return new Pair<>(minRange, maxRange);
    }

    private static Pair<Integer, Integer> calcMesoRange(int level, boolean boss) {
        int minRange, maxRange;

        // MIN range
        minRange = (int) (30.32032228 * Math.exp(0.03281144930 * level));

        // MAX range
        maxRange = (int) (44.45878459 * Math.exp(0.03289611686 * level));

        // boss perks
        if (boss) {
            minRange *= 3;
            maxRange *= 10;
        }

        return new Pair<>(minRange, maxRange);
    }

    private static void calcAllMobsMesoRange() {
        System.out.print("Calculating range... ");

        for (Entry<Integer, MapleMonsterStats> mobStat : mobStats.entrySet()) {
            MapleMonsterStats mms = mobStat.getValue();
            Pair<Integer, Integer> mesoRange;

            mesoRange =
                    mms.getLevel() < 90
                            ? calcMesoRange(mms.getLevel(), mms.isBoss())
                            : calcMesoRange90(mms.getLevel(), mms.isBoss());

            mobRange.put(mobStat.getKey(), mesoRange);
        }

        System.out.println("done!");
    }

    private static void printSqlHeader() {
        printWriter.println(
                " # SQL File autogenerated from the MapleMesoFetcher feature by Ronan Lana.");
        printWriter.println(
                " # Generated data takes into account mob stats such as level and boss for the meso ranges.");
        printWriter.println(
                " # Only mobs with "
                        + minItems
                        + " or more items with no meso entry on the DB it was compiled are presented here.");
        printWriter.println();

        printWriter.println(
                "  INSERT IGNORE INTO drop_data (`dropperid`, `itemid`, `minimum_quantity`, `maximum_quantity`, `questid`, `chance`) VALUES");
    }

    private static void printSqlExceptions() {
        if (!permitMesosOnDojoBosses) {
            printWriter.println(
                    "\r\n  DELETE FROM drop_data WHERE dropperid >= 9300184 AND dropperid <= 9300215 AND itemid = "
                            + mesoid
                            + ';');
        }
    }

    private static void printSqlMobMesoRange(int mobid) {
        Pair<Integer, Integer> mobmeso = mobRange.get(mobid);
        printWriter.println(
                "("
                        + mobid
                        + ", "
                        + mesoid
                        + ", "
                        + mobmeso.left
                        + ", "
                        + mobmeso.right
                        + ", 0, "
                        + chance
                        + "),");
    }

    private static void printSqlMobMesoRangeFinal(int mobid) {
        Pair<Integer, Integer> mobmeso = mobRange.get(mobid);
        printWriter.println(
                "("
                        + mobid
                        + ", "
                        + mesoid
                        + ", "
                        + mobmeso.left
                        + ", "
                        + mobmeso.right
                        + ", 0, "
                        + chance
                        + ");");
    }

    private static void generateMissingMobsMesoRange() {
        System.out.print("Generating missing ranges... ");
        Connection con = DatabaseConnection.getConnection();
        List<Integer> existingMobs = new ArrayList<>(200);

        try {
            // select all mobs which doesn't drop mesos and have a fair amount of items dropping
            // (meaning they are not an event mob)
            PreparedStatement ps =
                    con.prepareStatement(
                            "SELECT dropperid FROM drop_data WHERE dropperid NOT IN (SELECT DISTINCT dropperid FROM drop_data WHERE itemid = 0) GROUP BY dropperid HAVING count(*) >= "
                                    + minItems
                                    + ';');
            ResultSet rs = ps.executeQuery();

            if (rs.isBeforeFirst()) {
                while (rs.next()) {
                    int mobid = rs.getInt(1);

                    if (mobRange.containsKey(mobid)) {
                        existingMobs.add(mobid);
                    }
                }

                if (!existingMobs.isEmpty()) {
                    printWriter = new PrintWriter(newFile, StandardCharsets.UTF_8);
                    printSqlHeader();

                    for (int i = 0; i < existingMobs.size() - 1; i++)
                        printSqlMobMesoRange(existingMobs.get(i));

                    printSqlMobMesoRangeFinal(existingMobs.get(existingMobs.size() - 1));

                    printSqlExceptions();

                    printWriter.close();
                } else {
                    throw new Exception("ALREADY UPDATED");
                }

            } else {
                throw new Exception("ALREADY UPDATED");
            }

            rs.close();
            ps.close();
            con.close();

            System.out.println("done!");

        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().equals("ALREADY UPDATED")) {
                System.out.println("done! The DB is already up-to-date, no file generated.");
            } else {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        // load mob stats from WZ
        mobStats = MapleLifeFactory.getAllMonsterStats();

        calcAllMobsMesoRange();
        generateMissingMobsMesoRange();
    }
}
