package dev.rosewood.rosestacker.listener;

import dev.rosewood.rosegarden.RosePlugin;
import dev.rosewood.rosestacker.manager.StackManager;
import dev.rosewood.rosestacker.utils.FoliaCompat;
import io.papermc.paper.threadedregions.RegionizedServerInitEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * Listens for Folia's {@link RegionizedServerInitEvent}, which fires after plugins are enabled but
 * before region threads begin ticking in parallel. Until this event fires it is safe to touch world
 * state during plugin startup; afterwards all world access must happen through region/entity tasks.
 * Also performs a one-time backfill of per-chunk maintenance tasks for chunks that were already
 * loaded before region ticking began (their load events fired before this plugin was listening).
 * <p>
 * This class is only ever loaded on servers that have the event class (guarded by a class check in
 * {@link dev.rosewood.rosestacker.RoseStacker#enable()}).
 *
 * @author Esophose
 */
public class FoliaListener implements Listener {

    private final RosePlugin rosePlugin;

    public FoliaListener(RosePlugin rosePlugin) {
        this.rosePlugin = rosePlugin;
    }

    @EventHandler
    public void onRegionizedServerInit(RegionizedServerInitEvent event) {
        FoliaCompat.markRegionsTicking();

        StackManager stackManager = this.rosePlugin.getManager(StackManager.class);
        if (stackManager != null)
            stackManager.backfillChunkMaintenance();

        this.rosePlugin.getLogger().info("Folia region threading is active; RoseStacker is scheduling stack work per region.");
    }

}
