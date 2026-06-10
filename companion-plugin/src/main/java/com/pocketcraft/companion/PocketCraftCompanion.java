package com.pocketcraft.companion;

import io.netty.channel.Channel;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.logging.Level;

public class PocketCraftCompanion extends JavaPlugin {
    @Override
    public void onEnable() {
        installDebugSubscriptionFix();

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

        Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (Bukkit.getOnlinePlayers().isEmpty()) return;
            StringBuilder sb = new StringBuilder("[PocketCraftPing]");
            for (Player p : Bukkit.getOnlinePlayers()) {
                String host = "";
                if (p.getAddress() != null) {
                    if (p.getAddress().getAddress() != null) {
                        host = p.getAddress().getAddress().getHostAddress();
                    } else {
                        host = p.getAddress().getHostString();
                    }
                }
                sb.append(" ").append(p.getName()).append(":").append(p.getPing());
                if (!host.isEmpty()) {
                    sb.append("@").append(host);
                }
            }
            getLogger().info(sb.toString());
        }, 100L, 100L);
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
}
