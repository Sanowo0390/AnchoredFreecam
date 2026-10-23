package io.github.sanowo0390.anchoredfreecam;

import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.CaveSpider;
import org.bukkit.entity.AbstractCubeMob;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Creaking;
import org.bukkit.entity.Hoglin;
import org.bukkit.entity.Rabbit;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Mob;
import org.bukkit.entity.PigZombie;
import org.bukkit.entity.Piglin;
import org.bukkit.entity.PiglinBrute;
import org.bukkit.entity.Player;
import org.bukkit.entity.Spider;
import org.bukkit.entity.Warden;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerAttemptPickupItemEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class FreecamManager {
    private final AnchoredFreecamPlugin plugin;
    private final Messages messages;
    private final BodyNameplate nameplates;
    private final Map<UUID, FreecamSession> sessions = new HashMap<>();
    private final Map<UUID, UUID> bodyOwners = new HashMap<>();
    private final Map<UUID, UUID> hostileMobOwners = new HashMap<>();
    private final Map<UUID, UUID> bodyPickupItems = new HashMap<>();
    private final Set<UUID> collectingItems = new HashSet<>();
    private final Map<UUID, Location> lastLegalLocations = new HashMap<>();
    private final Set<UUID> internalTeleports = new HashSet<>();
    private final Set<UUID> forwardedBodyDamage = new HashSet<>();
    private final Map<UUID, EntityDamageEvent> forwardedDamageEvents = new HashMap<>();
    private record CameraCorrection(FreecamSession session, Location destination) {}
    private final Map<UUID, CameraCorrection> pendingCorrections = new HashMap<>();
    private final Set<UUID> synchronizingAir = new HashSet<>();
    private final Map<UUID, Long> lastMovementTrace = new HashMap<>();
    private int environmentTicks;
    private final Map<UUID, Long> lastBoundaryNotice = new HashMap<>();
    private final BukkitTask aggroTask;
    private final BukkitTask environmentTask;
    private TownyGuard towny;
    private boolean townyResolved;

    FreecamManager(AnchoredFreecamPlugin plugin, Messages messages) {
        this.plugin = plugin;
        this.messages = messages;
        this.nameplates = new BodyNameplate(plugin);
        this.aggroTask = plugin.getServer().getScheduler().runTaskTimer(
                plugin,
                this::maintainBodyAggro,
                1L,
                2L
        );
        this.environmentTask = plugin.getServer().getScheduler().runTaskTimer(
                plugin,
                this::maintainBodyEnvironment,
                1L,
                1L
        );
    }

    boolean isActive(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    FreecamSession getSession(Player player) {
        return sessions.get(player.getUniqueId());
    }

    boolean ignoresMobAggro(Player player) {
        return player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR;
    }

    static boolean shouldHoverBody(Player player) {
        return player.getGameMode() == GameMode.CREATIVE && player.isFlying();
    }

    boolean isProtectedMobTarget(LivingEntity target) {
        if (target instanceof Player player) return isActive(player) && ignoresMobAggro(player);
        if (target == null) return false;
        UUID ownerUuid = bodyOwners.get(target.getUniqueId());
        Player owner = ownerUuid == null ? null : plugin.getServer().getPlayer(ownerUuid);
        return owner != null && isActive(owner) && ignoresMobAggro(owner);
    }

    Mannequin getBody(Player player) {
        FreecamSession session = getSession(player);
        if (session == null || session.bodyUuid() == null) {
            return null;
        }
        Entity entity = plugin.getServer().getEntity(session.bodyUuid());
        return entity instanceof Mannequin mannequin ? mannequin : null;
    }

    boolean isOwnBody(Player player, Entity entity) {
        FreecamSession session = getSession(player);
        return session != null
                && session.bodyUuid() != null
                && session.bodyUuid().equals(entity.getUniqueId());
    }

    boolean isFreecamPortalEntity(Entity entity) {
        return (entity instanceof Player player && isActive(player))
                || bodyOwners.containsKey(entity.getUniqueId());
    }

    boolean isFreecamVehicle(Entity entity) {
        Set<UUID> visited = new HashSet<>();
        for (Entity current = entity; current != null && visited.add(current.getUniqueId()); current = current.getVehicle()) {
            if (current instanceof Player player && isActive(player)) return true;
            if (bodyOwners.containsKey(current.getUniqueId())) return true;
        }
        return false;
    }

    boolean prepareCarrierForFreecam(Player player) {
        if (player.isInsideVehicle()) return false;
        if (!player.getPassengers().isEmpty()) {
            // Dismount at the physical player BEFORE the session exists or the
            // camera moves. GSit can resolve the carrier's location here normally.
            player.eject();
        }
        // A cancelled eject / immediate remount must not carry anyone into the
        // detached camera. Keep the carrier physical and refuse to start instead.
        return !player.isInsideVehicle() && player.getPassengers().isEmpty();
    }

    void initTowny() {
        townyGuard();
    }

    private TownyGuard townyGuard() {
        if (!townyResolved) {
            townyResolved = true;
            towny = TownyGuard.create(plugin);
        }
        return towny;
    }

    void resetTowny() {
        townyResolved = false;
        towny = null;
    }

    boolean isTownyBlocked(Player player, Location target) {
        TownyGuard guard = townyGuard();
        return guard != null && target != null && guard.isBlocked(player, target);
    }

    private TownyGuard.TownRef manageableTown(Player player, TownyGuard guard) {
        if (guard == null) {
            player.sendMessage(message(messages.text("towny-disabled"), NamedTextColor.RED));
            return null;
        }
        TownyGuard.TownRef town = guard.townOf(player);
        if (town == null) {
            player.sendMessage(message(messages.text("towny-no-town"), NamedTextColor.RED));
            return null;
        }
        if (!guard.canManage(player)) {
            player.sendMessage(message(messages.text("towny-not-staff"), NamedTextColor.RED));
            return null;
        }
        return town;
    }

    void openTownMenu(Player player) {
        TownyGuard guard = townyGuard();
        TownyGuard.TownRef town = manageableTown(player, guard);
        if (town != null) {
            new TownSettingsMenu(guard, messages, this, town).open(player);
        }
    }

    void setTownProtection(Player player, boolean enabled) {
        TownyGuard guard = townyGuard();
        TownyGuard.TownRef town = manageableTown(player, guard);
        if (town == null) {
            return;
        }
        guard.setTownProtected(town.id(), enabled);
        player.sendMessage(message(
                messages.text(enabled ? "town-menu-changed-on" : "town-menu-changed-off", "town", town.name()),
                enabled ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
        enforceCurrentRange();
    }

    void showTownProtection(Player player) {
        TownyGuard guard = townyGuard();
        TownyGuard.TownRef town = manageableTown(player, guard);
        if (town == null) {
            return;
        }
        boolean enabled = guard.isTownProtected(town.id());
        player.sendMessage(message(
                messages.text("town-status",
                        "town", town.name(),
                        "state", messages.text(enabled ? "state-on" : "state-off")),
                enabled ? NamedTextColor.GREEN : NamedTextColor.GRAY));
    }

    double getMaxDistance() {
        if (plugin.getConfig().contains("max-distance-blocks")) {
            return Math.max(0.1D, plugin.getConfig().getDouble("max-distance-blocks", 15.0D));
        }
        return Math.max(0.1D, plugin.getConfig().getDouble("max-distance", 15.0D));
    }

    boolean start(Player player) {
        if (isActive(player)) {
            return false;
        }
        if (player.getGameMode() == GameMode.SPECTATOR) {
            player.sendMessage(message(messages.text("spectator-denied"), NamedTextColor.RED));
            return false;
        }
        if (isTownyBlocked(player, player.getLocation())) {
            player.sendMessage(message(messages.text("towny-start-denied"), NamedTextColor.RED));
            return false;
        }
        if (!prepareCarrierForFreecam(player)) {
            player.sendMessage(message(messages.text("vehicle-denied"), NamedTextColor.RED));
            return false;
        }

        Location anchor = player.getLocation().clone();
        Mannequin body = null;
        if (plugin.getConfig().getBoolean("leave-body-at-anchor", true)) {
            try {
                body = spawnBody(player, anchor);
            } catch (RuntimeException ex) {
                plugin.getLogger().severe("Failed to create freecam body for " + player.getName() + ": " + ex.getMessage());
                player.sendMessage(message(messages.text("body-spawn-failed"), NamedTextColor.RED));
                return false;
            }
        }

        UUID bodyUuid = body == null ? null : body.getUniqueId();
        FreecamSession session = new FreecamSession(
                anchor,
                bodyUuid,
                player.getAllowFlight(),
                player.isFlying(),
                player.isInvulnerable(),
                player.isCollidable(),
                player.isInvisible(),
                player.isVisibleByDefault(),
                player.isGliding(),
                player.hasGravity(),
                player.getRemainingAir(),
                player.getFallDistance(),
                shouldHoverBody(player),
                player.getFireTicks(),
                player.getFreezeTicks()
        );
        sessions.put(player.getUniqueId(), session);
        lastLegalLocations.put(player.getUniqueId(), anchor.clone());

        if (bodyUuid != null) {
            bodyOwners.put(bodyUuid, player.getUniqueId());
        }

        if (player.isGliding()) {
            player.setGliding(false);
        }
        player.setAllowFlight(true);
        player.setFlying(true);
        player.setGravity(false);
        player.setSwimming(false);
        player.clearActiveItem();
        player.setFallDistance(0.0F);
        player.setFireTicks(0);
        player.setFreezeTicks(0);
        plugin.pauseOwnerPets(player);

        // The moving real Player is only the camera. Keep it visually hidden.
        // Mob hostility is maintained against the anchored Mannequin separately.
        player.setInvisible(true);
        if (plugin.getConfig().getBoolean("hide-camera-player-from-others", true)) {
            player.setVisibleByDefault(false);
        }

        if (plugin.getConfig().getBoolean("protect-camera-player", true)) {
            player.setInvulnerable(true);
        }
        if (plugin.getConfig().getBoolean("disable-camera-entity-collision", true)) {
            player.setCollidable(false);
        }

        applyCameraVisibility(player);
        hideCameraEquipment(player);

        if (body != null) {
            retargetCurrentEnemies(player, body);
        }

        trace(player, "start");

        player.sendMessage(message(
                messages.text("freecam-enabled", "range", trimDistance(getMaxDistance())),
                NamedTextColor.GREEN));
        return true;
    }

    boolean stop(Player player, boolean returnToAnchor, boolean sendMessage) {
        trace(player, "stop return=" + returnToAnchor);
        FreecamSession session = sessions.remove(player.getUniqueId());
        clearTransientState(player.getUniqueId());

        if (session == null) {
            return false;
        }

        Mannequin body = getBody(session);
        int finalAir = getBodyAir(session);
        BodyEnvironment finalEnvironment = getBodyEnvironment(session);
        Location returnLocation = body != null && body.isValid()
                ? body.getLocation().clone()
                : session.anchor().clone();
        Vector bodyVelocity = body != null && body.isValid()
                ? body.getVelocity().clone()
                : new Vector();
        float bodyFallDistance = body != null && body.isValid()
                ? body.getFallDistance()
                : session.fallDistance();
        boolean bodyPhysical = !session.bodyHovering() && body != null && body.isValid()
                && (!body.isOnGround() || WaterContact.touches(body));

        boolean returned = false;
        if (returnToAnchor) {
            internalTeleports.add(player.getUniqueId());
            try {
                returned = player.teleport(returnLocation, PlayerTeleportEvent.TeleportCause.PLUGIN);
            } finally {
                internalTeleports.remove(player.getUniqueId());
            }
        }

        restoreState(player, session);
        // Keep the proxy alive until native Brain/anger references have been
        // rebound. Removing it first can invalidate the target and lose Warden anger.
        releaseMobTargets(player, session, returned);
        removeBody(session);
        player.setRemainingAir(finalAir);
        restoreBodyEnvironment(player, finalEnvironment);
        plugin.restoreOwnerPets(player.getUniqueId());
        if (returnToAnchor && bodyPhysical) {
            player.setFallDistance(bodyFallDistance);
            player.setVelocity(bodyVelocity);
        }
        if (returnToAnchor && session.bodyHovering()) {
            player.setFallDistance(0.0F);
            player.setVelocity(new Vector());
        }
        if (sendMessage && player.isOnline()) {
            player.sendMessage(message(
                    messages.text(returnToAnchor ? "freecam-disabled-return" : "freecam-disabled"),
                    NamedTextColor.YELLOW));
        }
        return true;
    }

    void stopWithoutReturn(Player player, boolean sendMessage) {
        trace(player, "stop external-teleport");
        FreecamSession session = sessions.remove(player.getUniqueId());
        clearTransientState(player.getUniqueId());

        if (session == null) {
            return;
        }

        syncAnchorToBody(session);
        int finalAir = getBodyAir(session);
        BodyEnvironment finalEnvironment = getBodyEnvironment(session);
        removeBody(session);
        restoreState(player, session);
        releaseMobTargets(player, session, false);
        player.setRemainingAir(finalAir);
        restoreBodyEnvironment(player, finalEnvironment);
        plugin.restoreOwnerPets(player.getUniqueId());

        if (sendMessage && player.isOnline()) {
            player.sendMessage(message(messages.text("freecam-disabled"), NamedTextColor.YELLOW));
        }
    }

    boolean isInternalTeleport(Player player) {
        return internalTeleports.contains(player.getUniqueId());
    }

    boolean isForwardedBodyDamage(Player player) {
        return forwardedBodyDamage.contains(player.getUniqueId());
    }

    void captureForwardedDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && isForwardedBodyDamage(player)) {
            forwardedDamageEvents.putIfAbsent(player.getUniqueId(), event);
        }
    }

    void finishForwardedDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)
                || forwardedDamageEvents.get(player.getUniqueId()) != event
                || event.isCancelled() || event.getFinalDamage() <= 0
                || !isActive(player) || !plugin.getConfig().getBoolean("exit-on-body-damage", true)) return;
        // MONITOR runs after protection decisions but before native damage/death.
        // Return first so a lethal hit still happens at the body, including drops.
        stop(player, true, false);
        player.sendMessage(message(messages.text("body-damaged"), NamedTextColor.RED));
    }

    void updateLastLegalLocation(Player player, Location location) {
        FreecamSession session = getSession(player);
        if (session == null || location.getWorld() != session.anchor().getWorld()) {
            return;
        }

        double max = getMaxDistance();
        if (location.distanceSquared(session.anchor()) <= max * max
                && !isTownyBlocked(player, location)) {
            lastLegalLocations.put(player.getUniqueId(), location.clone());
        }
    }

    void queueBoundaryReturn(Player player, float yaw, float pitch) {
        FreecamSession session = getSession(player);
        if (session == null) return;
        Location safe = player.getLocation();
        double max = getMaxDistance();
        if (safe.getWorld() != session.anchor().getWorld()
                || safe.distanceSquared(session.anchor()) > max * max
                || isTownyBlocked(player, safe)) {
            Location last = lastLegalLocations.get(player.getUniqueId());
            if (last != null && last.getWorld() == session.anchor().getWorld()
                    && last.distanceSquared(session.anchor()) <= max * max) safe = last;
        }
        safe = safe.clone();
        safe.setYaw(yaw);
        safe.setPitch(pitch);
        queueCameraCorrection(player, safe);
    }

    void queueCameraCorrection(Player player, Location destination) {
        FreecamSession session = getSession(player);
        if (session == null) return;
        UUID uuid = player.getUniqueId();
        CameraCorrection previous = pendingCorrections.put(uuid, new CameraCorrection(session, destination.clone()));
        if (previous != null && previous.session() == session) return;

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            CameraCorrection correction = pendingCorrections.get(uuid);
            if (correction == null || correction.session() != session) return;
            pendingCorrections.remove(uuid);
            if (!player.isOnline() || getSession(player) != session) return;
            syncAnchorToBody(session);
            Location safe = correction.destination().clone();
            if (isTownyBlocked(player, safe)) {
                Location origin = session.anchor().clone();
                origin.setYaw(safe.getYaw());
                origin.setPitch(safe.getPitch());
                safe = origin;
            }
            double max = getMaxDistance();
            if (safe.getWorld() != session.anchor().getWorld()
                    || safe.distanceSquared(session.anchor()) > max * max) {
                safe = nearestRangeLocation(player, session, safe);
                if (safe == null) {
                    trace(player, "boundary-no-clear-destination");
                    return;
                }
            }
            internalTeleports.add(uuid);
            try {
                if (player.teleport(safe, PlayerTeleportEvent.TeleportCause.PLUGIN)) {
                    updateLastLegalLocation(player, player.getLocation());
                    player.setFallDistance(0.0F);
                }
            } finally {
                internalTeleports.remove(uuid);
            }
        });
    }

    private Location nearestRangeLocation(Player player, FreecamSession session, Location requested) {
        Location anchor = session.anchor();
        if (requested.getWorld() != anchor.getWorld()) {
            Location result = anchor.clone();
            result.setYaw(requested.getYaw());
            result.setPitch(requested.getPitch());
            return result;
        }
        Vector offset = requested.toVector().subtract(anchor.toVector());
        double distance = offset.length();
        if (distance == 0) return requested.clone();
        Vector direction = offset.multiply(1.0 / distance);
        // Keep the view near the limit even if the body moved or the range was
        // reduced. Never replace an invalid saved edge position with the origin.
        double radius = Math.max(0, getMaxDistance() - Math.min(0.05, getMaxDistance() * 0.01));
        for (double remaining = Math.min(distance, radius); remaining >= 0; remaining -= 0.25) {
            Location candidate = anchor.clone().add(direction.clone().multiply(remaining));
            candidate.setYaw(requested.getYaw());
            candidate.setPitch(requested.getPitch());
            if (!player.collidesAt(candidate)) return candidate;
        }
        return null;
    }

    void hideCameraEquipment(Player player) {
        if (isActive(player)) {
            // Client-only equipment update: the real inventory and the body's
            // copied equipment remain untouched, including on disconnect/crash.
            ItemStack empty = ItemStack.empty();
            player.sendEquipmentChange(player, Map.of(
                    EquipmentSlot.HAND, empty,
                    EquipmentSlot.OFF_HAND, empty,
                    EquipmentSlot.HEAD, empty,
                    EquipmentSlot.CHEST, empty,
                    EquipmentSlot.LEGS, empty,
                    EquipmentSlot.FEET, empty));
            plugin.maskCameraInventory(player);
        }
    }

    void refreshCameraEquipmentNextTick(Player player) {
        FreecamSession session = getSession(player);
        if (session == null) return;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || getSession(player) != session) return;
            // Restore the previously selected slot, then mask both hands and armor.
            player.updateInventory();
            hideCameraEquipment(player);
        });
    }

    boolean handleBodyDamage(EntityDamageEvent event) {
        UUID ownerUuid = bodyOwners.get(event.getEntity().getUniqueId());
        if (ownerUuid == null) {
            return false;
        }

        event.setCancelled(true);

        Player player = plugin.getServer().getPlayer(ownerUuid);
        if (player == null || !player.isOnline() || !isActive(player)) {
            bodyOwners.remove(event.getEntity().getUniqueId());
            event.getEntity().remove();
            return true;
        }

        // A creative proxy must not turn an otherwise harmless mob hit into
        // an exit from freecam, even if another plugin targets it directly.
        if (ignoresMobAggro(player)) return true;

        FreecamSession session = getSession(player);
        // Do not remove invulnerability that existed before entering Freecam.
        if (session != null && session.invulnerable()) return true;
        if (session != null) {
            syncAnchorToBody(session);
        }

        if (event.getCause() == EntityDamageEvent.DamageCause.DROWNING
                && event.getEntity() instanceof Mannequin body
                && !body.isUnderWater()) {
            trace(player, "body-drowning-ignored");
            body.setRemainingAir(body.getMaximumAir());
            return true;
        }

        trace(player, "body-damage cause=" + event.getCause() + " damage=" + event.getDamage());

        double damage = event.getDamage();
        DamageSource damageSource = event.getDamageSource();

        boolean wasInvulnerable = player.isInvulnerable();
        // Preserve the session until the real player's native damage event has
        // passed every protection listener. A cancelled hit must not detach the body.
        if (isForwardedBodyDamage(player)) return true;
        forwardedBodyDamage.add(player.getUniqueId());
        try {
            player.setInvulnerable(false);
            player.damage(damage, damageSource);
        } finally {
            forwardedDamageEvents.remove(player.getUniqueId());
            forwardedBodyDamage.remove(player.getUniqueId());
            if (player.isOnline() && isActive(player)) {
                player.setInvulnerable(wasInvulnerable);
            }
        }
        return true;
    }

    void applyCameraVisibility(Player freecamPlayer) {
        if (!plugin.getConfig().getBoolean("hide-camera-player-from-others", true)) {
            return;
        }

        for (Player viewer : plugin.getServer().getOnlinePlayers()) {
            if (!viewer.getUniqueId().equals(freecamPlayer.getUniqueId())) {
                viewer.hidePlayer(plugin, freecamPlayer);
            }
        }
    }

    void hideActiveCamerasFrom(Player viewer) {
        if (!plugin.getConfig().getBoolean("hide-camera-player-from-others", true)) {
            return;
        }

        for (UUID uuid : sessions.keySet()) {
            Player freecamPlayer = plugin.getServer().getPlayer(uuid);
            if (freecamPlayer != null && !viewer.getUniqueId().equals(uuid)) {
                viewer.hidePlayer(plugin, freecamPlayer);
            }
        }
    }

    void refreshVisibility() {
        for (UUID uuid : sessions.keySet()) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player == null) {
                continue;
            }
            restoreVisibility(player);
            applyCameraVisibility(player);
        }
    }

    Location redirectedTeleportDestination(Player teleportedPlayer, Location requested) {
        if (!plugin.getConfig().getBoolean("redirect-teleports-to-body", true)) {
            return null;
        }

        double matchRadius = Math.max(0.05D,
                plugin.getConfig().getDouble("teleport-camera-match-radius-blocks", 0.75D));
        double maxSquared = matchRadius * matchRadius;

        for (Map.Entry<UUID, FreecamSession> entry : sessions.entrySet()) {
            if (entry.getKey().equals(teleportedPlayer.getUniqueId())) {
                continue;
            }

            Player freecamPlayer = plugin.getServer().getPlayer(entry.getKey());
            if (freecamPlayer == null || !freecamPlayer.isOnline()) {
                continue;
            }

            Location camera = freecamPlayer.getLocation();
            if (camera.getWorld() != requested.getWorld()) {
                continue;
            }

            if (camera.distanceSquared(requested) <= maxSquared) {
                Mannequin body = getBody(freecamPlayer);
                return body != null && body.isValid()
                        ? body.getLocation().clone()
                        : entry.getValue().anchor().clone();
            }
        }

        return null;
    }

    void enforceCurrentRange() {
        double max = getMaxDistance();
        double maxSquared = max * max;

        for (UUID uuid : sessions.keySet()) {
            Player player = plugin.getServer().getPlayer(uuid);
            FreecamSession session = sessions.get(uuid);
            if (player == null || session == null || !player.isOnline()) {
                continue;
            }

            Location current = player.getLocation();
            if (current.getWorld() != session.anchor().getWorld()
                    || current.distanceSquared(session.anchor()) > maxSquared
                    || isTownyBlocked(player, current)) {
                boundaryNotice(player);
                queueBoundaryReturn(player, current.getYaw(), current.getPitch());
            }
        }
    }

    void boundaryNotice(Player player) {
        if (!plugin.getConfig().getBoolean("show-boundary-message", true)) {
            return;
        }

        long now = System.currentTimeMillis();
        long previous = lastBoundaryNotice.getOrDefault(player.getUniqueId(), 0L);
        if (now - previous < 750L) {
            return;
        }

        lastBoundaryNotice.put(player.getUniqueId(), now);
        player.sendActionBar(Component.text(
                messages.text("boundary-return", "range", trimDistance(getMaxDistance())),
                NamedTextColor.RED));
    }

    void townyNotice(Player player) {
        if (!plugin.getConfig().getBoolean("show-boundary-message", true)) {
            return;
        }

        long now = System.currentTimeMillis();
        long previous = lastBoundaryNotice.getOrDefault(player.getUniqueId(), 0L);
        if (now - previous < 750L) {
            return;
        }

        lastBoundaryNotice.put(player.getUniqueId(), now);
        player.sendActionBar(Component.text(messages.text("towny-blocked"), NamedTextColor.RED));
    }

    void shutdown() {
        aggroTask.cancel();
        environmentTask.cancel();

        for (UUID uuid : Set.copyOf(sessions.keySet())) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null) {
                stop(player, true, false);
            } else {
                FreecamSession session = sessions.remove(uuid);
                clearTransientState(uuid);
                if (session != null) {
                    removeBody(session);
                    plugin.restoreOwnerPets(uuid);
                }
            }
        }
        bodyOwners.clear();
        hostileMobOwners.clear();
    }

    private void maintainBodyEnvironment() {
        environmentTicks++;
        for (Map.Entry<UUID, FreecamSession> entry : Map.copyOf(sessions).entrySet()) {
            Player player = plugin.getServer().getPlayer(entry.getKey());
            FreecamSession session = entry.getValue();

            if (player == null || !player.isOnline()) {
                continue;
            }

            if (isTownyBlocked(player, session.anchor())) {
                stop(player, true, false);
                player.sendMessage(message(messages.text("towny-anchor-ended"), NamedTextColor.RED));
                continue;
            }

            Mannequin body = getBody(player);
            if (body != null && body.isValid()) {
                syncAnchorToBody(session);
                if (environmentTicks % 20 == 0 && plugin.getConfig().getBoolean("show-body-nameplate", true)) {
                    Component name = nameplates.name(player);
                    if (!name.equals(body.customName())) body.customName(name);
                }

                // A body in water retains native current/buoyancy physics. Only
                // a dry grounded body is anchored; falling into water stays physical.
                boolean physical = !body.isOnGround() || WaterContact.touches(body);
                if (session.bodyHovering()) {
                    // Use the state captured BEFORE enabling camera flight.
                    // Survival cameras also fly, but their bodies must still fall.
                    body.setVelocity(new Vector());
                    body.setFallDistance(0.0F);
                    body.setGravity(false);
                    body.setImmovable(true);
                } else if (physical) {
                    body.setImmovable(false);
                    body.setGravity(true);
                } else if (!body.isImmovable()) {
                    body.setVelocity(new Vector());
                    body.setGravity(false);
                    body.setImmovable(true);
                    syncAnchorToBody(session);
                }

                // The freecam radius follows the real body while it is falling.
                double max = getMaxDistance();
                Location currentCamera = player.getLocation();
                boolean inRange = currentCamera.getWorld() == session.anchor().getWorld()
                        && currentCamera.distanceSquared(session.anchor()) <= max * max;
                if (inRange && !isTownyBlocked(player, currentCamera)) {
                    lastLegalLocations.put(player.getUniqueId(), currentCamera.clone());
                } else {
                    if (inRange) townyNotice(player);
                    queueBoundaryReturn(player, currentCamera.getYaw(), currentCamera.getPitch());
                }
                collectBodyItems(player, body);
                if (getSession(player) == session && !ignoresMobAggro(player) && plugin.mobBridge() != null) {
                    for (Entity nearby : body.getNearbyEntities(4, 4, 4)) {
                        if (getSession(player) != session) break;
                        if (nearby instanceof AbstractCubeMob cube && cube.isValid()) {
                            plugin.mobBridge().contactDamage(cube, body);
                        }
                    }
                }
            }

            // A pickup listener may end this session while handling its event.
            if (getSession(player) != session) continue;

            // The camera is a detached viewpoint. Keep normal flying active and
            // prevent water/gravity from turning the camera into a physical body.
            player.setAllowFlight(true);
            if (!player.isFlying()) {
                player.setFlying(true);
            }
            if (player.hasGravity()) {
                player.setGravity(false);
            }
            if (player.isSwimming()) {
                player.setSwimming(false);
            }
            if (player.hasActiveItem()) player.clearActiveItem();

            // Water must not physically carry the detached camera upward.
            // isInWater() can oscillate at the surface, so also check the feet
            // and eye blocks to keep the correction active across the boundary.
            boolean cameraTouchingWater = WaterContact.touches(player);
            if (cameraTouchingWater) {
                Vector velocity = WaterContact.cameraVelocity(player.getCurrentInput(), player.getVelocity());
                if (!velocity.equals(player.getVelocity())) player.setVelocity(velocity);
            }

            player.setFallDistance(0.0F);
            player.setFireTicks(0);
            player.setFreezeTicks(0);
            if (environmentTicks % 20 == 0) plugin.pauseOwnerPets(player);
            hideCameraEquipment(player);

            // Survival air belongs to the anchored body, not the moving camera.
            int air = body != null && body.isValid()
                    ? body.getRemainingAir()
                    : session.remainingAir();
            int clampedAir = Math.max(0, Math.min(air, player.getMaximumAir()));

            if (player.getRemainingAir() != clampedAir) {
                synchronizingAir.add(player.getUniqueId());
                try {
                    player.setRemainingAir(clampedAir);
                } finally {
                    synchronizingAir.remove(player.getUniqueId());
                }
            }
            if (environmentTicks % 20 == 0) trace(player, "environment");
        }
    }

    boolean isSynchronizingAir(Player player) {
        return synchronizingAir.contains(player.getUniqueId());
    }

    boolean isBodyPickup(Player player, Item item) {
        return item.getUniqueId().equals(bodyPickupItems.get(player.getUniqueId()));
    }

    void collectBodyItems(Player player, Mannequin body) {
        FreecamSession session = getSession(player);
        if (session == null || !player.getCanPickupItems() || player.getGameMode() == GameMode.SPECTATOR
                || body == null || !body.isValid() || bodyPickupItems.containsKey(player.getUniqueId())) return;
        for (Entity entity : body.getNearbyEntities(1.0D, 0.5D, 1.0D)) {
            if (getSession(player) != session) return;
            if (!(entity instanceof Item item) || !item.isValid() || item.isDead()
                    || !item.canPlayerPickup() || item.getPickupDelay() > 0
                    || item.getOwner() != null && !item.getOwner().equals(player.getUniqueId())) continue;
            if (!collectingItems.add(item.getUniqueId())) continue;
            try {
                ItemStack original = item.getItemStack().clone();
                int amount = original.getAmount();
                if (amount <= 0) continue;
                PlayerInventory inventory = player.getInventory();
                int capacity = pickupCapacity(inventory, original);
                if (capacity <= 0) continue;
                int remaining = Math.max(0, amount - capacity);
                bodyPickupItems.put(player.getUniqueId(), item.getUniqueId());
                PlayerAttemptPickupItemEvent attempt = new PlayerAttemptPickupItemEvent(player, item, remaining);
                attempt.setFlyAtPlayer(false); // The visible collector is the body, never the camera.
                plugin.getServer().getPluginManager().callEvent(attempt);
                if (attempt.isCancelled() || getSession(player) != session || !item.isValid()) continue;
                EntityPickupItemEvent pickup = new EntityPickupItemEvent(player, item, remaining);
                plugin.getServer().getPluginManager().callEvent(pickup);
                if (pickup.isCancelled() || getSession(player) != session || !item.isValid()) continue;
                // Plugins may consume or replace the drop in either callback.
                ItemStack current = item.getItemStack();
                if (current.getAmount() != amount || !current.isSimilar(original)) continue;
                Map<Integer, ItemStack> leftovers = inventory.addItem(original.clone());
                int left = leftovers.values().stream().mapToInt(ItemStack::getAmount).sum();
                int picked = amount - left;
                if (picked <= 0) continue;
                body.playPickupItemAnimation(item, picked);
                if (left == 0) item.remove();
                else {
                    ItemStack remainder = original.clone();
                    remainder.setAmount(left);
                    item.setItemStack(remainder);
                }
            } finally {
                bodyPickupItems.remove(player.getUniqueId(), item.getUniqueId());
                collectingItems.remove(item.getUniqueId());
            }
        }
    }

    private int pickupCapacity(PlayerInventory inventory, ItemStack incoming) {
        int available = 0;
        for (ItemStack slot : inventory.getStorageContents()) {
            int limit = Math.min(incoming.getMaxStackSize(), inventory.getMaxStackSize());
            if (slot == null || slot.isEmpty()) available += limit;
            else if (slot.isSimilar(incoming)) available += Math.max(0, limit - slot.getAmount());
            if (available >= incoming.getAmount()) return incoming.getAmount();
        }
        return available;
    }

    void trace(Player player, String reason) {
        if (!plugin.getConfig().getBoolean("debug-water", false) || !isActive(player)) return;
        if (reason.equals("water-move-correction")) {
            long now = System.nanoTime();
            Long previous = lastMovementTrace.get(player.getUniqueId());
            if (previous != null && now - previous < 1_000_000_000L) return;
            lastMovementTrace.put(player.getUniqueId(), now);
        }
        Mannequin body = getBody(player);
        Input input = player.getCurrentInput();
        plugin.getLogger().info("[water-debug] player=" + player.getName() + " reason=" + reason
                + " camera=" + player.getLocation() + " velocity=" + player.getVelocity()
                + " flying=" + player.isFlying() + " allowFlight=" + player.getAllowFlight()
                + " swimming=" + player.isSwimming() + " water=" + WaterContact.touches(player)
                + " air=" + player.getRemainingAir() + " jump=" + input.isJump() + " sneak=" + input.isSneak()
                + " forward=" + input.isForward() + " backward=" + input.isBackward()
                + " left=" + input.isLeft() + " right=" + input.isRight()
                + (body == null ? " body=none" : " body=" + body.getLocation() + " velocity=" + body.getVelocity()
                + " ground=" + body.isOnGround() + " immovable=" + body.isImmovable()
                + " underwater=" + body.isUnderWater() + " air=" + body.getRemainingAir()
                + " pose=" + body.getPose() + " fall=" + body.getFallDistance()));
    }

    private Mannequin getBody(FreecamSession session) {
        if (session.bodyUuid() == null) {
            return null;
        }

        Entity entity = plugin.getServer().getEntity(session.bodyUuid());
        return entity instanceof Mannequin mannequin ? mannequin : null;
    }

    private void syncAnchorToBody(FreecamSession session) {
        Mannequin body = getBody(session);
        if (body == null || !body.isValid()) {
            return;
        }

        Location bodyLocation = body.getLocation();
        Location anchor = session.anchor();
        anchor.setWorld(bodyLocation.getWorld());
        anchor.setX(bodyLocation.getX());
        anchor.setY(bodyLocation.getY());
        anchor.setZ(bodyLocation.getZ());
        anchor.setYaw(bodyLocation.getYaw());
        anchor.setPitch(bodyLocation.getPitch());
    }

    private int getBodyAir(FreecamSession session) {
        if (session.bodyUuid() == null) {
            return session.remainingAir();
        }

        Entity entity = plugin.getServer().getEntity(session.bodyUuid());
        if (entity instanceof Mannequin body && body.isValid()) {
            return body.getRemainingAir();
        }

        return session.remainingAir();
    }

    private record BodyEnvironment(int fireTicks, int freezeTicks) {}

    private BodyEnvironment getBodyEnvironment(FreecamSession session) {
        Mannequin body = getBody(session);
        return body != null && body.isValid()
                ? new BodyEnvironment(body.getFireTicks(), body.getFreezeTicks())
                : new BodyEnvironment(session.fireTicks(), session.freezeTicks());
    }

    private void restoreBodyEnvironment(Player player, BodyEnvironment state) {
        player.setFireTicks(state.fireTicks());
        player.setFreezeTicks(state.freezeTicks());
    }

    private void maintainBodyAggro() {
        if (!plugin.getConfig().getBoolean("force-hostile-mob-aggro", true)) {
            hostileMobOwners.clear();
            return;
        }

        double radius = Math.max(1.0D, plugin.getConfig().getDouble("mob-aggro-radius-blocks", 32.0D));

        for (Map.Entry<UUID, FreecamSession> entry : sessions.entrySet()) {
            FreecamSession session = entry.getValue();
            Player player = plugin.getServer().getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) {
                continue;
            }

            Mannequin body = getBody(player);
            if (body == null || !body.isValid()) {
                continue;
            }

            Set<UUID> nearbyMobs = new HashSet<>();
            for (Entity entity : body.getNearbyEntities(radius, radius, radius)) {
                if (!(entity instanceof Mob monster) || !monster.isValid() || !monster.isAware()) {
                    continue;
                }

                LivingEntity target = monster.getTarget();
                nearbyMobs.add(monster.getUniqueId());

                if (ignoresMobAggro(player)) {
                    hostileMobOwners.remove(monster.getUniqueId(), player.getUniqueId());
                    if (target == body || target == player) setMobTarget(monster, null, player, session);
                    continue;
                }

                if (target == body) {
                    rememberMobTarget(monster, body);
                    continue;
                }

                // Do not steal a mob from another legitimate target.
                if (target != null && target != player) {
                    hostileMobOwners.remove(monster.getUniqueId(), player.getUniqueId());
                    continue;
                }

                // If the mob was already targeting the real camera Player, always
                // redirect it to the body, even for normally neutral monsters.
                boolean remembered = player.getUniqueId().equals(hostileMobOwners.get(monster.getUniqueId()));
                boolean retainsAnger = remembered && (monster instanceof Spider
                        || (plugin.mobBridge() != null && plugin.mobBridge().retainsAnger(monster, player.getUniqueId(), session.bodyUuid())));
                boolean acquire = supportsBodyAggro(monster) && shouldForceTargetBody(monster)
                        && canAcquireBody(monster, body);
                if (target == player || retainsAnger || acquire) {
                    setMobTarget(monster, body, player, session);
                    rememberMobTarget(monster, monster.getTarget());
                } else if (remembered) {
                    hostileMobOwners.remove(monster.getUniqueId(), player.getUniqueId());
                }
            }
            hostileMobOwners.entrySet().removeIf(record -> record.getValue().equals(player.getUniqueId())
                    && !nearbyMobs.contains(record.getKey()));
        }
    }

    void rememberMobTarget(Entity entity, LivingEntity target) {
        if (!(entity instanceof Mob) || target == null) return;
        UUID ownerUuid = target instanceof Player player && isActive(player)
                ? player.getUniqueId() : bodyOwners.get(target.getUniqueId());
        Player owner = ownerUuid == null ? null : plugin.getServer().getPlayer(ownerUuid);
        if (owner != null && isActive(owner) && !ignoresMobAggro(owner)
                && plugin.getConfig().getBoolean("force-hostile-mob-aggro", true)) {
            hostileMobOwners.put(entity.getUniqueId(), ownerUuid);
        } else {
            hostileMobOwners.remove(entity.getUniqueId());
        }
    }

    private void releaseMobTargets(Player player, FreecamSession session, boolean returned) {
        double radius = Math.max(1, plugin.getConfig().getDouble("mob-aggro-radius-blocks", 32));
        for (Map.Entry<UUID, UUID> record : Map.copyOf(hostileMobOwners).entrySet()) {
            if (!record.getValue().equals(player.getUniqueId())) continue;
            hostileMobOwners.remove(record.getKey());
            if (!returned || ignoresMobAggro(player) || !player.isOnline() || player.isDead()) continue;
            Entity entity = plugin.getServer().getEntity(record.getKey());
            if (!(entity instanceof Mob spider) || !spider.isValid() || !spider.isAware()) continue;
            LivingEntity target = spider.getTarget();
            if (target != null && !target.getUniqueId().equals(session.bodyUuid())) continue;
            if (target == null && !(spider instanceof Spider)
                    && !(supportsBodyAggro(spider) && shouldForceTargetBody(spider) && canAcquireBody(spider, player))
                    && !(plugin.mobBridge() != null && plugin.mobBridge().retainsAnger(spider, player.getUniqueId(), session.bodyUuid()))) continue;
            Location location = spider.getLocation();
            Location restored = player.getLocation();
            if (location.getWorld() == restored.getWorld()
                    && location.distanceSquared(restored) <= radius * radius) setMobTarget(spider, player, player, session);
        }
    }

    private boolean supportsBodyAggro(Mob mob) {
        return mob instanceof Enemy || (mob instanceof Rabbit rabbit && rabbit.getRabbitType() == Rabbit.Type.THE_KILLER_BUNNY);
    }

    private boolean canAcquireBody(Mob mob, LivingEntity target) {
        BodyMobBridge bridge = plugin.mobBridge();
        return bridge != null ? bridge.canAcquire(mob, target) : mob.hasLineOfSight(target);
    }

    private void setMobTarget(Mob mob, LivingEntity target, Player owner, FreecamSession session) {
        BodyMobBridge bridge = plugin.mobBridge();
        if (bridge != null) bridge.retarget(mob, target, owner.getUniqueId(), session.bodyUuid());
        else mob.setTarget(target);
    }

    private boolean shouldForceTargetBody(Mob monster) {
        // These mobs are conditionally hostile in vanilla. Do not make them angry
        // just because freecam is enabled; if they were already targeting the
        // player, maintainBodyAggro() still redirects them to the body.
        if (monster instanceof Enderman) {
            return false;
        }
        if (monster instanceof Piglin && !(monster instanceof PiglinBrute)) {
            // Its native nearest-player sensor also checks gold, zombified mobs
            // and other priorities. Preserve established anger, not a synthetic
            // permanent anger state that would outlive those conditions.
            return false;
        }
        if (monster instanceof PigZombie) {
            return false;
        }
        if (monster instanceof Spider && !(monster instanceof CaveSpider)) {
            return false;
        }
        if (monster instanceof Warden || monster instanceof Creaking || monster instanceof EnderDragon) {
            return false;
        }
        if (monster instanceof Hoglin hoglin && !hoglin.isAdult()) return false;

        return true;
    }

    private void retargetCurrentEnemies(Player player, Mannequin body) {
        for (Entity entity : player.getNearbyEntities(64.0, 64.0, 64.0)) {
            if (entity instanceof Mob monster && monster.getTarget() == player) {
                setMobTarget(monster, ignoresMobAggro(player) ? null : body, player, getSession(player));
                rememberMobTarget(monster, monster.getTarget());
            }
        }
    }

    private Mannequin spawnBody(Player player, Location anchor) {
        Mannequin body = player.getWorld().spawn(anchor, Mannequin.class, mannequin -> {
            mannequin.setProfile(ResolvableProfile.resolvableProfile(player.getPlayerProfile()));
            mannequin.setInvisible(player.isInvisible());
            mannequin.setMainHand(player.getMainHand());

            boolean airborne = !shouldHoverBody(player)
                    && (!player.isOnGround() || WaterContact.touches(player));
            mannequin.setImmovable(!airborne);
            mannequin.setGravity(airborne);
            mannequin.setAI(false);
            mannequin.setCanPickupItems(false);
            mannequin.setCollidable(true);
            mannequin.setInvulnerable(ignoresMobAggro(player) || player.isInvulnerable());
            mannequin.setPersistent(false);
            mannequin.setRemoveWhenFarAway(false);
            mannequin.setSilent(true);

            // Keep the player's name, but remove Mannequin's default
            // description line (e.g. "NPC") under the name.
            mannequin.setDescription(null);
            if (plugin.getConfig().getBoolean("show-body-nameplate", true)) {
                mannequin.customName(nameplates.name(player));
                mannequin.setCustomNameVisible(true);
            }

            if (Mannequin.validPoses().contains(player.getPose())) {
                mannequin.setPose(player.getPose(), true);
            }

            EntityEquipment equipment = mannequin.getEquipment();
            equipment.setHelmet(copy(player.getInventory().getHelmet()));
            equipment.setChestplate(copy(player.getInventory().getChestplate()));
            equipment.setLeggings(copy(player.getInventory().getLeggings()));
            equipment.setBoots(copy(player.getInventory().getBoots()));
            equipment.setItemInMainHand(copy(player.getInventory().getItemInMainHand()));
            equipment.setItemInOffHand(copy(player.getInventory().getItemInOffHand()));

            mannequin.setMaxHealth(player.getMaxHealth());
            mannequin.setHealth(Math.min(player.getHealth(), mannequin.getMaxHealth()));
            mannequin.setAbsorptionAmount(player.getAbsorptionAmount());
            mannequin.setMaximumAir(player.getMaximumAir());
            mannequin.setRemainingAir(player.getRemainingAir());
            mannequin.setFireTicks(player.getFireTicks());
            mannequin.setFreezeTicks(player.getFreezeTicks());
            // Preserve starting breathing / slow-falling effects on the physical
            // body as well as its equipment. Native duration ticking remains active.
            mannequin.addPotionEffects(player.getActivePotionEffects());
        });

        body.setRotation(anchor.getYaw(), anchor.getPitch());
        if (!body.isImmovable()) {
            body.setVelocity(player.getVelocity().clone());
            body.setFallDistance(player.getFallDistance());
        }
        return body;
    }

    private ItemStack copy(ItemStack stack) {
        return stack == null ? null : stack.clone();
    }

    private void removeBody(FreecamSession session) {
        UUID bodyUuid = session.bodyUuid();
        if (bodyUuid == null) {
            return;
        }

        bodyOwners.remove(bodyUuid);

        Entity body = plugin.getServer().getEntity(bodyUuid);
        if (body != null) {
            body.remove();
        }
    }

    private void restoreState(Player player, FreecamSession session) {
        plugin.releaseCameraInventory(player);
        restoreVisibility(player);
        player.setInvulnerable(session.invulnerable());
        player.setCollidable(session.collidable());
        player.setInvisible(session.invisible());
        player.setVisibleByDefault(session.visibleByDefault());
        player.setGravity(session.gravity());
        player.setAllowFlight(session.allowFlight());
        player.setFlying(session.allowFlight() && session.flying());
        player.setGliding(session.gliding());
        player.setFallDistance(session.fallDistance());
        player.updateInventory();
    }

    private void restoreVisibility(Player player) {
        for (Player viewer : plugin.getServer().getOnlinePlayers()) {
            if (!viewer.getUniqueId().equals(player.getUniqueId())) {
                viewer.showPlayer(plugin, player);
            }
        }
    }

    private void clearTransientState(UUID uuid) {
        lastLegalLocations.remove(uuid);
        pendingCorrections.remove(uuid);
        lastBoundaryNotice.remove(uuid);
        lastMovementTrace.remove(uuid);
        synchronizingAir.remove(uuid);
        bodyPickupItems.remove(uuid);
    }

    private Component message(String text, NamedTextColor color) {
        return Component.text("[AnchoredFreecam] ", NamedTextColor.AQUA)
                .append(Component.text(text, color));
    }

    private String trimDistance(double value) {
        if (value == Math.rint(value)) {
            return Long.toString(Math.round(value));
        }
        return Double.toString(value);
    }
}
