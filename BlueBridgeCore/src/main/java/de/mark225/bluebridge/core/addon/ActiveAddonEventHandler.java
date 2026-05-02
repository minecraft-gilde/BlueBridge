package de.mark225.bluebridge.core.addon;

import de.mark225.bluebridge.core.region.RegionSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BiConsumer;

public class ActiveAddonEventHandler {
    private static final Queue<RegionSnapshot> addedOrUpdated = new ConcurrentLinkedQueue<>();
    private static final Queue<RegionSnapshot> deleted = new ConcurrentLinkedQueue<>();

    public static void addOrUpdate(RegionSnapshot region) {
        addedOrUpdated.offer(region);
    }

    public static void delete(RegionSnapshot region) {
        deleted.offer(region);
    }

    public static void resetLists() {
        addedOrUpdated.clear();
        deleted.clear();
    }

    public static void collectAndReset(BiConsumer<List<RegionSnapshot>, List<RegionSnapshot>> callback) {
        callback.accept(drain(addedOrUpdated), drain(deleted));
    }

    private static List<RegionSnapshot> drain(Queue<RegionSnapshot> queue) {
        List<RegionSnapshot> snapshots = new ArrayList<>();
        RegionSnapshot snapshot;
        while ((snapshot = queue.poll()) != null) {
            snapshots.add(snapshot);
        }
        return snapshots;
    }

}
