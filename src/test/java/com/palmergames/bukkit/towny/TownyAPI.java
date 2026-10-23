package com.palmergames.bukkit.towny;

import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TownyAPI {
    public static final Town TOWN_A = new Town(UUID.fromString("00000000-0000-0000-0000-00000000000a"), "TownA");
    public static final Town TOWN_B = new Town(UUID.fromString("00000000-0000-0000-0000-00000000000b"), "TownB");
    public static final Map<UUID, Town> TOWNS = new HashMap<>();
    public static final Map<UUID, Resident> RESIDENTS = new HashMap<>();
    private static final TownyAPI INSTANCE = new TownyAPI();

    public static TownyAPI getInstance() {
        return INSTANCE;
    }

    public static void reset() {
        TOWNS.clear();
        RESIDENTS.clear();
    }

    public Town getTown(Location location) {
        double x = location.getX();
        if (x >= 0 && x < 100) {
            return TOWN_A;
        }
        if (x >= 100 && x < 200) {
            return TOWN_B;
        }
        return null;
    }

    public Town getTown(Player player) {
        return TOWNS.get(player.getUniqueId());
    }

    public Resident getResident(Player player) {
        return RESIDENTS.get(player.getUniqueId());
    }
}
