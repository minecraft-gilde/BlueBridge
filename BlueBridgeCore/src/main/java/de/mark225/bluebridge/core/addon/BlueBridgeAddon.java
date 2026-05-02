package de.mark225.bluebridge.core.addon;

import de.mark225.bluebridge.core.region.RegionSnapshot;

import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

public abstract class BlueBridgeAddon {
    public abstract String name();

    public abstract AddonConfig addonConfig();

    public abstract String markerSetName();

    public abstract boolean defaultHide();

    public abstract ConcurrentMap<String, RegionSnapshot> fetchSnapshots(UUID world);

    public void loadInitialSnapshots(UUID world, Consumer<Collection<RegionSnapshot>> snapshotConsumer) {
        snapshotConsumer.accept(fetchSnapshots(world).values());
    }

    public boolean supportsAsync(){
        return false;
    }

    public abstract void reload();

    public abstract boolean usesEventUpdates();
}
