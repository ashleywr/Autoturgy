/* Runic filters edit ghost samples without consuming the player's cursor stack.
 * Sneak-use copies the entire configuration to another filter in the off hand. */
package com.sanhiruzu.autoturgy.sensing;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;

public final class FilterItem extends Item {
  public FilterItem() {
    super(new Properties().stacksTo(1));
  }

  @Override
  public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
    ItemStack stack = player.getItemInHand(hand);
    if (!level.isClientSide && player instanceof ServerPlayer server) {
      if (player.isShiftKeyDown()
          && hand == InteractionHand.MAIN_HAND
          && player.getOffhandItem().getItem() instanceof FilterItem) {
        SensingModule.itemData(player.getOffhandItem(), SensingModule.itemData(stack));
        player.displayClientMessage(
            Component.translatable("message.autoturgy.filter_copied"), true);
      } else {
        int slot = hand == InteractionHand.OFF_HAND ? 40 : player.getInventory().selected;
        server.openMenu(
            new SimpleMenuProvider(
                (id, inv, p) -> new FilterMenu(id, inv, slot),
                Component.translatable("item.autoturgy.runic_filter")),
            buf -> buf.writeVarInt(slot));
      }
    }
    return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
  }

  @Override
  public void appendHoverText(
      ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
    lines.add(Component.translatable("tooltip.autoturgy.filter"));
    lines.add(
        Component.translatable(
            SensingModule.itemData(stack).getBoolean("blacklist")
                ? "tooltip.autoturgy.blacklist"
                : "tooltip.autoturgy.whitelist"));
  }
}
