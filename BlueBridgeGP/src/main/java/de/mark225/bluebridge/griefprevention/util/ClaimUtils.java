package de.mark225.bluebridge.griefprevention.util;

import me.ryanhamshire.GriefPrevention.Claim;

public final class ClaimUtils {

    private ClaimUtils() {
    }

    public static boolean isDisplayAdminClaim(Claim claim) {
        // Subdivisions store no owner of their own; the top-level claim defines whether this is administrative.
        return getTopLevelClaim(claim).isAdminClaim();
    }

    public static Claim getTopLevelClaim(Claim claim) {
        Claim topLevelClaim = claim;
        while (topLevelClaim.parent != null) {
            topLevelClaim = topLevelClaim.parent;
        }
        return topLevelClaim;
    }
}
