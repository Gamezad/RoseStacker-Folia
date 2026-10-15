package dev.rosewood.rosestacker.manager;

import dev.rosewood.rosegarden.RosePlugin;
import dev.rosewood.rosegarden.manager.Manager;
import dev.rosewood.rosegarden.scheduler.task.ScheduledTask;
import dev.rosewood.rosestacker.config.SettingKey;
import dev.rosewood.rosestacker.utils.FoliaCompat;
import dev.rosewood.rosestacker.utils.ItemUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

/**
 * On Folia, a player's state (position, held items, ...) may only be read from the region that owns
 * the player. Stacking and hologram passes run on the regions that own the stacked entity/spawner, which
 * frequently are <i>not</i> the region owning a nearby player. To avoid reading another region's player
 * state, this manager keeps a lightweight snapshot of every online player updated by a task scheduled on
 * the player's own region. Other passes read these snapshots, which are plain memory and safe to use
 * from any thread.
 * <p>
 * This manager is a no-op when the server is not running Folia.
 *
 * @author Esophose
 */
public class PlayerCacheManager extends Manager implements Listener, AutoCloseable {

    /**
     * A thread-safe snapshot of a player's position and held item
     */
    public static final class PlayerSnapshot {

        private final UUID playerId;
        private volatile String worldName;
        private volatile double x, y, z;
        private volatile boolean stackingTool;
        private volatile boolean valid;

        private PlayerSnapshot(UUID playerId) {
            this.playerId = playerId;
            this.valid = false;
        }

        public UUID getPlayerId() {
            return this.playerId;
        }

        public String getWorldName() {
            return this.worldName;
        }

        public double getX() {
            return this.x;
        }

        public double getY() {
            return this.y;
        }

        public double getZ() {
            return this.z;
        }

        public boolean isStackingTool() {
            return this.stackingTool;
        }

        public boolean isValid() {
            return this.valid;
        }

    }

    private final Map<UUID, PlayerSnapshot> snapshots;
    private final Map<UUID, ScheduledTask> updateTasks;
    private long updateFrequency;

    public PlayerCacheManager(RosePlugin rosePlugin) {
        super(rosePlugin);

        this.snapshots = new ConcurrentHashMap<>();
        this.updateTasks = new ConcurrentHashMap<>();

        if (FoliaCompat.isFolia())
            Bukkit.getPluginManager().registerEvents(this, rosePlugin);
    }

    @Override
    public void reload() {
        if (!FoliaCompat.isFolia())
            return;

        long nametagFrequency = SettingKey.NAMETAG_UPDATE_FREQUENCY.get();
        long hologramFrequency = SettingKey.HOLOGRAM_UPDATE_FREQUENCY.get();
        this.updateFrequency = Math.max(5L, Math.min(nametagFrequency, hologramFrequency));

        // (Re)schedule snapshot updates for everyone currently online
        for (Player player : Bukkit.getOnlinePlayers())
            this.startTracking(player);
    }

    @Override
    public void disable() {
        for (ScheduledTask task : this.updateTasks.values())
            this.cancelQuietly(task);

        this.updateTasks.clear();
        this.snapshots.clear();
    }

    @Override
    public void close() {
        this.disable();
    }

    /**
     * Gets the snapshot of a player, which may be read from any thread
     *
     * @param playerId the UUID of the player
     * @return the player's snapshot, or null if the player is not being tracked
     */
    public PlayerSnapshot getSnapshot(UUID playerId) {
        return this.snapshots.get(playerId);
    }

    /**
     * Gets all valid snapshots of players currently in the given world. Safe to read from any thread.
     *
     * @param worldName the name of the world
     * @return the snapshots of players in the world
     */
    public List<PlayerSnapshot> getWorldSnapshots(String worldName) {
        List<PlayerSnapshot> snapshots = new ArrayList<>();
        for (PlayerSnapshot snapshot : this.snapshots.values()) {
            if (snapshot.isValid() && worldName.equals(snapshot.getWorldName()))
                snapshots.add(snapshot);
        }
        return snapshots;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        this.startTracking(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        this.snapshots.remove(playerId);
        ScheduledTask task = this.updateTasks.remove(playerId);
        this.cancelQuietly(task);
    }

    private void startTracking(Player player) {
        if (!FoliaCompat.isFolia() || !player.isOnline())
            return;

        UUID playerId = player.getUniqueId();

        // Cancel any previous tracking task before replacing it
        this.cancelQuietly(this.updateTasks.remove(playerId));

        PlayerSnapshot snapshot = this.snapshots.computeIfAbsent(playerId, PlayerSnapshot::new);
        this.updateSnapshot(player, snapshot); // Prime immediately so the first pass has data

        ScheduledTask task = this.rosePlugin.getScheduler().runTaskTimerAtEntity(player, () -> this.updateSnapshot(player, snapshot), 1L, this.updateFrequency);
        this.updateTasks.put(playerId, task);
    }

    /**
     * Must only be called from the region that owns the player
     */
    private void updateSnapshot(Player player, PlayerSnapshot snapshot) {
        if (!player.isOnline()) {
            snapshot.valid = false;
            return;
        }

        Location location = player.getLocation();
        snapshot.worldName = location.getWorld() != null ? location.getWorld().getName() : null;
        snapshot.x = location.getX();
        snapshot.y = location.getY();
        snapshot.z = location.getZ();

        ItemStack mainHand = player.getInventory().getItemInMainHand();
        snapshot.stackingTool = ItemUtils.isStackingTool(mainHand);
        snapshot.valid = true;
    }

    private void cancelQuietly(ScheduledTask task) {
        if (task == null)
            return;

        try {
            task.cancel();
        } catch (Throwable e) {
            // RoseGarden may wrap a null Folia handle if the entity scheduler was already retired, ignore
        }
    }

    /**
     * @return a copy of the currently tracked snapshots
     */
    public Collection<PlayerSnapshot> getSnapshots() {
        return new ArrayList<>(this.snapshots.values());
    }

}
