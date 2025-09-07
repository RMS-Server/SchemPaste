package net.rms.schempaste.mixin.world;

import net.minecraft.world.World;
import net.rms.schempaste.api.IWorldUpdateSuppressor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(World.class)
public abstract class WorldUpdateSuppressorMixin implements IWorldUpdateSuppressor {
    @Unique
    private boolean schempaste$preventUpdates;
    
    @Override
    public boolean schempaste_shouldPreventUpdates() {
        return schempaste$preventUpdates;
    }
    
    @Override
    public void schempaste_setPreventUpdates(boolean prevent) {
        this.schempaste$preventUpdates = prevent;
    }
}
