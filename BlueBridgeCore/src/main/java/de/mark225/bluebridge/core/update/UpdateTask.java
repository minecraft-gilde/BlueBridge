package de.mark225.bluebridge.core.update;

import de.bluecolored.bluemap.api.BlueMapWorld;
import de.mark225.bluebridge.core.BlueBridgeCore;
import de.mark225.bluebridge.core.addon.ActiveAddonEventHandler;
import de.mark225.bluebridge.core.addon.AddonRegistry;
import de.mark225.bluebridge.core.addon.BlueBridgeAddon;
import de.mark225.bluebridge.core.bluemap.BlueMapIntegration;
import de.mark225.bluebridge.core.config.BlueBridgeConfig;
import de.mark225.bluebridge.core.region.RegionSnapshot;
import de.mark225.bluebridge.core.scheduler.FoliaScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class UpdateTask implements Runnable {

    public static ConcurrentMap<UUID, BlueMapWorld> worlds = new ConcurrentHashMap<>();

    private static ConcurrentMap<String, ConcurrentMap<String, RegionSnapshot>> lastSnapshots = new ConcurrentHashMap<>();

    private static ScheduledTask currentTask;

    private static boolean scheduledOrRunning;

    private static boolean locked = true;

    public static synchronized void resetLastSnapshots() {
        lastSnapshots.clear();
    }

    public static synchronized void createAndSchedule(boolean instant) {
        if (!scheduledOrRunning && !locked) {
            scheduledOrRunning = true;
            if (instant) {
                FoliaScheduler.runGlobal(BlueBridgeCore.getInstance(), new UpdateTask());
            } else {
                currentTask = FoliaScheduler.runGlobalLater(BlueBridgeCore.getInstance(), new UpdateTask(), BlueBridgeConfig.updateInterval());
            }
        }
    }

    public static synchronized void setLocked(boolean locked) {
        UpdateTask.locked = locked;
        if (locked) {
            if (currentTask != null) {
                currentTask.cancel();
                currentTask = null;
            }
            scheduledOrRunning = false;
        }
    }

    private UpdateTask() {

    }

    @Override
    public void run() {
        synchronized (UpdateTask.class) {
            currentTask = null;
        }
        if (locked) {
            finish();
            return;
        }

        List<BlueBridgeAddon> addons = AddonRegistry.getPollingAddons();
        ConcurrentMap<String, ConcurrentMap<String, RegionSnapshot>> newSnapshots = new ConcurrentHashMap<>();
        for (BlueBridgeAddon addon : addons) {
            if(addon.supportsAsync()) continue;
            collectSnapshots(addon, newSnapshots);
        }

        if (addons.stream().noneMatch(BlueBridgeAddon::supportsAsync)) {
            doUpdate(newSnapshots);
            return;
        }

        FoliaScheduler.runAsync(BlueBridgeCore.getInstance(), () ->{
            for (BlueBridgeAddon addon : addons) {
                if(!addon.supportsAsync()) continue;
                collectSnapshots(addon, newSnapshots);
            }
            FoliaScheduler.runGlobal(BlueBridgeCore.getInstance(), () -> doUpdate(newSnapshots));
        });
    }

    private void collectSnapshots(BlueBridgeAddon addon, ConcurrentMap<String, ConcurrentMap<String, RegionSnapshot>> newSnapshots){
        newSnapshots.putIfAbsent(addon.name(), new ConcurrentHashMap<>());
        for (UUID world : worlds.keySet()) {
            ConcurrentMap<String, RegionSnapshot> worldSnapshots = addon.fetchSnapshots(world);
            newSnapshots.get(addon.name()).putAll(worldSnapshots);
        }
    }

    private void doSyncUpdate(BlueMapIntegration integration) {
        ActiveAddonEventHandler.collectAndReset((addedOrUpdated, deleted) -> {
            integration.addOrUpdate(addedOrUpdated);
            integration.remove(deleted);
        });
    }

    private void doUpdate(ConcurrentMap<String, ConcurrentMap<String, RegionSnapshot>> newSnapshots) {
        List<RegionSnapshot> addedOrUpdated = new ArrayList<>();
        List<RegionSnapshot> removed = new ArrayList<>();
        Set<String> addonKeys = new HashSet<>(lastSnapshots.keySet());
        addonKeys.addAll(newSnapshots.keySet());
        addonKeys.forEach(addon -> {
            ConcurrentMap<String, RegionSnapshot> regionMap = newSnapshots.getOrDefault(addon, new ConcurrentHashMap<>());
            ConcurrentMap<String, RegionSnapshot> lastRegionMap = lastSnapshots.getOrDefault(addon, new ConcurrentHashMap<>());

            lastRegionMap.forEach((oldKey, region) -> {
                if (!regionMap.containsKey(oldKey)) {
                    removed.add(region);
                }
            });
            regionMap.forEach((key, region) -> {
                RegionSnapshot lastRegion = lastRegionMap.get(key);
                if (lastRegion == null || !lastRegion.equals(region)) {
                    addedOrUpdated.add(region);
                }
            });
        });
        BlueMapIntegration integration = BlueBridgeCore.getInstance().getBlueMapIntegration();
        integration.addOrUpdate(addedOrUpdated);
        integration.remove(removed);
        doSyncUpdate(integration);
        lastSnapshots = newSnapshots;
        reschedule();
    }

    public synchronized void reschedule() {
        finish();
        BlueBridgeCore.getInstance().reschedule();
    }

    private static synchronized void finish() {
        currentTask = null;
        scheduledOrRunning = false;
    }

}
