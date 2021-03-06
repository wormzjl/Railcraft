/* 
 * Copyright (c) CovertJaguar, 2014 http://railcraft.info
 * 
 * This code is the property of CovertJaguar
 * and may only be used with explicit written
 * permission unless otherwise specified on the
 * license page at http://railcraft.info/wiki/info:license.
 */
package mods.railcraft.common.blocks.machine.alpha;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.ForgeChunkManager.Ticket;
import net.minecraftforge.common.ForgeChunkManager.Type;
import mods.railcraft.api.core.WorldCoordinate;
import mods.railcraft.api.core.items.IToolCrowbar;
import mods.railcraft.common.blocks.machine.IEnumMachine;
import mods.railcraft.common.blocks.machine.TileMachineItem;
import mods.railcraft.common.blocks.machine.beta.EnumMachineBeta;
import mods.railcraft.common.blocks.machine.beta.TileSentinel;
import mods.railcraft.common.carts.ItemCartAnchor;
import mods.railcraft.common.core.Railcraft;
import mods.railcraft.common.core.RailcraftConfig;
import mods.railcraft.common.core.RailcraftConstants;
import mods.railcraft.common.gui.EnumGui;
import mods.railcraft.common.gui.GuiHandler;
import mods.railcraft.common.plugins.forge.LocalizationPlugin;
import mods.railcraft.common.plugins.forge.ChatPlugin;
import mods.railcraft.common.plugins.forge.PowerPlugin;
import mods.railcraft.common.util.collections.ItemMap;
import mods.railcraft.common.util.effects.EffectManager;
import mods.railcraft.common.util.misc.ChunkManager;
import mods.railcraft.common.util.misc.Game;
import mods.railcraft.common.util.misc.IAnchor;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.ISidedInventory;

/**
 *
 * @author CovertJaguar <http://www.railcraft.info>
 */
public class TileAnchorWorld extends TileMachineItem implements IAnchor, ISidedInventory {

    public static final Map<EntityPlayer, WorldCoordinate> pairingMap = Collections.synchronizedMap(new HashMap<EntityPlayer, WorldCoordinate>());
    private static final int SENTINEL_CHECK = 128;
    private static final byte MAX_CHUNKS = 25;
    private static final byte FUEL_CYCLE = 9;
    private static final byte ANCHOR_RADIUS = 1;
    private static final int[] SLOTS = new int[]{0};
    private static final int[] SLOTS_NOACCESS = new int[]{};
    private int xSentinel = -1;
    private int ySentinel = -1;
    private int zSentinel = -1;
    protected Ticket ticket;
    private Set<ChunkCoordIntPair> chunks;
    private long fuel;
    private int fuelCycle;
    private boolean hasTicket;
    private boolean refreshTicket;
    private boolean powered;
    private ItemStack lastFuel;

    public TileAnchorWorld() {
        super(1);
    }

    @Override
    public int getSizeInventory() {
        return needsFuel() ? 1 : 0;
    }

    @Override
    public IEnumMachine getMachineType() {
        return EnumMachineAlpha.WORLD_ANCHOR;
    }

    @Override
    public IIcon getIcon(int side) {
        if (!hasTicket && side < 2)
            return getMachineType().getTexture(6);
        return getMachineType().getTexture(side);
    }

    @Override
    public boolean blockActivated(EntityPlayer player, int side) {
        ItemStack current = player.getCurrentEquippedItem();
        if (current != null && current.getItem() instanceof IToolCrowbar) {
            IToolCrowbar crowbar = (IToolCrowbar) current.getItem();
            if (crowbar.canWhack(player, current, xCoord, yCoord, zCoord)) {
                WorldCoordinate sentinel = pairingMap.get(player);
                if (sentinel != null) {
                    if (worldObj.provider.dimensionId == sentinel.dimension) {
                        setSentinel(player, sentinel.x, sentinel.y, sentinel.z);
                        crowbar.onWhack(player, current, xCoord, yCoord, zCoord);
                    }
                    return true;
                }
            }
        }
        return super.blockActivated(player, side);
    }

    @Override
    public boolean openGui(EntityPlayer player) {
        if (needsFuel()) {
            GuiHandler.openGui(EnumGui.WORLD_ANCHOR, player, worldObj, xCoord, yCoord, zCoord);
            return true;
        }
        return false;
    }

    public int getMaxSentinelChunks() {
        if (ticket == null)
            return MAX_CHUNKS;
        return Math.min(ticket.getMaxChunkListDepth(), MAX_CHUNKS);
    }

    public void setSentinel(EntityPlayer player, int x, int y, int z) {
        TileEntity tile = worldObj.getTileEntity(x, y, z);
        if (tile instanceof TileSentinel) {
            int xChunk = xCoord >> 4;
            int zChunk = zCoord >> 4;

            int xSentinelChunk = tile.xCoord >> 4;
            int zSentinelChunk = tile.zCoord >> 4;

            if (xChunk != xSentinelChunk && zChunk != zSentinelChunk) {
                if (Game.isNotHost(worldObj))
                    ChatPlugin.sendLocalizedChat(player, "gui.anchor.pair.fail.alignment", getName(), LocalizationPlugin.translate(EnumMachineBeta.SENTINEL.getTag()));
                return;
            }

            int max = getMaxSentinelChunks();
            if (Math.abs(xChunk - xSentinelChunk) >= max || Math.abs(zChunk - zSentinelChunk) >= max) {
                if (Game.isNotHost(worldObj))
                    ChatPlugin.sendLocalizedChat(player, "gui.anchor.pair.fail.distance", getName(), LocalizationPlugin.translate(EnumMachineBeta.SENTINEL.getTag()));
                return;
            }

            xSentinel = tile.xCoord;
            ySentinel = tile.yCoord;
            zSentinel = tile.zCoord;

            if (Game.isHost(worldObj)) {
                requestTicket();
                sendUpdateToClient();
            } else
                ChatPlugin.sendLocalizedChat(player, "gui.anchor.pair.success", getName());
        }
    }

    public void clearSentinel() {
        if (!hasSentinel())
            return;

        xSentinel = -1;
        ySentinel = -1;
        zSentinel = -1;

        requestTicket();
        sendUpdateToClient();
    }

    public boolean hasSentinel() {
        return ySentinel != -1;
    }

    public boolean hasFuel() {
        return fuel > 0;
    }

    @Override
    public ArrayList<ItemStack> getDrops(int fortune) {
        ArrayList<ItemStack> items = new ArrayList<ItemStack>();
        ItemStack drop = getMachineType().getItem();
        if (needsFuel() && hasFuel()) {
            NBTTagCompound nbt = new NBTTagCompound();
            nbt.setLong("fuel", fuel);
            drop.setTagCompound(nbt);
        }
        items.add(drop);
        return items;
    }

    @Override
    public void initFromItem(ItemStack stack) {
        super.initFromItem(stack);
        if (needsFuel())
            fuel = ItemCartAnchor.getFuel(stack);
    }

    @Override
    public void updateEntity() {
        super.updateEntity();
        if (Game.isNotHost(worldObj)) {
            if (chunks != null)
                EffectManager.instance.chunkLoaderEffect(worldObj, this, chunks);
            return;
        }

        if (RailcraftConfig.deleteAnchors()) {
            releaseTicket();
            worldObj.setBlock(xCoord, yCoord, zCoord, Blocks.obsidian);
            return;
        }

        if (ticket != null)
            if (refreshTicket || powered)
                releaseTicket();

        if (needsFuel()) {
            fuelCycle++;
            if (fuelCycle >= FUEL_CYCLE) {
                fuelCycle = 0;
                if (chunks != null && ticket != null && fuel > 0)
                    fuel -= chunks.size();
                if (fuel <= 0) {
                    ItemStack stack = getStackInSlot(0);
                    if (stack == null || stack.stackSize <= 0) {
                        setInventorySlotContents(0, null);
                        releaseTicket();
                    } else if (getFuelMap().containsKey(stack)) {
                        lastFuel = decrStackSize(0, 1);
                        fuel = (long) (getFuelMap().get(stack) * RailcraftConstants.TICKS_PER_HOUR);
                    }
                }
            }
        }

        if (clock % SENTINEL_CHECK == 0 && hasSentinel()) {
            TileEntity tile = worldObj.getTileEntity(xSentinel, ySentinel, zSentinel);
            if (!(tile instanceof TileSentinel))
                clearSentinel();
        }

        if (ticket == null)
            requestTicket();
    }

    @Override
    public void onBlockRemoval() {
        super.onBlockRemoval();
        releaseTicket();
    }

    @Override
    public void invalidate() {
        super.invalidate();
        refreshTicket = true;
    }

    @Override
    public void validate() {
        super.validate();
        refreshTicket = true;
    }

    protected void releaseTicket() {
        refreshTicket = false;
        setTicket(null);
    }

    protected void requestTicket() {
        if (meetsTicketRequirements()) {
            Ticket chunkTicket = getTicketFromForge();
            if (chunkTicket != null) {
                setTicketData(chunkTicket);
                forceChunkLoading(chunkTicket);
            }
        }
    }

    public boolean needsFuel() {
        return !getFuelMap().isEmpty();
    }

    @Override
    public ItemMap<Float> getFuelMap() {
        return RailcraftConfig.anchorFuelWorld;
    }

    protected boolean meetsTicketRequirements() {
        return !powered && (hasFuel() || !needsFuel());
    }

    protected Ticket getTicketFromForge() {
        return ForgeChunkManager.requestTicket(Railcraft.getMod(), worldObj, Type.NORMAL);
    }

    protected void setTicketData(Ticket chunkTicket) {
        chunkTicket.getModData().setInteger("xCoord", xCoord);
        chunkTicket.getModData().setInteger("yCoord", yCoord);
        chunkTicket.getModData().setInteger("zCoord", zCoord);
    }

    public void setTicket(Ticket ticket) {
        boolean changed = false;
        if (this.ticket != ticket) {
            ForgeChunkManager.releaseTicket(this.ticket);
            changed = true;
        }
        this.ticket = ticket;
        hasTicket = ticket != null;
        if (changed)
            sendUpdateToClient();
    }

    public void forceChunkLoading(Ticket ticket) {
        setTicket(ticket);

        setupChunks();

        if (chunks != null)
            for (ChunkCoordIntPair chunk : chunks) {
                ForgeChunkManager.forceChunk(ticket, chunk);
            }
    }

    public void setupChunks() {
        if (!hasTicket)
            chunks = null;
        else if (hasSentinel())
            chunks = ChunkManager.getInstance().getChunksBetween(xCoord >> 4, zCoord >> 4, xSentinel >> 4, zSentinel >> 4, getMaxSentinelChunks());
        else
            chunks = ChunkManager.getInstance().getChunksAround(xCoord >> 4, zCoord >> 4, ANCHOR_RADIUS);
    }

    public boolean isPowered() {
        return powered;
    }

    public void setPowered(boolean power) {
        powered = power;
    }

    @Override
    public void onNeighborBlockChange(Block block) {
        super.onNeighborBlockChange(block);
        if (Game.isNotHost(getWorld()))
            return;
        boolean newPower = PowerPlugin.isBlockBeingPowered(worldObj, xCoord, yCoord, zCoord);
        if (powered != newPower)
            powered = newPower;
    }

    @Override
    public void writePacketData(DataOutputStream data) throws IOException {
        super.writePacketData(data);

        data.writeBoolean(hasTicket);

        data.writeInt(xSentinel);
        data.writeInt(ySentinel);
        data.writeInt(zSentinel);
    }

    @Override
    public void readPacketData(DataInputStream data) throws IOException {
        super.readPacketData(data);

        boolean tick = data.readBoolean();
        if (hasTicket != tick) {
            hasTicket = tick;
            markBlockForUpdate();
        }

        xSentinel = data.readInt();
        ySentinel = data.readInt();
        zSentinel = data.readInt();

        setupChunks();
    }

    @Override
    public void writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);

        data.setLong("fuel", fuel);

        data.setBoolean("powered", powered);

        data.setInteger("xSentinel", xSentinel);
        data.setInteger("ySentinel", ySentinel);
        data.setInteger("zSentinel", zSentinel);
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);

        if (needsFuel())
            fuel = data.getLong("fuel");

        powered = data.getBoolean("powered");

        xSentinel = data.getInteger("xSentinel");
        ySentinel = data.getInteger("ySentinel");
        zSentinel = data.getInteger("zSentinel");
    }

    @Override
    public float getResistance(Entity exploder) {
        return 60f;
    }

    @Override
    public float getHardness() {
        return 20;
    }

    @Override
    public long getAnchorFuel() {
        return fuel;
    }

    @Override
    public int[] getAccessibleSlotsFromSide(int var1) {
        if (RailcraftConfig.anchorsCanInteractWithPipes())
            return SLOTS;
        return SLOTS_NOACCESS;
    }

    @Override
    public boolean canInsertItem(int i, ItemStack itemstack, int j) {
        return RailcraftConfig.anchorsCanInteractWithPipes();
    }

    @Override
    public boolean canExtractItem(int i, ItemStack itemstack, int j) {
        return false;
    }

}
