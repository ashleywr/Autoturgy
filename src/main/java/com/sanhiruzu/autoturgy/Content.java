/* Shared registration helpers for independently implemented devices.
 * Keeps creative ordering and registry ownership out of feature behavior. */
package com.sanhiruzu.autoturgy;

import java.util.*;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;

public final class Content {
  public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Autoturgy.ID);
  public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Autoturgy.ID);
  public static final DeferredRegister<BlockEntityType<?>> ENTITIES =
      DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Autoturgy.ID);
  public static final DeferredRegister<MenuType<?>> MENUS =
      DeferredRegister.create(Registries.MENU, Autoturgy.ID);
  public static final DeferredRegister<CreativeModeTab> TABS =
      DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Autoturgy.ID);
  public static final List<Supplier<? extends Item>> ORDER = new ArrayList<>();

  public static ResourceLocation id(String path) {
    return ResourceLocation.fromNamespaceAndPath(Autoturgy.ID, path);
  }

  public static <T extends Block> DeferredBlock<T> block(String name, Supplier<T> factory) {
    DeferredBlock<T> result = BLOCKS.register(name, factory);
    ORDER.add(ITEMS.registerSimpleBlockItem(name, result));
    return result;
  }

  public static <T extends Item> DeferredItem<T> item(String name, Supplier<T> factory) {
    DeferredItem<T> result = ITEMS.register(name, factory);
    ORDER.add(result);
    return result;
  }

  public static void register(IEventBus bus) {
    TABS.register(
        "devices",
        () ->
            CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.autoturgy"))
                .icon(() -> ORDER.getFirst().get().getDefaultInstance())
                .displayItems((p, out) -> ORDER.forEach(item -> out.accept(item.get())))
                .build());
    BLOCKS.register(bus);
    ITEMS.register(bus);
    ENTITIES.register(bus);
    MENUS.register(bus);
    TABS.register(bus);
  }

  private Content() {}
}
