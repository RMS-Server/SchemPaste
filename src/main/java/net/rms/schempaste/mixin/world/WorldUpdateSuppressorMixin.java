package net.rms.schempaste.mixin.world;

import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.rms.schempaste.api.IWorldUpdateSuppressor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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

    @Inject(method = "updateNeighborsAlways(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/Block;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void schempaste$skipUpdateNeighborsAlways(BlockPos pos, Block block, CallbackInfo ci) {
        if (schempaste_shouldPreventUpdates()) {
            ci.cancel();
        }
    }

    //#if MC < 12000
//#endif

    @Inject(method = "updateNeighborsExcept(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/Block;Lnet/minecraft/util/math/Direction;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void schempaste$skipUpdateNeighborsExcept(BlockPos pos, Block block, Direction direction, CallbackInfo ci) {
        if (schempaste_shouldPreventUpdates()) {
            ci.cancel();
        }
    }

    @Inject(method = "updateNeighbor(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/Block;Lnet/minecraft/util/math/BlockPos;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void schempaste$skipUpdateNeighbor(BlockPos pos, Block block, BlockPos sourcePos, CallbackInfo ci) {
        if (schempaste_shouldPreventUpdates()) {
            ci.cancel();
        }
    }
}
