/*
 * This file is part of Sponge, licensed under the MIT License (MIT).
 *
 * Copyright (c) SpongePowered <https://www.spongepowered.org>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.spongepowered.common.mixin.core.world;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import co.aikar.timings.WorldTimingsHandler;
import com.flowpowered.math.vector.Vector2i;
import com.flowpowered.math.vector.Vector3d;
import com.flowpowered.math.vector.Vector3i;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.mojang.authlib.GameProfile;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRedstoneRepeater;
import net.minecraft.block.BlockRedstoneTorch;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.boss.EntityDragonPart;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.item.EntityEnderPearl;
import net.minecraft.entity.item.EntityFallingBlock;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityPainting;
import net.minecraft.entity.item.EntityPainting.EnumArt;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.projectile.EntityPotion;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.server.S23PacketBlockChange;
import net.minecraft.profiler.Profiler;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.biome.WorldChunkManager;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraft.world.storage.ISaveHandler;
import net.minecraft.world.storage.WorldInfo;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.reflect.ConstructorUtils;
import org.spongepowered.api.Platform;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.block.BlockType;
import org.spongepowered.api.block.tileentity.TileEntity;
import org.spongepowered.api.data.DataContainer;
import org.spongepowered.api.data.manipulator.DataManipulator;
import org.spongepowered.api.entity.Entity;
import org.spongepowered.api.entity.EntitySnapshot;
import org.spongepowered.api.entity.EntityType;
import org.spongepowered.api.entity.Item;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.entity.living.player.User;
import org.spongepowered.api.entity.projectile.EnderPearl;
import org.spongepowered.api.entity.projectile.source.ProjectileSource;
import org.spongepowered.api.event.SpongeEventFactory;
import org.spongepowered.api.event.block.NotifyNeighborBlockEvent;
import org.spongepowered.api.event.cause.Cause;
import org.spongepowered.api.event.cause.NamedCause;
import org.spongepowered.api.event.cause.entity.spawn.SpawnCause;
import org.spongepowered.api.event.entity.SpawnEntityEvent;
import org.spongepowered.api.plugin.PluginContainer;
import org.spongepowered.api.service.context.Context;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.channel.MessageChannel;
import org.spongepowered.api.text.chat.ChatType;
import org.spongepowered.api.text.title.Title;
import org.spongepowered.api.util.Direction;
import org.spongepowered.api.util.DiscreteTransform3;
import org.spongepowered.api.util.Functional;
import org.spongepowered.api.util.PositionOutOfBoundsException;
import org.spongepowered.api.util.annotation.NonnullByDefault;
import org.spongepowered.api.world.Chunk;
import org.spongepowered.api.world.Dimension;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.PortalAgent;
import org.spongepowered.api.world.World;
import org.spongepowered.api.world.WorldBorder;
import org.spongepowered.api.world.WorldCreationSettings;
import org.spongepowered.api.world.biome.BiomeType;
import org.spongepowered.api.world.difficulty.Difficulty;
import org.spongepowered.api.world.explosion.Explosion;
import org.spongepowered.api.world.extent.Extent;
import org.spongepowered.api.world.extent.worker.MutableBiomeAreaWorker;
import org.spongepowered.api.world.extent.worker.MutableBlockVolumeWorker;
import org.spongepowered.api.world.gen.WorldGenerator;
import org.spongepowered.api.world.gen.WorldGeneratorModifier;
import org.spongepowered.api.world.storage.WorldProperties;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.common.SpongeImpl;
import org.spongepowered.common.block.SpongeBlockSnapshot;
import org.spongepowered.common.block.SpongeBlockSnapshotBuilder;
import org.spongepowered.common.config.SpongeConfig;
import org.spongepowered.common.config.type.WorldConfig;
import org.spongepowered.common.data.type.SpongeTileEntityType;
import org.spongepowered.common.event.CauseTracker;
import org.spongepowered.common.event.SpongeCommonEventFactory;
import org.spongepowered.common.interfaces.IMixinChunk;
import org.spongepowered.common.interfaces.entity.IMixinEntity;
import org.spongepowered.common.interfaces.entity.player.IMixinEntityPlayer;
import org.spongepowered.common.interfaces.world.IMixinWorld;
import org.spongepowered.common.interfaces.world.IMixinWorldInfo;
import org.spongepowered.common.interfaces.world.IMixinWorldSettings;
import org.spongepowered.common.interfaces.world.IMixinWorldType;
import org.spongepowered.common.interfaces.world.gen.IMixinChunkProviderServer;
import org.spongepowered.common.interfaces.world.gen.IPopulatorProvider;
import org.spongepowered.common.registry.provider.DirectionFacingProvider;
import org.spongepowered.common.registry.type.event.InternalSpawnTypes;
import org.spongepowered.common.util.SpongeHooks;
import org.spongepowered.common.util.StaticMixinHelper;
import org.spongepowered.common.util.VecHelper;
import org.spongepowered.common.world.CaptureType;
import org.spongepowered.common.world.DimensionManager;
import org.spongepowered.common.world.FakePlayer;
import org.spongepowered.common.world.SpongeChunkPreGenerate;
import org.spongepowered.common.world.border.PlayerBorderListener;
import org.spongepowered.common.world.extent.ExtentViewDownsize;
import org.spongepowered.common.world.extent.ExtentViewTransform;
import org.spongepowered.common.world.extent.worker.SpongeMutableBiomeAreaWorker;
import org.spongepowered.common.world.extent.worker.SpongeMutableBlockVolumeWorker;
import org.spongepowered.common.world.gen.SpongeChunkProvider;
import org.spongepowered.common.world.gen.SpongeWorldGenerator;
import org.spongepowered.common.world.gen.WorldGenConstants;
import org.spongepowered.common.world.storage.SpongeChunkLayout;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

@NonnullByDefault
@Mixin(net.minecraft.world.World.class)
public abstract class MixinWorld implements World, IMixinWorld {

    private static final Vector3i BLOCK_MIN = new Vector3i(-30000000, 0, -30000000);
    private static final Vector3i BLOCK_MAX = new Vector3i(30000000, 256, 30000000).sub(1, 1, 1);
    private static final Vector3i BLOCK_SIZE = BLOCK_MAX.sub(BLOCK_MIN).add(1, 1, 1);
    private static final Vector2i BIOME_MIN = BLOCK_MIN.toVector2(true);
    private static final Vector2i BIOME_MAX = BLOCK_MAX.toVector2(true);
    private static final Vector2i BIOME_SIZE = BIOME_MAX.sub(BIOME_MIN).add(1, 1);
    private static final String
            CHECK_NO_ENTITY_COLLISION =
            "checkNoEntityCollision(Lnet/minecraft/util/AxisAlignedBB;Lnet/minecraft/entity/Entity;)Z";
    private static final String
            GET_ENTITIES_WITHIN_AABB =
            "Lnet/minecraft/world/World;getEntitiesWithinAABBExcludingEntity(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/AxisAlignedBB;)Ljava/util/List;";
    public SpongeBlockSnapshotBuilder builder = new SpongeBlockSnapshotBuilder();
    private boolean keepSpawnLoaded;
    private Context worldContext;
    private SpongeChunkProvider spongegen;
    protected boolean processingExplosion = false;
    protected SpongeConfig<?> activeConfig;
    private MessageChannel channel = MessageChannel.world(this);
    protected CauseTracker causeTracker;
    private final Map<net.minecraft.entity.Entity, Vector3d> rotationUpdates = new HashMap<>();
    protected WorldTimingsHandler timings;

    // @formatter:off
    @Shadow @Final public boolean isRemote;
    @Shadow @Final public WorldProvider provider;
    @Shadow @Final public Random rand;
    @Shadow @Final public Profiler theProfiler;
    @Shadow @Final public List<net.minecraft.entity.Entity> loadedEntityList;
    @Shadow @Final public List<net.minecraft.entity.Entity> unloadedEntityList;
    @Shadow @Final public List<net.minecraft.tileentity.TileEntity> loadedTileEntityList;
    @Shadow @Final public List<net.minecraft.tileentity.TileEntity> tickableTileEntities;
    @Shadow @Final public List<net.minecraft.tileentity.TileEntity> addedTileEntityList;
    @Shadow @Final public List<net.minecraft.tileentity.TileEntity> tileEntitiesToBeRemoved;
    @Shadow @Final public List<EntityPlayer> playerEntities;
    @Shadow @Final public List<net.minecraft.entity.Entity> weatherEffects;
    @Shadow public boolean processingLoadedTiles;
    @Shadow @Final public net.minecraft.world.border.WorldBorder worldBorder;
    @Shadow protected boolean scheduledUpdatesAreImmediate;
    @Shadow protected WorldInfo worldInfo;
    @Shadow public Set<ChunkCoordIntPair> activeChunkSet;
    @Shadow protected int updateLCG;

    @Shadow public abstract net.minecraft.world.border.WorldBorder shadow$getWorldBorder();
    @Shadow public abstract EnumDifficulty shadow$getDifficulty();

    @Shadow public net.minecraft.world.World init() {
        // Should never be overwritten because this is @Shadow'ed
        throw new RuntimeException("Bad things have happened");
    }

    @Shadow public abstract void playSoundEffect(double x, double y, double z, String soundName, float volume, float pitch);
    @Shadow public abstract void updateComparatorOutputLevel(BlockPos pos, Block blockIn);
    @Shadow public abstract void notifyNeighborsRespectDebug(BlockPos pos, Block blockType);
    @Shadow public abstract void markBlockForUpdate(BlockPos pos);
    @Shadow public abstract boolean checkLight(BlockPos pos);
    @Shadow public abstract boolean isValid(BlockPos pos);
    @Shadow public abstract boolean addTileEntity(net.minecraft.tileentity.TileEntity tile);
    @Shadow public abstract void onEntityAdded(net.minecraft.entity.Entity entityIn);
    @Shadow protected abstract void onEntityRemoved(net.minecraft.entity.Entity entityIn);
    @Shadow public abstract void updateEntity(net.minecraft.entity.Entity ent);
    @Shadow public abstract net.minecraft.world.chunk.Chunk getChunkFromBlockCoords(BlockPos pos);
    @Shadow public abstract boolean isBlockLoaded(BlockPos pos);
    @Shadow public abstract boolean addWeatherEffect(net.minecraft.entity.Entity entityIn);
    @Shadow public abstract BiomeGenBase getBiomeGenForCoords(BlockPos pos);
    @Shadow public abstract IChunkProvider getChunkProvider();
    @Shadow public abstract WorldChunkManager getWorldChunkManager();
    @Shadow @Nullable public abstract net.minecraft.tileentity.TileEntity getTileEntity(BlockPos pos);
    @Shadow public abstract boolean isBlockPowered(BlockPos pos);
    @Shadow public abstract IBlockState getBlockState(BlockPos pos);
    @Shadow public abstract net.minecraft.world.chunk.Chunk getChunkFromChunkCoords(int chunkX, int chunkZ);
    @Shadow public abstract boolean isChunkLoaded(int x, int z, boolean allowEmpty);
    @Shadow public abstract net.minecraft.world.Explosion newExplosion(net.minecraft.entity.Entity entityIn, double x, double y, double z, float strength,
            boolean isFlaming, boolean isSmoking);
    @Shadow public abstract List<net.minecraft.entity.Entity> getEntities(Class<net.minecraft.entity.Entity> entityType,
            com.google.common.base.Predicate<net.minecraft.entity.Entity> filter);
    @Shadow public abstract List<net.minecraft.entity.Entity> getEntitiesWithinAABBExcludingEntity(net.minecraft.entity.Entity entityIn, AxisAlignedBB bb);
    @Shadow public abstract boolean isAreaLoaded(BlockPos center, int radius, boolean allowEmpty);
    @Shadow protected abstract void playMoodSoundAndCheckLight(int x, int z, net.minecraft.world.chunk.Chunk chunkIn);
    @Shadow protected abstract void setActivePlayerChunksAndCheckLight();

    // @formatter:on
    @Inject(method = "<init>", at = @At("RETURN"))
    public void onConstructed(ISaveHandler saveHandlerIn, WorldInfo info, WorldProvider providerIn, Profiler profilerIn, boolean client,
            CallbackInfo ci) {
        if (info == null) {
            SpongeImpl.getLogger().warn("World constructed without a WorldInfo! This will likely cause problems. Subsituting dummy info.",
                    new RuntimeException("Stack trace:"));
            this.worldInfo = new WorldInfo(new WorldSettings(0, WorldSettings.GameType.NOT_SET, false, false, WorldType.DEFAULT),
                    "sponge$dummy_world");
        }
        this.worldContext = new Context(Context.WORLD_KEY, this.worldInfo.getWorldName());
        if (SpongeImpl.getGame().getPlatform().getType() == Platform.Type.SERVER) {
            this.worldBorder.addListener(new PlayerBorderListener(providerIn.getDimensionId()));
        }
        this.activeConfig = SpongeHooks.getActiveConfig((net.minecraft.world.World)(Object) this);
    }

    @Override
    public SpongeBlockSnapshot createSpongeBlockSnapshot(IBlockState state, IBlockState extended, BlockPos pos, int updateFlag) {
        this.builder.reset();
        Location<World> location = new Location<>((World) this, VecHelper.toVector(pos));
        this.builder.blockState((BlockState) state)
                .extendedState((BlockState) extended)
                .worldId(location.getExtent().getUniqueId())
                .position(location.getBlockPosition());
        Optional<UUID> creator = getCreator(pos.getX(), pos.getY(), pos.getZ());
        Optional<UUID> notifier = getNotifier(pos.getX(), pos.getY(), pos.getZ());
        if (creator.isPresent()) {
            this.builder.creator(creator.get());
        }
        if (notifier.isPresent()) {
            this.builder.notifier(notifier.get());
        }
        if (state.getBlock() instanceof ITileEntityProvider) {
            net.minecraft.tileentity.TileEntity te = getTileEntity(pos);
            if (te != null) {
                TileEntity tile = (TileEntity) te;
                for (DataManipulator<?, ?> manipulator : tile.getContainers()) {
                    this.builder.add(manipulator);
                }
                NBTTagCompound nbt = new NBTTagCompound();
                te.writeToNBT(nbt);
                this.builder.unsafeNbt(nbt);
            }
        }
        return new SpongeBlockSnapshot(this.builder, updateFlag);
    }

    @SuppressWarnings("rawtypes")
    @Inject(method = "getCollidingBoundingBoxes(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/AxisAlignedBB;)Ljava/util/List;", at = @At("HEAD"), cancellable = true)
    public void onGetCollidingBoundingBoxes(net.minecraft.entity.Entity entity, AxisAlignedBB axis,
            CallbackInfoReturnable<List> cir) {
        if (!entity.worldObj.isRemote && SpongeHooks.checkBoundingBoxSize(entity, axis)) {
            // Removing misbehaved living entities
            cir.setReturnValue(new ArrayList());
        }
    }

    @Override
    public UUID getUniqueId() {
        return ((WorldProperties) this.worldInfo).getUniqueId();
    }

    @Override
    public String getName() {
        return this.worldInfo.getWorldName();
    }

    @Override
    public Optional<Chunk> getChunk(int x, int y, int z) {
        if (!SpongeChunkLayout.instance.isValidChunk(x, y, z)) {
            return Optional.empty();
        }
        final WorldServer worldserver = (WorldServer) (Object) this;
        return Optional.ofNullable((Chunk) ((IMixinChunkProviderServer) worldserver.theChunkProviderServer).getChunkIfLoaded(x, z));
    }

    @Override
    public Optional<Chunk> loadChunk(int x, int y, int z, boolean shouldGenerate) {
        if (!SpongeChunkLayout.instance.isValidChunk(x, y, z)) {
            return Optional.empty();
        }
        final WorldServer worldserver = (WorldServer) (Object) this;
        // If we aren't generating, return the loaded chunk or try and load one from file
        if (!shouldGenerate) {
            final Chunk existing = (Chunk) ((IMixinChunkProviderServer) worldserver.theChunkProviderServer).getChunkIfLoaded(x, z);
            return Optional.ofNullable(existing != null ? existing : (Chunk) worldserver.theChunkProviderServer.loadChunkFromFile(x, z));
        }
        return Optional.ofNullable((Chunk) worldserver.theChunkProviderServer.loadChunk(x, z));
    }

    @Override
    public BlockState getBlock(int x, int y, int z) {
        checkBlockBounds(x, y, z);
        return (BlockState) getBlockState(new BlockPos(x, y, z));
    }

    @Override
    public BlockType getBlockType(int x, int y, int z) {
        checkBlockBounds(x, y, z);
        // avoid intermediate object creation from using BlockState
        return (BlockType) getChunkFromChunkCoords(x >> 4, z >> 4).getBlock(x, y, z);
    }

    @Override
    public void setBlock(int x, int y, int z, BlockState block) {
        setBlock(x, y, z, block, true);
    }


    private Direction checkValidFace(Direction face) {
        checkArgument(checkNotNull(face, "side").isCardinal() || face.isUpright(), "Direction must be a valid block face");
        return face;
    }

    private GameProfile getProfileFromCause(Cause cause) {
        checkNotNull(cause, "cause");
        Optional<Object> simulatedCause = cause.get(NamedCause.PLAYER_SIMULATED, Object.class);
        checkArgument(simulatedCause.isPresent(), "Cause does not contain a NamedCause.PLAYER_SIMULATED object");
        Object obj = simulatedCause.get();
        checkArgument(obj instanceof GameProfile || obj instanceof User, "Simulated cause object is not a GameProfile or User");
        return obj instanceof GameProfile ? (GameProfile) obj : (GameProfile) ((User) obj).getProfile();
    }

    @Override
    public boolean hitBlock(int x, int y, int z, Direction side, Cause cause) {
        return FakePlayer.controller.hit(this, x, y, z, checkValidFace(side), getProfileFromCause(cause), cause);
    }

    @Override
    public boolean interactBlock(int x, int y, int z, Direction side, Cause cause) {
        return FakePlayer.controller.interact(this, x, y, z, null, checkValidFace(side), getProfileFromCause(cause), cause);
    }

    @Override
    public boolean interactBlockWith(int x, int y, int z, org.spongepowered.api.item.inventory.ItemStack itemStack, Direction side, Cause cause) {
        return FakePlayer.controller.interact(this, x, y, z, checkNotNull(itemStack, "itemStack"), checkValidFace(side), getProfileFromCause(cause),
                cause);
    }

    @Override
    public boolean placeBlock(int x, int y, int z, BlockState block, Direction side, Cause cause) {
        return FakePlayer.controller.place(this, x, y, z, checkNotNull(block, "block"), checkValidFace(side), getProfileFromCause(cause), cause);
    }

    @Override
    public boolean digBlock(int x, int y, int z, Cause cause) {
        return FakePlayer.controller.dig(this, x, y, z, null, getProfileFromCause(cause), cause);
    }

    @Override
    public boolean digBlockWith(int x, int y, int z, org.spongepowered.api.item.inventory.ItemStack itemStack, Cause cause) {
        return FakePlayer.controller.dig(this, x, y, z, checkNotNull(itemStack, "itemStack"), getProfileFromCause(cause), cause);
    }

    @Override
    public int getBlockDigTimeWith(int x, int y, int z, org.spongepowered.api.item.inventory.ItemStack itemStack, Cause cause) {
        return FakePlayer.controller.digTime(this, x, y, z, checkNotNull(itemStack, "itemStack"), getProfileFromCause(cause), cause);
    }

    @Override
    public BiomeType getBiome(int x, int z) {
        checkBiomeBounds(x, z);
        return (BiomeType) this.getBiomeGenForCoords(new BlockPos(x, 0, z));
    }

    @Override
    public void setBiome(int x, int z, BiomeType biome) {
        checkBiomeBounds(x, z);
        ((Chunk) getChunkFromChunkCoords(x >> 4, z >> 4)).setBiome(x, z, biome);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Collection<Entity> getEntities() {
        return Lists.newArrayList((Collection<Entity>) (Object) this.loadedEntityList);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Collection<Entity> getEntities(Predicate<Entity> filter) {
        // This already returns a new copy
        return (Collection<Entity>) (Object) this.getEntities(net.minecraft.entity.Entity.class,
                Functional.java8ToGuava((Predicate<net.minecraft.entity.Entity>) (Object) filter));
    }

    @Override
    public Optional<Entity> createEntity(EntityType type, Vector3d position) {
        checkNotNull(type, "The entity type cannot be null!");
        checkNotNull(position, "The position cannot be null!");

        Entity entity = null;

        Class<? extends Entity> entityClass = type.getEntityClass();
        double x = position.getX();
        double y = position.getY();
        double z = position.getZ();

        if (entityClass.isAssignableFrom(EntityPlayerMP.class) || entityClass.isAssignableFrom(EntityDragonPart.class)) {
            // Unable to construct these
            return Optional.empty();
        }

        net.minecraft.world.World world = (net.minecraft.world.World) (Object) this;

        // Not all entities have a single World parameter as their constructor
        if (entityClass.isAssignableFrom(EntityLightningBolt.class)) {
            entity = (Entity) new EntityLightningBolt(world, x, y, z);
        } else if (entityClass.isAssignableFrom(EntityEnderPearl.class)) {
            EntityArmorStand tempEntity = new EntityArmorStand(world, x, y, z);
            tempEntity.posY -= tempEntity.getEyeHeight();
            entity = (Entity) new EntityEnderPearl(world, tempEntity);
            ((EnderPearl) entity).setShooter(ProjectileSource.UNKNOWN);
        }

        // Some entities need to have non-null fields (and the easiest way to
        // set them is to use the more specialised constructor).
        if (entityClass.isAssignableFrom(EntityFallingBlock.class)) {
            entity = (Entity) new EntityFallingBlock(world, x, y, z, Blocks.sand.getDefaultState());
        } else if (entityClass.isAssignableFrom(EntityItem.class)) {
            entity = (Entity) new EntityItem(world, x, y, z, new ItemStack(Blocks.stone));
        }

        if (entity == null) {
            try {
                entity = ConstructorUtils.invokeConstructor(entityClass, this);
                ((net.minecraft.entity.Entity) entity).setPosition(x, y, z);
            } catch (Exception e) {
                SpongeImpl.getLogger().error(ExceptionUtils.getStackTrace(e));
            }
        }

        // TODO - replace this with an actual check
        /*
        if (entity instanceof EntityHanging) {
            if (((EntityHanging) entity).facingDirection == null) {
                // TODO Some sort of detection of a valid direction?
                // i.e scan immediate blocks for something to attach onto.
                ((EntityHanging) entity).facingDirection = EnumFacing.NORTH;
            }
            if (!((EntityHanging) entity).onValidSurface()) {
                return Optional.empty();
            }
        }*/

        // Last chance to fix null fields
        if (entity instanceof EntityPotion) {
            // make sure EntityPotion.potionDamage is not null
            ((EntityPotion) entity).getPotionDamage();
        } else if (entity instanceof EntityPainting) {
            // This is default when art is null when reading from NBT, could
            // choose a random art instead?
            ((EntityPainting) entity).art = EnumArt.KEBAB;
        }

        return Optional.ofNullable(entity);
    }

    @Override
    public Optional<Entity> createEntity(DataContainer entityContainer) {
        // TODO once entity containers are implemented
        return Optional.empty();
    }

    @Override
    public Optional<Entity> createEntity(DataContainer entityContainer, Vector3d position) {
        // TODO once entity containers are implemented
        return Optional.empty();
    }

    @Override
    public Optional<Entity> restoreSnapshot(EntitySnapshot snapshot, Vector3d position) {
        EntitySnapshot entitySnapshot = snapshot.withLocation(new Location<>(this, position));
        return entitySnapshot.restore();
    }

    @Override
    public WorldBorder getWorldBorder() {
        return (WorldBorder) shadow$getWorldBorder();
    }

    @Override
    public WorldBorder.ChunkPreGenerate newChunkPreGenerate(Vector3d center, double diameter) {
        return new SpongeChunkPreGenerate(this, center, diameter);
    }


    @Override
    public Dimension getDimension() {
        return (Dimension) this.provider;
    }

    @Override
    public boolean doesKeepSpawnLoaded() {
        return this.keepSpawnLoaded;
    }

    @Override
    public void setKeepSpawnLoaded(boolean keepLoaded) {
        this.keepSpawnLoaded = keepLoaded;
    }

    @Override
    public SpongeConfig<WorldConfig> getWorldConfig() {
        return ((IMixinWorldInfo) this.worldInfo).getWorldConfig();
    }

    @Override
    public Optional<Entity> getEntity(UUID uuid) {
        // Note that MixinWorldServer is properly overriding this to use it's own mapping.
        for (net.minecraft.entity.Entity entity : this.loadedEntityList) {
            if (entity.getUniqueID().equals(uuid)) {
                return Optional.of((Entity) entity);
            }
        }
        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    @Override
    public Iterable<Chunk> getLoadedChunks() {
        return (List<Chunk>) (List<?>) ((ChunkProviderServer) this.getChunkProvider()).loadedChunks;
    }

    @Override
    public boolean unloadChunk(Chunk chunk) {
        checkArgument(chunk != null, "Chunk cannot be null!");
        return chunk.unloadChunk();
    }

    @Override
    public WorldCreationSettings getCreationSettings() {
        WorldProperties properties = this.getProperties();

        // Create based on WorldProperties
        WorldSettings settings = new WorldSettings(this.worldInfo);
        IMixinWorldSettings mixin = (IMixinWorldSettings) (Object) settings;
        mixin.setDimensionType(properties.getDimensionType());
        mixin.setGeneratorSettings(properties.getGeneratorSettings());
        mixin.setGeneratorModifiers(properties.getGeneratorModifiers());
        mixin.setEnabled(true);
        mixin.setKeepSpawnLoaded(this.keepSpawnLoaded);
        mixin.setLoadOnStartup(properties.loadOnStartup());

        return (WorldCreationSettings) (Object) settings;
    }

    @Override
    public void updateWorldGenerator() {

        IMixinWorldType worldType = (IMixinWorldType) this.getProperties().getGeneratorType();
        // Get the default generator for the world type
        DataContainer generatorSettings = this.getProperties().getGeneratorSettings();

        SpongeWorldGenerator newGenerator = worldType.createGenerator(this, generatorSettings);
        // If the base generator is an IChunkProvider which implements
        // IPopulatorProvider we request that it add its populators not covered
        // by the base generation populator
        if (newGenerator.getBaseGenerationPopulator() instanceof IChunkProvider) {
            // We check here to ensure that the IPopulatorProvider is one of our mixed in ones and not
            // from a mod chunk provider extending a provider that we mixed into
            if (WorldGenConstants.isValid((IChunkProvider) newGenerator.getBaseGenerationPopulator(), IPopulatorProvider.class)) {
                ((IPopulatorProvider) newGenerator.getBaseGenerationPopulator()).addPopulators(newGenerator);
            }
        } else if (newGenerator.getBaseGenerationPopulator() instanceof IPopulatorProvider) {
            // If its not a chunk provider but is a populator provider then we call it as well
            ((IPopulatorProvider) newGenerator.getBaseGenerationPopulator()).addPopulators(newGenerator);
        }

        // Re-apply all world generator modifiers
        WorldCreationSettings creationSettings = this.getCreationSettings();

        for (WorldGeneratorModifier modifier : this.getProperties().getGeneratorModifiers()) {
            modifier.modifyWorldGenerator(creationSettings, generatorSettings, newGenerator);
        }

        this.spongegen = createChunkProvider(newGenerator);
        this.spongegen.setGenerationPopulators(newGenerator.getGenerationPopulators());
        this.spongegen.setPopulators(newGenerator.getPopulators());
        this.spongegen.setBiomeOverrides(newGenerator.getBiomeSettings());

        ChunkProviderServer chunkProviderServer = (ChunkProviderServer) this.getChunkProvider();
        chunkProviderServer.serverChunkGenerator = this.spongegen;
    }

    @Override
    public SpongeChunkProvider createChunkProvider(SpongeWorldGenerator newGenerator) {
        return new SpongeChunkProvider((net.minecraft.world.World) (Object) this, newGenerator.getBaseGenerationPopulator(),
                newGenerator.getBiomeGenerator());
    }

    @Override
    public void onSpongeEntityAdded(net.minecraft.entity.Entity entity) {
        this.onEntityAdded(entity);
    }

    @Override
    public WorldGenerator getWorldGenerator() {
        return this.spongegen;
    }

    @Override
    public WorldProperties getProperties() {
        return (WorldProperties) this.worldInfo;
    }

    @Override
    public Location<World> getSpawnLocation() {
        return new Location<>(this, this.worldInfo.getSpawnX(), this.worldInfo.getSpawnY(), this.worldInfo.getSpawnZ());
    }

    @Override
    public Context getContext() {
        return this.worldContext;
    }

    @Override
    public Optional<TileEntity> getTileEntity(int x, int y, int z) {
        net.minecraft.tileentity.TileEntity tileEntity = getTileEntity(new BlockPos(x, y, z));
        if (tileEntity == null) {
            return Optional.empty();
        } else {
            return Optional.of((TileEntity) tileEntity);
        }
    }

    @Override
    public Vector2i getBiomeMin() {
        return BIOME_MIN;
    }

    @Override
    public Vector2i getBiomeMax() {
        return BIOME_MAX;
    }

    @Override
    public Vector2i getBiomeSize() {
        return BIOME_SIZE;
    }

    @Override
    public Vector3i getBlockMin() {
        return BLOCK_MIN;
    }

    @Override
    public Vector3i getBlockMax() {
        return BLOCK_MAX;
    }

    @Override
    public Vector3i getBlockSize() {
        return BLOCK_SIZE;
    }

    @Override
    public boolean containsBiome(int x, int z) {
        return VecHelper.inBounds(x, z, BIOME_MIN, BIOME_MAX);
    }

    @Override
    public boolean containsBlock(int x, int y, int z) {
        return VecHelper.inBounds(x, y, z, BLOCK_MIN, BLOCK_MAX);
    }

    private void checkBiomeBounds(int x, int z) {
        if (!containsBiome(x, z)) {
            throw new PositionOutOfBoundsException(new Vector2i(x, z), BIOME_MIN, BIOME_MAX);
        }
    }

    private void checkBlockBounds(int x, int y, int z) {
        if (!containsBlock(x, y, z)) {
            throw new PositionOutOfBoundsException(new Vector3i(x, y, z), BLOCK_MIN, BLOCK_MAX);
        }
    }

    @Override
    public Difficulty getDifficulty() {
        return (Difficulty) (Object) this.shadow$getDifficulty();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private List<Player> getPlayers() {
        return (List) ((net.minecraft.world.World) (Object) this).getPlayers(EntityPlayerMP.class, Predicates.alwaysTrue());
    }

    @Override
    public void sendMessage(Text message) {
        checkNotNull(message, "message");

        for (Player player : this.getPlayers()) {
            player.sendMessage(message);
        }
    }

    @Override
    public MessageChannel getMessageChannel() {
        return this.channel;
    }

    @Override
    public void setMessageChannel(MessageChannel channel) {
        this.channel = checkNotNull(channel, "channel");
    }

    @Override
    public void sendMessage(ChatType type, Text message) {
        checkNotNull(type, "type");
        checkNotNull(message, "message");

        for (Player player : this.getPlayers()) {
            player.sendMessage(type, message);
        }
    }

    @Override
    public void sendTitle(Title title) {
        checkNotNull(title, "title");

        for (Player player : getPlayers()) {
            player.sendTitle(title);
        }
    }

    @Override
    public void resetTitle() {
        getPlayers().forEach(Player::resetTitle);
    }

    @Override
    public void clearTitle() {
        getPlayers().forEach(Player::clearTitle);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Collection<TileEntity> getTileEntities() {
        return Lists.newArrayList((List<TileEntity>) (Object) this.loadedTileEntityList);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Collection<TileEntity> getTileEntities(Predicate<TileEntity> filter) {
        return ((List<TileEntity>) (Object) this.loadedTileEntityList).stream()
                .filter(filter)
                .collect(Collectors.toList());
    }

    @Override
    public boolean isLoaded() {
        return DimensionManager.getWorldFromDimId(this.provider.getDimensionId()) != null;
    }

    @Override
    public Optional<String> getGameRule(String gameRule) {
        return this.getProperties().getGameRule(gameRule);
    }

    @Override
    public Map<String, String> getGameRules() {
        return this.getProperties().getGameRules();
    }

    @Override
    public void triggerExplosion(Explosion explosion) {
        checkNotNull(explosion, "explosion");
        checkNotNull(explosion.getOrigin(), "origin");

        newExplosion((net.minecraft.entity.Entity) explosion.getSourceExplosive().orElse(null), explosion
                        .getOrigin().getX(), explosion.getOrigin().getY(), explosion.getOrigin().getZ(), explosion.getRadius(), explosion.canCauseFire(),
                explosion.shouldBreakBlocks());
    }

    @Override
    public Extent getExtentView(Vector3i newMin, Vector3i newMax) {
        checkBlockBounds(newMin.getX(), newMin.getY(), newMin.getZ());
        checkBlockBounds(newMax.getX(), newMax.getY(), newMax.getZ());
        return new ExtentViewDownsize(this, newMin, newMax);
    }

    @Override
    public Extent getExtentView(DiscreteTransform3 transform) {
        return new ExtentViewTransform(this, transform);
    }

    @Override
    public MutableBiomeAreaWorker<? extends World> getBiomeWorker() {
        return new SpongeMutableBiomeAreaWorker<>(this);
    }

    @Override
    public MutableBlockVolumeWorker<? extends World> getBlockWorker() {
        return new SpongeMutableBlockVolumeWorker<>(this);
    }

    @Override
    public BlockSnapshot createSnapshot(int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        IBlockState currentState = this.getBlockState(pos);
        return this.createSpongeBlockSnapshot(currentState, currentState.getBlock().getActualState(currentState,
                (IBlockAccess) this, pos), pos, 2);
    }

    @Override
    public boolean restoreSnapshot(BlockSnapshot snapshot, boolean force, boolean notifyNeighbors) {
        return snapshot.restore(force, notifyNeighbors);
    }

    @Override
    public boolean restoreSnapshot(int x, int y, int z, BlockSnapshot snapshot, boolean force, boolean notifyNeighbors) {
        snapshot = snapshot.withLocation(new Location<>(this, new Vector3i(x, y, z)));
        return snapshot.restore(force, notifyNeighbors);
    }

    @Override
    public Optional<UUID> getCreator(int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        IMixinChunk spongeChunk = (IMixinChunk) getChunkFromBlockCoords(pos);
        Optional<User> user = spongeChunk.getBlockOwner(pos);
        if (user.isPresent()) {
            return Optional.of(user.get().getUniqueId());
        } else {
            return Optional.empty();
        }
    }

    @Override
    public Optional<UUID> getNotifier(int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        IMixinChunk spongeChunk = (IMixinChunk) getChunkFromBlockCoords(pos);
        Optional<User> user = spongeChunk.getBlockNotifier(pos);
        if (user.isPresent()) {
            return Optional.of(user.get().getUniqueId());
        } else {
            return Optional.empty();
        }
    }

    @Override
    public void setCreator(int x, int y, int z, @Nullable UUID uuid) {
        BlockPos pos = new BlockPos(x, y, z);
        IMixinChunk spongeChunk = (IMixinChunk) getChunkFromBlockCoords(pos);
        spongeChunk.setBlockCreator(pos, uuid);
    }

    @Override
    public void setNotifier(int x, int y, int z, @Nullable UUID uuid) {
        BlockPos pos = new BlockPos(x, y, z);
        IMixinChunk spongeChunk = (IMixinChunk) getChunkFromBlockCoords(pos);
        spongeChunk.setBlockNotifier(pos, uuid);
    }


    @Nullable
    @Override
    public EntityPlayer getClosestPlayerToEntityWhoAffectsSpawning(net.minecraft.entity.Entity entity, double distance) {
        return this.getClosestPlayerWhoAffectsSpawning(entity.posX, entity.posY, entity.posZ, distance);
    }

    @Nullable
    @Override
    public EntityPlayer getClosestPlayerWhoAffectsSpawning(double x, double y, double z, double distance) {
        double bestDistance = -1.0D;
        EntityPlayer result = null;

        for (Object entity : this.playerEntities) {
            EntityPlayer player = (EntityPlayer) entity;
            if (player == null || player.isDead || !((IMixinEntityPlayer) player).affectsSpawning()) {
                continue;
            }

            double playerDistance = player.getDistanceSq(x, y, z);

            if ((distance < 0.0D || playerDistance < distance * distance) && (bestDistance == -1.0D || playerDistance < bestDistance)) {
                bestDistance = playerDistance;
                result = player;
            }
        }

        return result;
    }

    @Redirect(method = "isAnyPlayerWithinRangeAt", at = @At(value = "INVOKE", target = "Lcom/google/common/base/Predicate;apply(Ljava/lang/Object;)Z", remap = false))
    public boolean onIsAnyPlayerWithinRangePredicate(com.google.common.base.Predicate<EntityPlayer> predicate, Object object) {
        EntityPlayer player = (EntityPlayer) object;
        return !(player.isDead || !((IMixinEntityPlayer) player).affectsSpawning()) && predicate.apply(player);
    }

    // For invisibility
    @Redirect(method = CHECK_NO_ENTITY_COLLISION, at = @At(value = "INVOKE", target = GET_ENTITIES_WITHIN_AABB))
    public List<net.minecraft.entity.Entity> filterInvisibile(net.minecraft.world.World world, net.minecraft.entity.Entity entityIn,
            AxisAlignedBB axisAlignedBB) {
        List<net.minecraft.entity.Entity> entities = world.getEntitiesWithinAABBExcludingEntity(entityIn, axisAlignedBB);
        Iterator<net.minecraft.entity.Entity> iterator = entities.iterator();
        while (iterator.hasNext()) {
            net.minecraft.entity.Entity entity = iterator.next();
            if (((IMixinEntity) entity).isVanished() && ((IMixinEntity) entity).ignoresCollision()) {
                iterator.remove();
            }
        }
        return entities;
    }

    @Redirect(method = "getClosestPlayer", at = @At(value = "INVOKE", target = "Lcom/google/common/base/Predicate;apply(Ljava/lang/Object;)Z", remap = false))
    private boolean onGetClosestPlayerCheck(com.google.common.base.Predicate<net.minecraft.entity.Entity> predicate, Object entityPlayer) {
        EntityPlayer player = (EntityPlayer) entityPlayer;
        IMixinEntity mixinEntity = (IMixinEntity) player;
        return predicate.apply(player) && !mixinEntity.isVanished();
    }

    /**
     * @author gabizou - February 7th, 2016
     *
     * This will short circuit all other patches such that we control the
     * entities being loaded by chunkloading and can throw our bulk entity
     * event. This will bypass Forge's hook for individual entity events,
     * but the SpongeModEventManager will still successfully throw the
     * appropriate event and cancel the entities otherwise contained.
     *
     * @param entities The entities being loaded
     * @param callbackInfo The callback info
     */
    @Final
    @Inject(method = "loadEntities", at = @At("HEAD"), cancellable = true)
    private void spongeLoadEntities(Collection<net.minecraft.entity.Entity> entities, CallbackInfo callbackInfo) {
        if (entities.isEmpty()) {
            // just return, no entities to load!
            callbackInfo.cancel();
            return;
        }
        List<Entity> entityList = new ArrayList<>();
        ImmutableList.Builder<EntitySnapshot> snapshotBuilder = ImmutableList.builder();
        for (net.minecraft.entity.Entity entity : entities) {
            entityList.add((Entity) entity);
            snapshotBuilder.add(((Entity) entity).createSnapshot());
        }
        SpawnCause cause = SpawnCause.builder().type(InternalSpawnTypes.CHUNK_LOAD).build();
        List<NamedCause> causes = new ArrayList<>();
        causes.add(NamedCause.source(cause));
        causes.add(NamedCause.of("World", this));
        SpawnEntityEvent.ChunkLoad chunkLoad = SpongeEventFactory.createSpawnEntityEventChunkLoad(Cause.of(causes), entityList,
            snapshotBuilder.build(), this);
        SpongeImpl.postEvent(chunkLoad);
        if (!chunkLoad.isCancelled() && chunkLoad.getEntities().size() > 0) {
            for (Entity successful : chunkLoad.getEntities()) {
                this.loadedEntityList.add((net.minecraft.entity.Entity) successful);
                this.onEntityAdded((net.minecraft.entity.Entity) successful);
            }
        }
        callbackInfo.cancel();
    }

    @Inject(method = "playSoundAtEntity", at = @At("HEAD"), cancellable = true)
    private void spongePlaySoundAtEntity(net.minecraft.entity.Entity entity, String name, float volume, float pitch, CallbackInfo callbackInfo) {
        if (((IMixinEntity) entity).isVanished()) {
            callbackInfo.cancel();
        }
    }

    @Override
    public void sendBlockChange(int x, int y, int z, BlockState state) {
        checkNotNull(state, "state");
        S23PacketBlockChange packet = new S23PacketBlockChange();
        packet.blockPosition = new BlockPos(x, y, z);
        packet.blockState = (IBlockState) state;

        for (EntityPlayer player : this.playerEntities) {
            if (player instanceof EntityPlayerMP) {
                ((EntityPlayerMP) player).playerNetServerHandler.sendPacket(packet);
            }
        }
    }

    @Override
    public void resetBlockChange(int x, int y, int z) {
        S23PacketBlockChange packet = new S23PacketBlockChange((net.minecraft.world.World) (Object) this, new BlockPos(x, y, z));

        for (EntityPlayer player : this.playerEntities) {
            if (player instanceof EntityPlayerMP) {
                ((EntityPlayerMP) player).playerNetServerHandler.sendPacket(packet);
            }
        }
    }

    /**
     * @author amaranth - April 25th, 2016
     * @reason Avoid 25 chunk map lookups per entity per tick by using neighbor pointers
     *
     * @param xStart X block start coordinate
     * @param yStart Y block start coordinate
     * @param zStart Z block start coordinate
     * @param xEnd X block end coordinate
     * @param yEnd Y block end coordinate
     * @param zEnd Z block end coordinate
     * @param allowEmpty Whether empty chunks should be accepted
     * @return If the chunks for the area are loaded
     */
    @Inject(method = "isAreaLoaded(IIIIIIZ)Z", at = @At("HEAD"), cancellable = true)
    public void onIsAreaLoaded(int xStart, int yStart, int zStart, int xEnd, int yEnd, int zEnd, boolean allowEmpty, CallbackInfoReturnable<Boolean> cir) {
        if (!this.isRemote) {
            if (yEnd < 0 || yStart > 255) {
                cir.setReturnValue(false);
                return;
            }

            xStart = xStart >> 4;
            zStart = zStart >> 4;
            xEnd = xEnd >> 4;
            zEnd = zEnd >> 4;

            Chunk base = (Chunk) ((IMixinChunkProviderServer) this.getChunkProvider()).getChunkIfLoaded(xStart, zStart);
            if (base == null) {
                cir.setReturnValue(false);
                return;
            }

            Optional<Chunk> currentColumn = Optional.of(base);
            for (int i = xStart; i <= xEnd; i++) {
                if (!currentColumn.isPresent()) {
                    cir.setReturnValue(false);
                    return;
                }

                Chunk column = currentColumn.get();

                Optional<Chunk> currentRow = column.getNeighbor(Direction.SOUTH);
                for (int j = zStart; j <= zEnd; j++) {
                    if (!currentRow.isPresent()) {
                        cir.setReturnValue(false);
                        return;
                    }

                    Chunk row = currentRow.get();

                    if (!allowEmpty && ((net.minecraft.world.chunk.Chunk) row).isEmpty()) {
                        cir.setReturnValue(false);
                        return;
                    }

                    currentRow = row.getNeighbor(Direction.SOUTH);
                }

                currentColumn = column.getNeighbor(Direction.EAST);
            }

            cir.setReturnValue(true);
        }
    }

    @Override
    public boolean isProcessingExplosion() {
        return this.processingExplosion;
    }

    @Override
    public SpongeConfig<?> getActiveConfig() {
        return this.activeConfig;
    }

    @Override
    public void setActiveConfig(SpongeConfig<?> config) {
        this.activeConfig = config;
    }

    @Override
    public PortalAgent getPortalAgent() {
        return null;
    }

    @Redirect(method = "addTileEntity", at = @At(value = "INVOKE", target = "Ljava/util/List;add(Ljava/lang/Object;)Z", ordinal = 1, remap = false))
    public boolean onAddTileEntity(List<net.minecraft.tileentity.TileEntity> list, Object tile) {
        if (!this.isRemote && !canTileUpdate((net.minecraft.tileentity.TileEntity) tile)) {
            return false;
        }

        return list.add((net.minecraft.tileentity.TileEntity) tile);
    }

    @Redirect(method = "addTileEntities", at = @At(value = "INVOKE", target = "Ljava/util/List;add(Ljava/lang/Object;)Z", ordinal = 1, remap = false))
    public boolean onAddTileEntities(List<net.minecraft.tileentity.TileEntity> list, Object tile) {
        if (!this.isRemote && !canTileUpdate((net.minecraft.tileentity.TileEntity) tile)) {
            return false;
        }

        return list.add((net.minecraft.tileentity.TileEntity) tile);
    }

    private boolean canTileUpdate(net.minecraft.tileentity.TileEntity tile) {
        TileEntity spongeTile = (TileEntity) tile;
        if (spongeTile.getType() != null && !((SpongeTileEntityType) spongeTile.getType()).canTick()) {
            return false;
        }

        return true;
    }
    /**************************** TRACKER AND TIMINGS ****************************************/

    /**
     * @author bloodmc
     * @reason Rewritten to support capturing blocks
     */
    @Overwrite
    public boolean setBlockState(BlockPos pos, IBlockState newState, int flags) {
        if (!this.isValid(pos)) {
            return false;
        } else if (!this.isRemote && this.worldInfo.getTerrainType() == WorldType.DEBUG_WORLD) {
            return false;
        } else {
            net.minecraft.world.chunk.Chunk chunk = this.getChunkFromBlockCoords(pos);
            IBlockState currentState = chunk.getBlockState(pos);
            if (currentState == newState) {
                return false;
            }

            Block originalBlock = currentState.getBlock();
            Block newBlock = newState.getBlock();
            BlockSnapshot originalBlockSnapshot = null;

            // Don't capture if we are restoring blocks
            final CauseTracker causeTracker = this.getCauseTracker();
            if (!this.isRemote && causeTracker.isCapturingBlocks()) {
                originalBlockSnapshot = null;
                originalBlockSnapshot = createSpongeBlockSnapshot(currentState, currentState.getBlock().getActualState(currentState,
                        (IBlockAccess) this, pos), pos, flags);

                if (causeTracker.isCaptureBlockDecay()) {
                    // Only capture final state of decay, ignore the rest
                    if (newBlock == Blocks.air) {
                        ((SpongeBlockSnapshot) originalBlockSnapshot).captureType = CaptureType.DECAY;
                        causeTracker.getCapturedSpongeBlockSnapshots().add(originalBlockSnapshot);
                    }
                } else if (newBlock == Blocks.air) {
                    ((SpongeBlockSnapshot) originalBlockSnapshot).captureType = CaptureType.BREAK;
                    causeTracker.getCapturedSpongeBlockSnapshots().add(originalBlockSnapshot);
                } else if (newBlock != originalBlock && !forceModify(originalBlock, newBlock)) {
                    ((SpongeBlockSnapshot) originalBlockSnapshot).captureType = CaptureType.PLACE;
                    causeTracker.getCapturedSpongeBlockSnapshots().add(originalBlockSnapshot);
                } else {
                    ((SpongeBlockSnapshot) originalBlockSnapshot).captureType = CaptureType.MODIFY;
                    causeTracker.getCapturedSpongeBlockSnapshots().add(originalBlockSnapshot);
                }
            }

            int oldLight = currentState.getBlock().getLightValue();

            // We pass the originalBlockSnapshot for generating block spawn causes if items drop
            IBlockState iblockstate1 = ((IMixinChunk) chunk).setBlockState(pos, newState, currentState, originalBlockSnapshot);

            if (iblockstate1 == null) {
                if (originalBlockSnapshot != null) {
                    causeTracker.getCapturedSpongeBlockSnapshots().remove(originalBlockSnapshot);
                }
                return false;
            } else {
                Block block1 = iblockstate1.getBlock();

                if (newBlock.getLightOpacity() != block1.getLightOpacity() || newBlock.getLightValue() != oldLight) {
                    this.theProfiler.startSection("checkLight");
                    this.checkLight(pos);
                    this.theProfiler.endSection();
                }

                // Don't notify clients or update physics while capturing blockstates
                if (originalBlockSnapshot == null) {
                    // Modularize client and physic updates
                    markAndNotifyNeighbors(pos, chunk, iblockstate1, newState, flags);
                }

                return true;
            }
        }
    }

    private boolean forceModify(Block originalBlock, Block newBlock) {
        if (originalBlock instanceof BlockRedstoneRepeater && newBlock instanceof BlockRedstoneRepeater) {
            return true;
        }
        if (originalBlock instanceof BlockRedstoneTorch && newBlock instanceof BlockRedstoneTorch) {
            return true;
        }

        return false;
    }

    @Override
    public void addEntityRotationUpdate(net.minecraft.entity.Entity entity, Vector3d rotation) {
        this.rotationUpdates.put(entity, rotation);
    }

    protected void updateRotation(net.minecraft.entity.Entity entityIn) {
        Vector3d rotationUpdate = this.rotationUpdates.get(entityIn);
        if (rotationUpdate != null) {
            entityIn.rotationPitch = (float) rotationUpdate.getX();
            entityIn.rotationYaw = (float) rotationUpdate.getY();
        }
        this.rotationUpdates.remove(entityIn);
    }


    @Override
    public void markAndNotifyNeighbors(BlockPos pos, @Nullable net.minecraft.world.chunk.Chunk chunk, IBlockState old, IBlockState new_, int flags) {
        if ((flags & 2) != 0 && (!this.isRemote || (flags & 4) == 0) && (chunk == null || chunk.isPopulated())) {
            this.markBlockForUpdate(pos);
        }

        if (!this.isRemote && (flags & 1) != 0) {
            this.notifyNeighborsRespectDebug(pos, old.getBlock());

            if (new_.getBlock().hasComparatorInputOverride()) {
                this.updateComparatorOutputLevel(pos, new_.getBlock());
            }
        }
    }

    @Redirect(method = "updateEntityWithOptionalForce", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;onUpdate()V"))
    public void onCallEntityUpdate(net.minecraft.entity.Entity entity) {
        final CauseTracker causeTracker = this.getCauseTracker();
        if (this.isRemote || causeTracker.hasTickingEntity() || StaticMixinHelper.packetPlayer != null) {
            entity.onUpdate();
            return;
        }

        boolean captureBlocks = this.causeTracker.isCapturingBlocks();
        this.causeTracker.setCaptureBlocks(true);
        causeTracker.preTrackEntity((org.spongepowered.api.entity.Entity) entity);
        entity.onUpdate();
        updateRotation(entity);
        SpongeCommonEventFactory.handleEntityMovement(entity);
        causeTracker.postTrackEntity();
        this.causeTracker.setCaptureBlocks(captureBlocks);
    }

    @Redirect(method = "updateEntityWithOptionalForce", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;updateRidden()V"))
    public void onUpdateRidden(net.minecraft.entity.Entity entity) {
        final CauseTracker causeTracker = this.getCauseTracker();
        if (this.isRemote || causeTracker.hasTickingEntity() || StaticMixinHelper.packetPlayer != null) {
            entity.updateRidden();
            return;
        }

        boolean captureBlocks = this.causeTracker.isCapturingBlocks();
        this.causeTracker.setCaptureBlocks(true);
        causeTracker.preTrackEntity((org.spongepowered.api.entity.Entity) entity);
        entity.updateRidden();
        causeTracker.postTrackEntity();
        this.causeTracker.setCaptureBlocks(captureBlocks);
    }

    @Inject(method = "onEntityRemoved", at = @At(value = "HEAD"))
    public void onEntityRemoval(net.minecraft.entity.Entity entityIn, CallbackInfo ci) {
        if (!this.isRemote && (!(entityIn instanceof EntityLivingBase) || entityIn instanceof EntityArmorStand)) {
            getCauseTracker().handleNonLivingEntityDestruct(entityIn);
        }
    }

    @Inject(method = "spawnEntityInWorld", at = @At("HEAD"), cancellable = true)
    public void onSpawnEntityInWorld(net.minecraft.entity.Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (!this.isRemote) {
            if (!canEntitySpawn(entity)) {
                cir.setReturnValue(true);
            } else if (!this.causeTracker.isIgnoringSpawnEvents()) {
                cir.setReturnValue(spawnEntity((Entity) entity, SpongeCommonEventFactory.getEntitySpawnCause(entity)));
            } else {
                // track entity and proceed with spawn bypassing events
                this.causeTracker.trackEntityOwner((Entity) entity);
            }
        }
    }

    @Override
    public boolean spawnEntity(Entity entity, Cause cause) {
        if (this.canEntitySpawn((net.minecraft.entity.Entity) entity)) {
            return this.getCauseTracker().processSpawnEntity(entity, cause);
        }
        return false;
    }

    private boolean canEntitySpawn(net.minecraft.entity.Entity entity) {
        // do not drop any items while restoring blocksnapshots. Prevents dupes
        if (!this.isRemote && (entity == null || (entity instanceof net.minecraft.entity.item.EntityItem && this.causeTracker.isRestoringBlocks()))) {
            return false;
        } else if (!(entity instanceof EntityPlayer) && ((this.causeTracker.isCapturingSpawnedEntities()))) {
            if (entity instanceof EntityItem) {
                this.causeTracker.getCapturedSpawnedEntityItems().add((Item) entity);
            } else {
                this.causeTracker.getCapturedSpawnedEntities().add((Entity) entity);
            }
            return false;
        }

        return true;
    }

    /**
     * @author bloodmc - November 15th, 2015
     *
     * @reason Rewritten to pass the source block position.
     */
    @Overwrite
    public void notifyNeighborsOfStateChange(BlockPos pos, Block blockType) {
        if (this.isRemote || !isValid(pos)) {
            return;
        }

        final CauseTracker causeTracker = this.getCauseTracker();
        if (causeTracker.isIgnoringCaptures()) {
            for (EnumFacing facing : EnumFacing.values()) {
                causeTracker.notifyBlockOfStateChange(pos.offset(facing), blockType, pos);
            }
            return;
        }

        NotifyNeighborBlockEvent
                event =
                SpongeCommonEventFactory.callNotifyNeighborEvent(this, pos, java.util.EnumSet.allOf(EnumFacing.class));
        if (event.isCancelled()) {
            return;
        }

        for (EnumFacing facing : EnumFacing.values()) {
            if (event.getNeighbors().keySet().contains(DirectionFacingProvider.getInstance().getKey(facing).get())) {
                causeTracker.notifyBlockOfStateChange(pos.offset(facing), blockType, pos);
            }
        }
    }

    /**
     * @author bloodmc - November 15th, 2015
     *
     * @reason Rewritten to pass the source block position.
     */
    @SuppressWarnings("rawtypes")
    @Overwrite
    public void notifyNeighborsOfStateExcept(BlockPos pos, Block blockType, EnumFacing skipSide) {
        if (this.isRemote || !isValid(pos)) {
            return;
        }

        EnumSet directions = EnumSet.allOf(EnumFacing.class);
        directions.remove(skipSide);

        final CauseTracker causeTracker = this.getCauseTracker();
        if (causeTracker.isIgnoringCaptures()) {
            for (Object obj : directions) {
                EnumFacing facing = (EnumFacing) obj;
                causeTracker.notifyBlockOfStateChange(pos.offset(facing), blockType, pos);
            }
            return;
        }

        NotifyNeighborBlockEvent event = SpongeCommonEventFactory.callNotifyNeighborEvent(this, pos, directions);
        if (event.isCancelled()) {
            return;
        }

        for (EnumFacing facing : EnumFacing.values()) {
            if (event.getNeighbors().keySet().contains(DirectionFacingProvider.getInstance().getKey(facing).get())) {
                causeTracker.notifyBlockOfStateChange(pos.offset(facing), blockType, pos);
            }
        }
    }

    /**
     * @author bloodmc - November 15th, 2015
     *
     * @reason Redirect's vanilla method to ours that includes source block
     * position.
     */
    @Overwrite
    public void notifyBlockOfStateChange(BlockPos notifyPos, final Block blockIn) {
        if (!this.isRemote) {
            this.getCauseTracker().notifyBlockOfStateChange(notifyPos, blockIn, null);
        }
    }

    @Override
    public CauseTracker getCauseTracker() {
        return this.causeTracker;
    }


    @Override
    public void setBlock(int x, int y, int z, BlockState block, boolean notifyNeighbors) {
        checkBlockBounds(x, y, z);
        final CauseTracker causeTracker = this.getCauseTracker();
        if (causeTracker.isCapturingTerrainGen()) {
            setBlockState(new BlockPos(x, y, z), (IBlockState) block, notifyNeighbors ? 3 : 2);
            return;
        }

        boolean captureBlocks = causeTracker.isCapturingBlocks();
        causeTracker.setCaptureBlocks(true);
        causeTracker.addCause(Cause.of(NamedCause.source(this)));
        setBlockState(new BlockPos(x, y, z), (IBlockState) block, notifyNeighbors ? 3 : 2);
        causeTracker.handleBlockCaptures();
        causeTracker.removeCurrentCause();
        causeTracker.setCaptureBlocks(captureBlocks);
    }

    @Override
    public void setBlock(int x, int y, int z, BlockState blockState, boolean notifyNeighbors, Cause cause) {
        checkArgument(cause != null, "Cause cannot be null!");
        checkArgument(cause.root() instanceof PluginContainer, "PluginContainer must be at the ROOT of a cause!");
        checkBlockBounds(x, y, z);
        final CauseTracker causeTracker = this.getCauseTracker();
        if (causeTracker.isCapturingTerrainGen()) {
            setBlockState(new BlockPos(x, y, z), (IBlockState) blockState, notifyNeighbors ? 3 : 2);
            return;
        }

        boolean captureBlocks = causeTracker.isCapturingBlocks();
        causeTracker.setCaptureBlocks(true);
        causeTracker.addCause(cause);
        setBlockState(new BlockPos(x, y, z), (IBlockState) blockState, notifyNeighbors ? 3 : 2);
        causeTracker.handleBlockCaptures();
        causeTracker.removeCurrentCause();
        causeTracker.setCaptureBlocks(captureBlocks);
    }

    private net.minecraft.world.World asMinecraftWorld() {
        return (net.minecraft.world.World) (Object) this;
    }

    /**
     * @author blood - July 1st, 2016
     *
     * @reason Added chunk and block tick optimizations.
     */
    @Overwrite
    protected void updateBlocks() {
        this.setActivePlayerChunksAndCheckLight();
    }

    @Override
    public WorldTimingsHandler getTimingsHandler() {
        return this.timings;
    }
}
