package net.rms.schempaste.util;

import net.minecraft.world.World;
import net.rms.schempaste.api.IWorldUpdateSuppressor;

public class WorldUtils {


    public static void setShouldPreventBlockUpdates(World world, boolean prevent) {
        if (world instanceof IWorldUpdateSuppressor) {
            ((IWorldUpdateSuppressor) world).schempaste_setPreventUpdates(prevent);
        }
    }


    public static boolean shouldPreventBlockUpdates(World world) {
        if (world instanceof IWorldUpdateSuppressor) {
            return ((IWorldUpdateSuppressor) world).schempaste_shouldPreventUpdates();
        }
        return false;
    }
}