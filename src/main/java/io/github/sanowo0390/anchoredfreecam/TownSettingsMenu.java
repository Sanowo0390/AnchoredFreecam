package io.github.sanowo0390.anchoredfreecam;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.List;

final class TownSettingsMenu implements InventoryHolder {
    static final int TOGGLE_SLOT = 13;

    private final TownyGuard guard;
    private final Messages messages;
    private final FreecamManager manager;
    private final TownyGuard.TownRef town;
    private final Inventory inventory;

    TownSettingsMenu(TownyGuard guard, Messages messages, FreecamManager manager, TownyGuard.TownRef town) {
        this.guard = guard;
        this.messages = messages;
        this.manager = manager;
        this.town = town;
        this.inventory = Bukkit.createInventory(this, 27,
                Component.text(messages.text("town-menu-title", "town", town.name())));
        render();
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    void open(Player player) {
        player.openInventory(inventory);
    }

    void click(Player player, int rawSlot) {
        if (rawSlot != TOGGLE_SLOT) {
            return;
        }

        TownyGuard.TownRef current = guard.townOf(player);
        if (current == null || !current.id().equals(town.id()) || !guard.canManage(player)) {
            player.sendMessage(Component.text(messages.text("town-menu-denied"), NamedTextColor.RED));
            return;
        }

        boolean enabled = !guard.isTownProtected(town.id());
        guard.setTownProtected(town.id(), enabled);
        render();
        player.sendMessage(Component.text(
                messages.text(enabled ? "town-menu-changed-on" : "town-menu-changed-off", "town", town.name()),
                enabled ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
        manager.enforceCurrentRange();
    }

    private void render() {
        boolean enabled = guard.isTownProtected(town.id());
        ItemStack item = new ItemStack(enabled ? Material.LIME_CONCRETE : Material.RED_CONCRETE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(line(enabled ? "town-menu-on" : "town-menu-off",
                enabled ? NamedTextColor.GREEN : NamedTextColor.RED));
        meta.lore(List.of(
                line(enabled ? "town-menu-lore-on" : "town-menu-lore-off", NamedTextColor.GRAY),
                line("town-menu-lore-click", NamedTextColor.YELLOW)));
        item.setItemMeta(meta);
        inventory.setItem(TOGGLE_SLOT, item);
    }

    private Component line(String key, TextColor color) {
        return Component.text(messages.text(key), color).decoration(TextDecoration.ITALIC, false);
    }
}
