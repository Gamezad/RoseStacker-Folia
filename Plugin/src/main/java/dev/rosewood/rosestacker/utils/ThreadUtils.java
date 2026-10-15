package dev.rosewood.rosestacker.utils;

import dev.rosewood.rosegarden.RosePlugin;
import dev.rosewood.rosegarden.scheduler.RoseScheduler;
import dev.rosewood.rosestacker.RoseStacker;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

public final class ThreadUtils {

    private static final RosePlugin PLUGIN = RoseStacker.getInstance();
    private static final RoseScheduler SCHEDULER = PLUGIN.getScheduler();

    private ThreadUtils() {

    }

    public static void runOnPrimary(Runnable runnable) {
        if (Bukkit.isPrimaryThread()) {
            runnable.run();
        } else {
            runSync(runnable);
        }
    }

    /**
     * Runs the runnable on the thread that owns the given entity's region, immediately if the current
     * thread already owns it, otherwise on the next tick of the owning region. On non-Folia servers this
     * behaves exactly like {@link #runOnPrimary(Runnable)}.
     *
     * @param entity   the entity whose region should run the task
     * @param runnable the task to run
     */
    public static void runOnPrimary(Entity entity, Runnable runnable) {
        if (!checkEnabled())
            return;

        if (FoliaCompat.isFolia()) {
            if (FoliaCompat.canUseEntity(entity)) {
                runnable.run();
            } else {
                SCHEDULER.runTaskAtEntity(entity, runnable);
            }
        } else {
            runOnPrimary(runnable);
        }
    }

    /**
     * Runs the runnable on the region that owns the given location, immediately if the current thread
     * already owns it, otherwise on the next tick of the owning region. On non-Folia servers this behaves
     * exactly like {@link #runOnPrimary(Runnable)}.
     *
     * @param location the location whose region should run the task
     * @param runnable the task to run
     */
    public static void runOnPrimary(Location location, Runnable runnable) {
        if (!checkEnabled())
            return;

        if (FoliaCompat.isFolia()) {
            if (FoliaCompat.canUseLocation(location)) {
                runnable.run();
            } else {
                SCHEDULER.runTaskAtLocation(location, runnable);
            }
        } else {
            runOnPrimary(runnable);
        }
    }

    public static void runOffPrimary(Runnable runnable) {
        if (!Bukkit.isPrimaryThread()) {
            runnable.run();
        } else {
            runAsync(runnable);
        }
    }

    public static void runSync(Runnable runnable) {
        if (checkEnabled())
            SCHEDULER.runTask(runnable);
    }

    /**
     * Schedules a task to run on the next tick on the region that owns the given entity.
     * On non-Folia servers this is identical to {@link #runSync(Runnable)}.
     *
     * @param entity   the entity whose region should run the task
     * @param runnable the task to run
     */
    public static void runSync(Entity entity, Runnable runnable) {
        if (!checkEnabled())
            return;

        if (FoliaCompat.isFolia()) {
            SCHEDULER.runTaskAtEntity(entity, runnable);
        } else {
            SCHEDULER.runTask(runnable);
        }
    }

    /**
     * Schedules a task to run on the next tick on the region that owns the given location.
     * On non-Folia servers this is identical to {@link #runSync(Runnable)}.
     *
     * @param location the location whose region should run the task
     * @param runnable the task to run
     */
    public static void runSync(Location location, Runnable runnable) {
        if (!checkEnabled())
            return;

        if (FoliaCompat.isFolia()) {
            SCHEDULER.runTaskAtLocation(location, runnable);
        } else {
            SCHEDULER.runTask(runnable);
        }
    }

    public static void runSyncDelayed(Runnable runnable, long delay) {
        if (checkEnabled())
            SCHEDULER.runTaskLater(runnable, delay);
    }

    /**
     * Schedules a task to run after the given delay on the region that owns the given entity.
     * On non-Folia servers this is identical to {@link #runSyncDelayed(Runnable, long)}.
     *
     * @param entity   the entity whose region should run the task
     * @param runnable the task to run
     * @param delay    the delay in ticks
     */
    public static void runSyncDelayed(Entity entity, Runnable runnable, long delay) {
        if (!checkEnabled())
            return;

        if (FoliaCompat.isFolia()) {
            SCHEDULER.runTaskAtEntityLater(entity, runnable, delay);
        } else {
            SCHEDULER.runTaskLater(runnable, delay);
        }
    }

    /**
     * Schedules a task to run after the given delay on the region that owns the given location.
     * On non-Folia servers this is identical to {@link #runSyncDelayed(Runnable, long)}.
     *
     * @param location the location whose region should run the task
     * @param runnable the task to run
     * @param delay    the delay in ticks
     */
    public static void runSyncDelayed(Location location, Runnable runnable, long delay) {
        if (!checkEnabled())
            return;

        if (FoliaCompat.isFolia()) {
            SCHEDULER.runTaskAtLocationLater(location, runnable, delay);
        } else {
            SCHEDULER.runTaskLater(runnable, delay);
        }
    }

    public static void runAsync(Runnable runnable) {
        if (checkEnabled())
            SCHEDULER.runTaskAsync(runnable);
    }

    public static void runAsyncDelayed(Runnable runnable, long delay) {
        if (checkEnabled())
            SCHEDULER.runTaskLaterAsync(runnable, delay);
    }

    public static int getActiveThreads() {
        return SCHEDULER.getRunningTaskCount();
    }

    private static boolean checkEnabled() {
        return PLUGIN.isEnabled();
    }

}
