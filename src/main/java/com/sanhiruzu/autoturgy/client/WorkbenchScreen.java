/* Displays clearly marked ghost patterns alongside real ingredients and products.
 * Vanilla slot and button transport leaves all crafting authority on the server. */
package com.sanhiruzu.autoturgy.client;

import com.sanhiruzu.autoturgy.golems.WorkbenchMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class WorkbenchScreen extends AbstractContainerScreen<WorkbenchMenu> {
  private final Button[] pages = new Button[3];

  public WorkbenchScreen(WorkbenchMenu menu, Inventory inv, Component title) {
    super(menu, inv, title);
    imageWidth = 300;
    imageHeight = 250;
    inventoryLabelX = 70;
    inventoryLabelY = 127;
  }

  protected void init() {
    super.init();
    for (int i = 0; i < 3; i++) {
      final int page = i;
      pages[i] =
          addRenderableWidget(
              Button.builder(
                      Component.literal(Integer.toString(i + 1)),
                      b -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, page))
                  .bounds(leftPos + 195 + i * 28, topPos + 17, 24, 13)
                  .build());
    }
  }

  protected void renderBg(GuiGraphics g, float partial, int mx, int my) {
    DeviceScreens.panel(g, leftPos, topPos, imageWidth, imageHeight);
    for (var s : menu.slots) {
      g.fill(
          leftPos + s.x - 1, topPos + s.y - 1, leftPos + s.x + 17, topPos + s.y + 17, 0xFF15252C);
      g.fill(
          leftPos + s.x,
          topPos + s.y,
          leftPos + s.x + 16,
          topPos + s.y + 16,
          s.index < 9 ? 0xFF405C74 : 0xFF62736A);
    }
  }

  protected void renderLabels(GuiGraphics g, int mx, int my) {
    g.drawString(font, title, 10, 5, 0xFFF1D89C, false);
    g.drawString(
        font, Component.translatable("menu.autoturgy.ghost_pattern"), 12, 29, 0xFFB5D6F0, false);
    g.drawString(
        font, Component.translatable("menu.autoturgy.ingredients"), 97, 32, 0xFFDDE8E3, false);
    g.drawString(
        font, Component.translatable("menu.autoturgy.products"), 229, 32, 0xFFDDE8E3, false);
    g.drawString(
        font,
        Component.translatable(
            "menu.autoturgy.bench_status",
            menu.data.get(0) + 1,
            menu.data.get(1),
            menu.data.get(2)),
        12,
        103,
        0xFFFFD27B,
        false);
    g.drawString(
        font,
        Component.translatable(
            menu.data.get(3) == 1 ? "menu.autoturgy.bench_paused" : "menu.autoturgy.bench_hint"),
        12,
        115,
        0xFFDDE8E3,
        false);
  }

  public void render(GuiGraphics g, int mx, int my, float partial) {
    for (int i = 0; i < 3; i++)
      if (pages[i] != null)
        pages[i].active = i < Math.max(1, menu.data.get(1)) && i != menu.data.get(0);
    super.render(g, mx, my, partial);
    for (int i = 0; i < 4; i++) {
      int value = menu.data.get(i + 4);
      String side = new String[] {"N", "E", "S", "W"}[i];
      String name =
          value < 1 || value > 6
              ? "—"
              : Component.translatable(
                      "upgrade.autoturgy."
                          + new String[] {"air", "fire", "earth", "water", "order", "entropy"}
                              [value - 1])
                  .getString();
      g.drawString(
          font,
          side + ": " + name,
          leftPos + 12 + (i % 2) * 144,
          topPos + 226 + (i / 2) * 11,
          0xFFDDE8E3,
          false);
    }
    renderTooltip(g, mx, my);
  }
}
