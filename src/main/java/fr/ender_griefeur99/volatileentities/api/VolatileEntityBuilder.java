package fr.ender_griefeur99.volatileentities.api;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.vector.Rotation3f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import fr.ender_griefeur99.volatileentities.component.VolatileComponent;
import fr.ender_griefeur99.volatileentities.config.VolatileConfig;
import fr.ender_griefeur99.volatileentities.config.VolatileContext;
import fr.ender_griefeur99.volatileentities.policy.VolatilePolicy;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Fluent builder for creating volatile entities.
 *
 * Example:
 * <pre>
 *   Ref&lt;EntityStore&gt; ref = VolatileEntities.builder()
 *       .at(new Vector3d(x, y, z))
 *       .with(MyComponent.TYPE, new MyComponent(...))
 *       .idleTimeoutSeconds(10f, 20) // 10s at 20 TPS
 *       .spawn(commandBuffer);
 * </pre>
 */
@NoArgsConstructor(access = AccessLevel.PACKAGE)
public final class VolatileEntityBuilder {

    private static final int DEFAULT_TICK_RATE = 20;

    private record CompEntry<T extends Component<EntityStore>>(
            ComponentType<EntityStore, T> type,
            T component
    ) {}

    private final List<CompEntry<?>> entries = new ArrayList<>();
    private final VolatileConfig.VolatileConfigBuilder configBuilder = VolatileConfig.builder();

    private <T extends Component<EntityStore>> void add(
            @Nonnull ComponentType<EntityStore, T> type,
            @Nonnull T component
    ) {
        entries.add(new CompEntry<>(type, component));
    }

    // ---------- Position & Transform ----------

    /**
     * Set the entity's position. Rotation defaults to (0, 0, 0).
     */
    @Nonnull
    public VolatileEntityBuilder at(@Nonnull Vector3d position) {
        add(TransformComponent.getComponentType(),
                new TransformComponent(position, new Rotation3f(0f, 0f, 0f)));
        return this;
    }

    /**
     * Set the entity's position.
     */
    @Nonnull
    public VolatileEntityBuilder at(double x, double y, double z) {
        return at(new Vector3d(x, y, z));
    }

    /**
     * Set the entity's transform (position + rotation).
     */
    @Nonnull
    public VolatileEntityBuilder at(@Nonnull Vector3d position, @Nonnull Rotation3f rotation) {
        add(TransformComponent.getComponentType(), new TransformComponent(position, rotation));
        return this;
    }

    /**
     * Use a pre-built {@link TransformComponent}.
     */
    @Nonnull
    public VolatileEntityBuilder withTransform(@Nonnull TransformComponent transform) {
        add(TransformComponent.getComponentType(), transform);
        return this;
    }

    // ---------- Components ----------

    /**
     * Add a component with its type.
     */
    @Nonnull
    public <T extends Component<EntityStore>> VolatileEntityBuilder with(
            @Nonnull ComponentType<EntityStore, T> type,
            @Nonnull T component
    ) {
        add(type, component);
        return this;
    }

    // ---------- Policies ----------

    /**
     * Entity is removed when its chunk unloads (CHUNK_UNLOAD).
     */
    @Nonnull
    public VolatileEntityBuilder chunkBound() {
        configBuilder.policy(VolatilePolicy.CHUNK_UNLOAD);
        return this;
    }

    /**
     * Entity is removed when the linked entity becomes invalid (LINKED_ENTITY_INVALID).
     */
    @Nonnull
    public VolatileEntityBuilder linkedTo(@Nonnull Ref<EntityStore> entity) {
        configBuilder.policy(VolatilePolicy.LINKED_ENTITY_INVALID)
                .linkedEntity(entity);
        return this;
    }

    /**
     * Entity is removed when further than {@code distance} from the linked entity (MAX_DISTANCE).
     */
    @Nonnull
    public VolatileEntityBuilder withinDistance(@Nonnull Ref<EntityStore> entity, float distance) {
        configBuilder.policy(VolatilePolicy.MAX_DISTANCE)
                .linkedEntity(entity)
                .maxDistance(distance);
        return this;
    }

    /**
     * Entity is removed when the owner player disconnects (OWNER_DISCONNECT).
     */
    @Nonnull
    public VolatileEntityBuilder ownedBy(@Nonnull UUID playerUuid) {
        configBuilder.policy(VolatilePolicy.OWNER_DISCONNECT)
                .ownerUuid(playerUuid);
        return this;
    }

    /**
     * Entity is removed when leaving world bounds (OUT_OF_BOUNDS).
     * (You must implement the actual bounds logic in your own system if needed.)
     */
    @Nonnull
    public VolatileEntityBuilder worldBounded() {
        configBuilder.policy(VolatilePolicy.OUT_OF_BOUNDS);
        return this;
    }

    /**
     * Entity is removed after {@code ticks} of idle time (IDLE_TIMEOUT).
     */
    @Nonnull
    public VolatileEntityBuilder idleTimeout(int ticks) {
        configBuilder.policy(VolatilePolicy.IDLE_TIMEOUT)
                .idleTimeoutTicks(ticks);
        return this;
    }

    /**
     * Entity is removed after {@code seconds} of idle time at the given tick rate.
     */
    @Nonnull
    public VolatileEntityBuilder idleTimeoutSeconds(float seconds, int tickRate) {
        return idleTimeout((int) (seconds * tickRate));
    }

    /**
     * Entity is removed after {@code seconds} of idle time using the default tick rate (20 TPS).
     */
    @Nonnull
    public VolatileEntityBuilder idleTimeoutSeconds(float seconds) {
        return idleTimeoutSeconds(seconds, DEFAULT_TICK_RATE);
    }

    /**
     * Entity uses ONLY Hytale's DespawnComponent as TTL (USE_DESPAWN_TTL).
     * Volatile systems will not refresh DespawnComponent for this entity.
     */
    @Nonnull
    public VolatileEntityBuilder useDespawnTtl() {
        configBuilder.policy(VolatilePolicy.USE_DESPAWN_TTL);
        return this;
    }

    /**
     * Custom removal condition (CUSTOM policy).
     */
    @Nonnull
    public VolatileEntityBuilder customCondition(@Nonnull Predicate<VolatileContext> condition) {
        configBuilder.policy(VolatilePolicy.CUSTOM)
                .customCondition(condition);
        return this;
    }

    /**
     * When false, the entity will only be marked for removal, not immediately deleted.
     */
    @Nonnull
    public VolatileEntityBuilder dontRemoveOnInvalid() {
        configBuilder.removeOnInvalid(false);
        return this;
    }

    // ---------- Build & spawn ----------

    /**
     * Build the volatile configuration without spawning the entity.
     */
    @Nonnull
    public VolatileConfig buildConfig() {
        return configBuilder.build();
    }

    /**
     * Build a {@link Holder} containing all configured components, including the VolatileComponent.
     */
    @Nonnull
    public Holder<EntityStore> buildHolder() {
        Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();

        for (CompEntry<?> raw : entries) {
            applyEntry(holder, raw);
        }

        VolatileConfig cfg = buildConfig();
        VolatileComponent vc = new VolatileComponent(cfg);
        holder.addComponent(VolatileComponent.getComponentType(), vc);

        return holder;
    }

    /**
     * Spawn the built entity into the world using the given {@link CommandBuffer}.
     *
     * @return the ECS reference to the newly spawned entity.
     */
    @Nonnull
    public Ref<EntityStore> spawn(@Nonnull CommandBuffer<EntityStore> commandBuffer) {
        Holder<EntityStore> holder = buildHolder();
        return commandBuffer.addEntity(holder, AddReason.SPAWN);
    }

    /**
     * Spawn the entity and return both its reference and the VolatileComponent instance.
     */
    @Nonnull
    public SpawnResult spawnWithComponent(@Nonnull CommandBuffer<EntityStore> commandBuffer) {
        VolatileConfig cfg = buildConfig();
        VolatileComponent vc = new VolatileComponent(cfg);

        Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();
        for (CompEntry<?> raw : entries) {
            applyEntry(holder, raw);
        }
        holder.addComponent(VolatileComponent.getComponentType(), vc);

        Ref<EntityStore> ref = commandBuffer.addEntity(holder, AddReason.SPAWN);
        return new SpawnResult(ref, vc);
    }

    @SuppressWarnings("unchecked")
    private static void applyEntry(Holder<EntityStore> holder, CompEntry<?> raw) {
        CompEntry<? extends Component<EntityStore>> e =
                (CompEntry<? extends Component<EntityStore>>) raw;

        holder.addComponent(
                (ComponentType<EntityStore, Component<EntityStore>>) e.type(),
                (Component<EntityStore>) e.component()
        );
    }

    /**
     * Simple container returned by {@link #spawnWithComponent(CommandBuffer)}.
     */
    public record SpawnResult(
            @Nonnull Ref<EntityStore> entityRef,
            @Nonnull VolatileComponent volatileComponent
    ) {}
}