/* 
 * Copyright (c) CovertJaguar, 2014 http://railcraft.info
 * 
 * This code is the property of CovertJaguar
 * and may only be used with explicit written
 * permission unless otherwise specified on the
 * license page at http://railcraft.info/wiki/info:license.
 */
package mods.railcraft.common.carts;

import java.util.List;
import mods.railcraft.common.util.misc.Game;
import net.minecraft.entity.item.EntityMinecartContainer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.minecart.MinecartInteractEvent;

/**
 *
 * It also contains some generic code that most carts will find useful.
 *
 * @author CovertJaguar <http://www.railcraft.info>
 */
public abstract class CartContainerBase extends EntityMinecartContainer implements IRailcraftCart {

    public CartContainerBase(World world) {
        super(world);
        renderDistanceWeight = CartConstants.RENDER_DIST_MULTIPLIER;
    }

    public CartContainerBase(World world, double x, double y, double z) {
        super(world, x, y, z);
        renderDistanceWeight = CartConstants.RENDER_DIST_MULTIPLIER;
    }

    @Override
    public void initEntityFromItem(ItemStack stack) {
    }

    @Override
    public final boolean interactFirst(EntityPlayer player) {
        if (MinecraftForge.EVENT_BUS.post(new MinecartInteractEvent(this, player)))
            return true;
        return doInteract(player);
    }

    public boolean doInteract(EntityPlayer player) {
        return true;
    }

    public double getDrag() {
        return CartConstants.STANDARD_DRAG;
    }

    @Override
    public ItemStack getCartItem() {
        ItemStack stack = EnumCart.fromCart(this).getCartItem();
        if (hasCustomInventoryName())
            stack.setStackDisplayName(getCommandSenderName());
        return stack;
    }

    public abstract List<ItemStack> getItemsDropped();

    @Override
    public void setDead() {
        if (Game.isNotHost(worldObj))
            for (int slot = 0; slot < getSizeInventory(); slot++) {
                setInventorySlotContents(slot, null);
            }
        super.setDead();
    }

    @Override
    public void killMinecart(DamageSource par1DamageSource) {
        setDead();
        List<ItemStack> drops = getItemsDropped();
        if (this.func_95999_t() != null)
            drops.get(0).setStackDisplayName(this.func_95999_t());
        for (ItemStack item : drops) {
            entityDropItem(item, 0.0F);
        }
    }

    @Override
    public int getMinecartType() {
        return -1;
    }

}
