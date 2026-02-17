package fr.ender_griefeur99.volatileentities.system;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import fr.ender_griefeur99.volatileentities.VolatileEntitiesPlugin;
import fr.ender_griefeur99.volatileentities.component.VolatileComponent;
import fr.ender_griefeur99.volatileentities.policy.VolatilePolicy;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * Plugin event listener for the OWNER_DISCONNECT policy.
 *
 * This is not an ECS system – it's hooked into the server's event bus.
 * When a player disconnects, this listener:
 * - scans the world for volatile entities,
 * - and marks those with OWNER_DISCONNECT and matching owner UUID for removal.
 *
 * Actual deletion is performed by {@link fr.ender_griefeur99.volatileentities.system.VolatileTickSystem}
 * on the next tick.
 */
public final class VolatileOwnerDisconnectListener {

    private static final Archetype<EntityStore> QUERY = Archetype.of(
            VolatileComponent.getComponentType()
    );

    public static void onPlayerDisconnect(@Nonnull PlayerDisconnectEvent event) {
        UUID playerUuid = event.getPlayerRef().getUuid();

        // Get the world where the player was last present
        World world = Universe.get().getWorld(event.getPlayerRef().getWorldUuid());
        if (world == null) return;
        world.execute(() -> {
            Store<EntityStore> store = world.getEntityStore().getStore();

            // For safety, you could wrap this in world.execute(...) if needed
            store.forEachChunk(QUERY, (ArchetypeChunk<EntityStore> chunk, CommandBuffer<EntityStore> cmd) -> {
                for (int i = 0; i < chunk.size(); i++) {
                    VolatileComponent vc = chunk.getComponent(i, VolatileComponent.getComponentType());
                    if (vc == null) continue;
                    if (!vc.hasPolicy(VolatilePolicy.OWNER_DISCONNECT)) continue;

                    UUID owner = vc.getOwnerUuid();
                    if (owner != null && owner.equals(playerUuid)) {
                        // Simply mark the entity for removal. VolatileTickSystem will do the actual remove().
                        vc.markForRemoval("Owner disconnected");
                    }
                }
            });

            VolatileEntitiesPlugin.getInstance().getLogger().atFine().log(
                    "[VolatileEntities] Marked volatile entities of player %s for removal on disconnect",
                    playerUuid
            );
        });
    }
}