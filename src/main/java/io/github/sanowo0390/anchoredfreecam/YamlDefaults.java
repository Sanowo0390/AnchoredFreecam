package io.github.sanowo0390.anchoredfreecam;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

final class YamlDefaults {
    private YamlDefaults() {
    }

    static YamlConfiguration load(InputStream stream) {
        if (stream == null) {
            return new YamlConfiguration();
        }
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        } catch (IOException ex) {
            return new YamlConfiguration();
        }
    }

    static int prune(ConfigurationSection target, ConfigurationSection defaults) {
        if (defaults.getKeys(true).isEmpty()) {
            return 0;
        }
        List<String> unused = new ArrayList<>();
        for (String key : target.getKeys(true)) {
            if (!target.isConfigurationSection(key) && !defaults.contains(key, true)) {
                unused.add(key);
            }
        }
        for (String key : unused) {
            target.set(key, null);
        }
        return unused.size();
    }

    static int merge(ConfigurationSection target, ConfigurationSection defaults) {
        int added = 0;
        for (String key : defaults.getKeys(true)) {
            if (target.contains(key, true)) {
                continue;
            }
            if (defaults.isConfigurationSection(key)) {
                target.createSection(key);
            } else {
                target.set(key, defaults.get(key));
                added++;
            }
            target.setComments(key, defaults.getComments(key));
            target.setInlineComments(key, defaults.getInlineComments(key));
        }
        return added;
    }
}
