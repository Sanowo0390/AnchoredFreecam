package com.palmergames.bukkit.towny.object;

import java.util.UUID;

public class Town {
    private final UUID id;
    private final String name;

    public Town(UUID id, String name) {
        this.id = id;
        this.name = name;
    }

    public UUID getUUID() {
        return id;
    }

    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Town town && town.id.equals(id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
