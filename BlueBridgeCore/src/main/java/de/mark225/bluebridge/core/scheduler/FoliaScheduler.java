package de.mark225.bluebridge.core.scheduler;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

public final class FoliaScheduler {

    private FoliaScheduler() {
    }

    public static void runGlobal(Plugin plugin, Runnable runnable) {
        Bukkit.getGlobalRegionScheduler().execute(plugin, runnable);
    }

    public static ScheduledTask runGlobalLater(Plugin plugin, Runnable runnable, long delayTicks) {
        long normalizedDelay = Math.max(1L, delayTicks);
        return Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> runnable.run(), normalizedDelay);
    }

    public static void runAsync(Plugin plugin, Runnable runnable) {
        Bukkit.getAsyncScheduler().runNow(plugin, task -> runnable.run());
    }

    public static void runAtLocation(Plugin plugin, Location location, Runnable runnable) {
        Bukkit.getRegionScheduler().execute(plugin, location, runnable);
    }

    public static ScheduledTask runAtLocationLater(Plugin plugin, Location location, Runnable runnable, long delayTicks) {
        long normalizedDelay = Math.max(1L, delayTicks);
        return Bukkit.getRegionScheduler().runDelayed(plugin, location, task -> runnable.run(), normalizedDelay);
    }
}
