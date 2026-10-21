package io.github.sanowo0390.anchoredfreecam;

import org.bukkit.Input;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Spider;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.EntityAirChangeEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityToggleSwimEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityPortalEnterEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerAttemptPickupItemEvent;
import org.bukkit.event.block.Action;
import org.bukkit.block.BlockFace;
import org.bukkit.event.Event;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.entity.Item;
import com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.EquipmentSlot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WaterRegressionTest {
    private AnchoredFreecamPlugin plugin;
    private FreecamManager manager;
    private FreecamListener listener;
    private Messages messages;
    private Player player;
    private Mannequin body;
    private Server server;
    private World world;
    private Block block;
    private Input input;
    private YamlConfiguration config;
    private Location camera;
    private Location bodyLocation;
    private FreecamSession session;
    private final List<Runnable> queued = new ArrayList<>();
    private Runnable environment;
    private Runnable aggro;
    private MockedStatic<ItemStack> itemStacks;
    private ItemStack emptyHand;

    @AfterEach
    void closeItemStackMock() {
        if (itemStacks != null) itemStacks.close();
    }

    @BeforeEach
    void setup() throws Exception {
        emptyHand = mock(ItemStack.class);
        itemStacks = mockStatic(ItemStack.class);
        itemStacks.when(ItemStack::empty).thenReturn(emptyHand);
        plugin = mock(AnchoredFreecamPlugin.class);
        server = mock(Server.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        config = new YamlConfiguration();
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);
        when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), anyLong(), anyLong())).thenAnswer(call -> {
            if ((long) call.getArgument(3) == 1L) environment = call.getArgument(1);
            if ((long) call.getArgument(3) == 2L) aggro = call.getArgument(1);
            return mock(BukkitTask.class);
        });
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(call -> {
            queued.add(call.getArgument(1));
            return mock(BukkitTask.class);
        });
        world = mock(World.class);
        block = mock(Block.class);
        when(block.getType()).thenReturn(Material.AIR);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(block);
        when(world.getBlockAt(any(Location.class))).thenReturn(block);
        camera = new Location(world, 0, 64, 0);
        bodyLocation = camera.clone();
        player = mock(Player.class);
        body = mock(Mannequin.class);
        input = mock(Input.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(body.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOnline()).thenReturn(true);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(player.getCurrentInput()).thenReturn(input);
        when(player.getLocation()).thenAnswer(call -> camera.clone());
        when(player.getEyeLocation()).thenAnswer(call -> camera.clone().add(0, 1.62, 0));
        when(player.getVelocity()).thenReturn(new Vector());
        when(player.getMaximumAir()).thenReturn(300);
        when(body.getLocation()).thenAnswer(call -> bodyLocation.clone());
        when(body.getEyeLocation()).thenAnswer(call -> bodyLocation.clone().add(0, 1.62, 0));
        when(body.getVelocity()).thenReturn(new Vector());
        when(body.isValid()).thenReturn(true);
        when(body.getRemainingAir()).thenReturn(240);
        when(body.getMaximumAir()).thenReturn(300);
        when(server.getPlayer(player.getUniqueId())).thenReturn(player);
        when(server.getEntity(body.getUniqueId())).thenReturn(body);
        messages = mock(Messages.class);
        when(messages.text(anyString())).thenReturn("message");
        manager = new FreecamManager(plugin, messages);
        listener = new FreecamListener(plugin, manager);
        installSession();
        doAnswer(call -> {
            EntityDamageEvent forwarded = mock(EntityDamageEvent.class);
            when(forwarded.getEntity()).thenReturn(player);
            when(forwarded.getFinalDamage()).thenReturn((Double) call.getArgument(0));
            listener.onForwardedDamage(forwarded);
            listener.onForwardedDamageResult(forwarded);
            return null;
        }).when(player).damage(anyDouble(), any(org.bukkit.damage.DamageSource.class));
        when(player.teleport(any(Location.class), eq(PlayerTeleportEvent.TeleportCause.PLUGIN))).thenAnswer(call -> {
            Location target = call.getArgument(0);
            PlayerTeleportEvent event = teleport(target, PlayerTeleportEvent.TeleportCause.PLUGIN);
            assertTrue(manager.isInternalTeleport(player));
            listener.onTeleport(event);
            listener.onTeleportResult(event);
            if (event.isCancelled()) return false;
            camera = event.getTo().clone();
            return true;
        });
    }

    @SuppressWarnings("unchecked")
    private <T> Map<UUID, T> state(String name) throws Exception {
        Field field = FreecamManager.class.getDeclaredField(name);
        field.setAccessible(true);
        return (Map<UUID, T>) field.get(manager);
    }

    private void installSession() throws Exception {
        installSession(false);
    }

    private void installSession(boolean hovering) throws Exception {
        session = new FreecamSession(bodyLocation.clone(), body.getUniqueId(), false, false,
                false, true, false, true, false, true, 300, 0, hovering, 0, 0);
        state("sessions").put(player.getUniqueId(), session);
        state("bodyOwners").put(body.getUniqueId(), player.getUniqueId());
        state("lastLegalLocations").put(player.getUniqueId(), camera.clone());
    }

    private PlayerTeleportEvent teleport(Location to, PlayerTeleportEvent.TeleportCause cause) {
        return new PlayerTeleportEvent(player, camera.clone(), to, cause);
    }

    private void runQueued() {
        List<Runnable> tasks = List.copyOf(queued);
        queued.clear();
        tasks.forEach(Runnable::run);
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.04, -0.08, 0.3})
    void passiveWaterMovementUsesOwnedCorrection(double lift) {
        when(input.isForward()).thenReturn(true);
        when(player.isInWaterOrBubbleColumn()).thenReturn(true);
        Location to = camera.clone().add(0.2, lift, 0.1);
        PlayerMoveEvent event = new PlayerMoveEvent(player, camera.clone(), to);
        listener.onMove(event);
        assertTrue(event.isCancelled());
        assertEquals(64 + lift, event.getTo().getY(), 1e-9, "No unowned setTo teleport");
        runQueued();
        assertTrue(manager.isActive(player));
        assertEquals(64, camera.getY());
        assertEquals(0.2, camera.getX());
        assertFalse(manager.isInternalTeleport(player));
    }

    @Test
    void surfaceUsesWaterBlockEvenWhenEntityFlagIsFalse() {
        when(block.getType()).thenReturn(Material.WATER);
        PlayerMoveEvent event = new PlayerMoveEvent(player, camera.clone(), camera.clone().add(0.2, 0.04, 0));
        listener.onMove(event);
        assertTrue(event.isCancelled());
        runQueued();
        assertTrue(manager.isActive(player));
    }

    @Test
    void intentionalVerticalInputIsPreserved() {
        when(player.isInWaterOrBubbleColumn()).thenReturn(true);
        when(input.isJump()).thenReturn(true);
        PlayerMoveEvent up = new PlayerMoveEvent(player, camera.clone(), camera.clone().add(0, 0.2, 0));
        listener.onMove(up);
        assertFalse(up.isCancelled());
        when(input.isJump()).thenReturn(false);
        when(input.isSneak()).thenReturn(true);
        PlayerMoveEvent down = new PlayerMoveEvent(player, camera.clone(), camera.clone().add(0, -0.2, 0));
        listener.onMove(down);
        assertFalse(down.isCancelled());
        assertTrue(queued.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(doubles = {0, 1, 3, 4, 40})
    void externalPluginTeleportEndsFreecamAtEveryDistance(double distance) {
        PlayerTeleportEvent event = teleport(camera.clone().add(distance, 0, 0), PlayerTeleportEvent.TeleportCause.PLUGIN);
        listener.onTeleport(event);
        assertTrue(manager.isActive(player), "Wait until final cancellation state");
        listener.onTeleportResult(event);
        assertFalse(manager.isActive(player));
        assertFalse(event.isCancelled());
        verify(player, never()).teleport(any(Location.class), any(PlayerTeleportEvent.TeleportCause.class));
    }

    @Test
    void cancelledExternalTeleportKeepsBodyAndSession() {
        PlayerTeleportEvent event = teleport(camera.clone().add(30, 0, 0), PlayerTeleportEvent.TeleportCause.COMMAND);
        listener.onTeleport(event);
        event.setCancelled(true);
        listener.onTeleportResult(event);
        assertTrue(manager.isActive(player));
        verify(body, never()).remove();
    }

    @Test
    void incomingTpaRedirectsToBody() {
        Player visitor = mock(Player.class);
        when(visitor.getUniqueId()).thenReturn(UUID.randomUUID());
        camera.add(7, 0, 0);
        PlayerTeleportEvent event = new PlayerTeleportEvent(visitor, new Location(world, 30, 64, 0), camera.clone(),
                PlayerTeleportEvent.TeleportCause.PLUGIN);
        listener.onTeleport(event);
        assertEquals(bodyLocation, event.getTo());
        assertTrue(manager.isActive(player));
    }

    @Test
    void staleCorrectionCannotMoveNewSession() throws Exception {
        manager.queueCameraCorrection(player, camera.clone().add(1, 0, 0));
        manager.stopWithoutReturn(player, false);
        installSession();
        manager.queueCameraCorrection(player, camera.clone().add(2, 0, 0));
        runQueued();
        assertEquals(2, camera.getX());
        verify(player, times(1)).teleport(any(Location.class), any(PlayerTeleportEvent.TeleportCause.class));
    }

    @Test
    void movingBodyRevalidatesQueuedDestination() {
        manager.queueCameraCorrection(player, camera.clone().add(14, 0, 0));
        bodyLocation.setY(40);
        runQueued();
        assertNotEquals(bodyLocation, camera);
        assertEquals(14.95, camera.distance(bodyLocation), 1e-6);
        assertTrue(manager.isActive(player));
    }

    @Test
    void cancelledCorrectionDoesNotCommitLastLegalPosition() throws Exception {
        doReturn(false).when(player).teleport(any(Location.class), any(PlayerTeleportEvent.TeleportCause.class));
        Location original = camera.clone();
        manager.queueCameraCorrection(player, camera.clone().add(2, 0, 0));
        runQueued();
        assertEquals(original, state("lastLegalLocations").get(player.getUniqueId()));
    }

    @Test
    void defaultRangeBoundaryReturnsWithoutEndingSession() {
        assertEquals(15, manager.getMaxDistance());
        config.set("show-boundary-message", false);
        PlayerMoveEvent event = new PlayerMoveEvent(player, camera.clone(), camera.clone().add(16, 0, 0));
        listener.onMove(event);
        assertTrue(event.isCancelled());
        runQueued();
        assertTrue(manager.isActive(player));
        assertTrue(camera.distanceSquared(session.anchor()) <= 225);
    }

    @Test
    void cancelledMovesDoNotAdvanceLastLegalLocation() throws Exception {
        PlayerMoveEvent event = new PlayerMoveEvent(player, camera.clone(), camera.clone().add(2, 0, 0));
        listener.onMove(event);
        assertEquals(camera, state("lastLegalLocations").get(player.getUniqueId()));
        listener.onMoveResult(event);
        assertEquals(event.getTo(), state("lastLegalLocations").get(player.getUniqueId()));
    }

    @Test
    void cameraCannotChangeAirOrEnterSwimmingPose() {
        EntityAirChangeEvent air = new EntityAirChangeEvent(player, 299);
        listener.onAirChange(air);
        assertTrue(air.isCancelled());
        EntityAirChangeEvent bodyAir = new EntityAirChangeEvent(body, 239);
        listener.onAirChange(bodyAir);
        assertFalse(bodyAir.isCancelled());
        EntityToggleSwimEvent swim = new EntityToggleSwimEvent(player, true);
        listener.onSwim(swim);
        assertTrue(swim.isCancelled());
    }

    @Test
    void flightDisableIsCancelledAndReasserted() {
        PlayerToggleFlightEvent flight = new PlayerToggleFlightEvent(player, false);
        listener.onToggleFlight(flight);
        assertTrue(flight.isCancelled());
        runQueued();
        verify(player).setAllowFlight(true);
        verify(player).setFlying(true);
    }

    @Test
    void waterVelocityPreservesHorizontalMotionAndIntentionalVerticalMotion() {
        when(input.isForward()).thenReturn(true);
        when(player.isInWaterOrBubbleColumn()).thenReturn(true);
        PlayerVelocityEvent passive = new PlayerVelocityEvent(player, new Vector(0.1, 0.04, 0.2));
        listener.onVelocity(passive);
        assertEquals(new Vector(0.1, 0, 0.2), passive.getVelocity());
        when(input.isJump()).thenReturn(true);
        PlayerVelocityEvent deliberate = new PlayerVelocityEvent(player, new Vector(0, 0.2, 0));
        listener.onVelocity(deliberate);
        assertEquals(0.2, deliberate.getVelocity().getY());
    }

    @Test
    void waterloggedAndBubbleBlocksCountAsWaterButLavaDoesNot() {
        when(block.getType()).thenReturn(Material.LAVA);
        assertFalse(WaterContact.at(camera));
        when(block.getType()).thenReturn(Material.BUBBLE_COLUMN);
        assertTrue(WaterContact.at(camera));
        when(block.getType()).thenReturn(Material.OAK_SLAB);
        Waterlogged data = mock(Waterlogged.class);
        when(data.isWaterlogged()).thenReturn(true);
        when(block.getBlockData()).thenReturn(data);
        assertTrue(WaterContact.at(camera));
    }

    @Test
    void surfaceDrowningUsesNativeEyeSubmersionInsteadOfLiquidBlock() {
        when(block.getType()).thenReturn(Material.WATER);
        when(body.isUnderWater()).thenReturn(false);
        EntityDamageEvent damage = mock(EntityDamageEvent.class);
        when(damage.getEntity()).thenReturn(body);
        when(damage.getCause()).thenReturn(EntityDamageEvent.DamageCause.DROWNING);
        assertTrue(manager.handleBodyDamage(damage));
        assertTrue(manager.isActive(player));
        verify(body).setRemainingAir(300);
        verify(player, never()).damage(anyDouble(), any(org.bukkit.damage.DamageSource.class));
    }

    @Test
    void manualExitRestoresFallingBodyPositionVelocityAndDistance() {
        bodyLocation.setY(50);
        when(body.getVelocity()).thenReturn(new Vector(0, -0.8, 0));
        when(body.getFallDistance()).thenReturn(14f);
        // stop() intentionally removes the session before its return teleport.
        doReturn(true).when(player).teleport(any(Location.class), any(PlayerTeleportEvent.TeleportCause.class));
        manager.stop(player, true, false);
        verify(player).teleport(bodyLocation, PlayerTeleportEvent.TeleportCause.PLUGIN);
        verify(player).setVelocity(new Vector(0, -0.8, 0));
        verify(player).setFallDistance(14f);
        verify(player).setRemainingAir(240);
        verify(player).setInvisible(false);
        verify(player).setVisibleByDefault(true);
        verify(player).setAllowFlight(false);
    }

    @Test
    void idleCameraDoesNotDriftWithHorizontalWaterCurrent() {
        when(player.isInWaterOrBubbleColumn()).thenReturn(true);
        Location original = camera.clone();
        PlayerMoveEvent event = new PlayerMoveEvent(player, camera.clone(), camera.clone().add(0.08, 0.04, -0.1));
        listener.onMove(event);
        assertTrue(event.isCancelled());
        runQueued();
        assertEquals(original, camera);
        PlayerVelocityEvent velocity = new PlayerVelocityEvent(player, new Vector(0.08, 0.04, -0.1));
        listener.onVelocity(velocity);
        assertEquals(new Vector(), velocity.getVelocity());
    }

    @Test
    void waterBodyStaysPhysicalAndCameraAirComesOnlyFromBody() {
        when(body.isInWaterOrBubbleColumn()).thenReturn(true);
        when(body.isOnGround()).thenReturn(true);
        doAnswer(call -> {
            EntityAirChangeEvent air = new EntityAirChangeEvent(player, call.getArgument(0));
            listener.onAirChange(air);
            assertFalse(air.isCancelled(), "Explicit body air sync must be allowed");
            return null;
        }).when(player).setRemainingAir(anyInt());
        environment.run();
        verify(body).setImmovable(false);
        verify(body).setGravity(true);
        verify(player).setRemainingAir(240);
        assertFalse(manager.isSynchronizingAir(player));
        assertTrue(manager.isActive(player));
    }

    @Test
    void dryLandingFreezesBodyAndFallingBodyMovesAnchor() {
        bodyLocation.setY(60);
        environment.run();
        verify(body).setImmovable(false);
        verify(body).setGravity(true);
        assertEquals(bodyLocation, session.anchor());
        when(body.isOnGround()).thenReturn(true);
        environment.run();
        verify(body).setImmovable(true);
        verify(body).setGravity(false);
        verify(body).setVelocity(new Vector());
    }

    @Test
    void realUnderwaterBodyDamageStillEndsFreecamAndIsForwarded() {
        when(body.isUnderWater()).thenReturn(true);
        EntityDamageEvent damage = mock(EntityDamageEvent.class);
        org.bukkit.damage.DamageSource source = mock(org.bukkit.damage.DamageSource.class);
        when(damage.getEntity()).thenReturn(body);
        when(damage.getCause()).thenReturn(EntityDamageEvent.DamageCause.DROWNING);
        when(damage.getDamage()).thenReturn(2.0);
        when(damage.getDamageSource()).thenReturn(source);
        assertTrue(manager.handleBodyDamage(damage));
        assertFalse(manager.isActive(player));
        verify(player).damage(2.0, source);
    }

    @Test
    void ownBodyRightClickEndsFreecamButOtherBodyDoesNot() {
        Mannequin other = mock(Mannequin.class);
        when(other.getUniqueId()).thenReturn(UUID.randomUUID());
        PlayerInteractEntityEvent foreignClick = new PlayerInteractEntityEvent(player, other);
        listener.onInteractEntity(foreignClick);
        assertTrue(foreignClick.isCancelled());
        assertTrue(manager.isActive(player));
        PlayerInteractEntityEvent ownClick = new PlayerInteractEntityEvent(player, body);
        listener.onInteractEntity(ownClick);
        assertTrue(ownClick.isCancelled());
        assertFalse(manager.isActive(player));
        verify(body).remove();
    }

    @Test
    void crossWorldTeleportEndsFreecamWithoutReturningToBody() {
        Location target = new Location(mock(World.class), 0, 70, 0);
        PlayerTeleportEvent event = teleport(target, PlayerTeleportEvent.TeleportCause.UNKNOWN);
        listener.onTeleport(event);
        listener.onTeleportResult(event);
        assertFalse(manager.isActive(player));
        assertEquals(target, event.getTo());
        verify(player, never()).teleport(any(Location.class), any(PlayerTeleportEvent.TeleportCause.class));
    }

    @Test
    void groundedWaterBodyRetainsCurrentVelocityOnExit() {
        when(body.isOnGround()).thenReturn(true);
        when(body.isInWaterOrBubbleColumn()).thenReturn(true);
        when(body.getVelocity()).thenReturn(new Vector(0.08, 0, 0.02));
        manager.stop(player, true, false);
        verify(player).setVelocity(new Vector(0.08, 0, 0.02));
    }

    @Test
    void crossingBoundaryUsesCurrentEdgeInsteadOfStaleOrigin() {
        config.set("show-boundary-message", false);
        camera.setX(14.95);
        PlayerMoveEvent move = new PlayerMoveEvent(player, camera.clone(), camera.clone().add(0.1, 0, 0));
        listener.onMove(move);
        runQueued();
        assertEquals(14.95, camera.getX(), 1e-9);
        assertTrue(manager.isActive(player));
    }

    @Test
    void environmentDoesNotOverwriteSavedEdgeWithBodyPosition() throws Exception {
        Location edge = camera.clone().add(14.95, 0, 0);
        state("lastLegalLocations").put(player.getUniqueId(), edge);
        camera.setX(15.05);
        config.set("show-boundary-message", false);
        environment.run();
        assertEquals(edge, state("lastLegalLocations").get(player.getUniqueId()));
        runQueued();
        assertEquals(edge, camera);
        assertTrue(manager.isActive(player));
    }

    @Test
    void invalidSavedEdgeProjectsNearLimitAfterBodyMovement() throws Exception {
        camera.setX(15);
        state("lastLegalLocations").put(player.getUniqueId(), camera.clone());
        bodyLocation.setX(-0.1);
        config.set("show-boundary-message", false);
        environment.run();
        runQueued();
        assertEquals(14.85, camera.getX(), 1e-9);
        assertEquals(14.95, camera.distance(bodyLocation), 1e-9);
    }

    @Test
    void shrinkingRangeProjectsToNewEdgeAndPreservesLook() {
        camera.setX(14);
        camera.setYaw(75);
        camera.setPitch(-25);
        config.set("max-distance-blocks", 10);
        manager.queueCameraCorrection(player, camera.clone());
        runQueued();
        assertEquals(9.95, camera.getX(), 1e-9);
        assertEquals(75, camera.getYaw());
        assertEquals(-25, camera.getPitch());
    }

    @Test
    void projectedCorrectionAvoidsCollidingEndpoint() {
        camera.setX(15.1);
        when(player.collidesAt(any(Location.class))).thenAnswer(call -> ((Location) call.getArgument(0)).getX() > 14.8);
        manager.queueCameraCorrection(player, camera.clone());
        runQueued();
        assertEquals(14.7, camera.getX(), 1e-9);
    }

    @Test
    void cameraAttackCannotExitVictimEvenWhenGeneralHandlerRunsFirst() throws Exception {
        Player attacker = mock(Player.class);
        when(attacker.getUniqueId()).thenReturn(UUID.randomUUID());
        state("sessions").put(attacker.getUniqueId(), session);
        EntityDamageByEntityEvent hit = mock(EntityDamageByEntityEvent.class);
        when(hit.getDamager()).thenReturn(attacker);
        when(hit.getEntity()).thenReturn(body);
        listener.onDamage(hit);
        verify(hit).setCancelled(true);
        assertTrue(manager.isActive(player));
        verify(body, never()).remove();
        verify(player, never()).damage(anyDouble(), any(org.bukkit.damage.DamageSource.class));
    }

    @Test
    void cameraProjectileCannotExitVictim() throws Exception {
        Player attacker = mock(Player.class);
        when(attacker.getUniqueId()).thenReturn(UUID.randomUUID());
        state("sessions").put(attacker.getUniqueId(), session);
        org.bukkit.entity.Projectile projectile = mock(org.bukkit.entity.Projectile.class);
        when(projectile.getShooter()).thenReturn(attacker);
        EntityDamageByEntityEvent hit = mock(EntityDamageByEntityEvent.class);
        when(hit.getDamager()).thenReturn(projectile);
        when(hit.getEntity()).thenReturn(body);
        listener.onDamageByEntity(hit);
        listener.onDamage(hit);
        verify(hit, times(2)).setCancelled(true);
        assertTrue(manager.isActive(player));
        verify(body, never()).remove();
    }

    @Test
    void normalPlayerCanStillDamageBodyAndEndFreecam() {
        Player attacker = mock(Player.class);
        when(attacker.getUniqueId()).thenReturn(UUID.randomUUID());
        EntityDamageByEntityEvent hit = mock(EntityDamageByEntityEvent.class);
        org.bukkit.damage.DamageSource source = mock(org.bukkit.damage.DamageSource.class);
        when(hit.getDamager()).thenReturn(attacker);
        when(hit.getEntity()).thenReturn(body);
        when(hit.getCause()).thenReturn(EntityDamageEvent.DamageCause.ENTITY_ATTACK);
        when(hit.getDamage()).thenReturn(3.0);
        when(hit.getDamageSource()).thenReturn(source);
        listener.onDamage(hit);
        assertFalse(manager.isActive(player));
        verify(player).damage(3.0, source);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void protectionCancellationPreservesBodyAndSession(boolean exitOnDamage) {
        config.set("exit-on-body-damage", exitOnDamage);
        Player attacker = mock(Player.class);
        org.bukkit.damage.DamageSource source = mock(org.bukkit.damage.DamageSource.class);
        EntityDamageByEntityEvent hit = mock(EntityDamageByEntityEvent.class);
        when(hit.getDamager()).thenReturn(attacker);
        when(hit.getEntity()).thenReturn(body);
        when(hit.getDamage()).thenReturn(3.0);
        when(hit.getDamageSource()).thenReturn(source);
        EntityDamageByEntityEvent forwarded = mock(EntityDamageByEntityEvent.class);
        when(forwarded.getEntity()).thenReturn(player);
        when(forwarded.getDamager()).thenReturn(attacker);
        when(forwarded.isCancelled()).thenReturn(true);
        when(forwarded.getFinalDamage()).thenReturn(3.0);
        doAnswer(call -> {
            assertTrue(manager.isActive(player), "Protection must run before ending Freecam");
            listener.onForwardedDamage(forwarded);
            listener.onDamage(forwarded);
            listener.onForwardedDamageResult(forwarded);
            return null;
        }).when(player).damage(3.0, source);
        listener.onDamage(hit);
        assertTrue(manager.isActive(player));
        assertFalse(manager.isForwardedBodyDamage(player));
        verify(body, never()).remove();
        verify(player, never()).teleport(any(Location.class), any(PlayerTeleportEvent.TeleportCause.class));
    }

    @Test
    void nativeDamageRejectionWithoutEventKeepsFreecam() {
        EntityDamageEvent hit = mock(EntityDamageEvent.class);
        org.bukkit.damage.DamageSource source = mock(org.bukkit.damage.DamageSource.class);
        when(hit.getEntity()).thenReturn(body);
        when(hit.getDamage()).thenReturn(3.0);
        when(hit.getDamageSource()).thenReturn(source);
        doNothing().when(player).damage(3.0, source);
        manager.handleBodyDamage(hit);
        assertTrue(manager.isActive(player));
        verify(body, never()).remove();
    }

    @Test
    void allowedLethalHitReturnsBeforeNativeDamageContinues() {
        org.bukkit.damage.DamageSource source = mock(org.bukkit.damage.DamageSource.class);
        EntityDamageEvent hit = mock(EntityDamageEvent.class);
        when(hit.getEntity()).thenReturn(body);
        when(hit.getDamage()).thenReturn(100.0);
        when(hit.getDamageSource()).thenReturn(source);
        camera.setX(10);
        doAnswer(call -> {
            EntityDamageEvent forwarded = mock(EntityDamageEvent.class);
            when(forwarded.getEntity()).thenReturn(player);
            when(forwarded.getFinalDamage()).thenReturn(100.0);
            listener.onForwardedDamage(forwarded);
            assertTrue(manager.isActive(player));
            listener.onForwardedDamageResult(forwarded);
            assertFalse(manager.isActive(player));
            assertEquals(bodyLocation, camera, "Native damage must continue at the body");
            return null;
        }).when(player).damage(100.0, source);
        manager.handleBodyDamage(hit);
    }

    @Test
    void preexistingInvulnerabilityIsNotBypassed() throws Exception {
        session = new FreecamSession(bodyLocation.clone(), body.getUniqueId(), false, false,
                true, true, false, true, false, true, 300, 0, false, 0, 0);
        state("sessions").put(player.getUniqueId(), session);
        EntityDamageEvent hit = mock(EntityDamageEvent.class);
        when(hit.getEntity()).thenReturn(body);
        manager.handleBodyDamage(hit);
        assertTrue(manager.isActive(player));
        verify(player, never()).damage(anyDouble(), any(org.bukkit.damage.DamageSource.class));
    }

    @Test
    void zeroDamageAfterProtectionDoesNotEndFreecam() {
        org.bukkit.damage.DamageSource source = mock(org.bukkit.damage.DamageSource.class);
        EntityDamageEvent hit = mock(EntityDamageEvent.class);
        when(hit.getEntity()).thenReturn(body);
        when(hit.getDamage()).thenReturn(3.0);
        when(hit.getDamageSource()).thenReturn(source);
        doAnswer(call -> {
            EntityDamageEvent forwarded = mock(EntityDamageEvent.class);
            when(forwarded.getEntity()).thenReturn(player);
            listener.onForwardedDamage(forwarded);
            listener.onForwardedDamageResult(forwarded);
            return null;
        }).when(player).damage(3.0, source);
        manager.handleBodyDamage(hit);
        assertTrue(manager.isActive(player));
        verify(body, never()).remove();
    }

    @Test
    void equipmentMaskSendsBothHandsAndAllArmorWithoutMutatingInventory() {
        manager.hideCameraEquipment(player);
        verify(player).sendEquipmentChange(player, maskedEquipment());
        verify(player, never()).getInventory();
        verify(body, never()).getEquipment();
        verify(plugin).maskCameraInventory(player);
    }

    @Test
    void hotbarSwitchRestoresPreviousSlotBeforeMaskingHandsAndArmor() {
        listener.onItemHeld(new PlayerItemHeldEvent(player, 0, 1));
        runQueued();
        org.mockito.InOrder order = inOrder(player);
        order.verify(player).updateInventory();
        order.verify(player).sendEquipmentChange(player, maskedEquipment());
        verify(player, never()).getInventory();
    }

    @Test
    void stoppingRestoresInventoryAndDiscardsPendingHandMask() {
        manager.refreshCameraEquipmentNextTick(player);
        manager.stopWithoutReturn(player, false);
        runQueued();
        verify(player).updateInventory();
        verify(player, never()).sendEquipmentChange(any(), anyMap());
        verify(plugin, never()).maskCameraInventory(any());
    }

    @Test
    void staleHandRefreshCannotAffectNewSession() throws Exception {
        manager.refreshCameraEquipmentNextTick(player);
        manager.stopWithoutReturn(player, false);
        installSession();
        runQueued();
        verify(player, times(1)).updateInventory();
        verify(player, never()).sendEquipmentChange(any(), anyMap());
    }

    private Map<EquipmentSlot, ItemStack> maskedEquipment() {
        return Map.of(EquipmentSlot.HAND, emptyHand, EquipmentSlot.OFF_HAND, emptyHand,
                EquipmentSlot.HEAD, emptyHand, EquipmentSlot.CHEST, emptyHand,
                EquipmentSlot.LEGS, emptyHand, EquipmentSlot.FEET, emptyHand);
    }

    @Test
    void environmentReappliesEntireEquipmentMask() {
        environment.run();
        verify(player).sendEquipmentChange(player, maskedEquipment());
        verify(player, never()).getInventory();
        verify(body, never()).getEquipment();
    }

    @Test
    void inactivePlayerReceivesNoEquipmentMask() {
        manager.stopWithoutReturn(player, false);
        manager.hideCameraEquipment(player);
        verify(player, never()).sendEquipmentChange(any(), anyMap());
    }

    private Monster nearbyMonster() {
        Monster monster = mock(Monster.class);
        when(monster.isValid()).thenReturn(true);
        when(monster.isAware()).thenReturn(true);
        when(body.getNearbyEntities(anyDouble(), anyDouble(), anyDouble())).thenReturn(List.of(monster));
        return monster;
    }

    @Test
    void creativeBodyDoesNotAcquireAggroAndClearsExistingTarget() {
        when(player.getGameMode()).thenReturn(GameMode.CREATIVE);
        Monster monster = nearbyMonster();
        aggro.run();
        verify(monster, never()).setTarget(any());
        when(monster.getTarget()).thenReturn(body);
        aggro.run();
        verify(monster).setTarget(null);
        verify(monster, never()).setTarget(body);
    }

    @Test
    void creativeAggroCleanupDoesNotStealAnotherPlayersTarget() {
        when(player.getGameMode()).thenReturn(GameMode.CREATIVE);
        Monster monster = nearbyMonster();
        when(monster.getTarget()).thenReturn(mock(Player.class));
        aggro.run();
        verify(monster, never()).setTarget(any());
    }

    @ParameterizedTest
    @EnumSource(value = GameMode.class, names = {"SURVIVAL", "ADVENTURE"})
    void survivalAndAdventureStillAttractHostileMobs(GameMode mode) {
        when(player.getGameMode()).thenReturn(mode);
        Monster monster = nearbyMonster();
        aggro.run();
        verify(monster).setTarget(body);
    }

    @Test
    void creativeCameraAndBodyCannotBeTargetedEvenWithAggroOptionOff() {
        config.set("force-hostile-mob-aggro", false);
        when(player.getGameMode()).thenReturn(GameMode.CREATIVE);
        for (LivingEntity target : new LivingEntity[]{player, body}) {
            EntityTargetLivingEntityEvent event = mock(EntityTargetLivingEntityEvent.class);
            when(event.getTarget()).thenReturn(target);
            listener.onTarget(event);
            verify(event).setTarget(null);
        }
    }

    @Test
    void survivalCameraStillRedirectsMobsToBody() {
        EntityTargetLivingEntityEvent event = mock(EntityTargetLivingEntityEvent.class);
        when(event.getTarget()).thenReturn(player);
        listener.onTarget(event);
        verify(event).setTarget(body);
    }

    @Test
    void creativeBodyDamageDoesNotExitOrForwardDamage() {
        when(player.getGameMode()).thenReturn(GameMode.CREATIVE);
        EntityDamageEvent event = mock(EntityDamageEvent.class);
        when(event.getEntity()).thenReturn(body);
        when(event.getCause()).thenReturn(EntityDamageEvent.DamageCause.ENTITY_ATTACK);
        listener.onDamage(event);
        verify(event).setCancelled(true);
        assertTrue(manager.isActive(player));
        verify(body, never()).remove();
        verify(player, never()).damage(anyDouble(), any(org.bukkit.damage.DamageSource.class));
    }

    @ParameterizedTest
    @EnumSource(value = GameMode.class, names = {"CREATIVE", "SURVIVAL"})
    void startingRetargetRespectsGameMode(GameMode mode) throws Exception {
        when(player.getGameMode()).thenReturn(mode);
        Monster monster = nearbyMonster();
        when(monster.getTarget()).thenReturn(player);
        when(player.getNearbyEntities(anyDouble(), anyDouble(), anyDouble())).thenReturn(List.of(monster));
        var method = FreecamManager.class.getDeclaredMethod("retargetCurrentEnemies", Player.class, Mannequin.class);
        method.setAccessible(true);
        method.invoke(manager, player, body);
        verify(monster).setTarget(mode == GameMode.CREATIVE ? null : body);
    }

    @Test
    void onlyCreativeAlreadyFlyingAtStartGetsHoveringBody() {
        when(player.getGameMode()).thenReturn(GameMode.CREATIVE);
        when(player.isFlying()).thenReturn(true);
        assertTrue(FreecamManager.shouldHoverBody(player));
        when(player.isFlying()).thenReturn(false);
        assertFalse(FreecamManager.shouldHoverBody(player));
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(player.isFlying()).thenReturn(true);
        assertFalse(FreecamManager.shouldHoverBody(player));
    }

    @Test
    void hoveringBodyKeepsGravityOffAcrossTicksAndWaterContact() throws Exception {
        installSession(true);
        when(player.getGameMode()).thenReturn(GameMode.CREATIVE);
        when(player.isFlying()).thenReturn(true);
        when(body.isOnGround()).thenReturn(false);
        when(body.isInWaterOrBubbleColumn()).thenReturn(true);
        for (int i = 0; i < 3; i++) environment.run();
        verify(body, times(3)).setGravity(false);
        verify(body, times(3)).setImmovable(true);
        verify(body, times(3)).setVelocity(new Vector());
        verify(body, never()).setGravity(true);
        assertEquals(bodyLocation, session.anchor());
    }

    @Test
    void cameraFlightDoesNotMakeNonFlyingCreativeBodyHover() {
        when(player.getGameMode()).thenReturn(GameMode.CREATIVE);
        when(player.isFlying()).thenReturn(true);
        // Session was captured before camera flight: bodyHovering remains false.
        environment.run();
        verify(body).setGravity(true);
        verify(body).setImmovable(false);
    }

    @Test
    void hoveringExitRestoresCreativeFlightWithoutCameraMomentum() throws Exception {
        session = new FreecamSession(bodyLocation.clone(), body.getUniqueId(), true, true,
                false, true, false, true, false, true, 300, 0, true, 0, 0);
        state("sessions").put(player.getUniqueId(), session);
        when(body.getVelocity()).thenReturn(new Vector(0, -0.8, 0));
        when(body.getFallDistance()).thenReturn(10f);
        manager.stop(player, true, false);
        verify(player).setAllowFlight(true);
        verify(player).setFlying(true);
        verify(player).setVelocity(new Vector());
        verify(player, never()).setVelocity(new Vector(0, -0.8, 0));
        verify(player, never()).setFallDistance(10f);
    }

    private Spider nearbySpider(LivingEntity initialTarget) {
        Spider spider = mock(Spider.class);
        when(spider.getUniqueId()).thenReturn(UUID.randomUUID());
        when(spider.isValid()).thenReturn(true);
        when(spider.isAware()).thenReturn(true);
        when(spider.getLocation()).thenAnswer(call -> bodyLocation.clone().add(2, 0, 0));
        var target = new java.util.concurrent.atomic.AtomicReference<>(initialTarget);
        when(spider.getTarget()).thenAnswer(call -> target.get());
        doAnswer(call -> { target.set(call.getArgument(0)); return null; }).when(spider).setTarget(any());
        when(body.getNearbyEntities(anyDouble(), anyDouble(), anyDouble())).thenReturn(List.of(spider));
        when(player.getNearbyEntities(anyDouble(), anyDouble(), anyDouble())).thenReturn(List.of(spider));
        when(server.getEntity(spider.getUniqueId())).thenReturn(spider);
        return spider;
    }

    @Test
    void hostileSpiderReacquiresBodyAfterLosingProxyTarget() {
        Spider spider = nearbySpider(player);
        aggro.run();
        assertSame(body, spider.getTarget());
        spider.setTarget(null);
        aggro.run();
        assertSame(body, spider.getTarget());
        spider.setTarget(null);
        aggro.run();
        assertSame(body, spider.getTarget());
    }

    @Test
    void startingSpiderAggroSurvivesLossBeforeFirstMaintenanceTick() throws Exception {
        Spider spider = nearbySpider(player);
        var method = FreecamManager.class.getDeclaredMethod("retargetCurrentEnemies", Player.class, Mannequin.class);
        method.setAccessible(true);
        method.invoke(manager, player, body);
        spider.setTarget(null);
        aggro.run();
        assertSame(body, spider.getTarget());
    }

    @Test
    void targetingEventRecordsNewlyHostileSpiderWithoutAngeringNeutralSpiders() {
        Spider spider = nearbySpider(null);
        aggro.run();
        verify(spider, never()).setTarget(any());
        EntityTargetLivingEntityEvent event = mock(EntityTargetLivingEntityEvent.class);
        when(event.getEntity()).thenReturn(spider);
        when(event.getTarget()).thenReturn(body);
        listener.onTargetResult(event);
        aggro.run();
        assertSame(body, spider.getTarget());
    }

    @Test
    void spiderThatSwitchesToAnotherPlayerIsNotStolenBack() {
        Spider spider = nearbySpider(player);
        aggro.run();
        Player other = mock(Player.class);
        when(other.getUniqueId()).thenReturn(UUID.randomUUID());
        spider.setTarget(other);
        aggro.run();
        assertSame(other, spider.getTarget());
        spider.setTarget(null);
        aggro.run();
        assertNull(spider.getTarget());
    }

    @Test
    void spiderMemoryExpiresOutsideBodyScanRange() {
        Spider spider = nearbySpider(player);
        aggro.run();
        when(body.getNearbyEntities(anyDouble(), anyDouble(), anyDouble())).thenReturn(List.of());
        aggro.run();
        when(body.getNearbyEntities(anyDouble(), anyDouble(), anyDouble())).thenReturn(List.of(spider));
        spider.setTarget(null);
        aggro.run();
        assertNull(spider.getTarget());
    }

    @Test
    void creativeAndDisabledAggroDoNotRestoreRememberedSpider() {
        Spider spider = nearbySpider(player);
        aggro.run();
        when(player.getGameMode()).thenReturn(GameMode.CREATIVE);
        aggro.run();
        assertNull(spider.getTarget());
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        aggro.run();
        assertNull(spider.getTarget());
        spider.setTarget(player);
        aggro.run();
        config.set("force-hostile-mob-aggro", false);
        aggro.run();
        config.set("force-hostile-mob-aggro", true);
        spider.setTarget(null);
        aggro.run();
        assertNull(spider.getTarget());
    }

    @Test
    void returningFromFreecamTransfersSpiderBackToPlayer() throws Exception {
        Spider spider = nearbySpider(player);
        aggro.run();
        manager.stop(player, true, false);
        assertSame(player, spider.getTarget());
        assertTrue(state("hostileSpiderOwners").isEmpty());
    }

    @Test
    void externalTeleportForgetsSpiderWithoutTargetingCameraLocation() throws Exception {
        Spider spider = nearbySpider(player);
        aggro.run();
        manager.stopWithoutReturn(player, false);
        verify(spider, never()).setTarget(player);
        assertTrue(state("hostileSpiderOwners").isEmpty());
    }

    @Test
    void carrierDismountsPassengerBeforeEnablingFreecam() throws Exception {
        state("sessions").clear();
        state("bodyOwners").clear();
        config.set("leave-body-at-anchor", false);
        Player passenger = mock(Player.class);
        var passengers = new ArrayList<org.bukkit.entity.Entity>(List.of(passenger));
        when(player.getPassengers()).thenReturn(passengers);
        when(messages.text(eq("freecam-enabled"), eq("range"), anyString())).thenReturn("enabled");
        doAnswer(call -> {
            assertFalse(manager.isActive(player));
            assertEquals(bodyLocation, player.getLocation());
            verify(player, never()).setFlying(true);
            passengers.clear();
            return true;
        }).when(player).eject();
        assertTrue(manager.start(player));
        verify(player).eject();
        assertTrue(manager.isActive(player));
        verify(player).setFlying(true);
        verify(passenger, never()).teleport(any(Location.class), any(PlayerTeleportEvent.TeleportCause.class));
    }

    @Test
    void failedPassengerEjectionRefusesToStartCamera() throws Exception {
        state("sessions").clear();
        when(player.getPassengers()).thenReturn(List.of(mock(Player.class)));
        when(player.eject()).thenReturn(false);
        assertFalse(manager.start(player));
        assertFalse(manager.isActive(player));
        verify(player, never()).setFlying(true);
        verify(player, never()).setInvisible(true);
    }

    @Test
    void riderStillCannotStartFreecam() throws Exception {
        state("sessions").clear();
        when(player.isInsideVehicle()).thenReturn(true);
        assertFalse(manager.start(player));
        verify(player, never()).eject();
        verify(player, never()).setFlying(true);
    }

    @Test
    void activeCameraCannotRideNormalPlayerOrSeat() {
        EntityMountEvent event = new EntityMountEvent(player, mock(Player.class));
        listener.onMount(event);
        assertTrue(event.isCancelled());
        assertTrue(manager.isActive(player));
    }

    @Test
    void normalPlayerCannotMountCameraOrBody() {
        Player passenger = mock(Player.class);
        when(passenger.getUniqueId()).thenReturn(UUID.randomUUID());
        for (org.bukkit.entity.Entity mount : new org.bukkit.entity.Entity[]{player, body}) {
            EntityMountEvent event = new EntityMountEvent(passenger, mount);
            listener.onMount(event);
            assertTrue(event.isCancelled());
        }
    }

    @Test
    void nestedSeatOnCameraCannotBeMounted() {
        org.bukkit.entity.Entity seat = mock(org.bukkit.entity.Entity.class);
        when(seat.getUniqueId()).thenReturn(UUID.randomUUID());
        when(seat.getVehicle()).thenReturn(player);
        Player passenger = mock(Player.class);
        when(passenger.getUniqueId()).thenReturn(UUID.randomUUID());
        EntityMountEvent event = new EntityMountEvent(passenger, seat);
        listener.onMount(event);
        assertTrue(event.isCancelled());
    }

    @Test
    void normalRidingRemainsAllowed() {
        EntityMountEvent event = new EntityMountEvent(mock(Player.class), mock(Player.class));
        listener.onMount(event);
        assertFalse(event.isCancelled());
    }

    @Test
    void clickingCameraIsCancelledBeforeRidePluginsRun() {
        Player passenger = mock(Player.class);
        when(passenger.getUniqueId()).thenReturn(UUID.randomUUID());
        PlayerInteractEntityEvent click = new PlayerInteractEntityEvent(passenger, player);
        listener.onInteractEntity(click);
        assertTrue(click.isCancelled());
        PlayerInteractAtEntityEvent clickAt = new PlayerInteractAtEntityEvent(passenger, player, new Vector());
        listener.onInteractAtEntity(clickAt);
        assertTrue(clickAt.isCancelled());
        assertTrue(manager.isActive(player));
    }

    @Test
    void activeCameraCannotStartRideByClickingAnotherPlayer() {
        PlayerInteractEntityEvent event = new PlayerInteractEntityEvent(player, mock(Player.class));
        listener.onInteractEntity(event);
        assertTrue(event.isCancelled());
        assertTrue(manager.isActive(player));
    }

    @Test
    void normalPlayerInteractionRemainsAllowed() {
        Player actor = mock(Player.class);
        when(actor.getUniqueId()).thenReturn(UUID.randomUUID());
        PlayerInteractEntityEvent event = new PlayerInteractEntityEvent(actor, mock(Player.class));
        listener.onInteractEntity(event);
        assertFalse(event.isCancelled());
    }

    @ParameterizedTest
    @EnumSource(value = EquipmentSlot.class, names = {"HAND", "OFF_HAND"})
    void preCancelledAirClickStillDeniesWindChargeUseInBothHands(EquipmentSlot hand) {
        ItemStack charge = mock(ItemStack.class);
        when(charge.getType()).thenReturn(Material.WIND_CHARGE);
        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_AIR, charge,
                null, BlockFace.SELF, hand);
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DEFAULT);
        assertTrue(event.isCancelled());
        listener.onInteract(event);
        assertEquals(Event.Result.DENY, event.useItemInHand());
        assertEquals(Event.Result.DENY, event.useInteractedBlock());
    }

    @Test
    void itemUseIsDeniedEarlyAndAgainAfterAnotherHandlerAllowsIt() {
        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK,
                mock(ItemStack.class), block, BlockFace.UP, EquipmentSlot.HAND);
        listener.onInteractEarly(event);
        assertEquals(Event.Result.DENY, event.useItemInHand());
        event.setUseItemInHand(Event.Result.ALLOW);
        listener.onInteract(event);
        assertEquals(Event.Result.DENY, event.useItemInHand());
    }

    @Test
    void playerLaunchPreventsProjectileAndItemConsumption() {
        PlayerLaunchProjectileEvent event = new PlayerLaunchProjectileEvent(player, mock(ItemStack.class),
                mock(org.bukkit.entity.WindCharge.class));
        listener.onPlayerLaunch(event);
        assertTrue(event.isCancelled());
        assertFalse(event.shouldConsume());
    }

    @Test
    void genericProjectileLaunchFromActiveCameraIsCancelled() {
        org.bukkit.entity.WindCharge charge = mock(org.bukkit.entity.WindCharge.class);
        when(charge.getShooter()).thenReturn(player);
        ProjectileLaunchEvent event = new ProjectileLaunchEvent(charge);
        listener.onProjectileLaunch(event);
        assertTrue(event.isCancelled());
    }

    @Test
    void bowUseCannotConsumeAmmoOrLaunchDuringFreecam() {
        EntityShootBowEvent event = new EntityShootBowEvent(player, mock(ItemStack.class), mock(ItemStack.class),
                mock(org.bukkit.entity.Projectile.class), EquipmentSlot.HAND, 1f, true);
        listener.onShootBow(event);
        assertTrue(event.isCancelled());
        assertFalse(event.shouldConsumeItem());
    }

    @Test
    void normalPlayerCanStillUseItemsAndLaunchProjectiles() {
        Player normal = mock(Player.class);
        when(normal.getUniqueId()).thenReturn(UUID.randomUUID());
        PlayerInteractEvent click = new PlayerInteractEvent(normal, Action.RIGHT_CLICK_AIR,
                mock(ItemStack.class), null, BlockFace.SELF, EquipmentSlot.HAND);
        Event.Result original = click.useItemInHand();
        listener.onInteract(click);
        assertEquals(original, click.useItemInHand());
        org.bukkit.entity.WindCharge charge = mock(org.bukkit.entity.WindCharge.class);
        when(charge.getShooter()).thenReturn(normal);
        ProjectileLaunchEvent launch = new ProjectileLaunchEvent(charge);
        listener.onProjectileLaunch(launch);
        assertFalse(launch.isCancelled());
    }

    @Test
    void usingItemStateIsClearedWhileFreecamContinues() {
        when(player.hasActiveItem()).thenReturn(true);
        environment.run();
        verify(player).clearActiveItem();
    }

    private ItemStack stack(int amount) {
        ItemStack result = mock(ItemStack.class);
        var count = new java.util.concurrent.atomic.AtomicInteger(amount);
        when(result.getAmount()).thenAnswer(call -> count.get());
        doAnswer(call -> { count.set(call.getArgument(0)); return null; }).when(result).setAmount(anyInt());
        when(result.getMaxStackSize()).thenReturn(64);
        when(result.isEmpty()).thenAnswer(call -> count.get() == 0);
        when(result.isSimilar(any(ItemStack.class))).thenReturn(true);
        when(result.clone()).thenAnswer(call -> stack(count.get()));
        return result;
    }

    private Item bodyDrop(int amount) {
        Item drop = mock(Item.class);
        when(drop.getUniqueId()).thenReturn(UUID.randomUUID());
        when(drop.isValid()).thenReturn(true);
        doAnswer(call -> { when(drop.isValid()).thenReturn(false); return null; }).when(drop).remove();
        when(drop.canPlayerPickup()).thenReturn(true);
        ItemStack contents = stack(amount);
        when(drop.getItemStack()).thenReturn(contents);
        when(body.getNearbyEntities(anyDouble(), anyDouble(), anyDouble())).thenReturn(List.of(drop));
        when(player.getCanPickupItems()).thenReturn(true);
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getMaxStackSize()).thenReturn(64);
        when(inventory.getStorageContents()).thenReturn(new ItemStack[36]);
        when(inventory.addItem(any(ItemStack.class))).thenReturn(new java.util.HashMap<>());
        org.bukkit.plugin.PluginManager pluginManager = mock(org.bukkit.plugin.PluginManager.class);
        when(server.getPluginManager()).thenReturn(pluginManager);
        doAnswer(call -> {
            Event event = call.getArgument(0);
            if (event instanceof PlayerAttemptPickupItemEvent attempt) listener.onAttemptPickup(attempt);
            if (event instanceof EntityPickupItemEvent pickup) listener.onPickup(pickup);
            return null;
        }).when(pluginManager).callEvent(any(Event.class));
        return drop;
    }

    @Test
    void itemsAtBodyGoIntoRealInventoryWhileCameraIsFarAway() {
        Item drop = bodyDrop(7);
        camera.setX(12);
        manager.collectBodyItems(player, body);
        verify(player.getInventory()).addItem(argThat((ItemStack value) -> value.getAmount() == 7));
        verify(drop).remove();
        verify(body).playPickupItemAnimation(drop, 7);
        verify(player, never()).teleport(any(Location.class), any(PlayerTeleportEvent.TeleportCause.class));
        assertFalse(manager.isBodyPickup(player, drop));
    }

    @Test
    void pickupCanBeCancelledByOtherPluginsWithoutRemovingItem() {
        Item drop = bodyDrop(5);
        var pluginManager = server.getPluginManager();
        doAnswer(call -> {
            Event event = call.getArgument(0);
            if (event instanceof EntityPickupItemEvent pickup) pickup.setCancelled(true);
            return null;
        }).when(pluginManager).callEvent(any(Event.class));
        manager.collectBodyItems(player, body);
        verify(player.getInventory(), never()).addItem(any(ItemStack.class));
        verify(drop, never()).remove();
        assertFalse(manager.isBodyPickup(player, drop));
    }

    @Test
    void fullInventoryDoesNotDeleteOrChangeDrop() {
        Item drop = bodyDrop(5);
        ItemStack[] full = new ItemStack[36];
        java.util.Arrays.fill(full, stack(64));
        when(player.getInventory().getStorageContents()).thenReturn(full);
        manager.collectBodyItems(player, body);
        verify(player.getInventory(), never()).addItem(any(ItemStack.class));
        verify(drop, never()).remove();
        verify(drop, never()).setItemStack(any());
    }

    @Test
    void partialPickupLeavesExactRemainderInWorld() {
        Item drop = bodyDrop(9);
        ItemStack remainder = stack(4);
        when(player.getInventory().addItem(any(ItemStack.class))).thenReturn(new java.util.HashMap<>(Map.of(0, remainder)));
        manager.collectBodyItems(player, body);
        verify(drop).setItemStack(argThat((ItemStack value) -> value.getAmount() == 4));
        verify(drop, never()).remove();
        verify(body).playPickupItemAnimation(drop, 5);
    }

    @Test
    void pickupDelayOwnershipAndPlayerPickupFlagAreRespected() {
        Item drop = bodyDrop(2);
        when(drop.getPickupDelay()).thenReturn(20);
        manager.collectBodyItems(player, body);
        when(drop.getPickupDelay()).thenReturn(0);
        when(drop.getOwner()).thenReturn(UUID.randomUUID());
        manager.collectBodyItems(player, body);
        UUID owner = player.getUniqueId();
        when(drop.getOwner()).thenReturn(owner);
        when(drop.canPlayerPickup()).thenReturn(false);
        manager.collectBodyItems(player, body);
        verify(player.getInventory(), never()).addItem(any(ItemStack.class));
        verify(drop, never()).remove();
    }

    @Test
    void nativeCameraPickupIsBlockedOutsideBodyPickupContext() {
        Item drop = bodyDrop(1);
        PlayerAttemptPickupItemEvent attempt = new PlayerAttemptPickupItemEvent(player, drop, 0);
        listener.onAttemptPickup(attempt);
        assertTrue(attempt.isCancelled());
        assertFalse(attempt.getFlyAtPlayer());
        EntityPickupItemEvent pickup = new EntityPickupItemEvent(player, drop, 0);
        listener.onPickup(pickup);
        assertTrue(pickup.isCancelled());
    }

    @Test
    void endingSessionFromPickupCallbackDoesNotCollectOrLeakContext() {
        Item drop = bodyDrop(3);
        var pluginManager = server.getPluginManager();
        doAnswer(call -> { manager.stopWithoutReturn(player, false); return null; })
                .when(pluginManager).callEvent(any(Event.class));
        manager.collectBodyItems(player, body);
        verify(player.getInventory(), never()).addItem(any(ItemStack.class));
        verify(drop, never()).remove();
        assertFalse(manager.isBodyPickup(player, drop));
    }

    @Test
    void removedDropCannotBeCollectedAgainOnNextScan() {
        Item drop = bodyDrop(3);
        manager.collectBodyItems(player, body);
        manager.collectBodyItems(player, body);
        verify(player.getInventory(), times(1)).addItem(any(ItemStack.class));
        verify(drop, times(1)).remove();
    }

    @Test
    void pickupCallbackCannotReenterCollectorOrAllowUnrelatedCameraPickup() {
        Item drop = bodyDrop(3);
        Item unrelated = mock(Item.class);
        when(unrelated.getUniqueId()).thenReturn(UUID.randomUUID());
        var pluginManager = server.getPluginManager();
        doAnswer(call -> {
            manager.collectBodyItems(player, body);
            EntityPickupItemEvent remotePickup = new EntityPickupItemEvent(player, unrelated, 0);
            listener.onPickup(remotePickup);
            assertTrue(remotePickup.isCancelled());
            return null;
        }).when(pluginManager).callEvent(any(Event.class));
        manager.collectBodyItems(player, body);
        verify(player.getInventory(), times(1)).addItem(any(ItemStack.class));
        verify(drop, times(1)).remove();
    }

    @Test
    void cameraIgnitionIsCancelledButPhysicalBodyIgnitionIsAllowed() {
        EntityCombustEvent cameraFire = new EntityCombustEvent(player, 8f);
        listener.onCombust(cameraFire);
        assertTrue(cameraFire.isCancelled());
        EntityCombustEvent bodyFire = new EntityCombustEvent(body, 8f);
        listener.onCombust(bodyFire);
        assertFalse(bodyFire.isCancelled());
    }

    @Test
    void lavaBurningOnCameraDoesNotSurviveReturnToDryBody() {
        when(player.getFireTicks()).thenReturn(200);
        when(player.getFreezeTicks()).thenReturn(80);
        when(body.getFireTicks()).thenReturn(0);
        when(body.getFreezeTicks()).thenReturn(0);
        manager.stop(player, true, false);
        verify(player).setFireTicks(0);
        verify(player).setFreezeTicks(0);
        verify(plugin).restoreOwnerPets(player.getUniqueId());
    }

    @Test
    void legitimateBodyFireAndFreezingAreRestoredEvenOnExternalTeleport() {
        when(body.getFireTicks()).thenReturn(90);
        when(body.getFreezeTicks()).thenReturn(35);
        manager.stopWithoutReturn(player, false);
        verify(player).setFireTicks(90);
        verify(player).setFreezeTicks(35);
        verify(plugin).restoreOwnerPets(player.getUniqueId());
    }

    @Test
    void environmentClearsCameraFireAndFreezeWithoutClearingBody() {
        environment.run();
        verify(player).setFireTicks(0);
        verify(player).setFreezeTicks(0);
        verify(body, never()).setFireTicks(anyInt());
        verify(body, never()).setFreezeTicks(anyInt());
    }

    @ParameterizedTest
    @EnumSource(value = PlayerTeleportEvent.TeleportCause.class, names = {"NETHER_PORTAL", "END_PORTAL", "END_GATEWAY"})
    void cameraPortalTeleportsAreDeniedWithoutEndingSession(PlayerTeleportEvent.TeleportCause cause) {
        PlayerTeleportEvent event = teleport(new Location(mock(World.class), 0, 70, 0), cause);
        listener.onTeleport(event);
        assertTrue(event.isCancelled());
        listener.onTeleportResult(event);
        assertTrue(manager.isActive(player));
        verify(body, never()).remove();
    }

    @Test
    void portalEntryAndPortalEventAreBothBlocked() {
        EntityPortalEnterEvent enter = new EntityPortalEnterEvent(player, camera.clone());
        listener.onPortalEnter(enter);
        assertTrue(enter.isCancelled());
        PlayerPortalEvent portal = new PlayerPortalEvent(player, camera.clone(), new Location(mock(World.class), 0, 64, 0));
        listener.onPortal(portal);
        assertTrue(portal.isCancelled());
        assertTrue(manager.isActive(player));
    }

    @Test
    void normalPlayersCanStillIgniteAndUsePortals() {
        Player normal = mock(Player.class);
        when(normal.getUniqueId()).thenReturn(UUID.randomUUID());
        EntityCombustEvent fire = new EntityCombustEvent(normal, 8f);
        listener.onCombust(fire);
        assertFalse(fire.isCancelled());
        EntityPortalEnterEvent enter = new EntityPortalEnterEvent(normal, camera.clone());
        listener.onPortalEnter(enter);
        assertFalse(enter.isCancelled());
        PlayerPortalEvent portal = new PlayerPortalEvent(normal, camera.clone(), new Location(mock(World.class), 0, 64, 0));
        listener.onPortal(portal);
        assertFalse(portal.isCancelled());
    }

    @Test
    void ownedPetsTeleportIsStoppedButOtherEntitiesAreUnaffected() {
        org.bukkit.entity.Wolf wolf = mock(org.bukkit.entity.Wolf.class);
        when(plugin.pausePetIfOwnerFreecam(wolf)).thenReturn(true);
        EntityTeleportEvent petTeleport = new EntityTeleportEvent(wolf, bodyLocation.clone(), camera.clone());
        listener.onPetTeleport(petTeleport);
        assertTrue(petTeleport.isCancelled());
        EntityTeleportEvent normal = new EntityTeleportEvent(mock(org.bukkit.entity.Entity.class), bodyLocation.clone(), camera.clone());
        listener.onPetTeleport(normal);
        assertFalse(normal.isCancelled());
    }
}
