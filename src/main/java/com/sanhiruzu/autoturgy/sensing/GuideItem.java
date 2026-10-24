/* Field notes open a lightweight client-rendered book through a normal menu.
 * Original guide text lives in language resources so translations remain possible. */
package com.sanhiruzu.autoturgy.sensing;

import com.sanhiruzu.autoturgy.Content;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class GuideItem extends Item {
  public static final java.util.List<String> PAGES = new java.util.ArrayList<>();

  static {
    for (int i = 0; i < 11; i++) PAGES.add("guide.autoturgy." + i);
  }

  public static DeferredHolder<MenuType<?>, MenuType<GuideMenu>> TYPE;

  public GuideItem() {
    super(new Properties().stacksTo(1));
  }

  public static void register() {
    Content.item("field_guide", GuideItem::new);
    TYPE =
        Content.MENUS.register(
            "guide", () -> IMenuTypeExtension.create((id, inv, buf) -> new GuideMenu(id)));
  }

  @Override
  public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
    if (player instanceof ServerPlayer server)
      server.openMenu(
          new SimpleMenuProvider(
              (id, inv, p) -> new GuideMenu(id),
              Component.translatable("item.autoturgy.field_guide")));
    return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
  }

  public static final class GuideMenu extends AbstractContainerMenu {
    public GuideMenu(int id) {
      super(TYPE.get(), id);
    }

    public boolean stillValid(Player player) {
      return true;
    }

    public ItemStack quickMoveStack(Player player, int slot) {
      return ItemStack.EMPTY;
    }
  }
}
