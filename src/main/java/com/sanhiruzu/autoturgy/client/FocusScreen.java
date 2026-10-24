/* Shows physical focus inputs and a server-owned recipe preview.
 * Ordinary and arcane buttons submit bounded single-craft intents. */
package com.sanhiruzu.autoturgy.client;

import com.sanhiruzu.autoturgy.equipment.CraftingFocus;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class FocusScreen extends AbstractContainerScreen<CraftingFocus.FocusMenu> {
  public FocusScreen(CraftingFocus.FocusMenu m, Inventory i, Component t) {
    super(m, i, t);
    imageHeight = 245;
    inventoryLabelY = 153;
  }

  protected void init() {
    super.init();
    addRenderableWidget(
        Button.builder(
                Component.translatable("menu.autoturgy.focus_mode"),
                b -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 0))
            .bounds(leftPos + 10, topPos + 134, 100, 18)
            .build());
    addRenderableWidget(
        Button.builder(
                Component.translatable("menu.autoturgy.craft"),
                b -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 1))
            .bounds(leftPos + 115, topPos + 134, 52, 18)
            .build());
  }

  protected void renderBg(GuiGraphics g, float partial, int mx, int my) {
    DeviceScreens.panel(g, leftPos, topPos, imageWidth, imageHeight);
    for (var s : menu.slots) {
      g.fill(
          leftPos + s.x - 1, topPos + s.y - 1, leftPos + s.x + 17, topPos + s.y + 17, 0xFF162A31);
      g.fill(leftPos + s.x, topPos + s.y, leftPos + s.x + 16, topPos + s.y + 16, 0xFF62736A);
    }
  }

  protected void renderLabels(GuiGraphics g, int mx, int my) {
    g.drawString(font, title, 8, 6, 0xFFF1D89C, false);
    g.drawString(
        font,
        Component.translatable(
            menu.status.get(0) == 0 ? "menu.autoturgy.mundane" : "menu.autoturgy.arcane"),
        109,
        27,
        0xFFEAEACA,
        false);
    g.drawString(
        font,
        Component.translatable("menu.autoturgy.aura_cost", menu.status.get(8)),
        104,
        65,
        0xFFEAEACA,
        false);
    if (menu.status.get(0) == 1 && menu.status.get(15) > 0)
      g.drawString(
          font,
          Component.translatable("menu.autoturgy.focus_reason." + menu.status.get(15)),
          8,
          17,
          0xFFF4B692,
          false);
    for (int i = 0; i < 6; i++) {
      var aspect =
          com.leclowndu93150.thaumaturge.api.aspect.Aspects.resolve(
              minecraft.level, com.leclowndu93150.thaumaturge.api.aspect.TTAspects.PRIMALS.get(i));
      g.drawString(font, "×" + menu.status.get(9 + i), 10 + i * 25, 102, 0xFFEAEACA, false);
      com.leclowndu93150.thaumaturge.api.client.AspectRendering.renderGui(
          g, font, 10 + i * 25, 83, aspect, menu.status.get(i + 2) / 100F);
    }
  }

  public void render(GuiGraphics g, int mx, int my, float partial) {
    renderBackground(g, mx, my, partial);
    super.render(g, mx, my, partial);
    renderTooltip(g, mx, my);
    for (int i = 0; i < 6; i++)
      if (mx >= leftPos + 10 + i * 25
          && mx < leftPos + 26 + i * 25
          && my >= topPos + 83
          && my < topPos + 112) {
        var key = com.leclowndu93150.thaumaturge.api.aspect.TTAspects.PRIMALS.get(i);
        g.renderTooltip(
            font,
            com.leclowndu93150.thaumaturge.api.aspect.AspectComponents.name(
                    com.leclowndu93150.thaumaturge.api.aspect.Aspects.resolve(minecraft.level, key))
                .append(": " + menu.status.get(i + 2) / 100F + " vis; ")
                .append(
                    Component.translatable(
                        "menu.autoturgy.required_crystals", menu.status.get(9 + i))),
            mx,
            my);
      }
    if (menu.status.get(1) == 1 && mx >= leftPos + 100 && my >= topPos + 20 && my < topPos + 60)
      g.renderTooltip(font, Component.translatable("menu.autoturgy.staff"), mx, my);
  }
}
