package dev.rosewood.rosestacker.utils;

import dev.rosewood.rosegarden.utils.NMSUtil;
import dev.rosewood.rosestacker.RoseStacker;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;

/**
 * Central helper for Folia region-threading checks.
 * <p>
 * On Folia, world state (entities, blocks, chunks) may only be read or modified from the region
 * thread that owns it. All Folia-specific behavior in this plugin is gated behind {@link #isFolia()}
 * so Paper/Spigot servers keep their original behavior and never link against Folia-only APIs.
 * <p>
 * The scheduler itself is provided by RoseGarden, which already routes {@code runTaskAtEntity} /
 * {@code runTaskAtLocation} to Folia's EntityScheduler / RegionScheduler when Folia is detected.
 *
 * @author Esophose
 */
public final class FoliaCompat {

    private static final boolean FOLIA = NMSUtil.isFolia();
    private static volatile boolean regionsTicking;

    private FoliaCompat() {

    }

    /**
     * @return true if the server is running Folia
     */
    public static boolean isFolia() {
        return FOLIA;
    }

    /**
     * Marks that the server has reached the point where regions may tick in parallel.
     * Set from {@code io.papermc.paper.threadedregions.RegionizedServerInitEvent}, which Folia fires
     * after plugin startup but before region threads start.
     */
    public static void markRegionsTicking() {
        regionsTicking = true;
    }

    /**
     * @return true if Folia regions may be ticking in parallel (plugin startup work is still single threaded before this)
     */
    public static boolean isRegionsTicking() {
        return regionsTicking;
    }

    /**
     * Checks whether the current thread is allowed to directly touch the given entity's world state.
     * Always true on non-Folia servers to preserve original behavior.
     *
     * @param entity the entity to check, may be null
     * @return true if the current thread may read/mutate the entity directly
     */
    public static boolean canUseEntity(Entity entity) {
        if (!FOLIA || entity == null)
            return true;

        if (!regionsTicking)
            return true; // Startup is single threaded until the region threads start ticking

        return RoseStacker.getInstance().getScheduler().isEntityThread(entity);
    }

    /**
     * Checks whether the current thread is allowed to directly touch world state at the given location.
     * Always true on non-Folia servers to preserve original behavior.
     *
     * @param location the location to check, may be null
     * @return true if the current thread may read/mutate blocks and entities at the location directly
     */
    public static boolean canUseLocation(Location location) {
        if (!FOLIA || location == null || location.getWorld() == null)
            return true;

        if (!regionsTicking)
            return true; // Startup is single threaded until the region threads start ticking

        return RoseStacker.getInstance().getScheduler().isLocationThread(location);
    }

    /**
     * Checks whether the current thread is allowed to directly touch the given block's world state.
     * Always true on non-Folia servers to preserve original behavior.
     *
     * @param block the block to check, may be null
     * @return true if the current thread may read/mutate the block directly
     */
    public static boolean canUseBlock(Block block) {
        if (!FOLIA || block == null)
            return true;

        return RoseStacker.getInstance().getScheduler().isLocationThread(block.getLocation());
    }

}
