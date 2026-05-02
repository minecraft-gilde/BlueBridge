package de.mark225.bluebridge.griefprevention.addon;

import de.mark225.bluebridge.core.addon.AddonConfig;
import de.mark225.bluebridge.core.addon.BlueBridgeAddon;
import de.mark225.bluebridge.core.region.RegionSnapshot;
import de.mark225.bluebridge.griefprevention.BlueBridgeGP;
import de.mark225.bluebridge.griefprevention.config.BlueBridgeGPConfig;

import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class BlueBridgeGPAddon extends BlueBridgeAddon {
    @Override
    public String name() {
        return "BlueBridgeGP";
    }

    @Override
    public AddonConfig addonConfig() {
        return BlueBridgeGPConfig.getInstance();
    }

    @Override
    public String markerSetName() {
        return BlueBridgeGPConfig.getInstance().markerSetName();
    }

    @Override
    public boolean defaultHide() {
        return BlueBridgeGPConfig.getInstance().defaultHideSets();
    }

    @Override
    public ConcurrentMap<String, RegionSnapshot> fetchSnapshots(UUID world) {
        return BlueBridgeGP.getInstance().getGPIntegration().getAllClaims(world).stream().collect(Collectors.toConcurrentMap(RegionSnapshot::getId, rs -> rs));
    }

    @Override
    public void loadInitialSnapshots(UUID world, Consumer<Collection<RegionSnapshot>> snapshotConsumer) {
        BlueBridgeGP.getInstance().getGPIntegration().loadInitialClaims(world);
    }

    @Override
    public void reload() {
        BlueBridgeGP.getInstance().updateConfig();
    }

    @Override
    public boolean usesEventUpdates() {
        return true;
    }
}
