package dev.rosewood.rosestacker.utils;

import dev.rosewood.rosegarden.RosePlugin;
import dev.rosewood.rosestacker.RoseStacker;
import dev.rosewood.rosestacker.config.SettingKey;
import dev.rosewood.rosestacker.manager.StackSettingManager;
import dev.rosewood.rosestacker.nms.NMSAdapter;
import dev.rosewood.rosestacker.nms.NMSHandler;
import dev.rosewood.rosestacker.nms.spawner.StackedSpawnerTile;
import dev.rosewood.rosestacker.stack.settings.EntityStackSettings;
import java.util.ConcurrentModificationException;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Hoglin;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.PiglinAbstract;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public final class PersistentDataUtils {

    private static final String UNSTACKABLE_METADATA_NAME = "unstackable";
    private static final String NO_AI_METADATA_NAME = "no_ai";
    private static final String SPAWNED_FROM_SPAWNER_METADATA_NAME = "spawner_spawned";
    private static final String SPAWNED_FROM_TRIAL_SPAWNER_METADATA_NAME = "trial_spawner_spawned";
    private static final String TOTAL_SPAWNS_METADATA_NAME = "total_spawns";
    private static final String STACK_SIZE_METADATA_NAME = "stack_size";

    private static boolean mobAiDisabledLogged;

    public static void setUnstackable(Entity entity, boolean unstackable) {
        RosePlugin rosePlugin = RoseStacker.getInstance();
        if (unstackable) {
            entity.getPersistentDataContainer().set(new NamespacedKey(rosePlugin, UNSTACKABLE_METADATA_NAME), PersistentDataType.INTEGER, 1);
        } else {
            entity.getPersistentDataContainer().remove(new NamespacedKey(rosePlugin, UNSTACKABLE_METADATA_NAME));
        }
    }

    public static boolean isUnstackable(Entity entity) {
        RosePlugin rosePlugin = RoseStacker.getInstance();
        return entity.getPersistentDataContainer().has(new NamespacedKey(rosePlugin, UNSTACKABLE_METADATA_NAME), PersistentDataType.INTEGER);
    }

    public static void removeEntityAi(LivingEntity entity) {
        RosePlugin rosePlugin = RoseStacker.getInstance();
        PersistentDataContainer dataContainer = entity.getPersistentDataContainer();
        NamespacedKey key = new NamespacedKey(rosePlugin, NO_AI_METADATA_NAME);
        if (!dataContainer.has(key, PersistentDataType.INTEGER))
            dataContainer.set(key, PersistentDataType.INTEGER, 1);

        logAiDisabledSource(entity);

        applyDisabledAi(entity);
    }

    /**
     * Logs which setting causes mobs to have their AI disabled, once per server session. Without this it
     * looks like a bug when mobs don't move, can't be knocked back and are silent.
     *
     * @param entity an entity whose AI is being disabled
     */
    private static void logAiDisabledSource(LivingEntity entity) {
        if (mobAiDisabledLogged)
            return;

        mobAiDisabledLogged = true;
        RosePlugin rosePlugin = RoseStacker.getInstance();
        EntityStackSettings entityStackSettings = rosePlugin.getManager(StackSettingManager.class).getEntityStackSettings(entity.getType());
        String source;
        if (entityStackSettings != null && entityStackSettings.isMobAIDisabled()) {
            source = "the 'disable-all-mob-ai' entity setting for " + entity.getType();
        } else if (SettingKey.SPAWNER_DISABLE_MOB_AI.get()) {
            source = "global-spawner-settings.disable-mob-ai in config.yml";
        } else {
            source = "a per-spawner 'disable-mob-ai' setting";
        }

        rosePlugin.getLogger().info("Mob AI is being disabled because of " + source
                + ". Mobs with disabled AI will not move, cannot be knocked back and are silent. "
                + "Set it to false and restart if they should keep their AI.");
    }

    public static void reenableEntityAi(LivingEntity entity) {
        RosePlugin rosePlugin = RoseStacker.getInstance();
        PersistentDataContainer dataContainer = entity.getPersistentDataContainer();
        NamespacedKey key = new NamespacedKey(rosePlugin, NO_AI_METADATA_NAME);
        dataContainer.remove(key);

        applyDisabledAi(entity, false);
    }

    public static void applyDisabledAi(LivingEntity entity) {
        applyDisabledAi(entity, true);
    }

    public static void applyDisabledAi(LivingEntity entity, boolean disable) {
        if (isAiDisabled(entity) || !disable) {
            if (disable)
                logAiDisabledSource(entity);

            if (SettingKey.SPAWNER_DISABLE_MOB_AI_OPTIONS_REMOVE_GOALS.get() && disable) {
                NMSHandler nmsHandler = NMSAdapter.getHandler();
                nmsHandler.removeEntityGoals(entity);
            }

            if (SettingKey.SPAWNER_DISABLE_MOB_AI_OPTIONS_DISABLE_ITEM_PICKUP.get())
                entity.setCanPickupItems(!disable);

            if (SettingKey.SPAWNER_DISABLE_MOB_AI_OPTIONS_SET_UNAWARE.get() && entity instanceof Mob mob)
                mob.setAware(!disable);

            if (SettingKey.SPAWNER_DISABLE_MOB_AI_OPTIONS_SILENCE.get())
                entity.setSilent(disable);

            AttributeInstance knockbackAttribute = entity.getAttribute(VersionUtils.KNOCKBACK_RESISTANCE);
            if (knockbackAttribute != null) {
                if (SettingKey.SPAWNER_DISABLE_MOB_AI_OPTIONS_NO_KNOCKBACK.get()) {
                    // Restore the mob's own value when AI comes back, not a flat 0 (an iron golem, ravager,
                    // hoglin, warden etc. have a natural knockback resistance that must not be wiped)
                    knockbackAttribute.setBaseValue(disable ? Double.MAX_VALUE : knockbackAttribute.getDefaultValue());
                } else if (knockbackAttribute.getBaseValue() == Double.MAX_VALUE) {
                    // The option was switched off while the mob already had its knockback disabled by us,
                    // put the mob's own value back so hits move it again
                    knockbackAttribute.setBaseValue(knockbackAttribute.getDefaultValue());
                }
            }

            if (SettingKey.SPAWNER_DISABLE_MOB_AI_OPTIONS_DISABLE_ZOMBIFICATION.get()) {
                if (entity instanceof PiglinAbstract piglin) {
                    piglin.setImmuneToZombification(disable);
                } else if (entity instanceof Hoglin hoglin) {
                    hoglin.setImmuneToZombification(disable);
                }
            }

            if (SettingKey.SPAWNER_DISABLE_MOB_AI_OPTIONS_DISABLE_COLLISION.get())
                entity.setCollidable(!disable);
        }
    }

    public static boolean isAiDisabled(LivingEntity entity) {
        EntityStackSettings entityStackSettings = RoseStacker.getInstance().getManager(StackSettingManager.class).getEntityStackSettings(entity.getType());
        if (entityStackSettings != null && entityStackSettings.isMobAIDisabled())
            return true;

        RosePlugin rosePlugin = RoseStacker.getInstance();
        return entity.getPersistentDataContainer().has(new NamespacedKey(rosePlugin, NO_AI_METADATA_NAME), PersistentDataType.INTEGER);
    }

    public static void tagSpawnedFromSpawner(Entity entity) {
        RosePlugin rosePlugin = RoseStacker.getInstance();
        entity.getPersistentDataContainer().set(new NamespacedKey(rosePlugin, SPAWNED_FROM_SPAWNER_METADATA_NAME), PersistentDataType.INTEGER, 1);
    }

    /**
     * Checks if RoseStacker has already tagged this entity as being spawned from a spawner.
     * Unlike {@link #isSpawnedFromSpawner(Entity)} this only looks at the tag RoseStacker sets, so it
     * can be used to tell "RoseStacker already handled this mob" apart from "the server says this mob
     * came from a spawner".
     *
     * @param entity The entity to check
     * @return true if RoseStacker tagged this entity as being spawned from a spawner
     */
    public static boolean isTaggedSpawnedFromSpawner(Entity entity) {
        RosePlugin rosePlugin = RoseStacker.getInstance();
        return entity.getPersistentDataContainer().has(new NamespacedKey(rosePlugin, SPAWNED_FROM_SPAWNER_METADATA_NAME), PersistentDataType.INTEGER);
    }

    /**
     * Stores the stack size of a spawner stack on the spawner block itself.
     * <p>
     * The size RoseStacker keeps in the per-chunk data is only written when RoseStacker saves, so a chunk
     * that is saved or reloaded in between can restore a stale or missing size (usually leaving behind a
     * single spawner) while the spawner itself keeps spawning with the spawn count of the old stack. The
     * block entity is saved by the server together with the block, so this value always matches the last
     * known stack size and allows the stack to be restored instead of silently being reduced.
     *
     * @param spawner the spawner tile to write the size to
     * @param size    the stack size, values below 1 clear the stored size
     */
    public static void setStackedSpawnerSize(StackedSpawnerTile spawner, int size) {
        if (spawner == null)
            return;

        PersistentDataContainer dataContainer = spawner.getPersistentDataContainer();
        if (dataContainer == null)
            return;

        NamespacedKey key = new NamespacedKey(RoseStacker.getInstance(), STACK_SIZE_METADATA_NAME);
        if (size > 0) {
            dataContainer.set(key, PersistentDataType.INTEGER, size);
        } else {
            dataContainer.remove(key);
        }
    }

    /**
     * Reads the stack size stored on a spawner block.
     *
     * @param spawner the spawner tile to read the size from
     * @return the stored stack size, or 0 if there isn't one
     */
    public static int getStackedSpawnerSize(StackedSpawnerTile spawner) {
        if (spawner == null)
            return 0;

        try {
            PersistentDataContainer dataContainer = spawner.getPersistentDataContainer();
            if (dataContainer == null)
                return 0;

            Integer size = dataContainer.get(new NamespacedKey(RoseStacker.getInstance(), STACK_SIZE_METADATA_NAME), PersistentDataType.INTEGER);
            return size != null ? Math.max(size, 0) : 0;
        } catch (Exception e) {
            return 0; // Best effort, the spawner will fall back to the size from the chunk data
        }
    }

    /**
     * Checks if an entity was spawned from a spawner
     *
     * @param entity The entity to check
     * @return true if the entity was spawned from a spawner, otherwise false
     */
    public static boolean isSpawnedFromSpawner(Entity entity) {
        RosePlugin rosePlugin = RoseStacker.getInstance();
        return entity.getPersistentDataContainer().has(new NamespacedKey(rosePlugin, SPAWNED_FROM_SPAWNER_METADATA_NAME), PersistentDataType.INTEGER)
                || EntityUtils.hasSpawnerSpawnReason(entity);
    }

    public static void tagSpawnedFromTrialSpawner(Entity entity) {
        RosePlugin rosePlugin = RoseStacker.getInstance();
        entity.getPersistentDataContainer().set(new NamespacedKey(rosePlugin, SPAWNED_FROM_TRIAL_SPAWNER_METADATA_NAME), PersistentDataType.INTEGER, 1);
    }

    /**
     * Checks if an entity was spawned from a trial spawner
     *
     * @param entity The entity to check
     * @return true if the entity was spawned from a trial spawner, otherwise false
     */
    public static boolean isSpawnedFromTrialSpawner(Entity entity) {
        RosePlugin rosePlugin = RoseStacker.getInstance();
        return entity.getPersistentDataContainer().has(new NamespacedKey(rosePlugin, SPAWNED_FROM_TRIAL_SPAWNER_METADATA_NAME), PersistentDataType.INTEGER)
                || EntityUtils.hasTrialSpawnerSpawnReason(entity);
    }

    public static void increaseSpawnCount(StackedSpawnerTile spawner, long amount) {
        RosePlugin rosePlugin = RoseStacker.getInstance();
        PersistentDataContainer dataContainer = spawner.getPersistentDataContainer();
        if (dataContainer != null) {
            NamespacedKey key = new NamespacedKey(rosePlugin, TOTAL_SPAWNS_METADATA_NAME);
            if (!dataContainer.has(key, PersistentDataType.LONG)) {
                dataContainer.set(key, PersistentDataType.LONG, amount);
            } else {
                dataContainer.set(key, PersistentDataType.LONG, getTotalSpawnCount(spawner) + amount);
            }
        }
    }

    public static long getTotalSpawnCount(StackedSpawnerTile spawner) {
        try {
            RosePlugin rosePlugin = RoseStacker.getInstance();
            PersistentDataContainer persistentDataContainer = spawner.getPersistentDataContainer();
            if (persistentDataContainer == null)
                return 0;

            NamespacedKey key = new NamespacedKey(rosePlugin, TOTAL_SPAWNS_METADATA_NAME);
            Long amount = persistentDataContainer.get(key, PersistentDataType.LONG);
            return amount != null ? amount : 0;
        } catch (ConcurrentModificationException e) {
            return 0; // StackedSpawner#updateDisplay can cause a CME sometimes here when run async
        }
    }

}
