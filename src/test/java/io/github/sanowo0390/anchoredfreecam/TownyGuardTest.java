package io.github.sanowo0390.anchoredfreecam;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TownyGuardTest {
    private static final Logger LOGGER = Logger.getAnonymousLogger();

    private boolean defaultProtected;
    private File file;
    private TownyGuard guard;
    private Player member;
    private Player outsider;
    private Player bypass;

    @BeforeEach
    void setup() throws Exception {
        TownyAPI.reset();
        defaultProtected = true;
        file = new File(Files.createTempDirectory("towns").toFile(), "towns.yml");
        guard = TownyGuard.forClasses(LOGGER, new TownProtectionStore(file, () -> defaultProtected, LOGGER),
                TownyAPI.class, Town.class, Resident.class);
        member = player(false);
        outsider = player(false);
        bypass = player(true);
        TownyAPI.TOWNS.put(member.getUniqueId(), TownyAPI.TOWN_A);
        TownyAPI.RESIDENTS.put(member.getUniqueId(), new Resident(false, false));
        TownyAPI.RESIDENTS.put(outsider.getUniqueId(), new Resident(false, false));
    }

    private static Player player(boolean hasBypass) {
        UUID id = UUID.randomUUID();
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> id;
                    case "hasPermission" -> hasBypass && TownyGuard.BYPASS_PERMISSION.equals(args[0]);
                    case "hashCode" -> id.hashCode();
                    case "equals" -> proxy == args[0];
                    default -> null;
                });
    }

    private static Location at(double x) {
        return new Location(null, x, 64, 0);
    }

    private Player staff(boolean mayor, boolean assistant) {
        Player player = player(false);
        TownyAPI.TOWNS.put(player.getUniqueId(), TownyAPI.TOWN_A);
        TownyAPI.RESIDENTS.put(player.getUniqueId(), new Resident(mayor, assistant));
        return player;
    }

    @Test
    void ownTownIsNeverBlocked() {
        assertFalse(guard.isBlocked(member, at(10)));
    }

    @Test
    void otherProtectedTownIsBlocked() {
        assertTrue(guard.isBlocked(member, at(150)));
    }

    @Test
    void wildernessIsNeverBlocked() {
        assertFalse(guard.isBlocked(member, at(500)));
    }

    @Test
    void playerWithoutTownIsBlockedInsideProtectedTown() {
        assertTrue(guard.isBlocked(outsider, at(10)));
        assertTrue(guard.isBlocked(outsider, at(150)));
    }

    @Test
    void bypassPermissionSkipsTheRestriction() {
        assertFalse(guard.isBlocked(bypass, at(150)));
    }

    @Test
    void turningProtectionOffOpensOnlyThatTown() {
        guard.setTownProtected(TownyAPI.TOWN_B.getUUID(), false);
        assertFalse(guard.isBlocked(member, at(150)));
        assertTrue(guard.isBlocked(outsider, at(10)));
    }

    @Test
    void defaultProtectedAppliesToTownsWithoutASetting() {
        defaultProtected = false;
        assertFalse(guard.isBlocked(outsider, at(10)));
        guard.setTownProtected(TownyAPI.TOWN_A.getUUID(), true);
        assertTrue(guard.isBlocked(outsider, at(10)));
    }

    @Test
    void mayorAndAssistantCanManageButResidentsCannot() {
        assertTrue(guard.canManage(staff(true, false)));
        assertTrue(guard.canManage(staff(false, true)));
        assertFalse(guard.canManage(member));
        assertFalse(guard.canManage(outsider));
    }

    @Test
    void townOfReturnsIdAndName() {
        TownyGuard.TownRef ref = guard.townOf(member);
        assertEquals("TownA", ref.name());
        assertEquals(TownyAPI.TOWN_A.getUUID(), ref.id());
        assertNull(guard.townOf(outsider));
    }
}
