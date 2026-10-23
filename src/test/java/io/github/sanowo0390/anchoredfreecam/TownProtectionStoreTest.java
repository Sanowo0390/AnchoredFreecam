package io.github.sanowo0390.anchoredfreecam;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TownProtectionStoreTest {
    private static final Logger LOGGER = Logger.getAnonymousLogger();

    private File file;
    private boolean defaultProtected;

    @BeforeEach
    void setup() throws Exception {
        file = new File(Files.createTempDirectory("towns").toFile(), "towns.yml");
        defaultProtected = true;
    }

    private TownProtectionStore open() {
        return new TownProtectionStore(file, () -> defaultProtected, LOGGER);
    }

    @Test
    void unsetTownUsesTheDefault() {
        UUID town = UUID.randomUUID();
        assertTrue(open().isProtected(town));
        defaultProtected = false;
        assertFalse(open().isProtected(town));
    }

    @Test
    void savedValueSurvivesReopeningAndBeatsTheDefault() {
        UUID town = UUID.randomUUID();
        open().setProtected(town, false);
        assertFalse(open().isProtected(town));
        defaultProtected = false;
        open().setProtected(town, true);
        assertTrue(open().isProtected(town));
    }

    @Test
    void townsAreStoredIndependently() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        open().setProtected(first, false);
        assertFalse(open().isProtected(first));
        assertTrue(open().isProtected(second));
    }
}
