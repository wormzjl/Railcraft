/* 
 * Copyright (c) CovertJaguar, 2014 http://railcraft.info
 * 
 * This code is the property of CovertJaguar
 * and may only be used with explicit written
 * permission unless otherwise specified on the
 * license page at http://railcraft.info/wiki/info:license.
 */
package mods.railcraft.common.blocks.signals;

import cpw.mods.fml.common.Optional;
import ic2.api.item.IBoxable;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import mods.railcraft.api.core.WorldCoordinate;
import mods.railcraft.common.plugins.forge.ItemRegistry;
import mods.railcraft.common.core.RailcraftConfig;
import mods.railcraft.common.items.IActivationBlockingItem;
import mods.railcraft.common.items.ItemRailcraft;
import mods.railcraft.common.plugins.forge.*;
import mods.railcraft.common.util.misc.Game;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;

@Optional.Interface(iface = "ic2.api.item.IBoxable", modid = "IC2")
public class ItemSignalBlockSurveyor extends ItemRailcraft implements IBoxable, IActivationBlockingItem {

    private static Item item;

    public static void registerItem() {
        if (item == null) {
            String tag = "railcraft.tool.surveyor";
            if (RailcraftConfig.isItemEnabled(tag)) {
                item = new ItemSignalBlockSurveyor().setUnlocalizedName(tag);
                ItemRegistry.registerItem(item);

                CraftingPlugin.addShapedRecipe(new ItemStack(item),
                        " C ",
                        "BGB",
                        " R ",
                        'G', Blocks.glass_pane,
                        'C', Items.compass,
                        'B', Blocks.stone_button,
                        'R', Items.redstone);

                ItemRegistry.registerItemStack(tag, new ItemStack(item));

                LootPlugin.addLootWorkshop(new ItemStack(item), 1, 1, "tool.surveyor");
            }
        }
    }

    public static ItemStack getItem() {
        if (item == null)
            return null;
        return new ItemStack(item);
    }

    private ItemSignalBlockSurveyor() {
        setMaxDamage(0);
        setHasSubtypes(true);
        setMaxStackSize(1);

        setCreativeTab(CreativePlugin.RAILCRAFT_TAB);
    }

    @Override
    public boolean onItemUse(ItemStack item, EntityPlayer player, World world, int x, int y, int z, int side, float par8, float par9, float par10) {
//        System.out.println("click");
        TileEntity tile = world.getTileEntity(x, y, z);
        if (tile != null)
            if (tile instanceof ISignalBlockTile) {
//            System.out.println("target found");
                if (Game.isHost(world)) {
                    ISignalBlockTile signalTile = (ISignalBlockTile) tile;
                    SignalBlock signalBlock = signalTile.getSignalBlock();
                    WorldCoordinate pos = null;
                    NBTTagCompound data = item.getTagCompound();
                    if (data != null) {
                        int sDim = data.getInteger("signalDim");
                        int sx = data.getInteger("signalX");
                        int sy = data.getInteger("signalY");
                        int sz = data.getInteger("signalZ");
                        pos = new WorldCoordinate(sDim, sx, sy, sz);
                    }
                    WorldCoordinate track = signalBlock.getTrackLocation();
                    if (track == null)
                        ChatPlugin.sendLocalizedChat(player, "railcraft.gui.surveyor.track", signalTile.getName());
                    else if (pos == null) {
                        ChatPlugin.sendLocalizedChat(player, "railcraft.gui.surveyor.begin");
                        setSignalData(item, tile);
                        signalBlock.startPairing();
                    } else if (x != pos.x || y != pos.y || z != pos.z) {
//                System.out.println("attempt pairing");
                        tile = world.getTileEntity(pos.x, pos.y, pos.z);
                        if (tile != null && tile instanceof ISignalBlockTile) {
                            ISignalBlockTile otherTile = (ISignalBlockTile) tile;
                            SignalBlock otherSignal = otherTile.getSignalBlock();
                            if (otherSignal != signalBlock && signalBlock.createSignalBlock(otherSignal)) {
                                ChatPlugin.sendLocalizedChat(player, "railcraft.gui.surveyor.success");
                                item.setTagCompound(null);
                            } else
                                ChatPlugin.sendLocalizedChat(player, "railcraft.gui.surveyor.invalid");
                        } else if (world.blockExists(pos.x, pos.y, pos.z)) {
                            ChatPlugin.sendLocalizedChat(player, "railcraft.gui.surveyor.lost");
                            signalBlock.endPairing();
                            item.setTagCompound(null);
                        } else
                            ChatPlugin.sendLocalizedChat(player, "railcraft.gui.surveyor.unloaded");
                    } else {
                        ChatPlugin.sendLocalizedChat(player, "railcraft.gui.surveyor.abandon");
                        signalBlock.endPairing();
                        item.setTagCompound(null);
                    }
                }
                return true;
            } else if (Game.isHost(world))
                ChatPlugin.sendLocalizedChat(player, "railcraft.gui.surveyor.wrong");
        return false;
    }

    private void setSignalData(ItemStack item, TileEntity tile) {
        NBTTagCompound data = new NBTTagCompound();
        data.setInteger("signalDim", tile.getWorldObj().provider.dimensionId);
        data.setInteger("signalX", tile.xCoord);
        data.setInteger("signalY", tile.yCoord);
        data.setInteger("signalZ", tile.zCoord);
        item.setTagCompound(data);
    }

    @Override
    public boolean canBeStoredInToolbox(ItemStack itemstack) {
        return true;
    }

}
