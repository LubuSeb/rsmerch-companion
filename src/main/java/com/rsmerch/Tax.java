package com.rsmerch;

import java.util.Set;
import net.runelite.api.gameval.ItemID;

/** Rule-based estimate: the client exposes aggregate gross cash, not each execution's tax. */
public final class Tax {
    private Tax() { }
    private static final Set<Integer> ALWAYS_EXEMPT = Set.of(
        ItemID.CHISEL, ItemID.GARDENING_TROWEL, ItemID.HAMMER, ItemID.NEEDLE,
        ItemID.OSRS_BOND, ItemID.PESTLE_AND_MORTAR, ItemID.RAKE, ItemID.POH_SAW,
        ItemID.SECATEURS, ItemID.DIBBER, ItemID.SHEARS, ItemID.SPADE, ItemID.WATERING_CAN_0);
    private static final Set<Integer> NEW_EXEMPT = Set.of(
        ItemID.POH_TABLET_ARDOUGNETELEPORT, ItemID.BASS, ItemID.BREAD,
        ItemID.BRONZE_ARROW, ItemID.BRONZE_DART, ItemID.CAKE, ItemID.POH_TABLET_CAMELOTTELEPORT,
        ItemID.POH_TABLET_FORTISTELEPORT, ItemID.COOKED_CHICKEN, ItemID.COOKED_MEAT,
        ItemID._4DOSE1ENERGY, ItemID._3DOSE1ENERGY, ItemID._2DOSE1ENERGY, ItemID._1DOSE1ENERGY,
        ItemID.POH_TABLET_FALADORTELEPORT, ItemID.NECKLACE_OF_MINIGAMES_8, ItemID.HERRING,
        ItemID.IRON_ARROW, ItemID.IRON_DART, ItemID.POH_TABLET_KOURENDTELEPORT,
        ItemID.LOBSTER, ItemID.POH_TABLET_LUMBRIDGETELEPORT, ItemID.MACKEREL,
        ItemID.MEAT_PIE, ItemID.MINDRUNE, ItemID.PIKE, ItemID.RING_OF_DUELING_8,
        ItemID.SALMON, ItemID.SHRIMP, ItemID.STEEL_ARROW, ItemID.STEEL_DART,
        ItemID.POH_TABLET_TELEPORTTOHOUSE, ItemID.TUNA, ItemID.POH_TABLET_VARROCKTELEPORT);

    public static long unit(int item, long price, long at) {
        if (price < 0) { throw new IllegalArgumentException("Negative price"); }
        long seconds = at / 1000;
        if (seconds < 1639072800L || ALWAYS_EXEMPT.contains(item)
            || (seconds >= 1748514600L && NEW_EXEMPT.contains(item))) { return 0; }
        return Math.min(5_000_000L, price / (seconds < 1748514600L ? 100 : 50));
    }

    public static long estimate(int item, long gross, long quantity, long at) {
        if (quantity <= 0 || gross < 0) { throw new IllegalArgumentException("Invalid execution total"); }
        long low = gross / quantity;
        long remainder = gross % quantity;
        // Adjacent-price allocation preserves gross totals; actual price distribution is unknown.
        return (quantity - remainder) * unit(item, low, at) + remainder * unit(item, low + 1, at);
    }

    public static long breakEven(int item, double unitCost, long now) {
        if (!Double.isFinite(unitCost) || unitCost < 0) { throw new IllegalArgumentException("Invalid cost"); }
        long low = (long) Math.ceil(unitCost), high = Math.addExact(low, 5_000_001L);
        while (low < high) {
            long mid = low + (high - low) / 2;
            if (mid - unit(item, mid, now) >= unitCost) { high = mid; }
            else { low = mid + 1; }
        }
        return low;
    }
}
