package io.github.sanowo0390.anchoredfreecam;

import org.bukkit.entity.AbstractCubeMob;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Warden;
import org.bukkit.event.entity.EntityTargetEvent.TargetReason;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

/** Paper 26.2 native targeting rules and Brain/anger state for the body proxy. */
final class BodyMobBridge {
    private Logger logger;
    private final Set<String> reportedFailures = new HashSet<>();
    private final Method handle, level, attribute, forCombat, range, test;
    private final Method setTarget, rawTarget, getBrain, registered, readMemory, writeMemory, expiry, writeExpiring, erase;
    private final Method nativeUuid, bukkitEntity, angry, angerTarget, angerEnd, setAngerEnd, setAngerTarget, referenceOf, referenceUuid;
    private final Method cubeDealsDamage, cubeDamage;
    private final Class<?> neutral, cube;
    private final Object followRange, attackMemory, angerMemory, registeredStatus;
    private final Object pacifiedMemory, avoidingMemory, admiringMemory;

    private BodyMobBridge(ClassLoader loader) throws ReflectiveOperationException {
        this(loader, false);
    }

    // Allows a standalone contract check against the exact server JAR without
    // bootstrapping registries or starting a Minecraft server.
    private BodyMobBridge(ClassLoader loader, boolean symbolsOnly) throws ReflectiveOperationException {
        Class<?> living = loader.loadClass("net.minecraft.world.entity.LivingEntity");
        Class<?> entity = loader.loadClass("net.minecraft.world.entity.Entity");
        Class<?> mob = loader.loadClass("net.minecraft.world.entity.Mob");
        Class<?> holder = loader.loadClass("net.minecraft.core.Holder");
        Class<?> serverLevel = loader.loadClass("net.minecraft.server.level.ServerLevel");
        Class<?> conditions = loader.loadClass("net.minecraft.world.entity.ai.targeting.TargetingConditions");
        Class<?> memory = loader.loadClass("net.minecraft.world.entity.ai.memory.MemoryModuleType");
        Class<?> status = loader.loadClass("net.minecraft.world.entity.ai.memory.MemoryStatus");
        Class<?> brain = loader.loadClass("net.minecraft.world.entity.ai.Brain");
        Class<?> reference = loader.loadClass("net.minecraft.world.entity.EntityReference");
        neutral = loader.loadClass("net.minecraft.world.entity.NeutralMob");
        cube = loader.loadClass("net.minecraft.world.entity.monster.cubemob.AbstractCubeMob");
        handle = loader.loadClass("org.bukkit.craftbukkit.entity.CraftLivingEntity").getMethod("getHandle");
        level = entity.getMethod("level");
        nativeUuid = entity.getMethod("getUUID");
        bukkitEntity = entity.getMethod("getBukkitEntity");
        attribute = living.getMethod("getAttributeValue", holder);
        followRange = staticValue(loader.loadClass("net.minecraft.world.entity.ai.attributes.Attributes").getField("FOLLOW_RANGE"), symbolsOnly);
        forCombat = conditions.getMethod("forCombat");
        range = conditions.getMethod("range", double.class);
        test = conditions.getMethod("test", serverLevel, living, living);
        setTarget = mob.getMethod("setTarget", living, TargetReason.class);
        rawTarget = mob.getMethod("getTargetUnchecked");
        getBrain = living.getMethod("getBrain");
        registered = brain.getMethod("checkMemory", memory, status);
        registeredStatus = staticValue(status.getField("REGISTERED"), symbolsOnly);
        attackMemory = staticValue(memory.getField("ATTACK_TARGET"), symbolsOnly);
        angerMemory = staticValue(memory.getField("ANGRY_AT"), symbolsOnly);
        pacifiedMemory = staticValue(memory.getField("PACIFIED"), symbolsOnly);
        avoidingMemory = staticValue(memory.getField("AVOID_TARGET"), symbolsOnly);
        admiringMemory = staticValue(memory.getField("ADMIRING_ITEM"), symbolsOnly);
        readMemory = brain.getMethod("getMemory", memory);
        writeMemory = brain.getMethod("setMemory", memory, Object.class);
        expiry = brain.getMethod("getTimeUntilExpiry", memory);
        writeExpiring = brain.getMethod("setMemoryWithExpiry", memory, Object.class, long.class);
        erase = brain.getMethod("eraseMemory", memory);
        angry = neutral.getMethod("isAngry");
        angerTarget = neutral.getMethod("getPersistentAngerTarget");
        angerEnd = neutral.getMethod("getPersistentAngerEndTime");
        setAngerEnd = neutral.getMethod("setPersistentAngerEndTime", long.class);
        setAngerTarget = neutral.getMethod("setPersistentAngerTarget", reference);
        referenceOf = reference.getMethod("of", UUID.class);
        referenceUuid = reference.getMethod("getUUID");
        cubeDealsDamage = cube.getDeclaredMethod("isDealsDamage");
        cubeDamage = cube.getDeclaredMethod("dealDamage", living);
        cubeDealsDamage.setAccessible(true);
        cubeDamage.setAccessible(true);
    }

    static BodyMobBridge forPaper(Logger logger) {
        try {
            BodyMobBridge bridge = new BodyMobBridge(BodyMobBridge.class.getClassLoader());
            bridge.logger = logger;
            return bridge;
        } catch (ReflectiveOperationException | LinkageError ex) {
            logger.warning("Native body mob targeting unavailable; conservative Bukkit fallback remains active: " + ex);
            return null;
        }
    }

    boolean canAcquire(Mob source, LivingEntity target) {
        try {
            Object mob = handle.invoke(source);
            Object brain = getBrain.invoke(mob);
            if (Boolean.TRUE.equals(readRegistered(brain, pacifiedMemory))
                    || readRegistered(brain, avoidingMemory) != null
                    || Boolean.TRUE.equals(readRegistered(brain, admiringMemory))) return false;
            Object conditions = forCombat.invoke(null);
            range.invoke(conditions, ((Number) attribute.invoke(mob, followRange)).doubleValue());
            return (boolean) test.invoke(conditions, level.invoke(mob), mob, handle.invoke(target));
        } catch (ReflectiveOperationException ex) {
            reportFailure("acquisition", ex);
            return false; // Do not manufacture hostility if native eligibility is unknown.
        }
    }

    boolean retainsAnger(Mob source, UUID owner, UUID body) {
        try {
            Object mob = handle.invoke(source);
            if (neutral.isInstance(mob) && (boolean) angry.invoke(mob)) {
                Object reference = angerTarget.invoke(mob);
                if (reference != null && belongs(referenceUuid.invoke(reference), owner, body)) return true;
            }
            Object brain = getBrain.invoke(mob);
            Object remembered = readRegistered(brain, angerMemory);
            if (remembered != null && belongs(remembered, owner, body)
                    && (long) expiry.invoke(brain, angerMemory) > 0) return true;
            if (source instanceof Warden warden) {
                for (UUID uuid : new UUID[]{owner, body}) {
                    if (uuid != null && source.getServer().getEntity(uuid) instanceof LivingEntity entity
                            && warden.getAnger(entity) >= 80) return true;
                }
            }
            return false;
        } catch (ReflectiveOperationException ex) {
            reportFailure("anger lookup", ex);
            return false;
        }
    }

    boolean retarget(Mob source, LivingEntity target, UUID owner, UUID body) {
        try {
            Object mob = handle.invoke(source);
            Object brain = getBrain.invoke(mob);
            boolean hasAttack = (boolean) registered.invoke(brain, attackMemory, registeredStatus);
            Object previous = hasAttack ? memory(brain, attackMemory) : rawTarget.invoke(mob);
            long attackExpiry = previous == null ? Long.MAX_VALUE : hasAttack ? (long) expiry.invoke(brain, attackMemory) : Long.MAX_VALUE;
            boolean isNeutral = neutral.isInstance(mob);
            long previousAngerEnd = isNeutral ? (long) angerEnd.invoke(mob) : 0;
            Object previousReference = isNeutral ? angerTarget.invoke(mob) : null;
            // Dispatch once before changing either storage. Native setTarget can
            // return early when its raw field is unchanged but Brain has forgotten it.
            EntityTargetLivingEntityEvent decision = new EntityTargetLivingEntityEvent(source, target, TargetReason.CUSTOM);
            source.getServer().getPluginManager().callEvent(decision);
            if (decision.isCancelled()) return false;
            LivingEntity decided = decision.getTarget();
            Object expected = decided == null ? null : handle.invoke(decided);
            setTarget.invoke(mob, expected, null);
            Object accepted = rawTarget.invoke(mob);
            if (accepted != expected) return false;
            if (hasAttack) replaceMemory(brain, attackMemory, accepted, attackExpiry);
            // Rebind only anger belonging to this session; retain its original expiry.
            if (accepted != null && isNeutral && previousReference != null
                    && belongs(referenceUuid.invoke(previousReference), owner, body)) {
                setAngerTarget.invoke(mob, referenceOf.invoke(null, nativeUuid.invoke(accepted)));
                setAngerEnd.invoke(mob, previousAngerEnd);
            }
            if ((boolean) registered.invoke(brain, angerMemory, registeredStatus)) {
                Object oldAnger = memory(brain, angerMemory);
                if (oldAnger != null && belongs(oldAnger, owner, body)) {
                    long remaining = (long) expiry.invoke(brain, angerMemory);
                    replaceMemory(brain, angerMemory, accepted == null ? null : nativeUuid.invoke(accepted), remaining);
                }
            }
            if (source instanceof Warden warden && previous != null && accepted != null) {
                LivingEntity old = (LivingEntity) bukkitEntity.invoke(previous);
                if (belongs(old.getUniqueId(), owner, body) && !old.getUniqueId().equals(decided.getUniqueId())) {
                    int amount = warden.getAnger(old);
                    if (amount > 0) {
                        warden.setAnger(decided, amount);
                        warden.clearAnger(old);
                    }
                }
            }
            return decided == target;
        } catch (ReflectiveOperationException ex) {
            reportFailure("target transfer", ex);
            // A partially accepted native change must not be followed by a second,
            // uncancellable Bukkit mutation or an artificial anger reset.
            return false;
        }
    }

    void contactDamage(AbstractCubeMob source, LivingEntity target) {
        try {
            Object mob = handle.invoke(source);
            if (cube.isInstance(mob) && (boolean) cubeDealsDamage.invoke(mob)) {
                // Uses native size, melee range, line of sight, damage, cooldown,
                // enchantment effects and normal cancellable damage events.
                cubeDamage.invoke(mob, handle.invoke(target));
            }
        } catch (ReflectiveOperationException ex) {
            reportFailure("cube contact damage", ex);
            // Do not invent damage values when the native path is unavailable.
        }
    }

    private void reportFailure(String operation, ReflectiveOperationException failure) {
        if (logger != null && reportedFailures.add(operation)) {
            logger.warning("Native body mob " + operation + " failed; no artificial target/anger/damage fallback applied: " + failure);
        }
    }

    private Object memory(Object brain, Object key) throws ReflectiveOperationException {
        return ((Optional<?>) readMemory.invoke(brain, key)).orElse(null);
    }

    private Object readRegistered(Object brain, Object key) throws ReflectiveOperationException {
        return (boolean) registered.invoke(brain, key, registeredStatus) ? memory(brain, key) : null;
    }

    private void replaceMemory(Object brain, Object key, Object value, long remaining) throws ReflectiveOperationException {
        if (value == null || remaining <= 0) erase.invoke(brain, key);
        else if (remaining == Long.MAX_VALUE) writeMemory.invoke(brain, key, value);
        else writeExpiring.invoke(brain, key, value, remaining);
    }

    private static boolean belongs(Object uuid, UUID owner, UUID body) {
        return uuid.equals(owner) || uuid.equals(body);
    }

    private static Object staticValue(java.lang.reflect.Field field, boolean symbolsOnly) throws ReflectiveOperationException {
        return symbolsOnly ? null : field.get(null);
    }
}
