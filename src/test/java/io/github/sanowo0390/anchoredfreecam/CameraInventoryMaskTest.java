package io.github.sanowo0390.anchoredfreecam;

import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CameraInventoryMaskTest {
    @Test
    void sendsSelectedSlotAndAllEquipmentWithoutWritingInventory() {
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getHeldItemSlot()).thenReturn(4);
        List<Integer> slots = new ArrayList<>();
        var mask = new CameraInventoryMask((recipient, slot, contents) -> {
            assertSame(player, recipient);
            slots.add(slot);
        }, mock(Logger.class));
        mask.mask(player);
        assertEquals(List.of(40, 5, 6, 7, 8, 45), slots);
        verify(inventory).getHeldItemSlot();
        verifyNoMoreInteractions(inventory);
    }

    @Test
    void hotbarChangeMasksNewSelectionInsteadOfClearingEveryHotbarSlot() {
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getHeldItemSlot()).thenReturn(0, 8);
        List<Integer> slots = new ArrayList<>();
        ItemStack original = mock(ItemStack.class);
        when(inventory.getItem(0)).thenReturn(original);
        List<ItemStack> contentsSent = new ArrayList<>();
        var mask = new CameraInventoryMask((recipient, slot, contents) -> {
            slots.add(slot);
            contentsSent.add(contents);
        }, mock(Logger.class));
        mask.mask(player);
        mask.mask(player);
        assertEquals(36, slots.get(0));
        assertEquals(36, slots.get(6));
        assertEquals(44, slots.get(7));
        assertEquals(13, slots.size());
        assertFalse(slots.contains(1));
        assertSame(original, contentsSent.get(6));
        assertNull(contentsSent.get(7));
        verify(inventory, never()).setItem(anyInt(), any());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6, 7, 8})
    void eachSelectionOnlyMasksItsOwnHotbarSlot(int selected) {
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getHeldItemSlot()).thenReturn(selected);
        List<Integer> slots = new ArrayList<>();
        var mask = new CameraInventoryMask((recipient, slot, contents) -> {
            assertNull(contents);
            slots.add(slot);
        }, mock(Logger.class));
        mask.mask(player);
        assertEquals(List.of(36 + selected, 5, 6, 7, 8, 45), slots);
        slots.clear();
        mask.release(player);
        when(inventory.getHeldItemSlot()).thenReturn((selected + 1) % 9);
        mask.mask(player);
        assertEquals(6, slots.size(), "Ended sessions must not restore stale slots");
    }

    @Test
    void packetFailureRestoresInventoryAndStopsRetries() {
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);
        Logger logger = mock(Logger.class);
        var mask = new CameraInventoryMask((recipient, slot, contents) -> {
            throw new ReflectiveOperationException("unsupported runtime");
        }, logger);
        mask.mask(player);
        mask.mask(player);
        verify(player, times(1)).updateInventory();
        verify(logger, times(1)).warning(anyString());
    }

    @Test
    void unavailableAdapterDoesNotTouchInventory() {
        Player player = mock(Player.class);
        new CameraInventoryMask(null, mock(Logger.class)).mask(player);
        verifyNoInteractions(player);
    }
}
