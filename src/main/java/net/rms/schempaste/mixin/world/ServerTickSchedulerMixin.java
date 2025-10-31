package net.rms.schempaste.mixin.world;

//#if MC < 12000
import net.minecraft.server.world.ServerTickScheduler;
import net.minecraft.world.ScheduledTick;
import net.rms.schempaste.util.WorldUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerTickScheduler.class)
public abstract class ServerTickSchedulerMixin<T> {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, require = 0)
    private void schempaste$stopTick(CallbackInfo ci) {
        if (WorldUtils.isGlobalSuppressed()) {
            ci.cancel();
        }
    }

    @Inject(method = "addScheduledTick", at = @At("HEAD"), cancellable = true, require = 0)
    private void schempaste$dropScheduledTick(ScheduledTick<T> tick, CallbackInfo ci) {
        if (WorldUtils.isGlobalSuppressed()) {
            ci.cancel();
        }
    }
}
//#else
//$$ import net.minecraft.util.math.BlockPos;
//$$ import net.minecraft.world.tick.OrderedTick;
//$$ import net.minecraft.world.tick.WorldTickScheduler;
//$$ import net.rms.schempaste.util.WorldUtils;
//$$ import org.spongepowered.asm.mixin.Mixin;
//$$ import org.spongepowered.asm.mixin.injection.At;
//$$ import org.spongepowered.asm.mixin.injection.Inject;
//$$ import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//$$ import java.util.function.BiConsumer;
//$$
//$$ @Mixin(WorldTickScheduler.class)
//$$ public abstract class ServerTickSchedulerMixin<T> {
//$$
//$$     @Inject(method = "tick(JILjava/util/function/BiConsumer;)V", at = @At("HEAD"), cancellable = true, require = 0)
//$$     private void schempaste$stopTick(long time, int maxTicks, BiConsumer<BlockPos, T> tickConsumer, CallbackInfo ci) {
//$$         if (WorldUtils.isGlobalSuppressed()) {
//$$             ci.cancel();
//$$         }
//$$     }
//$$
//$$     @Inject(method = "schedule(Lnet/minecraft/world/tick/OrderedTick;)V", at = @At("HEAD"), cancellable = true, require = 0)
//$$     private void schempaste$dropScheduledTick(OrderedTick<T> orderedTick, CallbackInfo ci) {
//$$         if (WorldUtils.isGlobalSuppressed()) {
//$$             ci.cancel();
//$$         }
//$$     }
//$$
//$$ }
//#endif
