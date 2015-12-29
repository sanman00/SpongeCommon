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
package org.spongepowered.common.world;

import com.google.common.collect.MapMaker;
import com.mojang.authlib.GameProfile;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.world.WorldServer;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.event.SpongeEventFactory;
import org.spongepowered.api.event.block.InteractBlockEvent;
import org.spongepowered.api.event.cause.Cause;
import org.spongepowered.api.item.inventory.ItemStack;
import org.spongepowered.api.util.Direction;
import org.spongepowered.api.world.World;
import org.spongepowered.common.SpongeImpl;
import org.spongepowered.common.interfaces.world.IMixinWorld;
import org.spongepowered.common.registry.provider.DirectionFacingProvider;
import org.spongepowered.common.util.VecHelper;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * A marker to state that this is a fake player.
 */
public interface FakePlayer {

    Controller controller = new Controller(SpongeImpl.getGame().createFakePlayerFactory());

    public class Factory {

        private final Map<UUID, EntityPlayerMP> players = new MapMaker().weakValues().makeMap();

        public EntityPlayerMP getPlayer(WorldServer world, GameProfile profile) {
            EntityPlayerMP player = players.get(profile.getId());
            if (player == null) {
                players.put(profile.getId(), player = new SimulatedPlayer(world, profile));
            }
            return player;
        }

    }

    public static class Controller {

        private final Factory factory;

        Controller(Factory factory) {
            this.factory = factory;
        }

        private EntityPlayerMP setUpPlayer(GameProfile profile, World world, int x, int y, int z, ItemStack itemInHand, Cause cause) {
            EntityPlayerMP player = factory.getPlayer((WorldServer) world, profile);
            player.worldObj = (WorldServer) world;
            player.theItemInWorldManager.setWorld((WorldServer) world);
            player.posX = x;
            player.posY = y;
            player.posZ = z;
            player.onGround = true;
            player.inventory.setItemStack((net.minecraft.item.ItemStack) itemInHand);
            player.inventory.mainInventory[player.inventory.currentItem] = (net.minecraft.item.ItemStack) itemInHand;

            // Set up cause capture
            ((IMixinWorld) world).getCauseTracker().addCause(cause);
            ((IMixinWorld) world).getCauseTracker().setCaptureBlocks(true);
            ((IMixinWorld) world).getCauseTracker().setCaptureSpawnedEntities(true);

            return player;
        }

        private static void tearDownPlayer(EntityPlayerMP player) {
            // Tear down cause capture
            ((IMixinWorld) player.worldObj).getCauseTracker().handlePostTickCaptures();
            ((IMixinWorld) player.worldObj).getCauseTracker().removeCurrentCause();

            player.inventory.clear();
            player.worldObj = null;
            player.theItemInWorldManager.setWorld(null);
        }

        public boolean hit(World world, int x, int y, int z, Direction side, GameProfile profile, Cause cause) {
            EntityPlayerMP player = setUpPlayer(profile, world, x, y, z, null, cause);
            EnumFacing facing = DirectionFacingProvider.directionMap.get(side);
            boolean result = onBlockClicked(player, new BlockPos(x, y, z), facing);
            tearDownPlayer(player);
            return result;
        }

        // Partial copy of ItemInWorldManager#onBlockClicked
        private boolean onBlockClicked(EntityPlayerMP player, BlockPos pos, EnumFacing side) {
            InteractBlockEvent.Primary event = SpongeEventFactory.createInteractBlockEventPrimary(
                    ((IMixinWorld) player.worldObj).getCauseTracker().getCurrentCause(), Optional.empty(),
                    ((World) player.worldObj).createSnapshot(VecHelper.toVector(pos)), DirectionFacingProvider.directionMap.inverse().get(side));
            if (SpongeImpl.postEvent(event)) {
                return false;
            }
            net.minecraft.world.World world = player.theItemInWorldManager.theWorld;
            Block block = world.getBlockState(pos).getBlock();
            world.extinguishFire(null, pos, side);
            if (block.getMaterial() == Material.air) {
                return true;
            }
            block.onBlockClicked(world, pos, player);
            float f = block.getPlayerRelativeBlockHardness(player, player.worldObj, pos);
            return f >= 1.0F ? player.theItemInWorldManager.tryHarvestBlock(pos) : true;
        }

        public boolean interact(World world, int x, int y, int z, ItemStack itemStack, Direction side, GameProfile profile, Cause cause) {
            EntityPlayerMP player = setUpPlayer(profile, world, x, y, z, itemStack, cause);
            EnumFacing facing = DirectionFacingProvider.directionMap.get(side);
            boolean result = player.theItemInWorldManager.activateBlockOrUseItem(player, player.worldObj, (net.minecraft.item.ItemStack) itemStack,
                    new BlockPos(x, y, z), facing, 0, 0, 0);
            tearDownPlayer(player);
            return result;
        }

        public boolean place(World world, int x, int y, int z, BlockState block, Direction side, GameProfile profile, Cause cause) {
            Item item = Item.getItemFromBlock((Block) block.getType());
            if (item == null) {
                return false;
            }
            net.minecraft.item.ItemStack stack = new net.minecraft.item.ItemStack(item, 1, ((Block) block.getType())
                    .getMetaFromState((IBlockState) block));
            EntityPlayerMP player = setUpPlayer(profile, world, x, y, z, (ItemStack) stack, cause);
            EnumFacing facing = DirectionFacingProvider.directionMap.get(side);
            boolean result = stack.onItemUse(player, player.worldObj, new BlockPos(x, y, z), facing, 0, 0, 0);
            tearDownPlayer(player);
            return result;
        }

        public boolean dig(World world, int x, int y, int z, org.spongepowered.api.item.inventory.ItemStack itemStack, GameProfile profile,
                Cause cause) {
            EntityPlayerMP player = setUpPlayer(profile, world, x, y, z, itemStack, cause);
            boolean result = player.theItemInWorldManager.tryHarvestBlock(new BlockPos(x, y, z));
            tearDownPlayer(player);
            return result;
        }

        public int digTime(World world, int x, int y, int z, org.spongepowered.api.item.inventory.ItemStack itemStack, GameProfile profile,
                Cause cause) {
            EntityPlayerMP player = setUpPlayer(profile, world, x, y, z, itemStack, cause);
            BlockPos pos = new BlockPos(x, y, z);
            net.minecraft.world.World w = player.worldObj;
            // A value from 0.0 to 1.0 representing the percentage of the block
            // broken in one tick. We return the inverse.
            float percentagePerTick = w.getBlockState(pos).getBlock().getPlayerRelativeBlockHardness(player, w, pos);
            tearDownPlayer(player);
            return MathHelper.ceiling_float_int(1 / percentagePerTick);
        }

    }

}
