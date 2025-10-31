package net.rms.schempaste.util;

import net.minecraft.world.World;
import net.rms.schempaste.api.IWorldUpdateSuppressor;

import java.util.concurrent.atomic.AtomicBoolean;

public class WorldUtils {

    private static final AtomicBoolean GLOBAL_SUPPRESS = new AtomicBoolean(false);

    public static void setShouldPreventBlockUpdates(World world, boolean prevent) {
        if (world instanceof IWorldUpdateSuppressor) {
            ((IWorldUpdateSuppressor) world).schempaste_setPreventUpdates(prevent);
        }
        GLOBAL_SUPPRESS.set(prevent);
    }


    public static boolean shouldPreventBlockUpdates(World world) {
        if (world instanceof IWorldUpdateSuppressor) {
            return ((IWorldUpdateSuppressor) world).schempaste_shouldPreventUpdates();
        }
        return false;
    }

    public static boolean isGlobalSuppressed() {
        return GLOBAL_SUPPRESS.get();
    }
}
