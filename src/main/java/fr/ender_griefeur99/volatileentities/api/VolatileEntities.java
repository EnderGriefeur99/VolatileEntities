package fr.ender_griefeur99.volatileentities.api;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import org.joml.Vector3d;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import fr.ender_griefeur99.volatileentities.VolatileEntitiesPlugin;
import fr.ender_griefeur99.volatileentities.component.VolatileComponent;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Static entry point for working with volatile entities.
 *
 * Typical usage:
 * <pre>
 *   // Spawn a 10s volatile entity at a position
 *   VolatileEntities.builder()
 *       .at(new Vector3d(x, y, z))
 *       .idleTimeoutSeconds(10f)
 *       .spawn(commandBuffer);
 *
 *   // Check if an entity is volatile
 *   if (VolatileEntities.isVolatile(store, ref)) {
 *       VolatileEntities.invalidate(store, ref, "Cleanup");
 *   }
 * </pre>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class VolatileEntities {

    private static final int DEFAULT_TICK_RATE = 20;

    /**
     * Create a new builder to define and spawn a volatile entity.
     */
    @Nonnull
    public static VolatileEntityBuilder builder() {
        return new VolatileEntityBuilder();
    }

    // ---------- Convenience spawners (minimal) ----------

    /**
     * Spawn a simple volatile entity at a position with a runtime timeout (in seconds).
     * This is a minimal helper – for anything custom, prefer using the builder directly.
     */
    @Nonnull
    @SafeVarargs
    public static Ref<EntityStore> spawnTemporarySeconds(
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull Vector3d position,
            float seconds,
            @Nonnull Component<EntityStore>... ignoreComponents
    ) {
        return builder()
                .at(position)
                .idleTimeoutSeconds(seconds, DEFAULT_TICK_RATE)
                .spawn(commandBuffer);
    }

    // ---------- Introspection / management helpers ----------

    /**
     * Check if an entity has a VolatileComponent.
     */
    public static boolean isVolatile(
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref
    ) {
        if (!ref.isValid()) {
            return false;
        }
        return store.getComponent(ref, VolatileComponent.getComponentType()) != null;
    }

    /**
     * Get the VolatileComponent of an entity (or null if none).
     */
    @Nullable
    public static VolatileComponent getComponent(
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref
    ) {
        if (!ref.isValid()) {
            return null;
        }
        return store.getComponent(ref, VolatileComponent.getComponentType());
    }

    /**
     * Mark a volatile entity for removal (reason is optional).
     *
     * The actual removal is handled by VolatileTickSystem on the next tick.
     */
    public static void invalidate(
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nullable String reason
    ) {
        VolatileComponent vc = getComponent(store, ref);
        if (vc != null) {
            vc.markForRemoval(reason);
        }
    }

    /**
     * Simple variant of {@link #invalidate(Store, Ref, String)} without a reason.
     */
    public static void invalidate(
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref
    ) {
        invalidate(store, ref, null);
    }

    /**
     * Reset the idle timeout of a volatile entity (if IDLE_TIMEOUT is enabled).
     */
    public static void resetIdleTimer(
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref
    ) {
        VolatileComponent vc = getComponent(store, ref);
        if (vc != null) {
            vc.resetIdleTicks();
        }
    }

    /**
     * Check if an entity is currently marked for removal.
     */
    public static boolean isMarkedForRemoval(
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref
    ) {
        VolatileComponent vc = getComponent(store, ref);
        return vc != null && vc.isMarkedForRemoval();
    }

    /**
     * Get remaining idle ticks before timeout, or -1 if not volatile.
     */
    public static int getIdleTicksRemaining(
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref
    ) {
        VolatileComponent vc = getComponent(store, ref);
        return vc != null ? vc.getIdleTicksRemaining() : -1;
    }

    /**
     * Get remaining idle time in seconds (using the default server tick rate).
     */
    public static float getTimeRemainingSeconds(
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref
    ) {
        return getTimeRemainingSeconds(store, ref, DEFAULT_TICK_RATE);
    }

    /**
     * Get remaining idle time in seconds using a custom tick rate.
     */
    public static float getTimeRemainingSeconds(
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            int tickRate
    ) {
        int ticks = getIdleTicksRemaining(store, ref);
        return ticks > 0 ? (float) ticks / tickRate : 0f;
    }

    // ---------- Plugin & service access (if ever needed) ----------

    /**
     * Access the plugin instance (mainly useful for advanced integrations).
     */
    @Nonnull
    public static VolatileEntitiesPlugin getPlugin() {
        return VolatileEntitiesPlugin.getInstance();
    }
}