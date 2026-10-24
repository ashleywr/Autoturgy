/* Validates the quartz/pylon sky column and charges equipped host harness jars.
 * Transactional Aer and owned Potentia fund only conserved real item transfers. */
package com.sanhiruzu.autoturgy.constructs;

import com.leclowndu93150.thaumaturge.api.aspect.*;
import com.leclowndu93150.thaumaturge.api.aura.VisRelayHelper;
import com.leclowndu93150.thaumaturge.api.essentia.*;
import com.leclowndu93150.thaumaturge.api.transaction.Transaction;
import com.leclowndu93150.thaumaturge.content.equipment.hover.ThaumostaticHarnessItem;
import com.sanhiruzu.autoturgy.*;
import com.sanhiruzu.autoturgy.essentia.EssentiaModule;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.*;

public final class SpireModule {
  public static DeferredBlock<SpireBlock> CONTROLLER;
  public static DeferredBlock<Block> PYLON;
  public static DeferredHolder<BlockEntityType<?>, BlockEntityType<SpireEntity>> TYPE;

  public static void register(IEventBus bus) {
    CONTROLLER = Content.block("thaumostatic_spire", SpireBlock::new);
    PYLON =
        Content.block(
            "thaumostatic_pylon",
            () -> new Block(Block.Properties.of().strength(3).requiresCorrectToolForDrops()));
    TYPE =
        Content.ENTITIES.register(
            "thaumostatic_spire",
            () -> BlockEntityType.Builder.of(SpireEntity::new, CONTROLLER.get()).build(null));
    bus.addListener((RegisterCapabilitiesEvent e) -> ports(e, TYPE.get()));
  }

  public static <T extends ConstructEntity> void ports(
      RegisterCapabilitiesEvent e, BlockEntityType<T> type) {
    e.registerBlockEntity(EssentiaCapabilities.STORAGE, type, (be, side) -> be.port(side));
    e.registerBlockEntity(EssentiaCapabilities.CONDUIT, type, (be, side) -> be.port(side));
    e.registerBlockEntity(EssentiaCapabilities.TRANSPORT, type, (be, side) -> be.port(side));
    e.registerBlockEntity(
        EssentiaCapabilities.ASPECT_QUERY,
        type,
        (be, side) -> () -> ((IEssentiaStorage) be.port(side)).contents());
  }

  public record Structure(boolean valid, int pylons, BlockPos bottom, BlockPos cap) {}

  private static boolean column(Level l, BlockPos p) {
    return l.hasChunkAt(p)
        && (l.getBlockState(p).is(CONTROLLER.get()) || l.getBlockState(p).is(PYLON.get()));
  }

  private static boolean skyOpen(Level l, BlockPos cap) {
    if (!l.canSeeSky(cap.above())) return false;
    // Light propagation can lag a placement; inspect the bounded existing vertical column too.
    for (int y = cap.getY() + 1; y < l.getMaxBuildHeight(); y++) {
      BlockPos p = new BlockPos(cap.getX(), y, cap.getZ());
      if (l.getBlockState(p).getLightBlock(l, p) > 0) return false;
    }
    return true;
  }

  public static Structure structure(Level l, BlockPos controller) {
    BlockPos bottom = controller;
    for (int i = 0; i < 7 && column(l, bottom.below()); i++) bottom = bottom.below();
    int pylons = 0, controllers = 0;
    BlockPos top = bottom;
    for (int i = 0; i < 8 && column(l, top); i++, top = top.above()) {
      if (l.getBlockState(top).is(PYLON.get())) pylons++;
      else controllers++;
    }
    boolean valid =
        controllers == 1
            && pylons >= 2
            && pylons <= 6
            && l.hasChunkAt(bottom.below())
            && l.getBlockState(bottom.below()).is(Blocks.CHISELED_QUARTZ_BLOCK)
            && l.hasChunkAt(top)
            && l.getBlockState(top).is(EssentiaModule.host("metal_thaumium"))
            && l.isEmptyBlock(top.above())
            && skyOpen(l, top);
    return new Structure(valid, pylons, bottom, top);
  }

  public static final class SpireBlock extends ConstructEntity.ConstructBlock {
    public BlockEntity newBlockEntity(BlockPos p, BlockState s) {
      return new SpireEntity(p, s);
    }
  }

  public static final class SpireEntity extends ConstructEntity {
    private int playerCursor;

    public SpireEntity(BlockPos p, BlockState s) {
      super(TYPE.get(), p, s);
    }

    public String kind() {
      return "thaumostatic_spire";
    }

    public String research() {
      return "spires";
    }

    public List<ResourceKey<IAspect>> fuels() {
      return List.of(TTAspects.POTENTIA, TTAspects.AER, TTAspects.VOLATUS);
    }

    public ResourceKey<IAspect> activationAspect() {
      return TTAspects.AER;
    }

    public int activationCost() {
      return 100;
    }

    public boolean validStart() {
      boolean ok = structure(level, worldPosition).valid();
      failure = ok ? "ready" : "structure";
      return ok;
    }

    public int radius() {
      return radius(structure(level, worldPosition));
    }

    private int radius(Structure s) {
      return s.valid() ? 8 + 2 * s.pylons() + (amount(TTAspects.AER) > 0 ? 8 : 0) : 0;
    }

    public int charge(Player player) {
      if (amount(TTAspects.POTENTIA) <= 0) return 0;
      return charge(player, structure(level, worldPosition));
    }

    private int charge(Player player, Structure s) {
      if (!active || !s.valid() || player.level() != level) return 0;
      double dx = player.getX() - worldPosition.getX() - .5,
          dz = player.getZ() - worldPosition.getZ() - .5;
      int range = radius(s);
      if (dx * dx + dz * dz > range * range) return 0;
      ItemStack harness = player.getItemBySlot(EquipmentSlot.CHEST);
      if (!(harness.getItem() instanceof ThaumostaticHarnessItem)) return 0;
      ItemStack jar = ThaumostaticHarnessItem.getJar(harness);
      var storage = jar.getCapability(EssentiaCapabilities.ITEM_STORAGE);
      var potentia = Aspects.resolve(level, TTAspects.POTENTIA);
      if (storage == null
          || !(jar.getItem() instanceof com.leclowndu93150.thaumaturge.content.essentia.jar.JarItem)
          || !storage.canInsert(potentia)) return 0;
      boolean air = amount(TTAspects.AER) > 0, flight = amount(TTAspects.VOLATUS) > 0;
      int n =
          Math.min(
              Math.min(amount(TTAspects.POTENTIA), s.pylons() * (flight ? 2 : 1)),
              storage.capacity(potentia) - storage.contents().amountOf(potentia));
      if (n <= 0) return 0;
      try (Transaction tx = Transaction.openRoot()) {
        if (VisRelayHelper.drainCentivis((ServerLevel) level, worldPosition, TTAspects.AER, n, tx)
            != n) {
          failure = "relay_vis";
          return 0;
        }
        var moved = storage.insert(potentia, n);
        if (moved.amountMoved() != n) return 0;
        Map<ResourceKey<IAspect>, Integer> cost = new LinkedHashMap<>();
        cost.put(TTAspects.POTENTIA, n);
        if (air) cost.put(TTAspects.AER, 1);
        if (flight) cost.put(TTAspects.VOLATUS, 1);
        ThaumostaticHarnessItem.setJar(harness, moved.resultingStack());
        player.setItemSlot(EquipmentSlot.CHEST, harness);
        debit(cost);
        tx.commit();
        operations += n;
        failure = "ready";
        return n;
      }
    }

    public void serverTick() {
      intake();
      if (level.getGameTime() % 20 != 0) return;
      Structure s = structure(level, worldPosition);
      if (active && !s.valid()) {
        cancel();
        failure = "structure";
      }
      if (active && amount(TTAspects.POTENTIA) > 0 && level instanceof ServerLevel server) {
        var players = server.players();
        int count = players.size(), budget = Math.min(64, count);
        for (int i = 0; i < budget && amount(TTAspects.POTENTIA) > 0; i++)
          charge(players.get(Math.floorMod(playerCursor + i, count)), s);
        if (count > 0) playerCursor = Math.floorMod(playerCursor + budget, count);
      }
      settings[9] = radius(s);
      settings[10] = s.valid() ? s.pylons() : 0;
      status();
    }
  }

  private SpireModule() {}
}
