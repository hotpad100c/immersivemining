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
import net.minecraft.core.Direction;
import net.minecraft.server.level.BlockDestructionProgress;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
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
        assert mc.level != null;
		boolean diggingThis= blockShakeStateMap.containsKey(pos.immutable());
        BlockState state = mc.level.getBlockState(pos);
		if(state.getBlock() instanceof BedBlock){
			boolean diggingNeighbor = blockShakeStateMap.containsKey(pos.relative(BedBlock.getConnectedDirection(state)));
			return !(diggingNeighbor || diggingThis);
		}
		if(state.getBlock() instanceof DoorBlock){
			boolean diggingNeighbor = blockShakeStateMap.containsKey(pos.relative(state.getValue(DoorBlock.HALF).getDirectionToOther()));
			return !(diggingNeighbor || diggingThis);
		}
		if(state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE){
			boolean diggingNeighbor = blockShakeStateMap.containsKey(pos.relative(ChestBlock.getConnectedDirection(state)));
			return !(diggingNeighbor || diggingThis);
		}

		if (state.getBlock() instanceof PistonBaseBlock) {
			BlockPos posHead = pos.relative(state.getValue(PistonBaseBlock.FACING));
			BlockState head = mc.level.getBlockState(posHead);
			if(head.getBlock() instanceof PistonHeadBlock && head.getValue(PistonHeadBlock.FACING) == state.getValue(PistonBaseBlock.FACING)){
				boolean diggingNeighbor = blockShakeStateMap.containsKey(posHead);
				return !(diggingNeighbor || diggingThis);
			}
		}
		if(state.getBlock() instanceof PistonHeadBlock){
			BlockPos posBase = pos.relative(state.getValue(PistonBaseBlock.FACING).getOpposite());
			BlockState base = mc.level.getBlockState(posBase);
			if(base.getBlock() instanceof PistonBaseBlock && base.getValue(PistonBaseBlock.FACING) == state.getValue(PistonHeadBlock.FACING)){
				boolean diggingNeighbor = blockShakeStateMap.containsKey(posBase);
				return !(diggingNeighbor || diggingThis);
			}
		}

		return !diggingThis;
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
		minecraft.levelRenderer.setBlocksDirty(blockPos.getX()-3, blockPos.getY()-3, blockPos.getZ()-3, blockPos.getX()+3, blockPos.getY()+3, blockPos.getZ()+3);

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

		Vec3 pivotPoint = new Vec3(0.5, 0.5, 0.5);

		if (blockState.getBlock() instanceof DoorBlock) {

			pivotPoint = pivotPoint.relative(blockState.getValue(DoorBlock.HALF).getDirectionToOther(), 0.5);
		}
		else if (blockState.getBlock() instanceof ChestBlock && blockState.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {

			pivotPoint = pivotPoint.relative(ChestBlock.getConnectedDirection(blockState), 0.5);
		}
		else if (blockState.getBlock() instanceof BedBlock) {

			pivotPoint = pivotPoint.relative(BedBlock.getConnectedDirection(blockState), 0.5);
		}
		else if (blockState.getBlock() instanceof PistonBaseBlock) {
			BlockPos posHead = pos.relative(blockState.getValue(PistonBaseBlock.FACING));
			BlockState head = mc.level.getBlockState(posHead);
			if(head.getBlock() instanceof PistonHeadBlock && head.getValue(PistonHeadBlock.FACING) == blockState.getValue(PistonBaseBlock.FACING)){
				pivotPoint = pivotPoint.relative(blockState.getValue(PistonBaseBlock.FACING), 0.5);
			}
		}
		else if(blockState.getBlock() instanceof PistonHeadBlock){
			BlockPos posBase = pos.relative(blockState.getValue(PistonBaseBlock.FACING).getOpposite());
			BlockState base = mc.level.getBlockState(posBase);
			if(base.getBlock() instanceof PistonBaseBlock && base.getValue(PistonBaseBlock.FACING) == blockState.getValue(PistonHeadBlock.FACING)){
				pivotPoint = pivotPoint.relative(blockState.getValue(PistonBaseBlock.FACING), 0.5);
			}
		}


		poseStack.translate(pivotPoint);

		float delta = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Vector3d jitter = blockShakeState.getJitter(delta);
		float size = blockShakeState.getSize(delta);
		poseStack.mulPose(Axis.XP.rotationDegrees((float) jitter.x()));
		poseStack.mulPose(Axis.YP.rotationDegrees((float) jitter.y()));
		poseStack.mulPose(Axis.ZP.rotationDegrees((float) jitter.z()));
		poseStack.scale(size, size, size);

		poseStack.translate(pivotPoint.reverse());

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

		VertexConsumer vertexConsumer = new SheetedDecalTextureGenerator(multiBufferSource.getBuffer(ModelBakery.DESTROY_TYPES.get(blockDestructionProgress.getProgress())), poseStack.last(), 1.0F);
		renderBatched(dispatcher, blockState,pos,mc.level,poseStack,vertexConsumer,false,mc.level.getRandom());

		if (renderShape == RenderShape.MODEL && !(blockState.getBlock() instanceof ChestBlock) && !(blockState.getBlock() instanceof BedBlock)) {
			renderBatched(dispatcher, blockState,pos,mc.level,poseStack,multiBufferSource.getBuffer(RenderType.translucentMovingBlock()),false,mc.level.getRandom());
			((BlockRendererAccessor)dispatcher).getSpecialBlockModelRenderer().get().renderByBlock(blockState.getBlock(), ItemDisplayContext.NONE, poseStack, multiBufferSource, LightTexture.FULL_BLOCK, OverlayTexture.NO_OVERLAY);

			if (blockState.getBlock() instanceof DoorBlock) {
				BlockPos pos1 = pos.relative(blockState.getValue(DoorBlock.HALF).getDirectionToOther());
				BlockState blockState1 = mc.level.getBlockState(pos1);
				float dir = blockState.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER ? 1 : -1;
				poseStack.translate(0,dir,0);
				renderBatched(dispatcher, blockState1,pos1,mc.level,poseStack,multiBufferSource.getBuffer(ItemBlockRenderTypes.getChunkRenderType(blockState1)),false,mc.level.getRandom());
				((BlockRendererAccessor)dispatcher).getSpecialBlockModelRenderer().get().renderByBlock(blockState1.getBlock(), ItemDisplayContext.NONE, poseStack, multiBufferSource, LightTexture.FULL_BLOCK, OverlayTexture.NO_OVERLAY);
			}else if (blockState.getBlock() instanceof PistonBaseBlock) {
				Direction facing = blockState.getValue(PistonBaseBlock.FACING);
				BlockPos posHead = pos.relative(facing);
				BlockState head = mc.level.getBlockState(posHead);

				if (head.getBlock() instanceof PistonHeadBlock
						&& head.getValue(PistonHeadBlock.FACING) == facing) {

					poseStack.translate(
							facing.getStepX(),
							facing.getStepY(),
							facing.getStepZ()
					);

					renderBatched(dispatcher, head, posHead, mc.level, poseStack,
							multiBufferSource.getBuffer(ItemBlockRenderTypes.getChunkRenderType(head)),
							false, mc.level.getRandom());

					((BlockRendererAccessor) dispatcher).getSpecialBlockModelRenderer().get()
							.renderByBlock(head.getBlock(), ItemDisplayContext.NONE,
									poseStack, multiBufferSource,
									LightTexture.FULL_BLOCK, OverlayTexture.NO_OVERLAY);
				}
			}
			else if (blockState.getBlock() instanceof PistonHeadBlock) {
				Direction facing = blockState.getValue(PistonHeadBlock.FACING);
				BlockPos posBase = pos.relative(facing.getOpposite());
				BlockState base = mc.level.getBlockState(posBase);

				if (base.getBlock() instanceof PistonBaseBlock
						&& base.getValue(PistonBaseBlock.FACING) == facing) {

					poseStack.translate(
							-facing.getStepX(),
							-facing.getStepY(),
							-facing.getStepZ()
					);

					renderBatched(dispatcher, base, posBase, mc.level, poseStack,
							multiBufferSource.getBuffer(ItemBlockRenderTypes.getChunkRenderType(base)),
							false, mc.level.getRandom());

					((BlockRendererAccessor) dispatcher).getSpecialBlockModelRenderer().get()
							.renderByBlock(base.getBlock(), ItemDisplayContext.NONE,
									poseStack, multiBufferSource,
									LightTexture.FULL_BLOCK, OverlayTexture.NO_OVERLAY);
				}
			}

		}
		if(blockState.hasBlockEntity()){
			BlockEntity blockEntity = mc.level.getBlockEntity(pos);
			BlockEntityRenderDispatcher blockEntityRenderDispatcher = mc.getBlockEntityRenderDispatcher();
			blockEntityRenderDispatcher.render(blockEntity,mc.getDeltaTracker().getGameTimeDeltaTicks(),poseStack,multiBufferSource);

			if (blockState.getBlock() instanceof ChestBlock && blockState.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
				BlockPos pos1 = pos.relative((ChestBlock.getConnectedDirection(blockState)));
				poseStack.translate(pos1.getX() - pos.getX(),0,pos1.getZ() - pos.getZ());
				BlockEntity blockEntity1 = mc.level.getBlockEntity(pos1);
				blockEntityRenderDispatcher.render(blockEntity1,mc.getDeltaTracker().getGameTimeDeltaTicks(),poseStack,multiBufferSource);
			}
			else if (blockState.getBlock() instanceof BedBlock) {
				BlockPos pos1 = pos.relative((BedBlock.getConnectedDirection(blockState)));
				poseStack.translate(pos1.getX() - pos.getX(),0,pos1.getZ() - pos.getZ());
				BlockEntity blockEntity1 = mc.level.getBlockEntity(pos1);
				blockEntityRenderDispatcher.render(blockEntity1,mc.getDeltaTracker().getGameTimeDeltaTicks(),poseStack,multiBufferSource);
			}

		}

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
			float jitter =  (float) Math.sin(progress * Math.PI * time);
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