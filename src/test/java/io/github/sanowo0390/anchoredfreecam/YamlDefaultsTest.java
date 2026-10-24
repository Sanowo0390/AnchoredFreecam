package io.github.sanowo0390.anchoredfreecam;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YamlDefaultsTest {
    private static YamlConfiguration yaml(String text) throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString(text);
        return config;
    }

    @Test
    void mergeAddsMissingKeysAndKeepsExistingValues() throws Exception {
        YamlConfiguration target = yaml("range: 30.0\n");
        YamlConfiguration defaults = yaml("range: 15.0\nnested:\n  flag: true\n");
        assertEquals(1, YamlDefaults.merge(target, defaults));
        assertEquals(30.0, target.getDouble("range"));
        assertTrue(target.getBoolean("nested.flag"));
    }

    @Test
    void mergeCopiesCommentsOfAddedKeys() throws Exception {
        YamlConfiguration target = yaml("range: 30.0\n");
        YamlConfiguration defaults = yaml("range: 15.0\n# section note\nnested:\n  # flag note\n  flag: true\n");
        YamlDefaults.merge(target, defaults);
        String saved = target.saveToString();
        assertTrue(saved.contains("section note"));
        assertTrue(saved.contains("flag note"));
    }

    @Test
    void secondMergeAddsNothing() throws Exception {
        YamlConfiguration target = yaml("a: 1\n");
        YamlConfiguration defaults = yaml("a: 2\nb: 3\n");
        assertEquals(1, YamlDefaults.merge(target, defaults));
        assertEquals(0, YamlDefaults.merge(target, defaults));
    }

    @Test
    void loadingAMissingResourceGivesEmptyDefaults() {
        assertEquals(0, YamlDefaults.merge(new YamlConfiguration(), YamlDefaults.load(null)));
    }

    @Test
    void loadReadsUtf8Text() {
        byte[] bytes = "message: \"保護\"\n".getBytes(StandardCharsets.UTF_8);
        assertEquals("保護", YamlDefaults.load(new ByteArrayInputStream(bytes)).getString("message"));
    }
}
