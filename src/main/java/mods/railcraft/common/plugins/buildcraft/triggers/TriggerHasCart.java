package mods.railcraft.common.plugins.buildcraft.triggers;

import buildcraft.api.gates.ITriggerParameter;
import net.minecraft.tileentity.TileEntity;
import mods.railcraft.common.plugins.forge.LocalizationPlugin;
import net.minecraftforge.common.util.ForgeDirection;

/**
 *
 * @author CovertJaguar <http://www.railcraft.info>
 */
public class TriggerHasCart extends Trigger {

    @Override
    public boolean isTriggerActive(ForgeDirection side, TileEntity tile, ITriggerParameter parameter) {
        if (tile instanceof IHasCart) {
            return ((IHasCart) tile).hasMinecart();
        }
        return false;
    }
}
