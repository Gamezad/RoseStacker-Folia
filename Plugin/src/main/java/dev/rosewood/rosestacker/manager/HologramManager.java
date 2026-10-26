package dev.rosewood.rosestacker.manager;

import dev.rosewood.rosegarden.RosePlugin;
import dev.rosewood.rosegarden.manager.Manager;
import dev.rosewood.rosegarden.scheduler.task.ScheduledTask;
import dev.rosewood.rosestacker.config.SettingKey;
import dev.rosewood.rosestacker.nms.NMSAdapter;
import dev.rosewood.rosestacker.nms.NMSHandler;
import dev.rosewood.rosestacker.nms.hologram.Hologram;
import dev.rosewood.rosestacker.utils.FoliaCompat;
import dev.rosewood.rosestacker.utils.ThreadUtils;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.WorldUnloadEvent;

public class HologramManager extends Manager implements Listener {

    private final Map<Location, Hologram> holograms;
    private final NMSHandler nmsHandler;
    private ScheduledTask watcherTask;
    private double renderDistanceSqrd;
    private boolean hideThroughWalls;

    public HologramManager(RosePlugin rosePlugin) {
        super(rosePlugin);

        this.holograms = new ConcurrentHashMap<>();
        this.nmsHandler = NMSAdapter.getHandler();

        Bukkit.getPluginManager().registerEvents(this, this.rosePlugin);
    }

    @Override
    public void reload() {
        this.watcherTask = this.rosePlugin.getScheduler().runTaskTimerAsync(this::updateWatchers, 0L, SettingKey.HOLOGRAM_UPDATE_FREQUENCY.get());
        this.renderDistanceSqrd = SettingKey.BLOCK_DYNAMIC_TAG_VIEW_RANGE.get() * SettingKey.BLOCK_DYNAMIC_TAG_VIEW_RANGE.get();
        this.hideThroughWalls = SettingKey.BLOCK_DYNAMIC_TAG_VIEW_RANGE_WALL_DETECTION_ENABLED.get();
    }

    @Override
    public void disable() {
        if (this.watcherTask != null) {
            this.watcherTask.cancel();
            this.watcherTask = null;
        }

        this.holograms.values().forEach(Hologram::delete);
        this.holograms.clear();
    }

    private void updateWatchers() {
        if (FoliaCompat.isFolia()) {
            // Holograms are pure packet displays (no world state), only the range/LOS checks need care:
            // player positions come from region-safe snapshots and raycasts fall back gracefully
            PlayerCacheManager playerCacheManager = this.rosePlugin.getManager(PlayerCacheManager.class);
            if (playerCacheManager == null)
                return;

            for (Hologram hologram : this.holograms.values())
                for (PlayerCacheManager.PlayerSnapshot snapshot : playerCacheManager.getSnapshots())
                    this.updateWatcher(snapshot, hologram);

            return;
        }

        Collection<? extends Player> players = Bukkit.getOnlinePlayers();
        for (Player player : players)
            for (Hologram hologram : this.holograms.values())
                this.updateWatcher(player, hologram);
    }

    private void updateWatcher(Player player, Hologram hologram) {
        if (this.isPlayerInRange(player, hologram.getLocation())) {
            hologram.addWatcher(player);
            if (this.hideThroughWalls)
                hologram.setVisibility(player, this.nmsHandler.hasLineOfSight(player, hologram.getDisplayLocation()));
        } else {
            hologram.removeWatcher(player);
        }
    }

    /**
     * Folia variant of {@link #updateWatcher(Player, Hologram)} using a region-safe player snapshot
     */
    private void updateWatcher(PlayerCacheManager.PlayerSnapshot snapshot, Hologram hologram) {
        if (!snapshot.isValid())
            return;

        Location hologramLocation = hologram.getLocation();
        if (hologramLocation.getWorld() == null || !hologramLocation.getWorld().getName().equals(snapshot.getWorldName()))
            return;

        double dx = snapshot.getX() - hologramLocation.getX();
        double dy = snapshot.getY() - hologramLocation.getY();
        double dz = snapshot.getZ() - hologramLocation.getZ();
        boolean inRange = dx * dx + dy * dy + dz * dz <= this.renderDistanceSqrd;

        Player player = Bukkit.getPlayer(snapshot.getPlayerId());
        if (player == null)
            return;

        // Hologram watcher changes operate on the player's connection. A snapshot makes the
        // distance calculation safe off-thread, but does not make Player packet operations safe.
        ThreadUtils.runOnPrimary(player, () -> {
            if (!player.isOnline())
                return;

            if (inRange) {
                hologram.addWatcher(player);
                Location displayLocation = hologram.getDisplayLocation();
                if (this.hideThroughWalls && FoliaCompat.canUseLocation(displayLocation))
                    hologram.setVisibility(player, this.nmsHandler.hasLineOfSight(player, displayLocation));
            } else {
                hologram.removeWatcher(player);
            }
        });
    }

    private boolean isPlayerInRange(Player player, Location location) {
        return player.getWorld().equals(location.getWorld()) && player.getLocation().distanceSquared(location) <= this.renderDistanceSqrd;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (FoliaCompat.isFolia()) {
            // Give PlayerCacheManager a moment to prime the player's snapshot, then run the snapshot pass
            ThreadUtils.runAsyncDelayed(() -> {
                PlayerCacheManager playerCacheManager = this.rosePlugin.getManager(PlayerCacheManager.class);
                PlayerCacheManager.PlayerSnapshot snapshot = playerCacheManager != null ? playerCacheManager.getSnapshot(event.getPlayer().getUniqueId()) : null;
                if (snapshot == null || !snapshot.isValid())
                    return;

                for (Hologram hologram : this.holograms.values())
                    this.updateWatcher(snapshot, hologram);
            }, 2L);
            return;
        }

        ThreadUtils.runAsync(() -> {
            Player player = event.getPlayer();
            for (Hologram hologram : this.holograms.values())
                this.updateWatcher(player, hologram);
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        Runnable removeWatcher = () -> {
            for (Hologram hologram : this.holograms.values())
                hologram.removeWatcher(player);
        };
        if (FoliaCompat.isFolia())
            ThreadUtils.runOnPrimary(player, removeWatcher);
        else
            ThreadUtils.runAsync(removeWatcher);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldUnload(WorldUnloadEvent event) {
        this.holograms.entrySet().removeIf(x -> {
            if (x.getKey().getWorld().equals(event.getWorld())) {
                x.getValue().delete();
                return true;
            }
            return false;
        });
    }

    /**
     * Creates or updates a hologram at the given location
     *
     * @param location The location of the hologram
     * @param text The text for the hologram
     */
    public void createOrUpdateHologram(Location location, List<String> text) {
        Hologram hologram = this.holograms.get(location);
        if (hologram == null) {
            hologram = this.nmsHandler.createHologram(location, text);
            this.holograms.put(location, hologram);

            if (FoliaCompat.isFolia()) {
                // Initial watchers from region-safe snapshots; the periodic watcher pass keeps them fresh
                PlayerCacheManager playerCacheManager = this.rosePlugin.getManager(PlayerCacheManager.class);
                if (playerCacheManager != null)
                    for (PlayerCacheManager.PlayerSnapshot snapshot : playerCacheManager.getSnapshots())
                        this.updateWatcher(snapshot, hologram);
            } else {
                for (Player player : Bukkit.getOnlinePlayers())
                    this.updateWatcher(player, hologram);
            }
        } else {
            hologram.setText(text);
        }
    }

    /**
     * Deletes a hologram at a given location if one exists
     *
     * @param location The location of the hologram
     */
    public void deleteHologram(Location location) {
        Hologram hologram = this.holograms.get(location);
        if (hologram != null) {
            hologram.delete();
            this.holograms.remove(location);
        }
    }

}
