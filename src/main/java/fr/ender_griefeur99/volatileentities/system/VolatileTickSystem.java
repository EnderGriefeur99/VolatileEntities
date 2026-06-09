package fr.ender_griefeur99.volatileentities.system;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import org.joml.Vector3d;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import fr.ender_griefeur99.volatileentities.VolatileEntitiesPlugin;
import fr.ender_griefeur99.volatileentities.component.VolatileComponent;
import fr.ender_griefeur99.volatileentities.config.VolatileConfig;
import fr.ender_griefeur99.volatileentities.config.VolatileContext;
import fr.ender_griefeur99.volatileentities.policy.VolatilePolicy;
import fr.ender_griefeur99.volatileentities.policy.VolatileRemovalReason;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Main ticking system that applies Volatile policies every frame:
 * - kill-on-load (expiredOnLoad)
 * - LINKED_ENTITY_INVALID / MAX_DISTANCE / IDLE_TIMEOUT / CUSTOM
 */
public final class VolatileTickSystem extends EntityTickingSystem<EntityStore> {

    private static final Archetype<EntityStore> QUERY = Archetype.of(
            VolatileComponent.getComponentType(),
            TransformComponent.getComponentType()
    );

    @Nonnull
    private final VolatileEntitiesPlugin plugin;

    public VolatileTickSystem(@Nonnull VolatileEntitiesPlugin plugin) {
        this.plugin = plugin;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return QUERY;
    }

    @Override
    public void tick(
            float dt,
            int index,
            @Nonnull ArchetypeChunk<EntityStore> chunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        VolatileComponent vc = chunk.getComponent(index, VolatileComponent.getComponentType());
        if (vc == null) return;

        Ref<EntityStore> ref = chunk.getReferenceTo(index);
        if (ref == null || !ref.isValid()) return;

        // If this component was loaded from disk, remove the entity on first tick
        if (vc.isExpiredOnLoad()) {
            plugin.getLogger().atFine().log(
                    "[VolatileEntities] Removing reloaded volatile entity %s (timestamp=%d)",
                    ref, vc.getTimestamp()
            );
            commandBuffer.removeEntity(ref, VolatileRemovalReason.SERVER_SHUTDOWN.toHytale());
            return;
        }

        TransformComponent transform = chunk.getComponent(index, TransformComponent.getComponentType());
        if (transform == null) return;

        // If someone manually flagged it, remove it
        if (vc.isMarkedForRemoval()) {
            removeEntity(ref, commandBuffer, VolatileRemovalReason.MANUAL_INVALIDATION);
            return;
        }

        VolatileConfig config = vc.getConfig();

        VolatileContext context = VolatileContext.builder()
                .store(store)
                .chunk(chunk)
                .index(index)
                .entityRef(ref)
                .volatileComponent(vc)
                .deltaTime(dt)
                .build();

        // Evaluate all active policies
        for (VolatilePolicy policy : config.getPolicies()) {
            VolatileRemovalReason reason = checkPolicy(policy, vc, context, store);
            if (reason != null) {
                if (config.isRemoveOnInvalid()) {
                    removeEntity(ref, commandBuffer, reason);
                } else {
                    vc.markForRemoval(reason.getDescription());
                }
                return;
            }
        }

        // Handle idle timeout if enabled
        if (config.hasPolicy(VolatilePolicy.IDLE_TIMEOUT)) {
            vc.tickIdle();
        }
    }

    @Nullable
    private VolatileRemovalReason checkPolicy(
            @Nonnull VolatilePolicy policy,
            @Nonnull VolatileComponent vc,
            @Nonnull VolatileContext context,
            @Nonnull Store<EntityStore> store
    ) {
        return switch (policy) {
            case CHUNK_UNLOAD       -> null;                      // handled by VolatileChunkUnloadSystem
            case LINKED_ENTITY_INVALID -> checkLinkedEntity(vc);
            case MAX_DISTANCE       -> checkDistance(vc, context, store);
            case OWNER_DISCONNECT   -> null;                      // handled by VolatileOwnerDisconnectListener
            case OUT_OF_BOUNDS      -> null;                      // custom world-bounds logic if needed
            case IDLE_TIMEOUT       -> checkIdleTimeout(vc);
            case USE_DESPAWN_TTL    -> null;                      // DespawnComponent handles TTL alone
            case CUSTOM             -> checkCustomCondition(vc, context);
        };
    }

    @Nullable
    private VolatileRemovalReason checkLinkedEntity(@Nonnull VolatileComponent vc) {
        Ref<EntityStore> linked = vc.getLinkedEntity();
        if (linked == null) return null;
        if (!linked.isValid()) {
            return VolatileRemovalReason.LINKED_ENTITY_INVALID;
        }
        return null;
    }

    @Nullable
    private VolatileRemovalReason checkDistance(
            @Nonnull VolatileComponent vc,
            @Nonnull VolatileContext context,
            @Nonnull Store<EntityStore> store
    ) {
        Ref<EntityStore> linked = vc.getLinkedEntity();
        if (linked == null || !linked.isValid()) {
            return VolatileRemovalReason.LINKED_ENTITY_INVALID;
        }

        TransformComponent myT =
                context.getChunk().getComponent(context.getIndex(), TransformComponent.getComponentType());
        if (myT == null) {
            return VolatileRemovalReason.MAX_DISTANCE_EXCEEDED;
        }

        TransformComponent targetT = store.getComponent(linked, TransformComponent.getComponentType());
        if (targetT == null) {
            return VolatileRemovalReason.MAX_DISTANCE_EXCEEDED;
        }

        Vector3d myPos = myT.getPosition();
        Vector3d targetPos = targetT.getPosition();
        double distSq = myPos.distanceSquared(targetPos);

        if (distSq > (double) vc.getMaxDistanceSquared()) {
            return VolatileRemovalReason.MAX_DISTANCE_EXCEEDED;
        }
        return null;
    }

    @Nullable
    private VolatileRemovalReason checkIdleTimeout(@Nonnull VolatileComponent vc) {
        if (vc.isIdleTimedOut()) {
            return VolatileRemovalReason.IDLE_TIMEOUT;
        }
        return null;
    }

    @Nullable
    private VolatileRemovalReason checkCustomCondition(
            @Nonnull VolatileComponent vc,
            @Nonnull VolatileContext context
    ) {
        var condition = vc.getConfig().getCustomCondition();
        if (condition != null && condition.test(context)) {
            return VolatileRemovalReason.CUSTOM_CONDITION;
        }
        return null;
    }

    private void removeEntity(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull VolatileRemovalReason reason
    ) {
        plugin.getLogger().atFine().log(
                "[VolatileEntities] Removing entity %s - Reason: %s",
                ref,
                reason.getDescription()
        );
        commandBuffer.removeEntity(ref, reason.toHytale());
    }
}