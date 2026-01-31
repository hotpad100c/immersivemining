package ml.mypals.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.SheetedDecalTextureGenerator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import ml.mypals.ImmersiveMining;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.BlockDestructionProgress;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.SortedSet;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin {

	@Shadow @Final private Int2ObjectMap<BlockDestructionProgress> destroyingBlocks;

	@Shadow @Final private Long2ObjectMap<SortedSet<BlockDestructionProgress>> destructionProgress;

	@Shadow @Nullable private ClientLevel level;

	@Inject(
			method = "destroyBlockProgress",
			at = @At("HEAD")
	)
	private void onStart(
			int breakerId, BlockPos pos, int progress, CallbackInfo ci
	) {
		if (progress >= 0 && progress < 10) {
			BlockDestructionProgress old =
					this.destroyingBlocks.get(breakerId);

			if (old == null || !old.getPos().equals(pos)) {
				ImmersiveMining.blockBreakStarted(pos,breakerId);
			}
		}
	}
	@Inject(
			method = "destroyBlockProgress",
			at = @At("TAIL")
	)
	private void onEnd(int breakerId, BlockPos pos, int progress, CallbackInfo ci) {
		if (progress < 0 || progress >= 10) {
			ImmersiveMining.blockBreakEnded(pos,breakerId);
		}
	}
	@Inject(
			method = "renderBlockDestroyAnimation",
			at = @At("HEAD"),
			cancellable = true
	)
	private void onRenderDestroy(
			PoseStack poseStack,
			Camera camera,
			MultiBufferSource.BufferSource bufferSource,
			CallbackInfo ci
	) {

		Vec3 vec3 = camera.getPosition();
		double d = vec3.x();
		double e = vec3.y();
		double f = vec3.z();

        for (Long2ObjectMap.Entry<SortedSet<BlockDestructionProgress>> sortedSetEntry : this.destructionProgress.long2ObjectEntrySet()) {
            BlockPos blockPos = BlockPos.of(sortedSetEntry.getLongKey());
            if (!(blockPos.distToCenterSqr(d, e, f) > (double) 1024.0F)) {
                SortedSet<BlockDestructionProgress> sortedSet = sortedSetEntry.getValue();
                if (sortedSet != null && !sortedSet.isEmpty()) {
                    assert this.level != null;
                    ImmersiveMining.renderBlockDestroyAnimation(poseStack,camera,bufferSource,this.level.getBlockState(blockPos),blockPos,this.level.getLightEmission(blockPos),sortedSet.last());
                }
            }
        }

		ci.cancel();
	}

}