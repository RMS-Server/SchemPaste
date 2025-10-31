package net.rms.schempaste.mixin.world;

//#if MC < 12000
import net.minecraft.world.WorldAccess;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(WorldAccess.class)
public interface WorldAccessSchedulerMixin {
}
//#else
//$$ import net.minecraft.block.Block;
//$$ import net.minecraft.fluid.Fluid;
//$$ import net.minecraft.util.math.BlockPos;
//$$ import net.minecraft.world.tick.TickPriority;
//$$ import net.minecraft.world.WorldAccess;
//$$ import net.rms.schempaste.util.WorldUtils;
//$$ import org.spongepowered.asm.mixin.Mixin;
//$$ import org.spongepowered.asm.mixin.injection.At;
//$$ import org.spongepowered.asm.mixin.injection.Inject;
//$$ import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//$$
//$$ @Mixin(WorldAccess.class)
//$$ public interface WorldAccessSchedulerMixin {
//$$
//$$     @Inject(method = "scheduleBlockTick(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/Block;I)V",
//$$             at = @At("HEAD"), cancellable = true, require = 0)
//$$     private void schempaste$skipBlockTick(BlockPos pos, Block block, int delay, CallbackInfo ci) {
//$$         if (WorldUtils.isGlobalSuppressed()) {
//$$             ci.cancel();
//$$         }
//$$     }
//$$
//$$     @Inject(method = "scheduleBlockTick(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/Block;ILnet/minecraft/world/tick/TickPriority;)V",
//$$             at = @At("HEAD"), cancellable = true, require = 0)
//$$     private void schempaste$skipBlockTickWithPriority(BlockPos pos, Block block, int delay, TickPriority priority, CallbackInfo ci) {
//$$         if (WorldUtils.isGlobalSuppressed()) {
//$$             ci.cancel();
//$$         }
//$$     }
//$$
//$$     @Inject(method = "scheduleFluidTick(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/fluid/Fluid;I)V",
//$$             at = @At("HEAD"), cancellable = true, require = 0)
//$$     private void schempaste$skipFluidTick(BlockPos pos, Fluid fluid, int delay, CallbackInfo ci) {
//$$         if (WorldUtils.isGlobalSuppressed()) {
//$$             ci.cancel();
//$$         }
//$$     }
//$$
//$$     @Inject(method = "scheduleFluidTick(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/fluid/Fluid;ILnet/minecraft/world/tick/TickPriority;)V",
//$$             at = @At("HEAD"), cancellable = true, require = 0)
//$$     private void schempaste$skipFluidTickWithPriority(BlockPos pos, Fluid fluid, int delay, TickPriority priority, CallbackInfo ci) {
//$$         if (WorldUtils.isGlobalSuppressed()) {
//$$             ci.cancel();
//$$         }
//$$     }
//$$ }
//#endif
