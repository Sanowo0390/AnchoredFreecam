package io.github.sanowo0390.anchoredfreecam;

import org.bukkit.Server;
import org.bukkit.entity.AbstractCubeMob;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.event.entity.EntityTargetEvent.TargetReason;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BodyMobBridgeTest {
    private BodyMobBridge bridge;
    private Mob source;
    private NativeMob nativeMob;
    private NativeLiving owner, body;
    private Consumer<EntityTargetLivingEntityEvent> protection;

    public static final class Handles {
        static final Queue<Object> values = new ArrayDeque<>();
        public static Object next() { return values.remove(); }
    }

    public static class NativeLiving {
        final UUID uuid = UUID.randomUUID();
        public UUID getUUID() { return uuid; }
        public LivingEntity getBukkitEntity() { return null; }
    }

    public static class NativeReference {
        final UUID uuid;
        NativeReference(UUID uuid) { this.uuid = uuid; }
        public static NativeReference of(UUID uuid) { return new NativeReference(uuid); }
        public UUID getUUID() { return uuid; }
    }

    public static class NativeMob extends NativeLiving {
        final Brain brain = new Brain();
        NativeLiving raw;
        long angerEnd = 1000;
        NativeReference angryAt;
        boolean angry = true;
        public Brain getBrain() { return brain; }
        public NativeLiving getTargetUnchecked() { return raw; }
        public boolean setTarget(NativeLiving target, TargetReason reason) {
            if (raw == target) return false;
            raw = target;
            return true;
        }
        public boolean isAngry() { return angry; }
        public NativeReference getPersistentAngerTarget() { return angryAt; }
        public void setPersistentAngerTarget(NativeReference reference) { angryAt = reference; }
        public long getPersistentAngerEndTime() { return angerEnd; }
        public void setPersistentAngerEndTime(long value) { angerEnd = value; }
        public Object level() { return this; }
        public double getAttributeValue(Object key) { return 12.0; }
    }

    public static class NativeCube extends NativeMob {
        boolean dealsDamage;
        int hits;
        public boolean isDealsDamage() { return dealsDamage; }
        public void dealDamage(NativeLiving target) { hits++; }
    }

    public static class Brain {
        final Set<Object> keys = new HashSet<>(Set.of("attack", "anger"));
        final Map<Object, Object> values = new HashMap<>();
        final Map<Object, Long> times = new HashMap<>();
        public boolean checkMemory(Object key, Object status) { return keys.contains(key); }
        public Optional<Object> getMemory(Object key) { return Optional.ofNullable(values.get(key)); }
        public long getTimeUntilExpiry(Object key) { return times.getOrDefault(key, Long.MAX_VALUE); }
        public void setMemory(Object key, Object value) { values.put(key, value); times.put(key, Long.MAX_VALUE); }
        public void setMemoryWithExpiry(Object key, Object value, long remaining) { values.put(key, value); times.put(key, remaining); }
        public void eraseMemory(Object key) { values.remove(key); times.remove(key); }
    }

    public static class Conditions {
        static double observedRange;
        public static Conditions forCombat() { return new Conditions(); }
        public Conditions range(double value) { observedRange = value; return this; }
        public boolean test(Object level, NativeMob source, NativeLiving target) { return false; }
    }

    private void field(String name, Object value) throws Exception {
        var field = BodyMobBridge.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(bridge, value);
    }

    private void bind(String name, Class<?> type, String method, Class<?>... parameters) throws Exception {
        field(name, type.getMethod(method, parameters));
    }

    @BeforeEach
    void setup() throws Exception {
        bridge = mock(BodyMobBridge.class, CALLS_REAL_METHODS);
        source = mock(Mob.class);
        nativeMob = new NativeMob();
        owner = new NativeLiving();
        body = new NativeLiving();
        Handles.values.clear();
        protection = event -> {};
        Server server = mock(Server.class);
        PluginManager plugins = mock(PluginManager.class);
        when(source.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(plugins);
        doAnswer(call -> { protection.accept(call.getArgument(0)); return null; }).when(plugins).callEvent(any());
        bind("handle", Handles.class, "next");
        bind("getBrain", NativeMob.class, "getBrain");
        bind("rawTarget", NativeMob.class, "getTargetUnchecked");
        bind("setTarget", NativeMob.class, "setTarget", NativeLiving.class, TargetReason.class);
        bind("registered", Brain.class, "checkMemory", Object.class, Object.class);
        bind("readMemory", Brain.class, "getMemory", Object.class);
        bind("writeMemory", Brain.class, "setMemory", Object.class, Object.class);
        bind("writeExpiring", Brain.class, "setMemoryWithExpiry", Object.class, Object.class, long.class);
        bind("expiry", Brain.class, "getTimeUntilExpiry", Object.class);
        bind("erase", Brain.class, "eraseMemory", Object.class);
        bind("nativeUuid", NativeLiving.class, "getUUID");
        bind("bukkitEntity", NativeLiving.class, "getBukkitEntity");
        bind("angry", NativeMob.class, "isAngry");
        bind("angerTarget", NativeMob.class, "getPersistentAngerTarget");
        bind("angerEnd", NativeMob.class, "getPersistentAngerEndTime");
        bind("setAngerEnd", NativeMob.class, "setPersistentAngerEndTime", long.class);
        bind("setAngerTarget", NativeMob.class, "setPersistentAngerTarget", NativeReference.class);
        bind("referenceOf", NativeReference.class, "of", UUID.class);
        bind("referenceUuid", NativeReference.class, "getUUID");
        bind("level", NativeMob.class, "level");
        bind("attribute", NativeMob.class, "getAttributeValue", Object.class);
        bind("forCombat", Conditions.class, "forCombat");
        bind("range", Conditions.class, "range", double.class);
        bind("test", Conditions.class, "test", Object.class, NativeMob.class, NativeLiving.class);
        bind("cubeDealsDamage", NativeCube.class, "isDealsDamage");
        bind("cubeDamage", NativeCube.class, "dealDamage", NativeLiving.class);
        field("neutral", NativeMob.class);
        field("cube", NativeCube.class);
        field("attackMemory", "attack");
        field("angerMemory", "anger");
        field("registeredStatus", "registered");
        field("pacifiedMemory", "pacified");
        field("avoidingMemory", "avoiding");
        field("admiringMemory", "admiring");
    }

    private boolean retarget() {
        Handles.values.add(nativeMob);
        Handles.values.add(body);
        return bridge.retarget(source, mock(LivingEntity.class), owner.uuid, body.uuid);
    }

    @Test
    void forgottenBrainTargetIsRestoredEvenIfRawFieldStillHasBody() {
        nativeMob.raw = body;
        assertTrue(retarget());
        assertSame(body, nativeMob.brain.values.get("attack"));
    }

    @Test
    void cancelledRetargetCannotChangeRawTargetBrainOrAnger() {
        nativeMob.raw = owner;
        nativeMob.brain.values.put("attack", owner);
        protection = event -> event.setCancelled(true);
        assertFalse(retarget());
        assertSame(owner, nativeMob.raw);
        assertSame(owner, nativeMob.brain.values.get("attack"));
    }

    @Test
    void retargetPreservesExpiryAndMovesOnlyThisSessionsAnger() {
        nativeMob.raw = owner;
        nativeMob.brain.values.put("attack", owner);
        nativeMob.brain.times.put("attack", 77L);
        nativeMob.brain.values.put("anger", owner.uuid);
        nativeMob.brain.times.put("anger", 123L);
        nativeMob.angryAt = NativeReference.of(owner.uuid);
        assertTrue(retarget());
        assertSame(body, nativeMob.brain.values.get("attack"));
        assertEquals(77, nativeMob.brain.times.get("attack"));
        assertEquals(body.uuid, nativeMob.brain.values.get("anger"));
        assertEquals(123, nativeMob.brain.times.get("anger"));
        assertEquals(body.uuid, nativeMob.angryAt.uuid);
        assertEquals(1000, nativeMob.angerEnd);
    }

    @Test
    void unrelatedAngerIsNotRewritten() {
        UUID other = UUID.randomUUID();
        nativeMob.brain.values.put("anger", other);
        nativeMob.angryAt = NativeReference.of(other);
        assertTrue(retarget());
        assertEquals(other, nativeMob.angryAt.uuid);
        assertEquals(other, nativeMob.brain.values.get("anger"));
    }

    @Test
    void protectionPluginCanClearTargetAndBrainTogether() {
        nativeMob.raw = owner;
        nativeMob.brain.values.put("attack", owner);
        nativeMob.brain.values.put("anger", owner.uuid);
        protection = event -> event.setTarget(null);
        Handles.values.add(nativeMob);
        assertFalse(bridge.retarget(source, mock(LivingEntity.class), owner.uuid, body.uuid));
        assertNull(nativeMob.raw);
        assertFalse(nativeMob.brain.values.containsKey("attack"));
        assertFalse(nativeMob.brain.values.containsKey("anger"));
    }

    @Test
    void protectionPluginTargetRewriteIsHonoredInBothStorages() {
        NativeLiving replacement = new NativeLiving();
        LivingEntity bukkitReplacement = mock(LivingEntity.class);
        protection = event -> event.setTarget(bukkitReplacement);
        Handles.values.add(nativeMob);
        Handles.values.add(replacement);
        assertFalse(bridge.retarget(source, mock(LivingEntity.class), owner.uuid, body.uuid));
        assertSame(replacement, nativeMob.raw);
        assertSame(replacement, nativeMob.brain.values.get("attack"));
        verify(source.getServer().getPluginManager(), times(1)).callEvent(any(EntityTargetLivingEntityEvent.class));
    }

    @Test
    void unsupportedAttackMemoryIsNotInvented() {
        nativeMob.brain.keys.clear();
        assertTrue(retarget());
        assertTrue(nativeMob.brain.values.isEmpty());
    }

    @Test
    void nativeAngerExpirationAllowsMobToReturnToNeutral() {
        nativeMob.angryAt = NativeReference.of(body.uuid);
        Handles.values.add(nativeMob);
        assertTrue(bridge.retainsAnger(source, owner.uuid, body.uuid));
        nativeMob.angry = false;
        Handles.values.add(nativeMob);
        assertFalse(bridge.retainsAnger(source, owner.uuid, body.uuid));
    }

    @Test
    void nativeAcquisitionUsesFollowRangeAndHonorsRejection() {
        Handles.values.add(nativeMob);
        Handles.values.add(body);
        assertFalse(bridge.canAcquire(source, mock(LivingEntity.class)));
        assertEquals(12.0, Conditions.observedRange);
    }

    @Test
    void pacificationPreventsAcquisitionBeforeTargetSelection() {
        nativeMob.brain.keys.add("pacified");
        nativeMob.brain.values.put("pacified", true);
        Handles.values.add(nativeMob);
        assertFalse(bridge.canAcquire(source, mock(LivingEntity.class)));
    }

    @Test
    void cubeContactOnlyInvokesNativeDamageWhenItCanDealDamage() {
        NativeCube cube = new NativeCube();
        AbstractCubeMob bukkit = mock(AbstractCubeMob.class);
        Handles.values.add(cube);
        bridge.contactDamage(bukkit, mock(LivingEntity.class));
        assertEquals(0, cube.hits);
        cube.dealsDamage = true;
        Handles.values.add(cube);
        Handles.values.add(body);
        bridge.contactDamage(bukkit, mock(LivingEntity.class));
        assertEquals(1, cube.hits);
    }
}
