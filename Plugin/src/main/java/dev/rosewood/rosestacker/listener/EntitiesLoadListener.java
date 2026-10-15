package dev.rosewood.rosestacker.listener;

import dev.rosewood.rosegarden.RosePlugin;
import dev.rosewood.rosestacker.manager.StackManager;
import dev.rosewood.rosestacker.stack.StackingThread;
import dev.rosewood.rosestacker.utils.PersistentDataUtils;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;

public class EntitiesLoadListener implements Listener {

    private final RosePlugin rosePlugin;

    public EntitiesLoadListener(RosePlugin rosePlugin) {
        this.rosePlugin = rosePlugin;
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        StackManager stackManager = this.rosePlugin.getManager(StackManager.class);
        if (stackManager.isWorldDisabled(event.getWorld()))
            return;

        StackingThread stackingThread = stackManager.getStackingThread(event.getWorld());

        // Folia: start the region-local maintenance task (entity cache refresh + orphan rescan) for this chunk
        if (stackingThread != null)
            stackingThread.onChunkLoaded(event.getChunk());

        for (Entity entity : event.getEntities())
            if (entity instanceof LivingEntity livingEntity && !stackManager.isAreaDisabled(livingEntity.getLocation()))
                PersistentDataUtils.applyDisabledAi(livingEntity);

        stackManager.loadChunkEntities(event.getEntities());
    }

    @EventHandler
    public void onEntitiesUnload(EntitiesUnloadEvent event) {
        StackManager stackManager = this.rosePlugin.getManager(StackManager.class);
        if (stackManager.isWorldDisabled(event.getWorld()))
            return;

        StackingThread stackingThread = stackManager.getStackingThread(event.getWorld());
        if (stackingThread != null)
            stackingThread.onChunkUnloaded(event.getChunk());

        stackManager.saveChunkEntities(event.getEntities(), true);
    }

}
