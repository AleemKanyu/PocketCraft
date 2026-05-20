package com.pocketcraft.companion;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.lang.reflect.Method;

public class PocketCraftCompanion extends JavaPlugin {
    @Override
    public void onEnable() {
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

        // Clean up any stale stop signal from a previous failed shutdown
        File staleSignal = new File(getDataFolder(), "graceful_stop.signal");
        if (staleSignal.exists()) {
            staleSignal.delete();
            getLogger().info("Cleaned up stale graceful_stop.signal from previous session.");
        }

        // Cache the MinecraftServer for graceful stop. Resolved via reflection
        // because the plugin is compiled against an older Paper API (1.20.4)
        // but runs on Paper 1.21.11+.
        final Object minecraftServer = resolveMinecraftServer();
        if (minecraftServer == null) {
            getLogger().warning("Could not resolve MinecraftServer. Graceful stop will use Bukkit.shutdown() (may hang on Java 21+).");
        }

        // Graceful stop signal monitor - checks for a stop signal file every 1 second
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
                sb.append(" ").append(p.getName()).append(":").append(p.getPing());
            }
            getLogger().info(sb.toString());
        }, 20L, 20L);
    }

    /**
     * Resolves the MinecraftServer instance via reflection.
     * Uses CraftServer.getServer() to access MinecraftServer.stopServer().
     */
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

    /**
     * Performs a graceful server stop that saves everything and closes the socket
     * WITHOUT calling System.exit(). This avoids hangs on Java 21+ where the
     * SecurityManager is deprecated and Bukkit.shutdown()'s System.exit() hangs
     * the embedded JVM.
     */
    private void performGracefulStop(Object mcServer) {
        if (mcServer == null) {
            getLogger().warning("No MinecraftServer reference, falling back to Bukkit.shutdown()");
            Bukkit.shutdown();
            return;
        }

        // Run in a separate thread so we don't block the Bukkit Scheduler task
        // (which runs on the main server thread). If we block the main thread, 
        // the server cannot finish its tick loop and deadlocks during shutdown.
        new Thread(() -> {
            try {
                // MinecraftServer.stopServer() saves players, chunks, and closes the
                // server socket. It also sets running = false. The only thing it doesn't
                // do is call System.exit(0), which is exactly what we want.
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
