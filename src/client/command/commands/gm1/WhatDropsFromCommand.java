/*
    This file is part of the HeavenMS MapleStory Server, commands OdinMS-based
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

/*
   @Author: Arthur L - Refactored command content into modules
*/
package client.command.commands.gm1;

import client.MapleCharacter;
import client.MapleClient;
import client.command.Command;
import java.util.Iterator;
import server.MapleItemInformationProvider;
import server.life.MapleMonsterInformationProvider;
import server.life.MonsterDropEntry;
import tools.MaplePacketCreator;
import tools.Pair;

public class WhatDropsFromCommand extends Command {
    {
        setDescription("");
    }

    @Override
    public void execute(MapleClient c, String[] params) {
        MapleCharacter player = c.getPlayer();
        if (params.length < 1) {
            player.dropMessage(5, "Please do @whatdropsfrom <monster name>");
            return;
        }
        String monsterName = joinStringFrom(params, 0);
        StringBuilder output = new StringBuilder();
        int limit = 3;
        Iterator<Pair<Integer, String>> listIterator =
                MapleMonsterInformationProvider.getMobsIDsFromName(monsterName).iterator();
        for (int i = 0; i < limit; i++) {
            if (listIterator.hasNext()) {
                Pair<Integer, String> data = listIterator.next();
                int mobId = data.getLeft();
                String mobName = data.getRight();
                output.append(mobName).append(" drops the following items:\n\n");
                for (MonsterDropEntry drop :
                        MapleMonsterInformationProvider.getInstance().retrieveDrop(mobId)) {
                    try {
                        String name =
                                MapleItemInformationProvider.getInstance().getName(drop.itemId);
                        if (name.equals("null") || drop.chance == 0) {
                            continue;
                        }
                        int chance = 1_000_000 / drop.chance / player.getDropRate();
                        output.append("- ")
                                .append(name)
                                .append(" (1/")
                                .append(chance)
                                .append(")\n");
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
                output.append('\n');
            }
        }
        c.announce(
                MaplePacketCreator.getNPCTalk(
                        9010000, (byte) 0, output.toString(), "00 00", (byte) 0));
    }
}
