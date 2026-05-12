package de.mark225.bluebridge.griefprevention.addon;

import com.flowpowered.math.vector.Vector2d;
import de.bluecolored.bluemap.api.math.Color;
import de.mark225.bluebridge.core.addon.ActiveAddonEventHandler;
import de.mark225.bluebridge.core.region.RegionSnapshot;
import de.mark225.bluebridge.core.region.RegionSnapshotBuilder;
import de.mark225.bluebridge.core.scheduler.FoliaScheduler;
import de.mark225.bluebridge.core.util.BlueBridgeUtils;
import de.mark225.bluebridge.griefprevention.BlueBridgeGP;
import de.mark225.bluebridge.griefprevention.addon.listener.GriefPreventionListener;
import de.mark225.bluebridge.griefprevention.config.BlueBridgeGPConfig;
import de.mark225.bluebridge.griefprevention.util.ClaimUtils;
import de.mark225.bluebridge.griefprevention.util.ClaimStringLookup;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.util.BoundingBox;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class GriefPreventionIntegration {
    private static final float SUBCLAIM_LAYER_HEIGHT_STEP = 0.25f;
    private static final float SUBCLAIM_FILL_ALPHA_STEP = 0.08f;
    private static final float SUBCLAIM_BRIGHTNESS_STEP = 0.08f;

    public void init() {
        Bukkit.getPluginManager().registerEvents(new GriefPreventionListener(), BlueBridgeGP.getInstance());
    }

    public void addOrUpdateClaim(Claim claim) {
        //Schedule updates for all child claims
        for (Claim child : childClaims(claim)) {
            FoliaScheduler.runAtLocation(BlueBridgeGP.getInstance(), child.getLesserBoundaryCorner(), () -> {
                if (child.inDataStore) {
                    addOrUpdateClaim(child);
                }
            });
        }

        ActiveAddonEventHandler.addOrUpdate(convertClaim(claim));
    }

    public void loadInitialClaims(UUID world) {
        for (Claim claim : topLevelClaims()) {
            if (!claim.inDataStore || !isClaimInWorld(claim, world)) {
                continue;
            }

            FoliaScheduler.runAtLocation(BlueBridgeGP.getInstance(), claim.getLesserBoundaryCorner(), () -> {
                if (claim.inDataStore && isClaimInWorld(claim, world)) {
                    addOrUpdateClaim(claim);
                }
            });
        }
    }

    private Collection<Claim> topLevelClaims() {
        return new ArrayList<>(GriefPrevention.instance.dataStore.getClaims());
    }

    private RegionSnapshot convertClaim(Claim claim) {
        //Figure out claim depth (how many layers of parents are above this claim)
        int layer = 0;
        Claim currentClaim = claim;
        while (currentClaim.parent != null) {
            layer++;
            currentClaim = currentClaim.parent;
        }

        BoundingBox boundingBox = trimmedBoundingBox(claim);
        List<Vector2d> points = boundingBoxToPolyCorners(boundingBox);
        boolean adminClaim = ClaimUtils.isDisplayAdminClaim(claim);
        boolean subclaim = claim.parent != null;
        String shortName = adminClaim ? BlueBridgeGPConfig.getInstance().adminDisplayName() : claim.getOwnerName() + "'s Claim";
        String label = BlueBridgeUtils.replace(new ClaimStringLookup(claim), BlueBridgeGPConfig.getInstance().htmlDisplay());
        int claimFloor = claim.getLesserBoundaryCorner().getBlockY();
        int claimCeiling = claim.getGreaterBoundaryCorner().getBlockY() + 1;
        boolean extrude = BlueBridgeGPConfig.getInstance().defaultExtrude();
        float heightModifier = 0f;
        //Adjust height if plugin is configured to layer child claims and 3D markers are off
        if (!extrude && BlueBridgeGPConfig.getInstance().layerChildren()) {
            heightModifier = (float) layer * SUBCLAIM_LAYER_HEIGHT_STEP;
        }

        Color borderColor;
        Color fillColor;
        if (adminClaim) {
            borderColor = BlueBridgeGPConfig.getInstance().adminOutlineColor();
            fillColor = BlueBridgeGPConfig.getInstance().adminFillColor();
        } else if (subclaim) {
            borderColor = adjustSubclaimColor(BlueBridgeGPConfig.getInstance().defaultOutlineColor(), layer, false);
            fillColor = adjustSubclaimColor(BlueBridgeGPConfig.getInstance().defaultColor(), layer, true);
        } else {
            borderColor = BlueBridgeGPConfig.getInstance().defaultOutlineColor();
            fillColor = BlueBridgeGPConfig.getInstance().defaultColor();
        }

        return new RegionSnapshotBuilder(BlueBridgeGP.getInstance().getAddon(), claim.getID().toString(), points, claim.getLesserBoundaryCorner().getWorld().getUID())
                .setHtmlDisplay(label)
                .setShortName(shortName)
                .setHeight(extrude ? claimFloor : (float) BlueBridgeGPConfig.getInstance().renderHeight() + heightModifier)
                .setExtrude(extrude)
                .setUpperHeight(claimCeiling)
                .setColor(fillColor)
                .setBorderColor(borderColor)
                .build();
    }

    public void removeClaim(Claim claim) {
        ActiveAddonEventHandler.delete(new RegionSnapshotBuilder(BlueBridgeGP.getInstance().getAddon(), claim.getID().toString(), Collections.emptyList(), claim.getLesserBoundaryCorner().getWorld().getUID()).build());
    }

    public List<RegionSnapshot> getAllClaims(UUID world) {
        return topLevelClaims().stream()
                .filter(claim -> isClaimInWorld(claim, world))
                .flatMap(claim -> claimAndChildren(claim).stream())
                .filter(claim -> claim.inDataStore)
                .map(claim -> convertClaim(claim))
                .collect(Collectors.toList());
    }

    private List<Claim> claimAndChildren(Claim claim) {
        List<Claim> claims = new ArrayList<>();
        claims.add(claim);
        for (Claim child : childClaims(claim)) {
            claims.addAll(claimAndChildren(child));
        }
        return claims;
    }

    private List<Claim> childClaims(Claim claim) {
        if (claim.children == null || claim.children.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(claim.children);
    }

    private boolean isClaimInWorld(Claim claim, UUID world) {
        World claimWorld = claim.getLesserBoundaryCorner().getWorld();
        return claimWorld != null && claimWorld.getUID().equals(world);
    }


    private BoundingBox trimmedBoundingBox(Claim claim) {
        BoundingBox bb = new BoundingBox(claim);
        while (claim.parent != null) {
            claim = claim.parent;
            BoundingBox parentBox = new BoundingBox(claim);
            //Failsafe for parent claims that don't overlap with their child. Should probably never happen.
            if (!parentBox.intersects(bb)) return bb;
            bb = parentBox.intersection(bb);
        }
        return bb;
    }

    private List<Vector2d> boundingBoxToPolyCorners(BoundingBox bb) {
        List<Vector2d> points = new ArrayList<>();
        points.add(new Vector2d(bb.getMinX(), bb.getMinZ()));
        points.add(new Vector2d(bb.getMaxX() + 1, bb.getMinZ()));
        points.add(new Vector2d(bb.getMaxX() + 1, bb.getMaxZ() + 1));
        points.add(new Vector2d(bb.getMinX(), bb.getMaxZ() + 1));
        return points;
    }

    private Color adjustSubclaimColor(Color color, int layer, boolean fill) {
        int depth = Math.max(0, layer - 1);
        int red = brighten(color.getRed(), depth);
        int green = brighten(color.getGreen(), depth);
        int blue = brighten(color.getBlue(), depth);
        float alpha = fill ? Math.min(0.85f, color.getAlpha() + depth * SUBCLAIM_FILL_ALPHA_STEP) : color.getAlpha();
        return new Color(red, green, blue, alpha);
    }

    private int brighten(int channel, int depth) {
        float amount = Math.min(0.35f, depth * SUBCLAIM_BRIGHTNESS_STEP);
        return Math.min(255, Math.round(channel + (255 - channel) * amount));
    }


}
