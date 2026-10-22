package com.palmergames.bukkit.towny.object;

public class Resident {
    private final boolean mayor;
    private final boolean assistant;

    public Resident(boolean mayor, boolean assistant) {
        this.mayor = mayor;
        this.assistant = assistant;
    }

    public boolean isMayor() {
        return mayor;
    }

    public boolean hasTownRank(String rank) {
        return assistant && rank.equalsIgnoreCase("assistant");
    }
}
