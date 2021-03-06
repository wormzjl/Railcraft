/* 
 * Copyright (c) CovertJaguar, 2014 http://railcraft.info
 * 
 * This code is the property of CovertJaguar
 * and may only be used with explicit written
 * permission unless otherwise specified on the
 * license page at http://railcraft.info/wiki/info:license.
 */
package mods.railcraft.common.blocks.machine.gamma;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import net.minecraft.entity.item.EntityMinecart;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.IIcon;
import net.minecraftforge.common.util.ForgeDirection;
import mods.railcraft.api.carts.CartTools;
import mods.railcraft.common.blocks.machine.IEnumMachine;
import mods.railcraft.common.carts.CartUtils;
import mods.railcraft.common.core.RailcraftConfig;
import mods.railcraft.common.gui.EnumGui;
import mods.railcraft.common.gui.GuiHandler;
import mods.railcraft.common.gui.buttons.IButtonTextureSet;
import mods.railcraft.common.gui.buttons.IMultiButtonState;
import mods.railcraft.common.gui.buttons.MultiButtonController;
import mods.railcraft.common.gui.buttons.StandardButtonTextureSets;
import mods.railcraft.common.gui.tooltips.ToolTip;
import mods.railcraft.common.plugins.forge.LocalizationPlugin;
import mods.railcraft.common.fluids.Fluids;
import mods.railcraft.common.util.inventory.PhantomInventory;
import mods.railcraft.common.fluids.FluidHelper;
import mods.railcraft.common.fluids.TankManager;
import mods.railcraft.common.fluids.TankToolkit;
import mods.railcraft.common.fluids.tanks.StandardTank;
import mods.railcraft.common.plugins.buildcraft.actions.Actions;
import mods.railcraft.common.util.misc.Game;
import mods.railcraft.common.util.network.IGuiReturnHandler;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidHandler;

import static mods.railcraft.common.blocks.machine.gamma.TileLoaderBase.STOP_VELOCITY;
import static mods.railcraft.common.blocks.machine.gamma.TileLoaderLiquidBase.SLOT_INPUT;

public class TileLiquidUnloader extends TileLoaderLiquidBase implements IGuiReturnHandler {

    private static final int CAPACITY = FluidHelper.BUCKET_VOLUME * 32;
    private static final int TRANSFER_RATE = 80;
    private final StandardTank tank = new StandardTank(CAPACITY, this);
    private final PhantomInventory invFilter = new PhantomInventory(1);
    private MultiButtonController<ButtonState> stateController = new MultiButtonController<ButtonState>(ButtonState.EMPTY_COMPLETELY.ordinal(), ButtonState.values());

    public enum ButtonState implements IMultiButtonState {

        EMPTY_COMPLETELY("railcraft.gui.liquid.unloader.empty"),
        IMMEDIATE("railcraft.gui.liquid.unloader.immediate"),
        MANUAL("railcraft.gui.liquid.unloader.manual");
        private String label;
        private final ToolTip tip;

        private ButtonState(String label) {
            this.label = label;
            this.tip = ToolTip.buildToolTip(label + ".tip");
        }

        @Override
        public String getLabel() {
            return LocalizationPlugin.translate(label);
        }

        @Override
        public IButtonTextureSet getTextureSet() {
            return StandardButtonTextureSets.SMALL_BUTTON;
        }

        @Override
        public ToolTip getToolTip() {
            return tip;
        }

    }

    public TileLiquidUnloader() {
        super();
        tankManager.add(tank);
    }

    @Override
    public IEnumMachine getMachineType() {
        return EnumMachineGamma.LIQUID_UNLOADER;
    }

    public PhantomInventory getLiquidFilter() {
        return invFilter;
    }

    public MultiButtonController<ButtonState> getStateController() {
        return stateController;
    }

    @Override
    public IIcon getIcon(int side) {
        if (side > 1)
            return getMachineType().getTexture(6);
        return getMachineType().getTexture(side);
    }

    public Fluid getFilterFluid() {
        if (invFilter.getStackInSlot(0) != null) {
            FluidStack fluidStack = FluidHelper.getFluidStackInContainer(invFilter.getStackInSlot(0));
            return fluidStack != null ? fluidStack.getFluid() : null;
        }
        return null;
    }

    @Override
    public boolean blockActivated(EntityPlayer player, int side) {
        return super.blockActivated(player, side);
    }

    @Override
    public void updateEntity() {
        super.updateEntity();
        if (Game.isNotHost(getWorld()))
            return;

        ItemStack topSlot = getStackInSlot(SLOT_INPUT);
        if (topSlot != null && !FluidHelper.isContainer(topSlot)) {
            setInventorySlotContents(SLOT_INPUT, null);
            dropItem(topSlot);
        }

        ItemStack bottomSlot = getStackInSlot(SLOT_OUTPUT);
        if (bottomSlot != null && !FluidHelper.isContainer(bottomSlot)) {
            setInventorySlotContents(SLOT_OUTPUT, null);
            dropItem(bottomSlot);
        }

        if (clock % FluidHelper.BUCKET_FILL_TIME == 0)
            FluidHelper.fillContainers(tankManager, this, SLOT_INPUT, SLOT_OUTPUT, tank.getFluidType());

        tankManager.outputLiquid(tileCache, TankManager.TANK_FILTER, ForgeDirection.VALID_DIRECTIONS, 0, TRANSFER_RATE);

        EntityMinecart cart = CartTools.getMinecartOnSide(worldObj, xCoord, yCoord, zCoord, 0.1f, ForgeDirection.UP);

        if (cart != currentCart) {
            setPowered(false);
            currentCart = cart;
            cartWasSent();
        }

        if (cart == null)
            return;

        if (!(cart instanceof IFluidHandler)) {
            if (CartTools.cartVelocityIsLessThan(cart, STOP_VELOCITY))
                setPowered(true);
            return;
        }

        if (isSendCartGateAction()) {
            if (CartTools.cartVelocityIsLessThan(cart, STOP_VELOCITY))
                setPowered(true);
            return;
        }

        ItemStack minecartSlot1 = getCartFilters().getStackInSlot(0);
        ItemStack minecartSlot2 = getCartFilters().getStackInSlot(1);
        if (minecartSlot1 != null || minecartSlot2 != null)
            if (!CartUtils.doesCartMatchFilter(minecartSlot1, cart) && !CartUtils.doesCartMatchFilter(minecartSlot2, cart)) {
                if (CartTools.cartVelocityIsLessThan(cart, STOP_VELOCITY))
                    setPowered(true);
                return;
            }

        if (isPaused())
            return;

        TankToolkit tankCart = new TankToolkit((IFluidHandler) cart);

        flow = 0;
        FluidStack drained = tankCart.drain(ForgeDirection.DOWN, RailcraftConfig.getTankCartFillRate(), false);
        if (getFilterFluid() == null || Fluids.areEqual(getFilterFluid(), drained)) {
            flow = tankManager.get(0).fill(drained, true);
            tankCart.drain(ForgeDirection.DOWN, flow, true);
        }

        if (flow > 0)
            setPowered(false);

        if (stateController.getButtonState() != ButtonState.MANUAL
                && flow <= 0 && !isPowered() && shouldSendCart(cart, tankCart))
            setPowered(true);
    }

    private boolean shouldSendCart(EntityMinecart cart, TankToolkit tankCart) {
        if (!CartTools.cartVelocityIsLessThan(cart, STOP_VELOCITY))
            return false;
        if (stateController.getButtonState() != ButtonState.EMPTY_COMPLETELY)
            return true;
        if (getFilterFluid() != null && tankCart.isTankEmpty(getFilterFluid()))
            return true;
        if (tankCart.areTanksEmpty())
            return true;
        return false;
    }

    @Override
    protected void setPowered(boolean p) {
        if (stateController.getButtonState() == ButtonState.MANUAL)
            p = false;
        super.setPowered(p);
    }

    @Override
    public int fill(ForgeDirection from, FluidStack resource, boolean doFill) {
        return 0;
    }

    @Override
    public boolean canFill(ForgeDirection from, Fluid fluid) {
        return false;
    }

    @Override
    public boolean canDrain(ForgeDirection from, Fluid fluid) {
        return true;
    }

    @Override
    public void writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);

        stateController.writeToNBT(data, "state");
        getLiquidFilter().writeToNBT("invFilter", data);
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);

        stateController.readFromNBT(data, "state");

        if (data.hasKey("filter")) {
            NBTTagCompound filter = data.getCompoundTag("filter");
            getLiquidFilter().readFromNBT("Items", filter);
        } else
            getLiquidFilter().readFromNBT("invFilter", data);
    }

    @Override
    public void writePacketData(DataOutputStream data) throws IOException {
        super.writePacketData(data);
        data.writeByte(stateController.getCurrentState());
    }

    @Override
    public void readPacketData(DataInputStream data) throws IOException {
        super.readPacketData(data);
        stateController.setCurrentState(data.readByte());
    }

    @Override
    public void writeGuiData(DataOutputStream data) throws IOException {
        data.writeByte(stateController.getCurrentState());
    }

    @Override
    public void readGuiData(DataInputStream data, EntityPlayer sender) throws IOException {
        stateController.setCurrentState(data.readByte());
    }

    public ForgeDirection getOrientation() {
        return ForgeDirection.UP;
    }

    @Override
    public boolean openGui(EntityPlayer player) {
        GuiHandler.openGui(EnumGui.UNLOADER_LIQUID, player, worldObj, xCoord, yCoord, zCoord);
        return true;
    }

    @Override
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        switch (slot) {
            case SLOT_INPUT:
                return FluidHelper.isEmptyContainer(stack);
        }
        return false;
    }

}
