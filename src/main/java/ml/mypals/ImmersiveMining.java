package ml.mypals;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.SheetedDecalTextureGenerator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import ml.mypals.mixin.BlockRendererAccessor;
import ml.mypals.mixin.LevelRendererAccessor;
import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.BlockDestructionProgress;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.BeaconBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Math;
import org.joml.Vector3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class ImmersiveMining implements ModInitializer {
	public static final String MOD_ID = "immersivemining";
	public static Map<BlockPos, BlockShakeState> blockShakeStateMap = new HashMap<>();
	@Override
	public void onInitialize() {

		ClientTickEvents.END_WORLD_TICK.register(clientLevel -> {
			LevelRendererAccessor levelRenderer =
					(LevelRendererAccessor) Minecraft.getInstance().levelRenderer;

			Collection<BlockDestructionProgress> destroying =
					levelRenderer.getDestroyingBlocks().values();

			Set<BlockPos> active = new ObjectOpenHashSet<>();
			for (BlockDestructionProgress p : destroying) {
				active.add(p.getPos());
			}

			blockShakeStateMap.entrySet().removeIf(e ->{
				if(!active.contains(e.getKey())){
					refreshChunkByPos(e.getKey());
					return true;
				}
			for (BlockDestructionProgress p : destroying) {
				BlockPos pos = p.getPos();
				BlockShakeState s = blockShakeStateMap.computeIfAbsent(
						pos.immutable(), k -> new BlockShakeState()
				);
				s.update(p.getProgress());
			}

			return false;
			});
		});

	}
	public static boolean shouldRenderBlock(BlockPos pos) {
		Minecraft mc = Minecraft.getInstance();
		return !blockShakeStateMap.containsKey(pos.immutable());
	}
	public static void blockBreakStarted(BlockPos pos, int breakId){
		blockShakeStateMap.put(pos.immutable(),new BlockShakeState());
		refreshChunkByPos(pos);
	}
	public static void blockBreakEnded(BlockPos pos, int breakId){
		refreshChunkByPos(pos);
	}
	public static void refreshChunkByPos(BlockPos blockPos){
		Minecraft minecraft = Minecraft.getInstance();
		minecraft.levelRenderer.setBlocksDirty(blockPos.getX(), blockPos.getY(), blockPos.getZ(), blockPos.getX(), blockPos.getY(), blockPos.getZ());

	}
	public static void renderBlockDestroyAnimation(
			PoseStack poseStack,
			Camera camera,
			MultiBufferSource.BufferSource bufferSource,
			BlockState blockState,
			BlockPos pos,
			int light,
			BlockDestructionProgress blockDestructionProgress
	) {
		Minecraft mc = Minecraft.getInstance();
		assert mc.level != null;

		BlockShakeState blockShakeState = blockShakeStateMap.get(blockDestructionProgress.getPos().immutable());
		if(blockShakeState == null) return;

		poseStack.pushPose();
		Vec3 cam = camera.getPosition();
		poseStack.translate(
				pos.getX() - cam.x,
				pos.getY() - cam.y,
				pos.getZ() - cam.z
		);
		poseStack.translate(0.5, 0.5, 0.5);

		float delta = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Vector3d jitter = blockShakeState.getJitter(delta);
		float size = blockShakeState.getSize(delta);
		poseStack.mulPose(Axis.XP.rotationDegrees((float) jitter.x()));
		poseStack.mulPose(Axis.YP.rotationDegrees((float) jitter.y()));
		poseStack.mulPose(Axis.ZP.rotationDegrees((float) jitter.z()));
		poseStack.scale(size, size, size);

		poseStack.translate(-0.5, -0.5, -0.5);

		renderBreakingBlock(mc,poseStack,camera,bufferSource,blockState,pos,light,blockDestructionProgress);

		poseStack.popPose();
	}
	public static void renderBreakingBlock(
			Minecraft mc,
			PoseStack poseStack,
			Camera camera,
			MultiBufferSource.BufferSource bufferSource,
			BlockState blockState,
			BlockPos pos,
			int light,
			BlockDestructionProgress blockDestructionProgress
	) {

        assert mc.level != null;
        renderSingleBlock(
				mc,
				blockState,
				poseStack,
				bufferSource,
				pos,
				LightTexture.FULL_BLOCK,
				blockDestructionProgress
				);
	}
	public static void renderSingleBlock(
            Minecraft mc,
            BlockState blockState,
            PoseStack poseStack,
            MultiBufferSource multiBufferSource,
            BlockPos pos,
            int i,
			BlockDestructionProgress blockDestructionProgress) {
		assert mc.level != null;
		RenderShape renderShape = blockState.getRenderShape();
		BlockRenderDispatcher dispatcher = mc.getBlockRenderer();

		if (renderShape == RenderShape.MODEL && !(blockState.getBlock() instanceof ChestBlock) && !(blockState.getBlock() instanceof BedBlock)) {
			renderBatched(dispatcher, blockState,pos,mc.level,poseStack,multiBufferSource.getBuffer(ItemBlockRenderTypes.getRenderType(blockState)),false,mc.level.getRandom());
			((BlockRendererAccessor)dispatcher).getSpecialBlockModelRenderer().get().renderByBlock(blockState.getBlock(), ItemDisplayContext.NONE, poseStack, multiBufferSource, LightTexture.FULL_BLOCK, OverlayTexture.NO_OVERLAY);
		}
		if(blockState.hasBlockEntity()){
			BlockEntity blockEntity = mc.level.getBlockEntity(pos);
			BlockEntityRenderDispatcher blockEntityRenderDispatcher = mc.getBlockEntityRenderDispatcher();
			blockEntityRenderDispatcher.render(blockEntity,mc.getDeltaTracker().getGameTimeDeltaTicks(),poseStack,multiBufferSource);
		}
		VertexConsumer vertexConsumer = new SheetedDecalTextureGenerator(multiBufferSource.getBuffer(ModelBakery.DESTROY_TYPES.get(blockDestructionProgress.getProgress())), poseStack.last(), 1.0F);
		renderBatched(dispatcher, blockState,pos,mc.level,poseStack,vertexConsumer,false,mc.level.getRandom());

	}
	public static void renderBatched(BlockRenderDispatcher dispatcher, BlockState blockState, BlockPos blockPos, BlockAndTintGetter blockAndTintGetter, PoseStack poseStack, VertexConsumer vertexConsumer, boolean bl, RandomSource randomSource) {
		try {
			dispatcher.getModelRenderer().tesselateBlock(blockAndTintGetter, dispatcher.getBlockModel(blockState), blockState, blockPos, poseStack, vertexConsumer, bl, randomSource, blockState.getSeed(blockPos), OverlayTexture.NO_OVERLAY);
		} catch (Throwable throwable) {
			CrashReport crashReport = CrashReport.forThrowable(throwable, "Tesselating block in world");
			CrashReportCategory crashReportCategory = crashReport.addCategory("Block being tesselated");
			CrashReportCategory.populateBlockDetails(crashReportCategory, blockAndTintGetter, blockPos, blockState);
			throw new ReportedException(crashReport);
		}
	}
	public static class BlockShakeState {
		public float lastSize;
		public float currentSize;
		public float time;
		public float progress;
		public Vector3d lastJitter = new Vector3d();
		public Vector3d currentJitter = new Vector3d();
		public int dirX, dirY, dirZ;
		public BlockShakeState(){
            assert Minecraft.getInstance().level != null;
            RandomSource randomSource = Minecraft.getInstance().level.getRandom();
			this.time = 0;
			this.dirX = randomSource.nextBoolean()?1:-1;
			this.dirY = randomSource.nextBoolean()?1:-1;
			this.dirZ = randomSource.nextBoolean()?1:-1;
			this.lastSize = 1;
			this.currentSize = 1;
			this.lastJitter = new Vector3d();
			this.currentJitter = new Vector3d();
		}
		public void update(float progress){
			this.progress = progress;
			if(progress != 0 && time < progress){
				this.time += (progress - time)*0.1f;
			}
			this.lastJitter = this.currentJitter;
			float jitter = Mth.sin(this.time) * this.time;
            assert Minecraft.getInstance().level != null;
            RandomSource randomSource = Minecraft.getInstance().level.getRandom();
			dirX = randomSource.nextBoolean()?dirX:-dirX;
			dirY = randomSource.nextBoolean()?dirY:-dirY;
			dirZ = randomSource.nextBoolean()?dirZ:-dirZ;
			this.currentJitter.x = jitter*dirX;
			this.currentJitter.y = jitter*dirY;
			this.currentJitter.z = jitter*dirZ;

			lastSize = currentSize;
			currentSize = (float) (1.0-Mth.clamp(this.time / 100f, 0, 0.1));
		}

		public Vector3d getJitter(double delta) {
			return lerpJitter(lastJitter,currentJitter,delta);
		}
		public float getSize(double delta) {
			return (float) Math.lerp(lastSize, currentSize, delta);
		}
		private static Vector3d lerpJitter(Vector3d a, Vector3d b, double t) {
			return new Vector3d(
					Mth.lerp(t, a.x, b.x),
					Mth.lerp(t, a.y, b.y),
					Mth.lerp(t, a.z, b.z)
			);
		}

	}
}