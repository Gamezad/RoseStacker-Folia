package dev.rosewood.rosestacker.utils;

import dev.rosewood.rosegarden.utils.NMSUtil;
import dev.rosewood.rosestacker.RoseStacker;
import dev.rosewood.rosestacker.config.SettingKey;
import dev.rosewood.rosestacker.nms.NMSAdapter;
import dev.rosewood.rosestacker.nms.NMSHandler;
import dev.rosewood.rosestacker.nms.storage.StackedEntityDataStorageType;
import dev.rosewood.rosestacker.nms.storage.StorageMigrationType;
import dev.rosewood.rosestacker.stack.StackedBlock;
import dev.rosewood.rosestacker.stack.StackedEntity;
import dev.rosewood.rosestacker.stack.StackedItem;
import dev.rosewood.rosestacker.stack.StackedSpawner;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;

public final class DataUtils {

    private static final NamespacedKey ENTITY_KEY = new NamespacedKey(RoseStacker.getInstance(), "stacked_entity_data");
    private static final int ENTITY_DATA_VERSION = 3;

    private static final NamespacedKey ITEM_KEY = new NamespacedKey(RoseStacker.getInstance(), "stacked_item_data");
    private static final int ITEM_DATA_VERSION = 1;

    private static final NamespacedKey CHUNK_SPAWNERS_KEY = new NamespacedKey(RoseStacker.getInstance(), "stacked_spawner_data");
    private static final int SPAWNER_DATA_VERSION = 1;

    private static final NamespacedKey CHUNK_BLOCKS_KEY = new NamespacedKey(RoseStacker.getInstance(), "stacked_block_data");
    private static final int BLOCK_DATA_VERSION = 1;

    public static StackedEntity readStackedEntity(LivingEntity entity, StackedEntityDataStorageType storageType) {
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        NMSHandler nmsHandler = NMSAdapter.getHandler();
        byte[] data = pdc.get(ENTITY_KEY, PersistentDataType.BYTE_ARRAY);
        if (data == null)
            return new StackedEntity(entity, nmsHandler.createEntityDataStorage(entity, storageType), false);

        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(data);
             ObjectInputStream dataInput = new ObjectInputStream(new GZIPInputStream(inputStream))) {

            int dataVersion = dataInput.readInt();
            if (dataVersion == 3) {
                StackedEntityDataStorageType type = StackedEntityDataStorageType.fromId(dataInput.readInt());
                int major = dataInput.readByte();
                int minor = dataInput.readByte();
                int length = dataInput.readInt();
                byte[] nbt = new byte[length];
                for (int i = 0; i < length; i++)
                    nbt[i] = dataInput.readByte();
                Set<StorageMigrationType> migrations = getNeededMigrations(major, minor);
                return new StackedEntity(entity, nmsHandler.deserializeEntityDataStorage(entity, nbt, type, migrations), false);
            } else if (dataVersion == 2) {
                StackedEntityDataStorageType type = StackedEntityDataStorageType.fromId(dataInput.readInt());
                int length = dataInput.readInt();
                byte[] nbt = new byte[length];
                for (int i = 0; i < length; i++)
                    nbt[i] = dataInput.readByte();
                Set<StorageMigrationType> migrations = getNeededMigrations(0, 0);
                return new StackedEntity(entity, nmsHandler.deserializeEntityDataStorage(entity, nbt, type, migrations), false);
            } else if (dataVersion == 1) {
                int length = dataInput.readInt();
                byte[] nbt = new byte[length];
                for (int i = 0; i < length; i++)
                    nbt[i] = dataInput.readByte();
                Set<StorageMigrationType> migrations = getNeededMigrations(0, 0);
                return new StackedEntity(entity, nmsHandler.deserializeEntityDataStorage(entity, nbt, StackedEntityDataStorageType.NBT, migrations), false);
            }
        } catch (Exception e) {
            e.printStackTrace();
            pdc.remove(ENTITY_KEY);
        }
        return null;
    }

    private static Set<StorageMigrationType> getNeededMigrations(int major, int minor) {
        if ((major < 21 || (major == 21 && minor < 3)) && (NMSUtil.getVersionNumber() > 21 || (NMSUtil.getVersionNumber() == 21 && NMSUtil.getMinorVersionNumber() >= 3)))
            return Set.of(StorageMigrationType.STRIP_OLD_ATTRIBUTES);
        return Set.of();
    }

    public static void writeStackedEntity(StackedEntity stackedEntity) {
        if (stackedEntity.getStackSize() == 1)
            return;

        PersistentDataContainer pdc = stackedEntity.getEntity().getPersistentDataContainer();
        byte[] data = null;

        int maxSaveAmount = SettingKey.ENTITY_SAVE_MAX_STACK_SIZE.get();
        if (maxSaveAmount <= 0)
            maxSaveAmount = Integer.MAX_VALUE;

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             ObjectOutputStream dataOutput = new ObjectOutputStream(new GZIPOutputStream(outputStream))) {

            dataOutput.writeInt(ENTITY_DATA_VERSION);
            dataOutput.writeInt(stackedEntity.getDataStorage().getType().getId());
            dataOutput.writeByte(NMSUtil.getVersionNumber());
            dataOutput.writeByte(NMSUtil.getMinorVersionNumber());
            byte[] nbt = stackedEntity.getDataStorage().serialize(maxSaveAmount - 1);
            dataOutput.writeInt(nbt.length);
            dataOutput.write(nbt);

            dataOutput.close();
            data = outputStream.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (data != null)
            pdc.set(ENTITY_KEY, PersistentDataType.BYTE_ARRAY, data);
    }

    public static void clearStackedEntityData(LivingEntity entity) {
        entity.getPersistentDataContainer().remove(ENTITY_KEY);
    }

    public static StackedItem readStackedItem(Item item) {
        PersistentDataContainer pdc = item.getPersistentDataContainer();

        byte[] data = pdc.get(ITEM_KEY, PersistentDataType.BYTE_ARRAY);
        if (data == null)
            return new StackedItem(item.getItemStack().getAmount(), item);

        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(data);
             ObjectInputStream dataInput = new ObjectInputStream(inputStream)) {

            int dataVersion = dataInput.readInt();
            if (dataVersion == 1) {
                int stackSize = dataInput.readInt();
                return new StackedItem(stackSize, item);
            }
        } catch (Exception e) {
            e.printStackTrace();
            pdc.remove(ITEM_KEY);
        }
        return null;
    }

    public static void writeStackedItem(StackedItem stackedItem) {
        PersistentDataContainer pdc = stackedItem.getItem().getPersistentDataContainer();
        byte[] data = null;

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             ObjectOutputStream dataOutput = new ObjectOutputStream(outputStream)) {

            dataOutput.writeInt(ITEM_DATA_VERSION);
            dataOutput.writeInt(stackedItem.getStackSize());

            dataOutput.close();
            data = outputStream.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (data != null)
            pdc.set(ITEM_KEY, PersistentDataType.BYTE_ARRAY, data);
    }

    public static List<StackedSpawner> readStackedSpawners(Chunk chunk) {
        return readStackedSpawners(chunk, null);
    }

    /**
     * Reads the spawner stacks stored on a chunk.
     *
     * @param chunk  the chunk to read the spawner stacks from
     * @param filter only blocks matching this filter are restored, null to restore all of them
     * @return the restored spawner stacks
     */
    public static List<StackedSpawner> readStackedSpawners(Chunk chunk, @Nullable Predicate<Block> filter) {
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();

        List<StackedSpawner> stackedSpawners = new ArrayList<>();
        byte[] data = pdc.get(CHUNK_SPAWNERS_KEY, PersistentDataType.BYTE_ARRAY);
        if (data == null)
            return stackedSpawners;

        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(data);
             ObjectInputStream dataInput = new ObjectInputStream(inputStream)) {

            int dataVersion = dataInput.readInt();
            if (dataVersion == 1) {
                int length = dataInput.readInt();
                for (int i = 0; i < length; i++) {
                    int stackSize = dataInput.readInt();
                    int x = dataInput.readInt();
                    int y = dataInput.readInt();
                    int z = dataInput.readInt();
                    boolean placedByPlayer = dataInput.readBoolean();
                    Block block = chunk.getBlock(x, y, z);
                    if (block.getType() != Material.SPAWNER)
                        continue;
                    if (filter != null && !filter.test(block))
                        continue;

                    try {
                        stackedSpawners.add(new StackedSpawner(stackSize, block, placedByPlayer, false));
                    } catch (Exception e) {
                        // A single spawner that fails to restore must not throw away the rest of the chunk
                        RoseStacker.getInstance().getLogger().warning("Failed to restore a spawner stack at "
                                + block.getLocation() + ", it will fall back to a single spawner: " + e);
                    }
                }
            }
        } catch (Exception e) {
            // Keep the data: deleting it would permanently turn every spawner in this chunk into a single
            // spawner while they keep spawning with the spawn count of their old stacks
            RoseStacker.getInstance().getLogger().warning("Failed to read the stored spawner stacks of chunk "
                    + chunk.getX() + ", " + chunk.getZ() + ", the data was kept: " + e);
        }

        return stackedSpawners;
    }

    /**
     * Writes the given spawner stacks to the chunk they belong to.
     *
     * @param stackedSpawners the spawner stacks to store
     * @param chunk           the chunk to store them in
     * @return true if the data was stored, false if storing it failed
     */
    public static boolean writeStackedSpawners(Collection<StackedSpawner> stackedSpawners, Chunk chunk) {
        try {
            PersistentDataContainer pdc = chunk.getPersistentDataContainer();

            byte[] data;
            try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                 ObjectOutputStream dataOutput = new ObjectOutputStream(outputStream)) {

                dataOutput.writeInt(SPAWNER_DATA_VERSION);
                dataOutput.writeInt(stackedSpawners.size());

                for (StackedSpawner stackedSpawner : stackedSpawners) {
                    dataOutput.writeInt(stackedSpawner.getStackSize());
                    dataOutput.writeInt(stackedSpawner.getLocation().getBlockX() & 0xF);
                    dataOutput.writeInt(stackedSpawner.getLocation().getBlockY());
                    dataOutput.writeInt(stackedSpawner.getLocation().getBlockZ() & 0xF);
                    dataOutput.writeBoolean(stackedSpawner.isPlacedByPlayer());
                }

                // Serialize that array
                dataOutput.close();
                data = outputStream.toByteArray();
            }

            pdc.set(CHUNK_SPAWNERS_KEY, PersistentDataType.BYTE_ARRAY, data);
            return true;
        } catch (Exception e) {
            // The caller needs to know: losing this data turns every spawner stack in the chunk back into
            // a single spawner while the spawners keep spawning with the spawn count of their old stacks
            RoseStacker.getInstance().getLogger().warning("Failed to store the spawner stacks of chunk "
                    + chunk.getX() + ", " + chunk.getZ() + ", it will be retried on the next save: " + e);
            return false;
        }
    }

    public static List<StackedBlock> readStackedBlocks(Chunk chunk) {
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();

        List<StackedBlock> stackedBlocks = new ArrayList<>();
        byte[] data = pdc.get(CHUNK_BLOCKS_KEY, PersistentDataType.BYTE_ARRAY);
        if (data == null)
            return stackedBlocks;

        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(data);
             ObjectInputStream dataInput = new ObjectInputStream(inputStream)) {

            int dataVersion = dataInput.readInt();
            if (dataVersion == 1) {
                int length = dataInput.readInt();
                for (int i = 0; i < length; i++) {
                    int stackSize = dataInput.readInt();
                    int x = dataInput.readInt();
                    int y = dataInput.readInt();
                    int z = dataInput.readInt();

                    try {
                        stackedBlocks.add(new StackedBlock(stackSize, chunk.getBlock(x, y, z), false));
                    } catch (Exception e) {
                        // A single block that fails to restore must not throw away the rest of the chunk
                        RoseStacker.getInstance().getLogger().warning("Failed to restore a stacked block in chunk "
                                + chunk.getX() + ", " + chunk.getZ() + ": " + e);
                    }
                }
            }
        } catch (Exception e) {
            // Keep the data: deleting it would permanently turn every stacked block in this chunk into a
            // single block
            RoseStacker.getInstance().getLogger().warning("Failed to read the stored stacked blocks of chunk "
                    + chunk.getX() + ", " + chunk.getZ() + ", the data was kept: " + e);
        }

        return stackedBlocks;
    }

    public static boolean writeStackedBlocks(Collection<StackedBlock> stackedBlocks, Chunk chunk) {
        try {
            PersistentDataContainer pdc = chunk.getPersistentDataContainer();

            byte[] data;
            try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                 ObjectOutputStream dataOutput = new ObjectOutputStream(outputStream)) {

                dataOutput.writeInt(BLOCK_DATA_VERSION);
                dataOutput.writeInt(stackedBlocks.size());

                for (StackedBlock stackedBlock : stackedBlocks) {
                    dataOutput.writeInt(stackedBlock.getStackSize());
                    dataOutput.writeInt(stackedBlock.getLocation().getBlockX() & 0xF);
                    dataOutput.writeInt(stackedBlock.getLocation().getBlockY());
                    dataOutput.writeInt(stackedBlock.getLocation().getBlockZ() & 0xF);
                }

                // Serialize that array
                dataOutput.close();
                data = outputStream.toByteArray();
            }

            pdc.set(CHUNK_BLOCKS_KEY, PersistentDataType.BYTE_ARRAY, data);
            return true;
        } catch (Exception e) {
            RoseStacker.getInstance().getLogger().warning("Failed to store the stacked blocks of chunk "
                    + chunk.getX() + ", " + chunk.getZ() + ", it will be retried on the next save: " + e);
            return false;
        }
    }

}
