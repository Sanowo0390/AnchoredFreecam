package io.github.sanowo0390.anchoredfreecam;

import com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Event.Result;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityAirChangeEvent;
import org.bukkit.event.entity.EntityToggleSwimEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityPortalEnterEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import io.papermc.paper.event.entity.EntityPortalReadyEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.event.world.GenericGameEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerAttemptPickupItemEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerRiptideEvent;
import org.bukkit.event.player.PlayerBucketEntityEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;

final class FreecamListener implements Listener {
    private final AnchoredFreecamPlugin plugin;
    private final FreecamManager manager;

    FreecamListener(AnchoredFreecamPlugin plugin, FreecamManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        FreecamSession session = manager.getSession(player);
        if (session == null || event instanceof PlayerTeleportEvent) {
            return;
        }

        Location to = event.getTo();
        if (to == null || to.getWorld() != session.anchor().getWorld()) {
            return;
        }

        Location from = event.getFrom();
        Input input = player.getCurrentInput();

        // A detached camera must not inherit passive water/bubble-column lift.
        // Keep deliberate jump/sneak vertical control, but pin passive Y movement.
        boolean cameraInWater = WaterContact.touches(player) || WaterContact.at(to);
        boolean correctedWaterMove = false;
        if (cameraInWater && !WaterContact.horizontalInput(input)
                && (Math.abs(to.getX() - from.getX()) > 1.0E-5D || Math.abs(to.getZ() - from.getZ()) > 1.0E-5D)) {
            to = to.clone();
            to.setX(from.getX());
            to.setZ(from.getZ());
            correctedWaterMove = true;
        }
        if (cameraInWater && !input.isJump() && !input.isSneak()
                && Math.abs(to.getY() - from.getY()) > 1.0E-5D) {
            Location corrected = to.clone();
            corrected.setY(from.getY());
            to = corrected;
            correctedWaterMove = true;
        }

        if (manager.isTownyBlocked(player, to)) {
            event.setCancelled(true);
            manager.townyNotice(player);
            manager.queueBoundaryReturn(player, to.getYaw(), to.getPitch());
            return;
        }

        double max = manager.getMaxDistance();
        if (to.distanceSquared(session.anchor()) <= max * max) {
            if (correctedWaterMove) {
                // Do not setTo(): a server-generated PLUGIN teleport loses our
                // ownership marker. Apply this correction explicitly next tick.
                event.setCancelled(true);
                manager.queueCameraCorrection(player, to);
                manager.trace(player, "water-move-correction");
            }
            return;
        }

        // Never end freecam just because the player crossed the radius.
        // Cancel the illegal move, then hard-correct the client/server position
        // to the last valid location on the next tick.
        event.setCancelled(true);
        manager.boundaryNotice(player);
        manager.queueBoundaryReturn(player, to.getYaw(), to.getPitch());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        Location to = event.getTo();
        if (to == null || manager.isInternalTeleport(player)) {
            return;
        }
        if (manager.isActive(player) && isPortalTeleport(event)) {
            event.setCancelled(true);
            manager.trace(player, "camera-portal-blocked");
            return;
        }

        // Generic TPA / teleport-plugin support:
        // if another player is being teleported to a freecam camera position,
        // rewrite the destination to the anchored body instead.
        if (isExternalTeleport(event)) {
            Location redirected = manager.redirectedTeleportDestination(player, to);
            if (redirected != null) {
                event.setTo(redirected);
            }
            return;
        }

        FreecamSession session = manager.getSession(player);
        if (session == null) {
            return;
        }

        // Cross-world teleports are finalized at MONITOR, after cancellation.
        if (to.getWorld() != session.anchor().getWorld()) {
            return;
        }

        if (manager.isTownyBlocked(player, to)) {
            event.setCancelled(true);
            manager.townyNotice(player);
            manager.queueBoundaryReturn(player, to.getYaw(), to.getPitch());
            return;
        }

        double max = manager.getMaxDistance();
        if (to.distanceSquared(session.anchor()) <= max * max) {
            return;
        }

        // Plugins, anti-cheat corrections, commands, etc. must not be able to
        // place the camera outside the configured radius while freecam is active.
        event.setCancelled(true);
        manager.boundaryNotice(player);
        manager.queueBoundaryReturn(player, to.getYaw(), to.getPitch());
    }

    private boolean isExternalTeleport(PlayerTeleportEvent event) {
        return event.getCause() == PlayerTeleportEvent.TeleportCause.PLUGIN
                || event.getCause() == PlayerTeleportEvent.TeleportCause.COMMAND;
    }

    private boolean isPortalTeleport(PlayerTeleportEvent event) {
        return event instanceof PlayerPortalEvent
                || event.getCause() == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL
                || event.getCause() == PlayerTeleportEvent.TeleportCause.END_PORTAL
                || event.getCause() == PlayerTeleportEvent.TeleportCause.END_GATEWAY;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        if (manager.isActive(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPortalEnter(EntityPortalEnterEvent event) {
        if (manager.isFreecamPortalEntity(event.getEntity())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPortalReady(EntityPortalReadyEvent event) {
        if (manager.isFreecamPortalEntity(event.getEntity())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBodyPortal(EntityPortalEvent event) {
        // Mannequins have their own portal processing and do not fire PlayerPortalEvent.
        // Never allow the physical anchor to change dimensions through a portal.
        if (manager.isFreecamPortalEntity(event.getEntity())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCombust(EntityCombustEvent event) {
        if (event.getEntity() instanceof Player player && manager.isActive(player)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCameraGameEvent(GenericGameEvent event) {
        if (event.getEntity() instanceof Player player && manager.isActive(player)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPetTeleport(EntityTeleportEvent event) {
        if (plugin.pausePetIfOwnerFreecam(event.getEntity())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPetChunkLoad(EntitiesLoadEvent event) {
        for (org.bukkit.entity.Entity entity : event.getEntities()) plugin.pausePetIfOwnerFreecam(entity);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPetChunkUnload(EntitiesUnloadEvent event) {
        plugin.releasePetsBeforeUnload(event.getEntities());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTeleportResult(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        FreecamSession session = manager.getSession(player);
        if (session == null) return;
        manager.trace(player, "teleport cause=" + event.getCause()
                + " internal=" + manager.isInternalTeleport(player)
                + " cancelled=" + event.isCancelled()
                + " from=" + event.getFrom() + " to=" + event.getTo());
        if (event.isCancelled() || manager.isInternalTeleport(player) || event.getTo() == null) return;
        if (isExternalTeleport(event) || event.getTo().getWorld() != session.anchor().getWorld()) {
            manager.stopWithoutReturn(player, true);
        } else {
            manager.updateLastLegalLocation(player, event.getTo());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMoveResult(PlayerMoveEvent event) {
        if (!(event instanceof PlayerTeleportEvent) && event.getTo() != null) {
            manager.updateLastLegalLocation(event.getPlayer(), event.getTo());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAirChange(EntityAirChangeEvent event) {
        if (event.getEntity() instanceof Player player && manager.isActive(player)
                && !manager.isSynchronizingAir(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSwim(EntityToggleSwimEvent event) {
        if (event.isSwimming() && event.getEntity() instanceof Player player && manager.isActive(player)) {
            event.setCancelled(true);
            manager.trace(player, "camera-swim-cancelled");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        manager.hideActiveCamerasFrom(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        manager.trace(event.getPlayer(), "quit");
        if (manager.isActive(event.getPlayer())) {
            manager.stop(event.getPlayer(), true, false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKick(PlayerKickEvent event) {
        manager.trace(event.getPlayer(), "kick");
        if (manager.isActive(event.getPlayer())) {
            manager.stop(event.getPlayer(), true, false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        manager.trace(event.getPlayer(), "game-mode-change to=" + event.getNewGameMode());
        if (manager.isActive(event.getPlayer())) {
            manager.stop(event.getPlayer(), true, true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVelocity(PlayerVelocityEvent event) {
        Player player = event.getPlayer();
        if (!manager.isActive(player)) {
            return;
        }

        boolean cameraTouchingWater = WaterContact.touches(player);
        if (!cameraTouchingWater) {
            return;
        }

        Vector velocity = WaterContact.cameraVelocity(player.getCurrentInput(), event.getVelocity());
        if (!velocity.equals(event.getVelocity())) {
            event.setVelocity(velocity);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        if (!manager.isActive(event.getPlayer()) || event.isFlying()) {
            return;
        }

        event.setCancelled(true);
        manager.trace(event.getPlayer(), "flight-disable-cancelled");
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Player player = event.getPlayer();
            if (player.isOnline() && manager.isActive(player)) {
                player.setAllowFlight(true);
                player.setFlying(true);
            }
        });
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onForwardedDamage(EntityDamageEvent event) {
        manager.captureForwardedDamage(event);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onForwardedDamageResult(EntityDamageEvent event) {
        manager.finishForwardedDamage(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        // EntityDamageByEntityEvent also reaches this handler. Reject camera
        // attacks before body damage can remove the victim's session, regardless
        // of handler registration order or another plugin uncancelling the hit.
        if (event instanceof EntityDamageByEntityEvent hit && isFreecamActor(hit.getDamager())) {
            event.setCancelled(true);
            return;
        }
        if (manager.handleBodyDamage(event)) {
            return;
        }

        if (event.getEntity() instanceof Player player
                && manager.isActive(player)
                && !manager.isForwardedBodyDamage(player)
                && plugin.getConfig().getBoolean("protect-camera-player", true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (isFreecamActor(event.getDamager())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (manager.isProtectedMobTarget(event.getTarget())) {
            event.setTarget(null);
            return;
        }
        if (!(event.getTarget() instanceof Player player) || !manager.isActive(player)) {
            return;
        }

        Mannequin body = manager.getBody(player);
        if (body != null && body.isValid()) {
            event.setTarget(body);
        } else {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTargetResult(EntityTargetLivingEntityEvent event) {
        manager.rememberMobTarget(event.getEntity(), event.getTarget());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (manager.isActive(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (manager.isActive(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteractEarly(PlayerInteractEvent event) {
        onInteract(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (manager.isActive(event.getPlayer())) {
            // Air interaction may already deny block use while item use is still
            // DEFAULT. Never skip that event: explicitly deny both result fields.
            event.setUseInteractedBlock(Result.DENY);
            event.setUseItemInHand(Result.DENY);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerLaunch(PlayerLaunchProjectileEvent event) {
        if (manager.isActive(event.getPlayer())) {
            event.setShouldConsume(false);
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (isFreecamActor(event.getEntity())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onShootBow(EntityShootBowEvent event) {
        if (event.getEntity() instanceof Player player && manager.isActive(player)) {
            event.setConsumeItem(false);
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (manager.isActive(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRiptide(PlayerRiptideEvent event) {
        if (manager.isActive(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketEntity(PlayerBucketEntityEvent event) {
        if (manager.isActive(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemDamage(PlayerItemDamageEvent event) {
        if (manager.isActive(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        if (!manager.isActive(player)) {
            if (manager.isFreecamVehicle(event.getRightClicked())) event.setCancelled(true);
            return;
        }

        event.setCancelled(true);
        if (manager.isOwnBody(player, event.getRightClicked())) {
            manager.trace(player, "own-body-right-click");
            manager.stop(player, true, true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInteractAtEntity(PlayerInteractAtEntityEvent event) {
        Player player = event.getPlayer();
        if (!manager.isActive(player)) {
            if (manager.isFreecamVehicle(event.getRightClicked())) event.setCancelled(true);
            return;
        }

        event.setCancelled(true);
        if (manager.isOwnBody(player, event.getRightClicked())) {
            manager.trace(player, "own-body-right-click-at");
            manager.stop(player, true, true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityInteractionResult(PlayerInteractEntityEvent event) {
        enforceEntityInteractionResult(event);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreciseEntityInteractionResult(PlayerInteractAtEntityEvent event) {
        enforceEntityInteractionResult(event);
    }

    private void enforceEntityInteractionResult(PlayerInteractEntityEvent event) {
        // MyPet 3.14.3 PlayerListener uncancels owner interactions at MONITOR.
        // softdepend loads MyPet first, so our final guard runs after that handler
        // and prevents its native equipment/feed/shear interaction from executing.
        if (manager.isActive(event.getPlayer()) || manager.isFreecamVehicle(event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMount(EntityMountEvent event) {
        if (manager.isFreecamVehicle(event.getEntity()) || manager.isFreecamVehicle(event.getMount())) {
            // Covers command-driven mounts as well as normal interaction, and
            // seat entities already attached to a freecam carrier/body.
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (manager.isActive(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && manager.isActive(player)
                && !manager.isBodyPickup(player, event.getItem())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAttemptPickup(PlayerAttemptPickupItemEvent event) {
        if (manager.isActive(event.getPlayer()) && !manager.isBodyPickup(event.getPlayer(), event.getItem())) {
            event.setCancelled(true);
            event.setFlyAtPlayer(false);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (manager.isActive(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (manager.isActive(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (manager.isActive(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (manager.isActive(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && manager.isActive(player)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryClickEarly(InventoryClickEvent event) {
        onInventoryClick(event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryDragEarly(InventoryDragEvent event) {
        onInventoryDrag(event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryOpenEarly(InventoryOpenEvent event) {
        onInventoryOpen(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player && manager.isActive(player)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player && manager.isActive(player)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemHeld(PlayerItemHeldEvent event) {
        manager.refreshCameraEquipmentNextTick(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) manager.refreshCameraEquipmentNextTick(player);
    }

    private boolean isFreecamActor(org.bukkit.entity.Entity entity) {
        if (entity instanceof Player player) {
            return manager.isActive(player);
        }
        if (entity instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            return shooter instanceof Player player && manager.isActive(player);
        }
        return false;
    }
}
