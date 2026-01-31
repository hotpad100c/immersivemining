package ml.mypals.mixin;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SpecialBlockModelRenderer;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.server.level.BlockDestructionProgress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.function.Supplier;

@Mixin(BlockRenderDispatcher.class)
public interface BlockRendererAccessor {
    @Accessor
    BlockColors getBlockColors();
    @Accessor
    Supplier<SpecialBlockModelRenderer> getSpecialBlockModelRenderer();
}
