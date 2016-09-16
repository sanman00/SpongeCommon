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
package org.spongepowered.common.mixin.core.entity;

import static com.google.common.base.Preconditions.checkNotNull;

import co.aikar.timings.SpongeTimings;
import co.aikar.timings.Timing;
import com.flowpowered.math.vector.Vector3d;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.DataWatcher;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.EntityTracker;
import net.minecraft.entity.EntityTrackerEntry;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S07PacketRespawn;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S13PacketDestroyEntities;
import net.minecraft.network.play.server.S1DPacketEntityEffect;
import net.minecraft.network.play.server.S38PacketPlayerListItem;
import net.minecraft.potion.PotionEffect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.world.WorldServer;
import net.minecraft.world.gen.ChunkProviderServer;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.data.DataContainer;
import org.spongepowered.api.data.DataTransactionResult;
import org.spongepowered.api.data.DataView;
import org.spongepowered.api.data.MemoryDataContainer;
import org.spongepowered.api.data.Queries;
import org.spongepowered.api.data.key.Keys;
import org.spongepowered.api.data.manipulator.DataManipulator;
import org.spongepowered.api.data.manipulator.mutable.entity.IgniteableData;
import org.spongepowered.api.data.manipulator.mutable.entity.VehicleData;
import org.spongepowered.api.data.persistence.InvalidDataException;
import org.spongepowered.api.entity.Entity;
import org.spongepowered.api.entity.EntitySnapshot;
import org.spongepowered.api.entity.EntityType;
import org.spongepowered.api.entity.EntityTypes;
import org.spongepowered.api.entity.Transform;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.entity.living.player.User;
import org.spongepowered.api.event.SpongeEventFactory;
import org.spongepowered.api.event.cause.Cause;
import org.spongepowered.api.event.cause.NamedCause;
import org.spongepowered.api.event.cause.entity.spawn.EntitySpawnCause;
import org.spongepowered.api.event.cause.entity.spawn.SpawnCause;
import org.spongepowered.api.event.cause.entity.spawn.SpawnTypes;
import org.spongepowered.api.event.entity.ConstructEntityEvent;
import org.spongepowered.api.event.entity.DisplaceEntityEvent;
import org.spongepowered.api.event.item.inventory.DropItemEvent;
import org.spongepowered.api.item.inventory.ItemStackSnapshot;
import org.spongepowered.api.profile.GameProfile;
import org.spongepowered.api.service.user.UserStorageService;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.translation.Translation;
import org.spongepowered.api.util.Direction;
import org.spongepowered.api.util.RelativePositions;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.TeleportHelper;
import org.spongepowered.api.world.World;
import org.spongepowered.api.world.storage.WorldProperties;
import org.spongepowered.asm.lib.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.common.SpongeImpl;
import org.spongepowered.common.SpongeImplHooks;
import org.spongepowered.common.data.persistence.NbtTranslator;
import org.spongepowered.common.data.util.DataQueries;
import org.spongepowered.common.data.util.DataUtil;
import org.spongepowered.common.data.util.NbtDataUtil;
import org.spongepowered.common.data.value.immutable.ImmutableSpongeValue;
import org.spongepowered.common.entity.SpongeEntitySnapshotBuilder;
import org.spongepowered.common.entity.SpongeEntityType;
import org.spongepowered.common.event.CauseTracker;
import org.spongepowered.common.event.DamageEventHandler;
import org.spongepowered.common.event.MinecraftBlockDamageSource;
import org.spongepowered.common.event.SpongeCommonEventFactory;
import org.spongepowered.common.interfaces.block.IMixinBlock;
import org.spongepowered.common.interfaces.data.IMixinCustomDataHolder;
import org.spongepowered.common.interfaces.entity.IMixinEntity;
import org.spongepowered.common.interfaces.entity.IMixinGriefer;
import org.spongepowered.common.interfaces.entity.player.IMixinEntityPlayerMP;
import org.spongepowered.common.interfaces.world.IMixinWorld;
import org.spongepowered.common.profile.SpongeProfileManager;
import org.spongepowered.common.registry.type.world.DimensionRegistryModule;
import org.spongepowered.common.registry.type.world.WorldPropertyRegistryModule;
import org.spongepowered.common.text.SpongeTexts;
import org.spongepowered.common.util.SpongeHooks;
import org.spongepowered.common.util.SpongeUsernameCache;
import org.spongepowered.common.util.StaticMixinHelper;
import org.spongepowered.common.world.DimensionManager;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

@Mixin(net.minecraft.entity.Entity.class)
public abstract class MixinEntity implements Entity, IMixinEntity {

    private static final String LAVA_DAMAGESOURCE_FIELD = "Lnet/minecraft/util/DamageSource;lava:Lnet/minecraft/util/DamageSource;";
    private static final String ATTACK_ENTITY_FROM_METHOD = "Lnet/minecraft/entity/Entity;attackEntityFrom(Lnet/minecraft/util/DamageSource;F)Z";
    private static final String FIRE_DAMAGESOURCE_FIELD = "Lnet/minecraft/util/DamageSource;inFire:Lnet/minecraft/util/DamageSource;";
    private static final String WORLD_SPAWN_PARTICLE = "Lnet/minecraft/world/World;spawnParticle(Lnet/minecraft/util/EnumParticleTypes;DDDDDD[I)V";
    // @formatter:off
    private EntityType entityType = SpongeImpl.getRegistry().getTranslated(this.getClass(), EntityType.class);
    private boolean teleporting;
    private net.minecraft.entity.Entity teleportVehicle;
    private float origWidth;
    private float origHeight;
    @Nullable private DamageSource originalLava;
    protected boolean isConstructing = true;
    @Nullable private Text displayName;
    protected DamageSource lastDamageSource;
    protected Cause destructCause;
    private BlockState currentCollidingBlock;
    private BlockPos lastCollidedBlockPos;
    private final boolean isVanilla = getClass().getName().startsWith("net.minecraft.");
    private net.minecraft.entity.Entity mcEntity = (net.minecraft.entity.Entity)(Object) this;
    public boolean captureItemDrops = false;
    public java.util.ArrayList<EntityItem> capturedItemDrops = new java.util.ArrayList<EntityItem>();
    private boolean spawnedFromBlockBreak = false;
    private SpawnCause spawnCause = null;
    private SpongeProfileManager spongeProfileManager;
    private UserStorageService userStorageService;
    private Timing timing;
    @Nullable private UUID creator;
    @Nullable private UUID notifier;

    @Shadow private UUID entityUniqueID;
    @Shadow public net.minecraft.world.World worldObj;
    @Shadow public double posX;
    @Shadow public double posY;
    @Shadow public double posZ;
    @Shadow public double motionX;
    @Shadow public double motionY;
    @Shadow public double motionZ;
    @Shadow public boolean velocityChanged;
    @Shadow public double prevPosX;
    @Shadow public double prevPosY;
    @Shadow public double prevPosZ;
    @Shadow public float rotationYaw;
    @Shadow public float rotationPitch;
    @Shadow public float width;
    @Shadow public float height;
    @Shadow public float fallDistance;
    @Shadow public boolean isDead;
    @Shadow public boolean onGround;
    @Shadow public boolean inWater;
    @Shadow protected boolean isImmuneToFire;
    @Shadow public int hurtResistantTime;
    @Shadow public int fireResistance;
    @Shadow public int fire;
    @Shadow public int dimension;
    @Shadow public net.minecraft.entity.Entity riddenByEntity;
    @Shadow public net.minecraft.entity.Entity ridingEntity;
    @Shadow protected DataWatcher dataWatcher;
    @Shadow protected Random rand;
    @Shadow public abstract void setPosition(double x, double y, double z);
    @Shadow public abstract void mountEntity(net.minecraft.entity.Entity entityIn);
    @Shadow public abstract void setDead();
    @Shadow public abstract void setFlag(int flag, boolean data);
    @Shadow public abstract boolean getFlag(int flag);
    @Shadow public abstract int getAir();
    @Shadow public abstract void setAir(int air);
    @Shadow public abstract float getEyeHeight();
    @Shadow public abstract String getCustomNameTag();
    @Shadow public abstract void setCustomNameTag(String name);
    @Shadow public abstract UUID getUniqueID();
    @Shadow public abstract AxisAlignedBB getEntityBoundingBox();
    @Shadow protected abstract boolean getAlwaysRenderNameTag();
    @Shadow protected abstract void setAlwaysRenderNameTag(boolean visible);
    @Shadow public abstract void setFire(int seconds);
    @Shadow public abstract void writeToNBT(NBTTagCompound compound);
    @Shadow public abstract boolean attackEntityFrom(DamageSource source, float amount);
    @Shadow protected abstract void shadow$setRotation(float yaw, float pitch);
    @Shadow public abstract void setSize(float width, float height);
    @Shadow public abstract boolean isSilent();
    @Shadow public abstract int getEntityId();
    @Shadow public abstract void setEating(boolean eating);
    @Shadow public abstract boolean isSprinting();
    @Shadow public abstract boolean isInWater();
    @Shadow public abstract void applyEnchantments(EntityLivingBase entityLivingBaseIn, net.minecraft.entity.Entity entityIn);
    @Shadow public abstract void addToPlayerScore(net.minecraft.entity.Entity entityIn, int amount);
    @Shadow public abstract void setLocationAndAngles(double x, double y, double z, float yaw, float pitch);


    // @formatter:on

    @Inject(method = "<init>", at = @At("RETURN"))
    public void onConstruction(net.minecraft.world.World worldIn, CallbackInfo ci) {
        if (this.entityType instanceof SpongeEntityType) {
            SpongeEntityType spongeEntityType = (SpongeEntityType) this.entityType;
            if (spongeEntityType.getEnumCreatureType() == null) {
                for (EnumCreatureType type : EnumCreatureType.values()) {
                    if (SpongeImplHooks.isCreatureOfType(this.mcEntity, type)) {
                        spongeEntityType.setEnumCreatureType(type);
                        break;
                    }
                }
            }
        }
        if (worldIn != null && !worldIn.isRemote) {
            this.spongeProfileManager = ((SpongeProfileManager) Sponge.getServer().getGameProfileManager());
            this.userStorageService = SpongeImpl.getGame().getServiceManager().provide(UserStorageService.class).get();
        }
    }

    @Override
    public boolean isInConstructPhase() {
        return this.isConstructing;
    }

    @Override
    public void firePostConstructEvents() {
        this.isConstructing = false;
    }

    @Inject(method = "setSize", at = @At("RETURN"))
    public void onSetSize(float width, float height, CallbackInfo ci) {
        if (this.origWidth == 0 || this.origHeight == 0) {
            this.origWidth = this.width;
            this.origHeight = this.height;
        }
    }

    @Inject(method = "moveEntity(DDD)V", at = @At("HEAD"), cancellable = true)
    public void onMoveEntity(double x, double y, double z, CallbackInfo ci) {
        if (!this.worldObj.isRemote && !SpongeHooks.checkEntitySpeed(((net.minecraft.entity.Entity) (Object) this), x, y, z)) {
            ci.cancel();
        }
    }

    @Inject(method = "setOnFireFromLava()V", at = @At(value = "FIELD", target = LAVA_DAMAGESOURCE_FIELD, opcode = Opcodes.GETSTATIC))
    public void preSetOnFire(CallbackInfo callbackInfo) {
        if (!this.worldObj.isRemote) {
            this.originalLava = DamageSource.lava;
            AxisAlignedBB bb = this.getEntityBoundingBox().expand(-0.10000000149011612D, -0.4000000059604645D, -0.10000000149011612D);
            Location<World> location = DamageEventHandler.findFirstMatchingBlock((net.minecraft.entity.Entity) (Object) this, bb, block ->
                block.getMaterial() == Material.lava);
            DamageSource.lava = new MinecraftBlockDamageSource("lava", location).setFireDamage();
        }
    }

    @Inject(method = "setOnFireFromLava()V", at = @At(value = "INVOKE_ASSIGN", target = ATTACK_ENTITY_FROM_METHOD))
    public void postSetOnFire(CallbackInfo callbackInfo) {
        if (!this.worldObj.isRemote) {
            if (this.originalLava == null) {
                SpongeImpl.getLogger().error("Original lava is null!");
                Thread.dumpStack();
            }
            DamageSource.lava = this.originalLava;
        }
    }

    private DamageSource originalInFire;

    @Inject(method = "dealFireDamage", at = @At(value = "FIELD", target = FIRE_DAMAGESOURCE_FIELD, opcode = Opcodes.GETSTATIC))
    public void preFire(CallbackInfo callbackInfo) {
        // Sponge Start - Find the fire block!
        if (!this.worldObj.isRemote) {
            this.originalInFire = DamageSource.inFire;
            AxisAlignedBB bb = this.getEntityBoundingBox().contract(0.001D, 0.001D, 0.001D);
            Location<World> location = DamageEventHandler.findFirstMatchingBlock((net.minecraft.entity.Entity) (Object) this, bb, block ->
                block == Blocks.fire || block == Blocks.flowing_lava || block == Blocks.lava);
            DamageSource.inFire = new MinecraftBlockDamageSource("inFire", location).setFireDamage();
        }
    }

    @Inject(method = "dealFireDamage", at = @At(value = "INVOKE_ASSIGN", target = ATTACK_ENTITY_FROM_METHOD))
    public void postDealFireDamage(CallbackInfo callbackInfo) {
        if (!this.worldObj.isRemote) {
            if (this.originalInFire == null) {
                SpongeImpl.getLogger().error("Original fire is null!");
                Thread.dumpStack();
            }
            DamageSource.inFire = this.originalInFire;
        }
    }

    @Override
    public void supplyVanillaManipulators(List<DataManipulator<?, ?>> manipulators) {
        Optional<VehicleData> vehicleData = get(VehicleData.class);
        if (vehicleData.isPresent()) {
            manipulators.add(vehicleData.get());
        }
        if (this.fire > 0) {
            manipulators.add(get(IgniteableData.class).get());
        }

    }

    @Override
    public World getWorld() {
        return (World) this.worldObj;
    }

    @Override
    public EntitySnapshot createSnapshot() {
        return new SpongeEntitySnapshotBuilder().from(this).build();
    }

    @Override
    public Random getRandom() {
        return this.rand;
    }

    public Vector3d getPosition() {
        return new Vector3d(this.posX, this.posY, this.posZ);
    }

    @Override
    public Location<World> getLocation() {
        return new Location<>((World) this.worldObj, getPosition());
    }

    @Override
    public boolean setLocationSafely(Location<World> location) {
        TeleportHelper teleportHelper = SpongeImpl.getGame().getTeleportHelper();
        Optional<Location<World>> safeLocation = teleportHelper.getSafeLocation(location);
        if (!safeLocation.isPresent()) {
            return false;
        }

        return setLocation(safeLocation.get());
    }

    @Override
    public boolean setLocationAndRotation(Location<World> location, Vector3d rotation) {
        boolean result = setLocation(location);
        if (result) {
            setRotation(rotation);
            return true;
        }

        return false;
    }

    @Override
    public boolean setLocationAndRotationSafely(Location<World> location, Vector3d rotation) {
        TeleportHelper teleportHelper = SpongeImpl.getGame().getTeleportHelper();
        Optional<Location<World>> safeLocation = teleportHelper.getSafeLocation(location);
        if (!safeLocation.isPresent()) {
            return false;
        }

        boolean relocated = setLocation(safeLocation.get());
        setRotation(rotation);
        return relocated;
    }

    @Override
    public boolean setLocationAndRotationSafely(Location<World> location, Vector3d rotation, EnumSet<RelativePositions> relativePositions) {
        TeleportHelper teleportHelper = SpongeImpl.getGame().getTeleportHelper();
        Optional<Location<World>> safeLocation = teleportHelper.getSafeLocation(location);
        if (!safeLocation.isPresent()) {
            return false;
        }

        return setLocationAndRotation(safeLocation.get(), rotation, relativePositions);
    }

    @Override
    public boolean setLocation(Location<World> location) {
        checkNotNull(location, "The location was null!");
        if (isRemoved()) {
            return false;
        }

        DisplaceEntityEvent.Teleport event = SpongeCommonEventFactory.handleDisplaceEntityTeleportEvent(this.mcEntity, location);
        if (event.isCancelled()) {
            return false;
        } else {
            location = event.getToTransform().getLocation();
            this.mcEntity.rotationPitch = (float) event.getToTransform().getPitch();
            this.mcEntity.rotationYaw = (float) event.getToTransform().getYaw();
        }

        ChunkProviderServer chunkProviderServer = ((WorldServer) location.getExtent()).theChunkProviderServer;
        boolean chunkLoadOverride = chunkProviderServer.chunkLoadOverride;
        chunkProviderServer.chunkLoadOverride = true;
        // detach passengers
        net.minecraft.entity.Entity passenger = this.mcEntity.riddenByEntity;
        ArrayDeque<net.minecraft.entity.Entity> passengers = new ArrayDeque<>();
        while (passenger != null) {
            if (passenger instanceof EntityPlayerMP && !this.worldObj.isRemote) {
                ((EntityPlayerMP) passenger).mountEntity(null);
            }
            net.minecraft.entity.Entity nextPassenger = null;
            if (passenger.riddenByEntity != null) {
                nextPassenger = passenger.riddenByEntity;
                this.riddenByEntity.mountEntity(null);
            }
            passengers.add(passenger);
            passenger = nextPassenger;
        }

        net.minecraft.world.World nmsWorld = null;
        if (location.getExtent().getUniqueId() != ((World) this.worldObj).getUniqueId()) {
            nmsWorld = (net.minecraft.world.World) location.getExtent();
            if (this.mcEntity instanceof EntityPlayerMP) {
                // Close open containers
                if (((EntityPlayerMP) this.mcEntity).openContainer != ((EntityPlayerMP) this.mcEntity).inventoryContainer) {
                    ((EntityPlayerMP) this.mcEntity).closeContainer();
                }
            }
            teleportEntity(this.mcEntity, location, this.mcEntity.dimension, nmsWorld.provider.getDimensionId());
        } else {
            if (this.mcEntity instanceof EntityPlayerMP && ((EntityPlayerMP) this.mcEntity).playerNetServerHandler != null) {
                // make sure chunk is loaded
                chunkProviderServer.loadChunk(location.getChunkPosition().getX(), location.getChunkPosition().getZ());
                ((EntityPlayerMP) this.mcEntity).playerNetServerHandler
                    .setPlayerLocation(location.getPosition().getX(), location.getPosition().getY(), location.getPosition().getZ(),
                            this.mcEntity.rotationYaw, this.mcEntity.rotationPitch);
            } else {
                setPosition(location.getPosition().getX(), location.getPosition().getY(), location.getPosition().getZ());
            }
        }

        // re-attach passengers
        net.minecraft.entity.Entity lastPassenger = this.mcEntity;
        while (!passengers.isEmpty()) {
            net.minecraft.entity.Entity passengerEntity = passengers.remove();
            if (nmsWorld != null) {
                teleportEntity(passengerEntity, location, passengerEntity.dimension, nmsWorld.provider.getDimensionId());
            }

            if (passengerEntity instanceof EntityPlayerMP && !this.worldObj.isRemote) {
                // The actual mount is handled in our event as mounting must be set after client fully loads.
                ((IMixinEntity) passengerEntity).setIsTeleporting(true);
                ((IMixinEntity) passengerEntity).setTeleportVehicle(lastPassenger);
            } else {
                passengerEntity.mountEntity(lastPassenger);
            }
            lastPassenger = passengerEntity;
        }

        chunkProviderServer.chunkLoadOverride = chunkLoadOverride;
        return true;
    }

    // always use these methods internally when setting locations from a transform or location
    // to avoid firing a DisplaceEntityEvent.Teleport
    @Override
    public void setLocationAndAngles(Location<World> location) {
        if (this.mcEntity instanceof EntityPlayerMP) {
            ((EntityPlayerMP) this.mcEntity).playerNetServerHandler.setPlayerLocation(location.getX(), location.getY(), location.getZ(), this.rotationYaw, this.rotationPitch);
        } else {
            this.setPosition(location.getX(), location.getY(), location.getZ());
        }
        if (this.worldObj != location.getExtent()) {
            this.worldObj = (net.minecraft.world.World) location.getExtent();
        }
    }

    @Override
    public void setLocationAndAngles(Transform<World> transform) {
        Vector3d position = transform.getPosition();
        if (this.mcEntity instanceof EntityPlayerMP) {
            ((EntityPlayerMP) this.mcEntity).playerNetServerHandler.setPlayerLocation(position.getX(), position.getY(), position.getZ(), (float) transform.getYaw(), (float) transform.getPitch());
        } else {
            this.setLocationAndAngles(position.getX(), position.getY(), position.getZ(), (float) transform.getYaw(), (float) transform.getPitch());
        }
        if (this.worldObj != transform.getExtent()) {
            this.worldObj = (net.minecraft.world.World) transform.getExtent();
        }
    }

    @Inject(method = "onUpdate", at = @At("RETURN"))
    private void spongeOnUpdate(CallbackInfo callbackInfo) {
        if (this.pendingVisibilityUpdate && !this.worldObj.isRemote) {
            final EntityTracker entityTracker = ((WorldServer) this.worldObj).getEntityTracker();
            final EntityTrackerEntry lookup = entityTracker.trackedEntityHashTable.lookup(this.getEntityId());
            if (this.visibilityTicks % 4 == 0) {
                if (this.isVanished) {
                    for (EntityPlayerMP entityPlayerMP : lookup.trackingPlayers) {
                        entityPlayerMP.playerNetServerHandler.sendPacket(new S13PacketDestroyEntities(this.getEntityId()));
                        if (((Object) this) instanceof EntityPlayerMP) {
                            entityPlayerMP.playerNetServerHandler.sendPacket(
                                    new S38PacketPlayerListItem(S38PacketPlayerListItem.Action.REMOVE_PLAYER, (EntityPlayerMP) (Object) this));
                        }
                    }
                } else {
                    this.visibilityTicks = 1;
                    this.pendingVisibilityUpdate = false;
                    for (EntityPlayerMP entityPlayerMP : MinecraftServer.getServer().getConfigurationManager().getPlayerList()) {
                        if (((Object) this) == entityPlayerMP) {
                            continue;
                        }
                        if (((Object) this) instanceof EntityPlayerMP) {
                            Packet<?> packet = new S38PacketPlayerListItem(S38PacketPlayerListItem.Action.ADD_PLAYER, (EntityPlayerMP) (Object) this);
                            entityPlayerMP.playerNetServerHandler.sendPacket(packet);
                        }
                        Packet<?> newPacket = lookup.createSpawnPacket(); // creates the spawn packet for us
                        entityPlayerMP.playerNetServerHandler.sendPacket(newPacket);
                    }
                }
            }
            if (this.visibilityTicks > 0) {
                this.visibilityTicks--;
            } else {
                this.pendingVisibilityUpdate = false;
            }
        }
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public boolean setLocationAndRotation(Location<World> location, Vector3d rotation, EnumSet<RelativePositions> relativePositions) {
        boolean relocated = true;

        if (relativePositions.isEmpty()) {
            // This is just a normal teleport that happens to set both.
            relocated = setLocation(location);
            setRotation(rotation);
        } else {
            if (((Entity) this) instanceof EntityPlayerMP && ((EntityPlayerMP) (Entity) this).playerNetServerHandler != null) {
                // Players use different logic, as they support real relative movement.
                EnumSet relativeFlags = EnumSet.noneOf(S08PacketPlayerPosLook.EnumFlags.class);

                if (relativePositions.contains(RelativePositions.X)) {
                    relativeFlags.add(S08PacketPlayerPosLook.EnumFlags.X);
                }

                if (relativePositions.contains(RelativePositions.Y)) {
                    relativeFlags.add(S08PacketPlayerPosLook.EnumFlags.Y);
                }

                if (relativePositions.contains(RelativePositions.Z)) {
                    relativeFlags.add(S08PacketPlayerPosLook.EnumFlags.Z);
                }

                if (relativePositions.contains(RelativePositions.PITCH)) {
                    relativeFlags.add(S08PacketPlayerPosLook.EnumFlags.X_ROT);
                }

                if (relativePositions.contains(RelativePositions.YAW)) {
                    relativeFlags.add(S08PacketPlayerPosLook.EnumFlags.Y_ROT);
                }

                ((EntityPlayerMP) (Entity) this).playerNetServerHandler.setPlayerLocation(location.getPosition().getX(), location.getPosition()
                    .getY(), location.getPosition().getZ(), (float) rotation.getY(), (float) rotation.getX(), relativeFlags);
            } else {
                Location<World> resultantLocation = getLocation();
                Vector3d resultantRotation = getRotation();

                if (relativePositions.contains(RelativePositions.X)) {
                    resultantLocation = resultantLocation.add(location.getPosition().getX(), 0, 0);
                }

                if (relativePositions.contains(RelativePositions.Y)) {
                    resultantLocation = resultantLocation.add(0, location.getPosition().getY(), 0);
                }

                if (relativePositions.contains(RelativePositions.Z)) {
                    resultantLocation = resultantLocation.add(0, 0, location.getPosition().getZ());
                }

                if (relativePositions.contains(RelativePositions.PITCH)) {
                    resultantRotation = resultantRotation.add(rotation.getX(), 0, 0);
                }

                if (relativePositions.contains(RelativePositions.YAW)) {
                    resultantRotation = resultantRotation.add(0, rotation.getY(), 0);
                }

                // From here just a normal teleport is needed.
                relocated = setLocation(resultantLocation);
                setRotation(resultantRotation);
            }
        }

        return relocated;
    }

    @Override
    public Vector3d getScale() {
        return Vector3d.ONE;
    }

    @Override
    public void setScale(Vector3d scale) {
        // do nothing, Minecraft doesn't properly support this yet
    }

    @Override
    public Transform<World> getTransform() {
        return new Transform<>(getWorld(), getPosition(), getRotation(), getScale());
    }

    @Override
    public boolean setTransform(Transform<World> transform) {
        checkNotNull(transform, "The transform cannot be null!");
        boolean result = setLocation(transform.getLocation());
        if (result) {
            setRotation(transform.getRotation());
            setScale(transform.getScale());
            return true;
        }

        return false;
    }

    @Override
    public boolean transferToWorld(String worldName, Vector3d position) {
        checkNotNull(worldName, "World name was null!");
        checkNotNull(position, "Position was null!");
        Optional<WorldProperties> props = WorldPropertyRegistryModule.getInstance().getWorldProperties(worldName);
        if (props.isPresent()) {
            if (props.get().isEnabled()) {
                Optional<World> world = SpongeImpl.getGame().getServer().loadWorld(worldName);
                if (world.isPresent()) {
                    Location<World> location = new Location<>(world.get(), position);
                    return setLocationSafely(location);
                }
            }
        }
        return false;
    }

    @Override
    public boolean transferToWorld(UUID uuid, Vector3d position) {
        checkNotNull(uuid, "The world uuid cannot be null!");
        checkNotNull(position, "The position cannot be null!");
        return transferToWorld(DimensionRegistryModule.getInstance().getWorldFolder(uuid), position);
    }

    @Override
    public Vector3d getRotation() {
        return new Vector3d(this.rotationPitch, this.rotationYaw, 0);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public void setRotation(Vector3d rotation) {
        checkNotNull(rotation, "Rotation was null!");
        ((IMixinWorld) getWorld()).addEntityRotationUpdate((net.minecraft.entity.Entity) (Entity) this, rotation);
        if (((Entity) this) instanceof EntityPlayerMP && ((EntityPlayerMP) (Entity) this).playerNetServerHandler != null) {
            // Force an update, this also set the rotation in this entity
            ((EntityPlayerMP) (Entity) this).playerNetServerHandler.setPlayerLocation(getPosition().getX(), getPosition().getY(),
                getPosition().getZ(), (float) rotation.getY(), (float) rotation.getX(), (Set) EnumSet.noneOf(RelativePositions.class));
        } else {
            // Let the entity tracker do its job, this just updates the variables
            shadow$setRotation((float) rotation.getY(), (float) rotation.getX());
        }
    }

    @Override
    public boolean isOnGround() {
        return this.onGround;
    }

    @Override
    public boolean isRemoved() {
        return this.isDead;
    }

    @Override
    public boolean isLoaded() {
        // TODO - add flag for entities loaded/unloaded into world
        return !isRemoved();
    }

    @Override
    public void remove() {
        this.isDead = true;
    }

    @Override
    public boolean damage(double damage, org.spongepowered.api.event.cause.entity.damage.source.DamageSource damageSource, Cause cause) {
        if (!(damageSource instanceof DamageSource)) {
            SpongeImpl.getLogger().error("An illegal DamageSource was provided in the cause! The damage source must extend AbstractDamageSource!");
            return false;
        }
        // todo hook the damage entity event with the cause.
        return attackEntityFrom((DamageSource) damageSource, (float) damage);
    }

    @Override
    public boolean isTeleporting() {
        return this.teleporting;
    }

    @Override
    public net.minecraft.entity.Entity getTeleportVehicle() {
        return this.teleportVehicle;
    }

    @Override
    public void setIsTeleporting(boolean teleporting) {
        this.teleporting = teleporting;
    }

    @Override
    public void setTeleportVehicle(net.minecraft.entity.Entity vehicle) {
        this.teleportVehicle = vehicle;
    }

    @Override
    public EntityType getType() {
        return this.entityType;
    }

    @Override
    public UUID getUniqueId() {
        return this.entityUniqueID;
    }

    @Override
    public Optional<Entity> getPassenger() {
        return Optional.ofNullable((Entity) this.riddenByEntity);
    }

    @Override
    public Optional<Entity> getVehicle() {
        return Optional.ofNullable((Entity) this.ridingEntity);
    }

    @Override
    public Entity getBaseVehicle() {
        if (this.ridingEntity == null) {
            return this;
        }

        net.minecraft.entity.Entity baseVehicle = this.ridingEntity;
        while (baseVehicle.ridingEntity != null) {
            baseVehicle = baseVehicle.ridingEntity;
        }
        return (Entity) baseVehicle;
    }

    @Override
    public DataTransactionResult setPassenger(@Nullable Entity entity) {
        net.minecraft.entity.Entity passenger = (net.minecraft.entity.Entity) entity;
        if (this.riddenByEntity == null && entity == null) {
            return DataTransactionResult.successNoData();
        }
        Entity thisEntity = this;
        DataTransactionResult.Builder builder = DataTransactionResult.builder();
        if (this.riddenByEntity != null) {
            final Entity previous = ((Entity) this.riddenByEntity);
            this.riddenByEntity.mountEntity(null); // eject current passenger
            builder.replace(new ImmutableSpongeValue<>(Keys.PASSENGER, previous.createSnapshot()));
        }
        if (passenger != null) {
            passenger.mountEntity((net.minecraft.entity.Entity) thisEntity);
            builder.success(new ImmutableSpongeValue<>(Keys.PASSENGER, ((Entity) passenger).createSnapshot()));
        }
        return builder.result(DataTransactionResult.Type.SUCCESS).build();

    }

    @Override
    public DataTransactionResult setVehicle(@Nullable Entity entity) {
        if (this.ridingEntity == null && entity == null) {
            return DataTransactionResult.successNoData();
        }
        DataTransactionResult.Builder builder = DataTransactionResult.builder();
        if (this.ridingEntity != null) {
            Entity formerVehicle = (Entity) this.ridingEntity;
            mountEntity(null);
            builder.replace(new ImmutableSpongeValue<>(Keys.VEHICLE, formerVehicle.createSnapshot()));
        }
        if (entity != null) {
            net.minecraft.entity.Entity newVehicle = ((net.minecraft.entity.Entity) entity);
            mountEntity(newVehicle);
            builder.success(new ImmutableSpongeValue<>(Keys.VEHICLE, entity.createSnapshot()));
        }
        return builder.result(DataTransactionResult.Type.SUCCESS).build();
    }

    // for sponge internal use only
    public boolean teleportEntity(net.minecraft.entity.Entity entity, Location<World> location, int currentDim, int targetDim) {
        MinecraftServer mcServer = MinecraftServer.getServer();
        final WorldServer fromWorld = mcServer.worldServerForDimension(currentDim);
        final WorldServer toWorld = mcServer.worldServerForDimension(targetDim);
        if (entity instanceof EntityPlayer) {
            fromWorld.getEntityTracker().removePlayerFromTrackers((EntityPlayerMP) entity);
            fromWorld.getPlayerManager().removePlayer((EntityPlayerMP) entity);
            mcServer.getConfigurationManager().getPlayerList().remove(entity);
        } else {
            fromWorld.getEntityTracker().untrackEntity(entity);
        }

        entity.worldObj.removePlayerEntityDangerously(entity);
        entity.isDead = false;
        entity.dimension = targetDim;
        entity.setPositionAndRotation(location.getX(), location.getY(), location.getZ(), 0, 0);
        while (!toWorld.getCollidingBoundingBoxes(entity, entity.getEntityBoundingBox()).isEmpty() && entity.posY < 256.0D) {
            entity.setPosition(entity.posX, entity.posY + 1.0D, entity.posZ);
        }

        toWorld.theChunkProviderServer.loadChunk((int) entity.posX >> 4, (int) entity.posZ >> 4);

        if (entity instanceof EntityPlayerMP && ((EntityPlayerMP) entity).playerNetServerHandler != null) {
            EntityPlayerMP entityplayermp1 = (EntityPlayerMP) entity;

            // Support vanilla clients going into custom dimensions
            int clientDimension = DimensionManager.getClientDimensionToSend(toWorld.provider.getDimensionId(), toWorld, entityplayermp1);
            if (((IMixinEntityPlayerMP) entityplayermp1).usesCustomClient()) {
                DimensionManager.sendDimensionRegistration(toWorld, entityplayermp1, clientDimension);
            } else {
                // Force vanilla client to refresh their chunk cache if same dimension
                if (currentDim != targetDim && (currentDim == clientDimension || targetDim == clientDimension)) {
                    entityplayermp1.playerNetServerHandler.sendPacket(
                        new S07PacketRespawn((byte) (clientDimension >= 0 ? -1 : 0), toWorld.getDifficulty(),
                            toWorld.getWorldInfo().getTerrainType(), entityplayermp1.theItemInWorldManager.getGameType()));
                }
            }

            entityplayermp1.playerNetServerHandler.sendPacket(
                new S07PacketRespawn(clientDimension, toWorld.getDifficulty(), toWorld.getWorldInfo().getTerrainType(),
                    entityplayermp1.theItemInWorldManager.getGameType()));
            entity.setWorld(toWorld);
            entityplayermp1.playerNetServerHandler.setPlayerLocation(entityplayermp1.posX, entityplayermp1.posY, entityplayermp1.posZ,
                entityplayermp1.rotationYaw, entityplayermp1.rotationPitch);
            entityplayermp1.setSneaking(false);
            mcServer.getConfigurationManager().updateTimeAndWeatherForPlayer(entityplayermp1, toWorld);
            toWorld.getPlayerManager().addPlayer(entityplayermp1);
            toWorld.spawnEntityInWorld(entityplayermp1);
            mcServer.getConfigurationManager().getPlayerList().add(entityplayermp1);
            entityplayermp1.theItemInWorldManager.setWorld(toWorld);
            entityplayermp1.addSelfToInternalCraftingInventory();
            entityplayermp1.setHealth(entityplayermp1.getHealth());
            for (Object effect : entityplayermp1.getActivePotionEffects()) {
                entityplayermp1.playerNetServerHandler.sendPacket(new S1DPacketEntityEffect(entityplayermp1.getEntityId(), (PotionEffect) effect));
            }
        } else {
            entity.setWorld(toWorld);
            toWorld.spawnEntityInWorld(entity);
        }

        fromWorld.resetUpdateEntityTick();
        toWorld.resetUpdateEntityTick();
        return true;
    }

    /**
     * @author blood - May 28th, 2016
     *
     * @reason - rewritten to support {@link DisplaceEntityEvent.Teleport.Portal}
     *
     * @param toDimensionId The id of target dimension.
     */
    @Overwrite
    public void travelToDimension(int toDimensionId)
    {
        if (!this.worldObj.isRemote && !this.isDead)
        {
            // handle portal event
            DisplaceEntityEvent.Teleport.Portal event = SpongeCommonEventFactory.handleDisplaceEntityPortalEvent(this.mcEntity, toDimensionId, null);
            if (event == null || event.isCancelled()) {
                return;
            }

            this.worldObj.theProfiler.startSection("changeDimension");
            // use the world from event
            WorldServer toWorld = (WorldServer) event.getToTransform().getExtent();
            this.worldObj.removeEntity(this.mcEntity);
            this.isDead = false;
            this.worldObj.theProfiler.startSection("reposition");
            // make sure chunk is loaded
            boolean chunkLoadOverride = toWorld.theChunkProviderServer.chunkLoadOverride;
            toWorld.theChunkProviderServer.chunkLoadOverride = true;
            toWorld.theChunkProviderServer.loadChunk(event.getToTransform().getLocation().getChunkPosition().getX(), event.getToTransform().getLocation().getChunkPosition().getZ());
            toWorld.theChunkProviderServer.chunkLoadOverride = chunkLoadOverride;
            // Only need to update the entity location here as the portal is handled in SpongeCommonEventFactory
            this.setLocationAndAngles(event.getToTransform().getPosition().getX(), event.getToTransform().getPosition().getY(), event.getToTransform().getPosition().getZ(), (float) event.getToTransform().getYaw(), (float) event.getToTransform().getPitch());
            toWorld.spawnEntityInWorld(this.mcEntity);
            toWorld.updateEntityWithOptionalForce(this.mcEntity, false);
            this.worldObj.theProfiler.endSection();
            this.worldObj = toWorld;

            // Disable recreation of entities when traveling through portals
            /*this.worldObj.theProfiler.endStartSection("reloading");
            net.minecraft.entity.Entity entity = EntityList.createEntityByName(EntityList.getEntityString(this.mcEntity), toWorld);

            if (entity != null)
            {
                entity.copyDataFromOld(this.mcEntity);

                if (toWorld.provider instanceof WorldProviderEnd)
                {
                    BlockPos blockpos = this.worldObj.getTopSolidOrLiquidBlock(toWorld.getSpawnPoint());
                    entity.moveToBlockPosAndAngles(blockpos, entity.rotationYaw, entity.rotationPitch);
                }

                toWorld.spawnEntityInWorld(entity);
            }

            this.isDead = true;*/
            this.worldObj.theProfiler.endSection();
            //fromWorld.resetUpdateEntityTick();
            //toWorld.resetUpdateEntityTick();
            this.worldObj.theProfiler.endSection();
        }
    }

    /**
     * Hooks into vanilla's writeToNBT to call {@link #writeToNbt}.
     *
     * <p> This makes it easier for other entity mixins to override writeToNBT
     * without having to specify the <code>@Inject</code> annotation. </p>
     *
     * @param compound The compound vanilla writes to (unused because we write
     *        to SpongeData)
     * @param ci (Unused) callback info
     */
    @Inject(method = "Lnet/minecraft/entity/Entity;writeToNBT(Lnet/minecraft/nbt/NBTTagCompound;)V", at = @At("HEAD"))
    public void onWriteToNBT(NBTTagCompound compound, CallbackInfo ci) {
        this.writeToNbt(this.getSpongeData());
    }

    /**
     * Hooks into vanilla's readFromNBT to call {@link #readFromNbt}.
     *
     * <p> This makes it easier for other entity mixins to override readFromNbt
     * without having to specify the <code>@Inject</code> annotation. </p>
     *
     * @param compound The compound vanilla reads from (unused because we read
     *        from SpongeData)
     * @param ci (Unused) callback info
     */
    @Inject(method = "Lnet/minecraft/entity/Entity;readFromNBT(Lnet/minecraft/nbt/NBTTagCompound;)V", at = @At("RETURN"))
    public void onReadFromNBT(NBTTagCompound compound, CallbackInfo ci) {
        if (this.isConstructing) {
            firePostConstructEvents(); // Do this early as possible
        }
        this.readFromNbt(this.getSpongeData());
    }

    @Override
    public boolean validateRawData(DataContainer container) {
        return false;
    }

    @Override
    public void setRawData(DataContainer container) throws InvalidDataException {

    }

    /**
     * Read extra data (SpongeData) from the entity's NBT tag.
     *
     * @param compound The SpongeData compound to read from
     */
    @Override
    public void readFromNbt(NBTTagCompound compound) {
        if (this instanceof IMixinCustomDataHolder) {
            if (compound.hasKey(NbtDataUtil.CUSTOM_MANIPULATOR_TAG_LIST, NbtDataUtil.TAG_LIST)) {
                final NBTTagList list = compound.getTagList(NbtDataUtil.CUSTOM_MANIPULATOR_TAG_LIST, NbtDataUtil.TAG_COMPOUND);
                final ImmutableList.Builder<DataView> builder = ImmutableList.builder();
                if (list != null && list.tagCount() != 0) {
                    for (int i = 0; i < list.tagCount(); i++) {
                        final NBTTagCompound internal = list.getCompoundTagAt(i);
                        builder.add(NbtTranslator.getInstance().translateFrom(internal));
                    }
                }
                try {
                    final List<DataManipulator<?, ?>> manipulators = DataUtil.deserializeManipulatorList(builder.build());
                    for (DataManipulator<?, ?> manipulator : manipulators) {
                        offer(manipulator);
                    }
                } catch (InvalidDataException e) {
                    SpongeImpl.getLogger().error("Could not deserialize custom plugin data! ", e);
                }
            }
        }
        if (this instanceof IMixinGriefer && ((IMixinGriefer) this).isGriefer() && compound.hasKey(NbtDataUtil.CAN_GRIEF)) {
            ((IMixinGriefer) this).setCanGrief(compound.getBoolean(NbtDataUtil.CAN_GRIEF));
        }
    }

    /**
     * Write extra data (SpongeData) to the entity's NBT tag.
     *
     * @param compound The SpongeData compound to write to
     */
    @Override
    public void writeToNbt(NBTTagCompound compound) {
        if (this instanceof IMixinCustomDataHolder) {
            final List<DataManipulator<?, ?>> manipulators = ((IMixinCustomDataHolder) this).getCustomManipulators();
            if (!manipulators.isEmpty()) {
                final List<DataView> manipulatorViews = DataUtil.getSerializedManipulatorList(manipulators);
                final NBTTagList manipulatorTagList = new NBTTagList();
                for (DataView dataView : manipulatorViews) {
                    manipulatorTagList.appendTag(NbtTranslator.getInstance().translateData(dataView));
                }
                compound.setTag(NbtDataUtil.CUSTOM_MANIPULATOR_TAG_LIST, manipulatorTagList);
            }
        }
        if (this instanceof IMixinGriefer && ((IMixinGriefer) this).isGriefer()) {
            compound.setBoolean(NbtDataUtil.CAN_GRIEF, ((IMixinGriefer) this).canGrief());
        }
    }

    @Override
    public int getContentVersion() {
        return 1;
    }

    @Override
    public DataContainer toContainer() {
        final Transform<World> transform = getTransform();
        final NBTTagCompound compound = new NBTTagCompound();
        writeToNBT(compound);
        NbtDataUtil.filterSpongeCustomData(compound); // We must filter the custom data so it isn't stored twice
        final DataContainer unsafeNbt = NbtTranslator.getInstance().translateFrom(compound);
        final DataContainer container = new MemoryDataContainer()
            .set(Queries.CONTENT_VERSION, getContentVersion())
            .set(DataQueries.ENTITY_CLASS, this.getClass().getName())
            .set(Queries.WORLD_ID, transform.getExtent().getUniqueId().toString())
            .createView(DataQueries.SNAPSHOT_WORLD_POSITION)
                .set(Queries.POSITION_X, transform.getPosition().getX())
                .set(Queries.POSITION_Y, transform.getPosition().getY())
                .set(Queries.POSITION_Z, transform.getPosition().getZ())
            .getContainer()
            .createView(DataQueries.ENTITY_ROTATION)
                .set(Queries.POSITION_X, transform.getRotation().getX())
                .set(Queries.POSITION_Y, transform.getRotation().getY())
                .set(Queries.POSITION_Z, transform.getRotation().getZ())
            .getContainer()
            .createView(DataQueries.ENTITY_SCALE)
                .set(Queries.POSITION_X, transform.getScale().getX())
                .set(Queries.POSITION_Y, transform.getScale().getY())
                .set(Queries.POSITION_Z, transform.getScale().getZ())
            .getContainer()
            .set(DataQueries.ENTITY_TYPE, this.entityType.getId())
            .set(DataQueries.UNSAFE_NBT, unsafeNbt);
        final Collection<DataManipulator<?, ?>> manipulators = getContainers();
        if (!manipulators.isEmpty()) {
            container.set(DataQueries.DATA_MANIPULATORS, DataUtil.getSerializedManipulatorList(manipulators));
        }
        return container;
    }

    @Override
    public Collection<DataManipulator<?, ?>> getContainers() {
        final List<DataManipulator<?, ?>> list = Lists.newArrayList();
        this.supplyVanillaManipulators(list);
        if (this instanceof IMixinCustomDataHolder && ((IMixinCustomDataHolder) this).hasManipulators()) {
            list.addAll(((IMixinCustomDataHolder) this).getCustomManipulators());
        }
        return list;
    }

    @Override
    public Entity copy() {
        if ((Object) this instanceof Player) {
            throw new IllegalArgumentException("Cannot copy player entities!");
        }
        try {
            final NBTTagCompound compound = new NBTTagCompound();
            writeToNBT(compound);
            net.minecraft.entity.Entity entity = EntityList.createEntityByName(this.entityType.getId(), this.worldObj);
            compound.setLong(NbtDataUtil.UUID_MOST, entity.getUniqueID().getMostSignificantBits());
            compound.setLong(NbtDataUtil.UUID_LEAST, entity.getUniqueID().getLeastSignificantBits());
            entity.readFromNBT(compound);
            return (Entity) entity;
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not copy the entity:", e);
        }
    }

    @Override
    public Optional<User> getTrackedPlayer(String nbtKey) {
        if (this.creator != null && NbtDataUtil.SPONGE_ENTITY_CREATOR.equals(nbtKey)) {
            return userForUUID(this.creator);
        } else if (this.notifier != null && NbtDataUtil.SPONGE_ENTITY_NOTIFIER.equals(nbtKey)) {
            return userForUUID(this.notifier);
        }
        NBTTagCompound nbt = getSpongeData();
        if (!nbt.hasKey(nbtKey)) {
            return Optional.empty();
        }
        NBTTagCompound creatorNbt = nbt.getCompoundTag(nbtKey);

        if (!creatorNbt.hasKey(NbtDataUtil.WORLD_UUID_MOST) && !creatorNbt.hasKey(NbtDataUtil.WORLD_UUID_LEAST)) {
            return Optional.empty();
        }

        UUID uuid = new UUID(creatorNbt.getLong(NbtDataUtil.WORLD_UUID_MOST), creatorNbt.getLong(NbtDataUtil.WORLD_UUID_LEAST));
        if (SpongeImpl.getGlobalConfig().getConfig().getWorld().getInvalidLookupUuids().contains(uuid)) {
            creatorNbt.removeTag(NbtDataUtil.WORLD_UUID_MOST);
            creatorNbt.removeTag(NbtDataUtil.WORLD_UUID_LEAST);
            return Optional.empty();
        }
        if (NbtDataUtil.SPONGE_ENTITY_CREATOR.equals(nbtKey)) {
            this.creator = uuid;
        } else if (NbtDataUtil.SPONGE_ENTITY_NOTIFIER.equals(nbtKey)) {
            this.notifier = uuid;
        }
        return userForUUID(uuid);
    }

    private Optional<User> userForUUID(UUID uuid) {
        // get player if online
        EntityPlayer player = this.worldObj.getPlayerEntityByUUID(uuid);
        if (player != null) {
            return Optional.of((User)player);
        }

        if (this.spongeProfileManager == null) {
            this.spongeProfileManager = ((SpongeProfileManager) Sponge.getServer().getGameProfileManager());
        }
        if (this.userStorageService == null) {
            this.userStorageService = SpongeImpl.getGame().getServiceManager().provide(UserStorageService.class).get();
        }

        // check username cache
        String username = SpongeUsernameCache.getLastKnownUsername(uuid);
        if (username == null) {
            // check mojang cache
            Optional<GameProfile> profile = this.spongeProfileManager.getCache().getById(uuid);
            if (profile.isPresent()) {
                return this.userStorageService.get(profile.get());
            }

            this.spongeProfileManager.getGameProfileQueryTask().queueUuid(uuid);
        }

        return this.userStorageService.get(GameProfile.of(uuid, username));
    }

    @Override
    public void trackEntityUniqueId(String nbtKey, UUID uuid) {
        boolean bannedUuid = false;
        if (uuid != null && SpongeImpl.getGlobalConfig().getConfig().getWorld().getInvalidLookupUuids().contains(uuid)) {
            bannedUuid = true;
        }
        if (NbtDataUtil.SPONGE_ENTITY_CREATOR.equals(nbtKey)) {
            this.creator = uuid;
        } else if (NbtDataUtil.SPONGE_ENTITY_NOTIFIER.equals(nbtKey)) {
            this.notifier = uuid;
        }
        if (!bannedUuid && !getSpongeData().hasKey(nbtKey)) {
            if (uuid == null) {
                return;
            }

            NBTTagCompound sourceNbt = new NBTTagCompound();
            sourceNbt.setLong(NbtDataUtil.WORLD_UUID_LEAST, uuid.getLeastSignificantBits());
            sourceNbt.setLong(NbtDataUtil.WORLD_UUID_MOST, uuid.getMostSignificantBits());
            getSpongeData().setTag(nbtKey, sourceNbt);
        } else {
            if (uuid == null || bannedUuid) {
                getSpongeData().getCompoundTag(nbtKey).removeTag(NbtDataUtil.WORLD_UUID_LEAST);
                getSpongeData().getCompoundTag(nbtKey).removeTag(NbtDataUtil.WORLD_UUID_MOST);
            } else {
                getSpongeData().getCompoundTag(nbtKey).setLong(NbtDataUtil.WORLD_UUID_LEAST, uuid.getLeastSignificantBits());
                getSpongeData().getCompoundTag(nbtKey).setLong(NbtDataUtil.WORLD_UUID_MOST, uuid.getMostSignificantBits());
            }
        }
    }

    @Override
    public Optional<UUID> getCreator() {
       Optional<User> user = getTrackedPlayer(NbtDataUtil.SPONGE_ENTITY_CREATOR);
       if (user.isPresent()) {
           return Optional.of(user.get().getUniqueId());
       } else {
           return Optional.empty();
       }
    }

    @Override
    public Optional<UUID> getNotifier() {
        Optional<User> user = getTrackedPlayer(NbtDataUtil.SPONGE_ENTITY_NOTIFIER);
        if (user.isPresent()) {
            return Optional.of(user.get().getUniqueId());
        } else {
            return Optional.empty();
        }
    }

    @Override
    public void setCreator(@Nullable UUID uuid) {
        trackEntityUniqueId(NbtDataUtil.SPONGE_ENTITY_CREATOR, uuid);
    }

    @Override
    public void setNotifier(@Nullable UUID uuid) {
        trackEntityUniqueId(NbtDataUtil.SPONGE_ENTITY_NOTIFIER, uuid);
    }

    @Override
    public void setImplVelocity(Vector3d velocity) {
        this.motionX = checkNotNull(velocity).getX();
        this.motionY = velocity.getY();
        this.motionZ = velocity.getZ();
        this.velocityChanged = true;
    }

    @Override
    public Vector3d getVelocity() {
        return new Vector3d(this.motionX, this.motionY, this.motionZ);
    }

    @Redirect(method = "moveEntity", at = @At(value = "INVOKE", target="Lnet/minecraft/block/Block;onEntityCollidedWithBlock(Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;Lnet/minecraft/entity/Entity;)V"))
    public void onEntityCollideWithBlock(Block block, net.minecraft.world.World world, BlockPos pos, net.minecraft.entity.Entity entity) {
        // if block can't collide, return
        if (!((IMixinBlock) block).hasCollideLogic()) {
            return;
        }

        if (world.isRemote) {
            block.onEntityCollidedWithBlock(world, pos, entity);
            return;
        }

        IBlockState state = world.getBlockState(pos);
        this.setCurrentCollidingBlock((BlockState) state);
        if (!SpongeCommonEventFactory.handleCollideBlockEvent(block, world, pos, state, entity, Direction.NONE)) {
            block.onEntityCollidedWithBlock(world, pos, entity);
            this.lastCollidedBlockPos = pos;
        }

        this.setCurrentCollidingBlock(null);
    }

    @Redirect(method = "doBlockCollisions", at = @At(value = "INVOKE", target="Lnet/minecraft/block/Block;onEntityCollidedWithBlock(Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/entity/Entity;)V"))
    public void onEntityCollideWithBlockState(Block block, net.minecraft.world.World world, BlockPos pos, IBlockState state, net.minecraft.entity.Entity entity) {
        // if block can't collide, return
        if (!((IMixinBlock) block).hasCollideWithStateLogic()) {
            return;
        }

        if (world.isRemote) {
            block.onEntityCollidedWithBlock(world, pos, state, entity);
            return;
        }

        this.setCurrentCollidingBlock((BlockState) state);
        if (!SpongeCommonEventFactory.handleCollideBlockEvent(block, world, pos, state, entity, Direction.NONE)) {
            block.onEntityCollidedWithBlock(world, pos, state, entity);
            this.lastCollidedBlockPos = pos;
        }

        this.setCurrentCollidingBlock(null);
    }

    @Redirect(method = "updateFallState", at = @At(value = "INVOKE", target="Lnet/minecraft/block/Block;onFallenUpon(Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;Lnet/minecraft/entity/Entity;F)V"))
    public void onBlockFallenUpon(Block block, net.minecraft.world.World world, BlockPos pos, net.minecraft.entity.Entity entity, float fallDistance) {

        if (world.isRemote) {
            block.onFallenUpon(world, pos, entity, fallDistance);
            return;
        }

        IBlockState state = world.getBlockState(pos);
        this.setCurrentCollidingBlock((BlockState) state);
        if (!SpongeCommonEventFactory.handleCollideBlockEvent(block, world, pos, state, entity, Direction.UP)) {
            block.onFallenUpon(world, pos, entity, fallDistance);
            this.lastCollidedBlockPos = pos;
        }

        this.setCurrentCollidingBlock(null);
    }

    @Override
    public Translation getTranslation() {
        return getType().getTranslation();
    }

    private boolean collision = false;
    private boolean untargetable = false;
    private boolean isVanished = false;

    private boolean pendingVisibilityUpdate = false;
    private int visibilityTicks = 0;

    @Override
    public boolean isVanished() {
        return this.isVanished;
    }

    @Override
    public void setVanished(boolean invisible) {
        this.isVanished = invisible;
        this.pendingVisibilityUpdate = true;
        this.visibilityTicks = 20;
    }

    @Override
    public boolean ignoresCollision() {
        return this.collision;
    }

    @Override
    public void setIgnoresCollision(boolean prevents) {
        this.collision = prevents;
    }

    @Override
    public boolean isUntargetable() {
        return this.untargetable;
    }

    @Override
    public void setUntargetable(boolean untargetable) {
        this.untargetable = untargetable;
    }

    /**
     * @author gabizou - January 4th, 2016
     * @updated gabizou - January 27th, 2016 - Rewrite to a redirect
     *
     * This prevents sounds from being sent to the server by entities that are invisible
     */
    @Redirect(method = "playSound(Ljava/lang/String;FF)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;isSilent()Z"))
    public boolean checkIsSilentOrInvis(net.minecraft.entity.Entity entity) {
        return entity.isSilent() || this.isVanished;
    }

    @Redirect(method = "applyEntityCollision", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;noClip:Z", opcode = Opcodes.GETFIELD))
    private boolean spongeApplyEntityCollisionCheckVanish(net.minecraft.entity.Entity entity) {
        return entity.noClip || ((IMixinEntity) entity).isVanished();
    }

    @Redirect(method = "resetHeight", at = @At(value = "INVOKE", target = WORLD_SPAWN_PARTICLE))
    public void spawnParticle(net.minecraft.world.World world, EnumParticleTypes particleTypes, double xCoord, double yCoord, double zCoord,
            double xOffset, double yOffset, double zOffset, int ... p_175688_14_) {
        if (!this.isVanished) {
            this.worldObj.spawnParticle(particleTypes, xCoord, yCoord, zCoord, xOffset, yOffset, zOffset, p_175688_14_);
        }
    }

    @Redirect(method = "createRunningParticles", at = @At(value = "INVOKE", target = WORLD_SPAWN_PARTICLE))
    public void runningSpawnParticle(net.minecraft.world.World world, EnumParticleTypes particleTypes, double xCoord, double yCoord, double zCoord,
            double xOffset, double yOffset, double zOffset, int ... p_175688_14_) {
        if (!this.isVanished) {
            this.worldObj.spawnParticle(particleTypes, xCoord, yCoord, zCoord, xOffset, yOffset, zOffset, p_175688_14_);
        }
    }

    @Nullable
    @Override
    public Text getDisplayNameText() {
        return this.displayName;
    }

    @Override
    public void setDisplayName(@Nullable Text displayName) {
        this.displayName = displayName;

        StaticMixinHelper.setCustomNameTagSkip = true;
        if (this.displayName == null) {
            this.setCustomNameTag("");
        } else {
            this.setCustomNameTag(SpongeTexts.toLegacy(this.displayName));
        }

        StaticMixinHelper.setCustomNameTagSkip = false;
    }

    @Inject(method = "setCustomNameTag", at = @At("RETURN"))
    public void onSetCustomNameTag(String name, CallbackInfo ci) {
        if (!StaticMixinHelper.setCustomNameTagSkip) {
            this.displayName = SpongeTexts.fromLegacy(name);
        }
    }

    @Override
    public boolean canSee(Entity entity) {
        // note: this implementation will be changing with contextual data
        Optional<Boolean> optional = entity.get(Keys.INVISIBLE);
        return (!optional.isPresent() || !optional.get()) && !((IMixinEntity) entity).isVanished();
    }

    @Nullable private ItemStackSnapshot custom;

    /**
     * @author gabizou - January 30th, 2016
     * @author blood - May 12th, 2016
     *
     * @reason SpongeForge requires an overwrite so we do it here instead.
     */

    @Overwrite
    public EntityItem entityDropItem(ItemStack itemStackIn, float offsetY) {
        if (this.worldObj.isRemote) {
            if (itemStackIn.stackSize != 0 && itemStackIn.getItem() != null) {
                EntityItem entityitem = new EntityItem(this.worldObj, this.posX, this.posY + (double)offsetY, this.posZ, itemStackIn);
                entityitem.setDefaultPickupDelay();
                this.worldObj.spawnEntityInWorld(entityitem);
                return entityitem;
            } else {
                return null;
            }
        }

        if (itemStackIn.stackSize == 0) {
            return null;
        } else if (itemStackIn.getItem() != null) {
            // First we want to throw the DropItemEvent.PRE
            ItemStackSnapshot snapshot = ((org.spongepowered.api.item.inventory.ItemStack) itemStackIn).createSnapshot();
            List<ItemStackSnapshot> original = new ArrayList<>();
            original.add(snapshot);
            DropItemEvent.Pre dropEvent = SpongeEventFactory.createDropItemEventPre(Cause.of(NamedCause.source(this)),
                    ImmutableList.of(snapshot), original);
            if (dropEvent.isCancelled()) {
                return null;
            }
            this.custom = dropEvent.getDroppedItems().get(0);

            // Then throw the ConstructEntityEvent
            Transform<World> suggested = new Transform<>(this.getWorld(), new Vector3d(this.posX, this.posY + (double) offsetY, this.posZ));
            SpawnCause cause = EntitySpawnCause.builder().entity(this).type(SpawnTypes.DROPPED_ITEM).build();
            ConstructEntityEvent.Pre event = SpongeEventFactory
                    .createConstructEntityEventPre(Cause.of(NamedCause.source(cause)), EntityTypes.ITEM, suggested);
            SpongeImpl.postEvent(event);
            if (!event.isCancelled()) {
                // Creates the argument where we can override the item stack being used to create
                // the entity item. based on the previous event.
                // --gabizou
                ItemStack stack = this.custom == null ? itemStackIn : ((ItemStack) this.custom.createStack());
                EntityItem entityitem = new EntityItem(this.worldObj, this.posX, this.posY + (double)offsetY, this.posZ, stack);
                entityitem.setDefaultPickupDelay();
                if (this.captureItemDrops) {
                    this.capturedItemDrops.add(entityitem);
                } else {
                    cause = EntitySpawnCause.builder()
                            .entity(this)
                            .type(SpawnTypes.DROPPED_ITEM)
                            .build();
                    ((World) this.worldObj).spawnEntity(((Entity) entityitem), Cause.of(NamedCause.source(cause)));
                }
                return entityitem;
            }
        }

        return null;
    }

    // Whenever attackEntityFrom is called, the first check is usually always this
    // so lets take advantage of it and store the last damage source =)
    @Inject(method = "isEntityInvulnerable", at = @At("HEAD"))
    public void onIsEntityInvulnerable(DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        this.lastDamageSource = source;
    }

    @Inject(method = "setDead", at = @At("HEAD"))
    public void onSetDead(CallbackInfo ci) {
        if (this.worldObj.isRemote || (this.mcEntity instanceof EntityLivingBase && !(this.mcEntity instanceof EntityArmorStand))) {
            return;
        }

        EntityPlayer player = StaticMixinHelper.packetPlayer;
        IMixinWorld spongeWorld = (IMixinWorld) this.worldObj;
        final CauseTracker causeTracker = spongeWorld.getCauseTracker();
        List<NamedCause> namedCauses = new ArrayList<>();
        if (this.lastDamageSource != null) {
            namedCauses.add(NamedCause.source(this.lastDamageSource));
        }
        if (causeTracker.hasTickingBlock()) {
            namedCauses.add(NamedCause.of("ParentSource", causeTracker.getCurrentTickBlock().get()));
        } else if (causeTracker.hasTickingTileEntity()) {
            namedCauses.add(NamedCause.of("ParentSource", causeTracker.getCurrentTickTileEntity().get()));
        }
        if (player != null) {
            namedCauses.add(NamedCause.of("PlayerSource", player));
        } else if (causeTracker.hasNotifier()) {
            namedCauses.add(NamedCause.of("PlayerSource", causeTracker.getCurrentNotifier().get()));
        }
        if (this.destructCause == null && !namedCauses.isEmpty()) {
            this.destructCause = Cause.of(namedCauses);
        } else if (!namedCauses.isEmpty()){
            this.destructCause = this.destructCause.merge(Cause.of(namedCauses));
        }
    }

    @Override
    public DamageSource getLastDamageSource() {
        return this.lastDamageSource;
    }

    @Override
    public Cause getNonLivingDestructCause() {
        return this.destructCause;
    }

    @Override
    public void setCurrentCollidingBlock(BlockState state) {
        this.currentCollidingBlock = state;
    }

    @Override
    public BlockState getCurrentCollidingBlock() {
        if (this.currentCollidingBlock == null) {
            return (BlockState) Blocks.air.getDefaultState();
        }
        return this.currentCollidingBlock;
    }

    @Override
    public BlockPos getLastCollidedBlockPos() {
        return this.lastCollidedBlockPos;
    }

    @Override
    public boolean isVanilla() {
        return this.isVanilla;
    }

    @Override
    public void setCaptureItemDrops(boolean capture) {
        this.captureItemDrops = capture;
    }

    @Override
    public List<EntityItem> getCapturedItemDrops() {
        return this.capturedItemDrops;
    }

    @Override
    public void setDestructCause(Cause cause) {
        this.destructCause = cause;
    }

    @Override
    public boolean spawnedFromBlockBreak() {
        return this.spawnedFromBlockBreak;
    }

    @Override
    public void setSpawnedFromBlockBreak(boolean flag) {
        this.spawnedFromBlockBreak = flag;
    }

    @Override
    public SpawnCause getSpawnCause() {
        return this.spawnCause;
    }

    @Override
    public void setSpawnCause(SpawnCause spawnCause) {
        this.spawnCause = spawnCause;
    }

    @Override
    public Timing getTimingsHandler() {
        if (this.timing == null) {
            this.timing = SpongeTimings.getEntityTiming(this);
        }
        return this.timing;
    }
}
