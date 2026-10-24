/* Validates warded silverwood shells and preserves intrinsic host node data.
 * Jar drop/place/release bridges omit stale spatial relay and ownership state. */
package com.sanhiruzu.autoturgy.equipment;

import com.leclowndu93150.thaumaturge.api.aspect.*;
import com.leclowndu93150.thaumaturge.api.recipe.ResearchGate;
import com.leclowndu93150.thaumaturge.api.wands.WandAccess;
import com.leclowndu93150.thaumaturge.content.aura.node.*;
import com.leclowndu93150.thaumaturge.content.wands.ItemWand;
import com.leclowndu93150.thaumaturge.content.warding.WardHandler;
import com.leclowndu93150.thaumaturge.registry.*;
import com.sanhiruzu.autoturgy.Content;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

public final class NodeJarring {
  private static final String ADVANCED = "autoturgyAdvancedNodeJar";

  public static void register(IEventBus bus) {
    NeoForge.EVENT_BUS.addListener(NodeJarring::use);
    NeoForge.EVENT_BUS.addListener(NodeJarring::drops);
  }

  public static CompoundTag intrinsic(BlockEntityNode node) {
    CompoundTag full = node.saveWithoutMetadata(node.getLevel().registryAccess()),
        safe = new CompoundTag();
    for (String key : List.of("Type", "Modifier", "Aspects", "AspectsBase", "AspectsBaseOriginal"))
      if (full.contains(key)) safe.put(key, full.get(key).copy());
    return safe;
  }

  public static boolean advanced(BlockEntityNode node) {
    return node.getPersistentData().getBoolean(ADVANCED);
  }

  private static boolean id(Block b, String name) {
    return BuiltInRegistries.BLOCK.getKey(b).toString().equals("thaumaturge:" + name);
  }

  public static boolean valid(ServerLevel l, BlockPos center, Player p) {
    for (BlockPos pos : NodeJarRitual.structurePositions(center)) {
      if (!l.hasChunkAt(pos)
          || !l.mayInteract(p, pos)
          || (WardHandler.isWarded(l, pos) && !WardHandler.canAccess(l, pos, p.getUUID())))
        return false;
      if (pos.getY() == center.getY() + 2) {
        if (!id(l.getBlockState(pos).getBlock(), "log_silverwood") || !WardHandler.isWarded(l, pos))
          return false;
      } else if (!id(l.getBlockState(pos).getBlock(), "warded_glass")) return false;
    }
    return true;
  }

  public static boolean capture(ServerLevel l, BlockPos center, Player p, InteractionHand hand) {
    if (!l.hasChunkAt(center)
        || !l.getWorldBorder().isWithinBounds(center)
        || !l.mayInteract(p, center)
        || p.distanceToSqr(center.getCenter()) > 64
        || (WardHandler.isWarded(l, center) && !WardHandler.canAccess(l, center, p.getUUID())))
      return false;
    ItemStack wand = p.getItemInHand(hand);
    if (!(wand.getItem() instanceof ItemWand w)
        || !w.socketedFocus(wand).isEmpty()
        || !(l.getBlockEntity(center) instanceof BlockEntityNode node)
        || node instanceof BlockEntityJarNode
        || node.isJarring()
        || !valid(l, center, p)
        || !ResearchGate.passes(
            p, new ResearchGate(Content.id("advanced_jarring"), Optional.empty(), false)))
      return false;
    if (node.isEnergized()) {
      p.displayClientMessage(
          Component.translatable("message.autoturgy.energized_capture_refused"), true);
      return false;
    }
    for (var a : TTAspects.PRIMALS) if (WandAccess.getVis(wand, a) < 7000) return false;
    CompoundTag saved = intrinsic(node);
    ItemStack paid = wand;
    for (var a : TTAspects.PRIMALS)
      paid = WandAccess.withVis(paid, a, WandAccess.getVis(paid, a) - 7000);
    l.removeBlockEntity(center);
    if (!l.setBlock(center, TTBlocks.JAR_NODE.get().defaultBlockState(), 3)) {
      l.setBlock(center, TTBlocks.NODE.get().defaultBlockState(), 3);
      if (l.getBlockEntity(center) instanceof BlockEntityNode restore)
        restore.loadWithComponents(saved, l.registryAccess());
      return false;
    }
    if (!(l.getBlockEntity(center) instanceof BlockEntityJarNode jar))
      throw new IllegalStateException("Missing host jar entity");
    jar.loadWithComponents(saved, l.registryAccess());
    jar.getPersistentData().putBoolean(ADVANCED, true);
    jar.setChanged();
    l.sendBlockUpdated(center, jar.getBlockState(), jar.getBlockState(), 3);
    p.setItemInHand(hand, paid);
    for (BlockPos shell : NodeJarRitual.structurePositions(center)) {
      if (WardHandler.isWarded(l, shell)) WardHandler.unward(l, shell, p.getUUID());
      l.setBlock(shell, Blocks.AIR.defaultBlockState(), 3);
    }
    return true;
  }

  public static boolean release(ServerLevel l, BlockPos pos, Player p) {
    if (!l.hasChunkAt(pos)
        || p.distanceToSqr(pos.getCenter()) > 64
        || !(l.getBlockEntity(pos) instanceof BlockEntityJarNode jar)
        || !advanced(jar)
        || !l.mayInteract(p, pos)
        || (WardHandler.isWarded(l, pos) && !WardHandler.canAccess(l, pos, p.getUUID())))
      return false;
    CompoundTag saved = intrinsic(jar);
    l.removeBlockEntity(pos);
    l.setBlock(pos, TTBlocks.NODE.get().defaultBlockState(), 3);
    if (l.getBlockEntity(pos) instanceof BlockEntityNode node) {
      node.loadWithComponents(saved, l.registryAccess());
      node.nodeChange();
      return true;
    }
    throw new IllegalStateException("Missing released node");
  }

  private static void use(PlayerInteractEvent.RightClickBlock e) {
    if (!(e.getLevel() instanceof ServerLevel l)
        || !(e.getItemStack().getItem() instanceof ItemWand w)
        || !w.socketedFocus(e.getItemStack()).isEmpty()) return;
    if (l.getBlockEntity(e.getPos()) instanceof BlockEntityJarNode jar && advanced(jar)) {
      if (release(l, e.getPos(), e.getEntity())) {
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.SUCCESS);
      }
      return;
    }
    if (!id(l.getBlockState(e.getPos()).getBlock(), "log_silverwood")) return;
    for (int x = -1; x <= 1; x++)
      for (int z = -1; z <= 1; z++) {
        BlockPos c = e.getPos().offset(x, -2, z);
        if (!l.hasChunkAt(c)) continue;
        if (l.getBlockEntity(c) instanceof BlockEntityNode n
            && !(n instanceof BlockEntityJarNode)) {
          capture(l, c, e.getEntity(), e.getHand());
          e.setCanceled(true);
          e.setCancellationResult(InteractionResult.SUCCESS);
          return;
        }
      }
  }

  private static void drops(BlockDropsEvent e) {
    if (!(e.getBlockEntity() instanceof BlockEntityJarNode jar) || !advanced(jar)) return;
    CompoundTag data = intrinsic(jar);
    data.putString("id", BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(jar.getType()).toString());
    CompoundTag persistent = new CompoundTag();
    persistent.putBoolean(ADVANCED, true);
    data.put("NeoForgeData", persistent);
    for (var entity : e.getDrops())
      if (entity.getItem().is(TTBlocks.JAR_NODE.get().asItem()))
        entity.getItem().set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(data));
  }

  private NodeJarring() {}
}
