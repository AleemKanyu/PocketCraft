package com.pocketcraft.companion;

import io.netty.channel.Channel;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.logging.Level;

public class PocketCraftCompanion extends JavaPlugin implements org.bukkit.event.Listener {
    @Override
    public void onEnable() {
        installDebugSubscriptionFix();
        getServer().getPluginManager().registerEvents(this, this);

        try {
            System.setSecurityManager(new SecurityManager() {
                @Override
                public void checkExit(int status) {
                    throw new SecurityException("PocketCraft JVM Exit Prevented");
                }
                @Override
                public void checkPermission(java.security.Permission perm) {
                    // Allow everything else
                }
            });
            getLogger().info("Successfully installed anti-exit SecurityManager.");
        } catch (Throwable t) {
            getLogger().warning("Failed to install SecurityManager: " + t.getMessage());
        }

        File staleSignal = new File(getDataFolder(), "graceful_stop.signal");
        if (staleSignal.exists()) {
            staleSignal.delete();
            getLogger().info("Cleaned up stale graceful_stop.signal from previous session.");
        }

        final Object minecraftServer = resolveMinecraftServer();
        if (minecraftServer == null) {
            getLogger().warning("Could not resolve MinecraftServer. Graceful stop will use Bukkit.shutdown() (may hang on Java 21+).");
        }

        Bukkit.getScheduler().runTaskTimer(this, () -> {
            File signalFile = new File(getDataFolder(), "graceful_stop.signal");
            if (signalFile.exists()) {
                getLogger().info("Graceful stop signal received. Shutting down server...");
                signalFile.delete();
                performGracefulStop(minecraftServer);
            }
        }, 20L, 20L);

        // Report player ping/telemetry to stdout every 5 seconds (100 ticks)
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (Bukkit.getOnlinePlayers().isEmpty()) return;
            StringBuilder sb = new StringBuilder("[PocketCraftPing] ");
            boolean hasPlayers = false;
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().startsWith("AFK_")) continue;
                int ping = getRealPlayerPing(p);
                String ip = (p.getAddress() != null && p.getAddress().getAddress() != null)
                        ? p.getAddress().getAddress().getHostAddress() : "";
                sb.append(p.getName()).append(":").append(ping).append("@").append(ip).append(" ");
                hasPlayers = true;
            }
            if (hasPlayers) {
                getLogger().info(sb.toString().trim());
            }
        }, 100L, 100L);


        // Periodic watchdog to force respawn dead dummy bots immediately and keep them in Creative mode
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            // Fix Spigot 1.21.11 compatibility: clear dummyChunks map in DummyManager
            // to bypass NoSuchFieldError exception when despawning (which references fields e/f).
            try {
                org.bukkit.plugin.Plugin dummyPlayersPlugin = Bukkit.getPluginManager().getPlugin("dummyplayers");
                if (dummyPlayersPlugin != null) {
                    Object dummyManager = dummyPlayersPlugin.getClass().getMethod("getDummyManager").invoke(dummyPlayersPlugin);
                    java.lang.reflect.Field dummyChunksField = dummyManager.getClass().getDeclaredField("dummyChunks");
                    dummyChunksField.setAccessible(true);
                    java.util.Map dummyChunks = (java.util.Map) dummyChunksField.get(dummyManager);
                    if (dummyChunks != null && !dummyChunks.isEmpty()) {
                        dummyChunks.clear();
                    }
                }
            } catch (Throwable ignored) {}

            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().startsWith("AFK_")) {
                    if (p.getGameMode() != org.bukkit.GameMode.CREATIVE) {
                        try {
                            getLogger().info("Watchdog detected dummy " + p.getName() + " in gamemode " + p.getGameMode() + ". Setting to CREATIVE...");
                            p.setGameMode(org.bukkit.GameMode.CREATIVE);
                        } catch (Throwable t) {
                            getLogger().warning("Failed to set gamemode for dummy " + p.getName() + ": " + t.getMessage());
                        }
                    }
                    if (p.isDead() || p.getHealth() <= 0.0) {
                        try {
                            getLogger().info("Watchdog detected dead dummy " + p.getName() + ". Force respawning...");
                            org.bukkit.Location loc = p.getLocation();
                            p.spigot().respawn();
                            p.teleport(loc);
                        } catch (Throwable t) {
                            getLogger().warning("Failed to watchdog-respawn " + p.getName() + ": " + t.getMessage());
                        }
                    }
                }
            }
        }, 30L, 30L); // Check every 1.5 seconds
    }

    /**
     * Paper 1.21.11 clients send debug_subscription_request right after join.
     * Some builds fail to decode it and the client sees "connection reset by peer".
     */
    private void installDebugSubscriptionFix() {
        try {
            Class<?> listenerClass = Class.forName("io.papermc.paper.network.ChannelInitializeListener");
            Class<?> holderClass = Class.forName("io.papermc.paper.network.ChannelInitializeListenerHolder");
            Class<?> keyClass = Class.forName("net.kyori.adventure.key.Key");

            Object key = keyClass.getMethod("key", String.class, String.class)
                .invoke(null, "pocketcraft", "debug_subscription_fix");

            Object listener = Proxy.newProxyInstance(
                listenerClass.getClassLoader(),
                new Class<?>[] { listenerClass },
                (proxy, method, args) -> {
                    if (!"afterInitChannel".equals(method.getName()) || args == null || args.length != 1) {
                        return null;
                    }
                    try {
                        Channel channel = (Channel) args[0];
                        var pipeline = channel.pipeline();
                        if (pipeline.get("packet_handler") != null) {
                            pipeline.addBefore(
                                "packet_handler",
                                "pocketcraft-debug-subscription-shield",
                                new DebugSubscriptionShield()
                            );
                        } else {
                            pipeline.addLast(
                                "pocketcraft-debug-subscription-shield",
                                new DebugSubscriptionShield()
                            );
                        }
                    } catch (Throwable ignored) {
                        // Never break player connections if shield injection fails.
                    }
                    return null;
                }
            );

            holderClass.getMethod("addListener", keyClass, listenerClass)
                .invoke(null, key, listener);
            getLogger().info("Installed debug_subscription_request decode shield.");
        } catch (Throwable t) {
            getLogger().log(Level.WARNING, "Could not install debug subscription shield: " + t.getMessage(), t);
        }
    }

    private Object resolveMinecraftServer() {
        try {
            Class<?> craftServerClass = Class.forName("org.bukkit.craftbukkit.CraftServer");
            Method getServerMethod = craftServerClass.getDeclaredMethod("getServer");
            return getServerMethod.invoke(Bukkit.getServer());
        } catch (Exception e) {
            getLogger().warning("Failed to resolve MinecraftServer: " + e.getMessage());
            return null;
        }
    }

    private void performGracefulStop(Object mcServer) {
        if (mcServer == null) {
            getLogger().warning("No MinecraftServer reference, falling back to Bukkit.shutdown()");
            Bukkit.shutdown();
            return;
        }

        new Thread(() -> {
            try {
                Method stopServer = mcServer.getClass().getDeclaredMethod("stopServer");
                stopServer.setAccessible(true);
                stopServer.invoke(mcServer);
                getLogger().info("Server stopped gracefully (System.exit bypassed).");
            } catch (Exception e) {
                getLogger().warning("Reflection stopServer() failed: " + e.getMessage() + ". Falling back to Bukkit.shutdown()");
                Bukkit.shutdown();
            }
        }, "PocketCraft-Shutdown-Thread").start();
    }

    @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.HIGH)
    public void onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        if (player.getName().startsWith("AFK_")) return; // Skip dummy bots

        // Do not override per-player view distance here. Paper should use the
        // server/user configured render distance without temporary join throttles.
        getLogger().info("[PocketCraft] Respecting configured view distance for " + player.getName());
    }

    @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.MONITOR)
    public void onPlayerDeath(org.bukkit.event.entity.PlayerDeathEvent event) {
        final Player player = event.getEntity();
        if (player.getName().startsWith("AFK_")) {
            event.setKeepInventory(true);
            event.getDrops().clear();
            event.setDroppedExp(0);
            final org.bukkit.Location deathLoc = player.getLocation();
            final java.util.UUID uuid = player.getUniqueId();
            final String name = player.getName();
            
            getLogger().info("Dummy player " + name + " died. Scheduling respawn...");
            
            Bukkit.getScheduler().runTaskLater(this, () -> {
                try {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p == null) {
                        p = Bukkit.getPlayer(name);
                    }
                    if (p != null) {
                        p.spigot().respawn();
                        p.teleport(deathLoc);
                        getLogger().info("Successfully respawned dummy player " + name);
                    } else {
                        getLogger().warning("Could not find player entity for dummy " + name + " to respawn.");
                    }
                } catch (Throwable t) {
                    getLogger().log(Level.WARNING, "Error respawning dummy " + name + ": " + t.getMessage(), t);
                }
            }, 5L); // Respawn even faster (0.25 seconds)
        }
    }

    private int getRealPlayerPing(Player p) {
        // 1. Try Reflection for Geyser API connection ping
        try {
            Class<?> geyserClass = Class.forName("org.geysermc.geyser.api.GeyserApi");
            Object api = geyserClass.getMethod("api").invoke(null);
            if (api != null) {
                Object conn = geyserClass.getMethod("connectionByUuid", java.util.UUID.class).invoke(api, p.getUniqueId());
                if (conn != null) {
                    Object pingObj = conn.getClass().getMethod("ping").invoke(conn);
                    if (pingObj instanceof Number) {
                        int geyserPing = ((Number) pingObj).intValue();
                        if (geyserPing >= 0 && geyserPing < 2000) {
                            return geyserPing;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        // 2. Fallback to Spigot getPing() with loopback artifact sanitization
        int rawPing = p.getPing();
        if (rawPing < 0 || rawPing > 2000) {
            // Spigot loopback timestamp diff artifact: default to clean estimated RTT
            return 25;
        }
        return rawPing;
    }
}
