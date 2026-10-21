package io.github.sanowo0390.anchoredfreecam;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.luckperms.api.platform.PlayerAdapter;
import org.bukkit.Server;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicesManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BodyNameplateTest {
    private AnchoredFreecamPlugin plugin;
    private Player player;
    private Server server;
    private PluginManager plugins;
    private YamlConfiguration config;

    @BeforeEach
    void setup() {
        plugin = mock(AnchoredFreecamPlugin.class);
        player = mock(Player.class);
        server = mock(Server.class);
        plugins = mock(PluginManager.class);
        config = new YamlConfiguration();
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(plugins);
        when(player.getName()).thenReturn("PlayerA");
    }

    private String text(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    @SuppressWarnings("unchecked")
    private CachedMetaData luckPerms() {
        when(plugins.isPluginEnabled("LuckPerms")).thenReturn(true);
        ServicesManager services = mock(ServicesManager.class);
        when(server.getServicesManager()).thenReturn(services);
        RegisteredServiceProvider<LuckPerms> registration = mock(RegisteredServiceProvider.class);
        when(services.getRegistration(LuckPerms.class)).thenReturn(registration);
        LuckPerms lp = mock(LuckPerms.class);
        when(registration.getProvider()).thenReturn(lp);
        PlayerAdapter<Player> adapter = mock(PlayerAdapter.class);
        when(lp.getPlayerAdapter(Player.class)).thenReturn(adapter);
        CachedMetaData meta = mock(CachedMetaData.class);
        when(adapter.getMetaData(player)).thenReturn(meta);
        return meta;
    }

    @Test
    void absentLuckPermsLeavesPlainPlayerName() {
        assertEquals("PlayerA", text(new BodyNameplate(plugin).name(player)));
        verify(server, never()).getServicesManager();
    }

    @Test
    void contextualPrefixIsRenderedAndRefreshReadsNewValue() {
        CachedMetaData meta = luckPerms();
        when(meta.getPrefix()).thenReturn("&a[Member] ", "&c[Admin] ");
        var names = new BodyNameplate(plugin);
        assertEquals("[Member] PlayerA", text(names.name(player)));
        assertEquals("[Admin] PlayerA", text(names.name(player)));
    }

    @Test
    void nullPrefixAndDisabledSettingLeaveNameUnchanged() {
        CachedMetaData meta = luckPerms();
        var names = new BodyNameplate(plugin);
        assertEquals("PlayerA", text(names.name(player)));
        config.set("show-luckperms-prefix", false);
        when(meta.getPrefix()).thenReturn("[Ignored] ");
        assertEquals("PlayerA", text(names.name(player)));
    }

    @Test
    void legacySectionHexAndMiniMessageFormatsAreSupported() {
        assertEquals(Component.text("[VIP] ", NamedTextColor.GREEN), BodyNameplate.format("&a[VIP] "));
        assertEquals(text(BodyNameplate.format("&a[VIP] ")), text(BodyNameplate.format("§a[VIP] ")));
        assertEquals("[VIP] ", text(BodyNameplate.format("&#12ab34[VIP] ")));
        assertEquals(Component.text("[VIP] ", NamedTextColor.GOLD), BodyNameplate.format("<gold>[VIP] "));
    }
}
