/* Registers luminous amber masonry as usable survival building materials.
 * Original recipes and research provide the paid progression in data resources. */
package com.sanhiruzu.autoturgy.equipment;

import com.sanhiruzu.autoturgy.Content;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;

public final class AuxiliaryContent {
  public static DeferredBlock<Block> AMBER, BRICKS;

  public static void register() {
    AMBER =
        Content.block(
            "glow_amber",
            () -> new Block(BlockBehaviour.Properties.of().strength(1.5F).lightLevel(s -> 15)));
    BRICKS =
        Content.block(
            "glow_amber_bricks",
            () -> new Block(BlockBehaviour.Properties.of().strength(1.5F).lightLevel(s -> 15)));
  }

  private AuxiliaryContent() {}
}
