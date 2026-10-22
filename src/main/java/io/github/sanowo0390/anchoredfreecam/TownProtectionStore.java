package io.github.sanowo0390.anchoredfreecam;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.logging.Logger;

final class TownProtectionStore {
    private final File file;
    private final BooleanSupplier defaultProtected;
    private final Logger logger;
    private final YamlConfiguration yaml;

    TownProtectionStore(File file, BooleanSupplier defaultProtected, Logger logger) {
        this.file = file;
        this.defaultProtected = defaultProtected;
        this.logger = logger;
        this.yaml = YamlConfiguration.loadConfiguration(file);
    }

    boolean isProtected(UUID town) {
        return yaml.getBoolean("towns." + town, defaultProtected.getAsBoolean());
    }

    void setProtected(UUID town, boolean value) {
        yaml.set("towns." + town, value);
        try {
            yaml.save(file);
        } catch (IOException ex) {
            logger.warning("Could not save " + file.getName() + ": " + ex.getMessage());
        }
    }
}
