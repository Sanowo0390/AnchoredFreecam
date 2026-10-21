package io.github.sanowo0390.anchoredfreecam;

import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Wolf;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PetControllerTest {
    private AnchoredFreecamPlugin plugin;
    private Server server;
    private World world;
    private Player owner;
    private Set<Player> active;

    @BeforeEach
    void setup() {
        plugin = mock(AnchoredFreecamPlugin.class);
        server = mock(Server.class);
        world = mock(World.class);
        owner = mock(Player.class);
        when(plugin.getServer()).thenReturn(server);
        when(owner.getWorld()).thenReturn(world);
        when(owner.getUniqueId()).thenReturn(UUID.randomUUID());
        when(server.getPlayer(owner.getUniqueId())).thenReturn(owner);
        active = new HashSet<>(Set.of(owner));
    }

    private Wolf wolf(boolean sitting) {
        Wolf wolf = mock(Wolf.class);
        when(wolf.getUniqueId()).thenReturn(UUID.randomUUID());
        UUID ownerId = owner.getUniqueId();
        when(wolf.getOwnerUniqueId()).thenReturn(ownerId);
        when(wolf.isValid()).thenReturn(true);
        AtomicBoolean state = new AtomicBoolean(sitting);
        when(wolf.isSitting()).thenAnswer(call -> state.get());
        doAnswer(call -> { state.set(call.getArgument(0)); return null; }).when(wolf).setSitting(anyBoolean());
        when(world.getEntitiesByClass(Tameable.class)).thenReturn(List.of(wolf));
        return wolf;
    }

    @Test
    void followingWolfWaitsAndResumesOnExit() {
        Wolf wolf = wolf(false);
        var controller = new PetController(plugin, active::contains, null);
        controller.pauseOwner(owner);
        assertTrue(wolf.isSitting());
        controller.restoreOwner(owner.getUniqueId());
        assertFalse(wolf.isSitting());
    }

    @Test
    void originallySittingWolfStaysSittingOnExit() {
        Wolf wolf = wolf(true);
        var controller = new PetController(plugin, active::contains, null);
        controller.pauseOwner(owner);
        controller.restoreOwner(owner.getUniqueId());
        assertTrue(wolf.isSitting());
        verify(wolf, never()).setSitting(false);
    }

    @Test
    void repeatedMaintenanceKeepsOriginalNonSittingState() {
        Wolf wolf = wolf(false);
        var controller = new PetController(plugin, active::contains, null);
        controller.pauseOwner(owner);
        controller.pauseOwner(owner);
        controller.pauseOwner(owner);
        controller.restoreAll();
        assertFalse(wolf.isSitting());
    }

    @Test
    void otherPlayersWolfAndInactiveOwnerAreUnaffected() {
        Wolf wolf = wolf(false);
        when(wolf.getOwnerUniqueId()).thenReturn(UUID.randomUUID());
        var controller = new PetController(plugin, active::contains, null);
        controller.pauseOwner(owner);
        assertFalse(controller.pauseEntity(wolf));
        active.clear();
        controller.pauseOwner(owner);
        verify(wolf, never()).setSitting(anyBoolean());
    }

    @Test
    void newTeleportingWolfIsPausedAndIdentifiedAsOwnedByCamera() {
        Wolf wolf = wolf(false);
        var controller = new PetController(plugin, active::contains, null);
        assertTrue(controller.pauseEntity(wolf));
        assertTrue(wolf.isSitting());
        active.clear();
        controller.restoreOwner(owner.getUniqueId());
        assertFalse(wolf.isSitting());
        assertFalse(controller.pauseEntity(wolf));
    }

    @Test
    void unloadReleasesSitStateAndReloadReappliesOnlyWhileOwnerActive() {
        Wolf wolf = wolf(false);
        var controller = new PetController(plugin, active::contains, null);
        controller.pauseOwner(owner);
        controller.beforeUnload(List.of(wolf));
        assertFalse(wolf.isSitting());
        controller.pauseEntity(wolf);
        assertTrue(wolf.isSitting());
        controller.restoreAll();
        assertFalse(wolf.isSitting());
    }

    @Test
    void changedOwnerDoesNotReceiveOldOwnersSitRestoration() {
        Wolf wolf = wolf(false);
        var controller = new PetController(plugin, active::contains, null);
        controller.pauseOwner(owner);
        when(wolf.getOwnerUniqueId()).thenReturn(UUID.randomUUID());
        controller.restoreOwner(owner.getUniqueId());
        verify(wolf, never()).setSitting(false);
    }

    @Test
    void myPetWrapperUsesItsOwnSittingApiAndRestoresPreviousState() {
        Mob entity = mock(Mob.class);
        when(entity.getUniqueId()).thenReturn(UUID.randomUUID());
        when(entity.isValid()).thenReturn(true);
        AtomicBoolean sitting = new AtomicBoolean();
        PetController.Pet handle = new PetController.Pet(entity, owner::getUniqueId, sitting::get, sitting::set);
        var myPet = mock(PetController.MyPetAccess.class);
        when(myPet.activePet(owner)).thenReturn(handle);
        when(myPet.fromEntity(entity)).thenReturn(handle);
        var controller = new PetController(plugin, active::contains, myPet);
        controller.pauseOwner(owner);
        assertTrue(sitting.get());
        assertTrue(controller.pauseEntity(entity));
        controller.restoreOwner(owner.getUniqueId());
        assertFalse(sitting.get());
    }

    @Test
    void ordinaryEntitiesAreNotTreatedAsPets() {
        var controller = new PetController(plugin, active::contains, null);
        assertFalse(controller.pauseEntity(mock(Entity.class)));
    }
}
