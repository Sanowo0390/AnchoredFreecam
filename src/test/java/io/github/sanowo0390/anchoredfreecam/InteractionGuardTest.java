package io.github.sanowo0390.anchoredfreecam;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InteractionGuardTest {
    private final FreecamManager manager = mock(FreecamManager.class);
    private final FreecamListener listener = new FreecamListener(mock(AnchoredFreecamPlugin.class), manager);
    private final Player player = mock(Player.class);
    private final Entity target = mock(Entity.class);

    @ParameterizedTest
    @EnumSource(value = EquipmentSlot.class, names = {"HAND", "OFF_HAND"})
    void ownerClickCannotBeReenabledByMyPetMonitor(EquipmentSlot hand) {
        when(manager.isActive(player)).thenReturn(true);
        PlayerInteractEntityEvent event = new PlayerInteractEntityEvent(player, target, hand);
        listener.onInteractEntity(event);
        assertTrue(event.isCancelled());
        event.setCancelled(false); // MyPet 3.14.3 PlayerListener.on at MONITOR
        listener.onEntityInteractionResult(event);
        assertTrue(event.isCancelled());
        verify(manager, never()).stop(player, true, true);
    }

    @ParameterizedTest
    @EnumSource(value = EquipmentSlot.class, names = {"HAND", "OFF_HAND"})
    void preciseClickCannotBeReenabledEither(EquipmentSlot hand) {
        when(manager.isActive(player)).thenReturn(true);
        PlayerInteractAtEntityEvent event = new PlayerInteractAtEntityEvent(player, target, new Vector(), hand);
        listener.onInteractAtEntity(event);
        event.setCancelled(false);
        listener.onPreciseEntityInteractionResult(event);
        assertTrue(event.isCancelled());
    }

    @Test
    void finalGuardRunsAtMonitorEvenWhenAlreadyCancelled() throws Exception {
        for (String name : new String[]{"onEntityInteractionResult", "onPreciseEntityInteractionResult"}) {
            Class<?> type = name.equals("onEntityInteractionResult") ? PlayerInteractEntityEvent.class : PlayerInteractAtEntityEvent.class;
            EventHandler handler = FreecamListener.class.getMethod(name, type).getAnnotation(EventHandler.class);
            assertEquals(EventPriority.MONITOR, handler.priority());
            assertFalse(handler.ignoreCancelled());
        }
    }

    @Test
    void normalPetInteractionsStayAllowedAndExistingCancellationIsPreserved() {
        PlayerInteractEntityEvent event = new PlayerInteractEntityEvent(player, target);
        listener.onEntityInteractionResult(event);
        assertFalse(event.isCancelled());
        event.setCancelled(true);
        listener.onEntityInteractionResult(event);
        assertTrue(event.isCancelled());
    }

    @Test
    void finalGuardAlsoProtectsOtherPlayersCamerasAndBodies() {
        when(manager.isFreecamVehicle(target)).thenReturn(true);
        PlayerInteractEntityEvent event = new PlayerInteractEntityEvent(player, target);
        listener.onEntityInteractionResult(event);
        assertTrue(event.isCancelled());
    }

    @Test
    void inventoryChangesAreDeniedBeforePluginClickHandlers() {
        when(manager.isActive(player)).thenReturn(true);
        InventoryClickEvent click = mock(InventoryClickEvent.class);
        when(click.getWhoClicked()).thenReturn(player);
        listener.onInventoryClickEarly(click);
        verify(click).setCancelled(true);
        InventoryDragEvent drag = mock(InventoryDragEvent.class);
        when(drag.getWhoClicked()).thenReturn(player);
        listener.onInventoryDragEarly(drag);
        verify(drag).setCancelled(true);
    }

    @Test
    void petOrOtherContainerCannotOpenDuringFreecam() {
        when(manager.isActive(player)).thenReturn(true);
        InventoryOpenEvent event = mock(InventoryOpenEvent.class);
        when(event.getPlayer()).thenReturn(player);
        listener.onInventoryOpenEarly(event);
        listener.onInventoryOpen(event);
        verify(event, times(2)).setCancelled(true);
    }

    @Test
    void normalPlayerContainersStayAllowed() {
        InventoryOpenEvent event = mock(InventoryOpenEvent.class);
        when(event.getPlayer()).thenReturn(player);
        listener.onInventoryOpenEarly(event);
        listener.onInventoryOpen(event);
        verify(event, never()).setCancelled(anyBoolean());
    }
}
