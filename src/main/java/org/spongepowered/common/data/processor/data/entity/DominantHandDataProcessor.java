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
package org.spongepowered.common.data.processor.data.entity;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumHandSide;
import org.spongepowered.api.data.DataTransactionResult;
import org.spongepowered.api.data.key.Keys;
import org.spongepowered.api.data.manipulator.immutable.entity.ImmutableDominantHandData;
import org.spongepowered.api.data.manipulator.mutable.entity.DominantHandData;
import org.spongepowered.api.data.type.HandPreference;
import org.spongepowered.api.data.type.HandPreferences;
import org.spongepowered.api.data.value.ValueContainer;
import org.spongepowered.api.data.value.immutable.ImmutableValue;
import org.spongepowered.api.data.value.mutable.Value;
import org.spongepowered.common.data.manipulator.mutable.entity.SpongeDominantHandData;
import org.spongepowered.common.data.processor.common.AbstractEntitySingleDataProcessor;
import org.spongepowered.common.data.value.immutable.ImmutableSpongeValue;
import org.spongepowered.common.data.value.mutable.SpongeValue;

import java.util.Optional;

public class DominantHandDataProcessor extends
        AbstractEntitySingleDataProcessor<EntityLivingBase, HandPreference, Value<HandPreference>, DominantHandData, ImmutableDominantHandData> {

    public DominantHandDataProcessor() {
        super(EntityLivingBase.class, Keys.DOMINANT_HAND);
    }

    @Override
    public DataTransactionResult removeFrom(ValueContainer<?> container) {
        return DataTransactionResult.failNoData();
    }

    @Override
    protected boolean set(EntityLivingBase dataHolder, HandPreference value) {
        EnumHandSide handSide = (EnumHandSide) (Object) value;
        if (dataHolder instanceof EntityPlayer) {
            ((EntityPlayer) dataHolder).setPrimaryHand(handSide);
        }
        else if (dataHolder instanceof EntityLiving) {
            ((EntityLiving) dataHolder).setLeftHanded(value == HandPreferences.LEFT);
        }
        else {
            return false; //if the value could not be set, this will be returned...
        }
        return true; //otherwise, this will be.
    }

    @Override
    protected Optional<HandPreference> getVal(EntityLivingBase dataHolder) {
        return Optional.of((HandPreference) (Object) dataHolder.getPrimaryHand());
    }

    @Override
    protected ImmutableValue<HandPreference> constructImmutableValue(HandPreference value) {
        return ImmutableSpongeValue.cachedOf(Keys.DOMINANT_HAND, HandPreferences.RIGHT, value);
    }

    @Override
    protected Value<HandPreference> constructValue(HandPreference actualValue) {
        return new SpongeValue<>(Keys.DOMINANT_HAND, HandPreferences.RIGHT, actualValue);
    }

    @Override
    protected DominantHandData createManipulator() {
        return new SpongeDominantHandData();
    }

}
