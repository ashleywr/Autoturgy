/* A compact three-row query view reaches the complete server catalogue.
 * Ghost requests and a distinct real mirror-input slot fit the minimum scaled viewport. */
package com.sanhiruzu.autoturgy.client;

import com.sanhiruzu.autoturgy.inventarium.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

public final class InventariumScreen extends AbstractContainerScreen<InventariumMenu> {
  private EditBox search;
  private int queryDelay;
  private boolean byCount;
  private final Button[] increase = new Button[3], decrease = new Button[3];
  private Button mirrors;

  public InventariumScreen(InventariumMenu menu, Inventory inv, Component title) {
    super(menu, inv, title);
    imageWidth = 310;
    imageHeight = 234;
  }

  @Override
  protected void init() {
    super.init();
    search =
        new EditBox(
            font,
            leftPos + 10,
            topPos + 31,
            190,
            18,
            Component.translatable("menu.autoturgy.inventory_search"));
    search.setMaxLength(64);
    search.setHint(Component.translatable("menu.autoturgy.inventory_search"));
    search.setResponder(s -> queryDelay = 5);
    addRenderableWidget(search);
    addRenderableWidget(
        Button.builder(
                Component.literal("A-Z / #"),
                b -> {
                  byCount = !byCount;
                  query(0);
                })
            .bounds(leftPos + 207, topPos + 31, 93, 18)
            .build());
    for (int i = 0; i < 3; i++) {
      final int row = i;
      decrease[i] =
          addRenderableWidget(
              Button.builder(Component.literal("−"), b -> request(row, false))
                  .bounds(leftPos + 250, topPos + 67 + i * 22, 22, 20)
                  .build());
      increase[i] =
          addRenderableWidget(
              Button.builder(Component.literal("+"), b -> request(row, true))
                  .bounds(leftPos + 278, topPos + 67 + i * 22, 22, 20)
                  .build());
    }
    addRenderableWidget(
        Button.builder(Component.literal("↑"), b -> query(page() - 1))
            .bounds(leftPos + 10, topPos + 154, 24, 20)
            .build());
    addRenderableWidget(
        Button.builder(Component.literal("↓"), b -> query(page() + 1))
            .bounds(leftPos + 39, topPos + 154, 24, 20)
            .build());
    if (menu.requests) {
      addRenderableWidget(
          Button.builder(Component.literal("−"), b -> request(3, false))
              .bounds(leftPos + 34, topPos + 131, 22, 20)
              .build());
      addRenderableWidget(
          Button.builder(Component.literal("+"), b -> request(3, true))
              .bounds(leftPos + 60, topPos + 131, 22, 20)
              .build());
      mirrors =
          addRenderableWidget(
              Button.builder(Component.empty(), b -> send(3))
                  .bounds(leftPos + 89, topPos + 131, 148, 20)
                  .build());
    } else {
      addRenderableWidget(
          Button.builder(Component.translatable("menu.autoturgy.tally_mode"), b -> send(1))
              .bounds(leftPos + 75, topPos + 154, 225, 20)
              .build());
      if (menu.scribeView)
        addRenderableWidget(
            Button.builder(Component.translatable("menu.autoturgy.local_all"), b -> send(2))
                .bounds(leftPos + 75, topPos + 180, 225, 20)
                .build());
    }
  }

  private int page() {
    return menu.counts.get(InventariumMenu.ROWS * 6 + 3);
  }

  private void query(int page) {
    PacketDistributor.sendToServer(
        new InventariumPayload(
            menu.containerId, menu.revision(), 0, search.getValue(), Math.max(0, page), byCount));
  }

  private void send(int action) {
    PacketDistributor.sendToServer(
        new InventariumPayload(menu.containerId, menu.revision(), action, "", 0, false));
  }

  private void request(int row, boolean add) {
    int mode =
        (add ? 1 : 0)
            | (hasShiftDown() ? 2 : 0)
            | (hasControlDown() ? 4 : 0)
            | (hasAltDown() ? 8 : 0);
    send(100 + row * 16 + mode);
  }

  @Override
  protected void containerTick() {
    if (queryDelay > 0 && --queryDelay == 0) query(0);
    for (int i = 0; i < 3; i++)
      increase[i].visible =
          decrease[i].visible = menu.requests && !menu.catalogue.getItem(i).isEmpty();
    if (mirrors != null)
      mirrors.setMessage(
          Component.translatable(
              menu.counts.get(25) == 2
                  ? "menu.autoturgy.mirrors_output"
                  : "menu.autoturgy.mirrors_input"));
  }

  @Override
  public boolean mouseScrolled(double x, double y, double dx, double dy) {
    query(page() - (int) Math.signum(dy));
    return true;
  }

  @Override
  protected void renderBg(GuiGraphics g, float partial, int x, int y) {
    DeviceScreens.panel(g, leftPos, topPos, imageWidth, imageHeight);
    for (int i = 0; i < 3; i++)
      g.fill(leftPos + 9, topPos + 65 + i * 22, leftPos + 301, topPos + 86 + i * 22, 0xFF28454A);
    for (var slot : menu.slots)
      if (slot.x >= 0) {
        g.fill(
            leftPos + slot.x - 1,
            topPos + slot.y - 1,
            leftPos + slot.x + 17,
            topPos + slot.y + 17,
            0xFF9D7A45);
        g.fill(
            leftPos + slot.x,
            topPos + slot.y,
            leftPos + slot.x + 16,
            topPos + slot.y + 16,
            0xFF28454A);
      }
  }

  @Override
  protected void renderLabels(GuiGraphics g, int x, int y) {
    g.drawString(font, title, 10, 12, 0xFFF1D89C, false);
    g.drawString(
        font, Component.translatable("menu.autoturgy.index_columns"), 10, 54, 0xFFC8E7DC, false);
    for (int i = 0; i < 3; i++) {
      var stack = menu.catalogue.getItem(i);
      if (stack.isEmpty()) continue;
      int py = 67 + i * 22;
      g.renderItem(stack, 12, py);
      g.drawString(
          font,
          font.plainSubstrByWidth(stack.getHoverName().getString(), 105),
          32,
          py,
          0xFFDDE8E3,
          false);
      g.drawString(
          font,
          compact(menu.value(i, 0))
              + " / "
              + compact(menu.value(i, 1))
              + " / "
              + compact(menu.value(i, 2)),
          140,
          py + 7,
          0xFFFFD27B,
          false);
    }
    if (menu.requests) {
      g.drawString(
          font, Component.translatable("menu.autoturgy.input"), 243, 136, 0xFFC8E7DC, false);
      g.drawString(
          font,
          (page() + 1) + " / " + Math.max(1, (menu.counts.get(22) + 2) / 3),
          10,
          181,
          0xFFC8E7DC,
          false);
      g.drawWordWrap(
          font, Component.translatable("menu.autoturgy.worker_pending"), 10, 195, 58, 0xFFC8E7DC);
    } else {
      g.drawString(
          font,
          menu.counts.get(19)
              + " / "
              + menu.counts.get(18)
              + " slots; tally "
              + (menu.counts.get(20) == 0 ? "stock" : "demand"),
          10,
          210,
          0xFFC8E7DC,
          false);
      g.drawString(
          font,
          "Memory "
              + menu.counts.get(26)
              + " / "
              + menu.counts.get(27)
              + "; Scribes "
              + menu.counts.get(28)
              + " / "
              + menu.counts.get(29),
          10,
          221,
          0xFFC8E7DC,
          false);
      g.drawString(
          font,
          "Page " + (page() + 1) + " / " + Math.max(1, (menu.counts.get(22) + 2) / 3),
          10,
          135,
          0xFFC8E7DC,
          false);
    }
  }

  private static String compact(long value) {
    return value < 1000
        ? Long.toString(value)
        : value < 1000000
            ? value / 1000 + "k"
            : value < 1000000000 ? value / 1000000 + "M" : value / 1000000000 + "G";
  }

  @Override
  public void render(GuiGraphics g, int x, int y, float partial) {
    super.render(g, x, y, partial);
    renderTooltip(g, x, y);
    int row = (y - topPos - 67) / 22;
    if (x >= leftPos + 10
        && x < leftPos + 245
        && y >= topPos + 67
        && row >= 0
        && row < 3
        && !menu.catalogue.getItem(row).isEmpty())
      g.renderComponentTooltip(
          font,
          java.util.List.of(
              menu.catalogue.getItem(row).getHoverName(),
              Component.literal("Stock: " + menu.value(row, 0)),
              Component.literal("Reserved: " + menu.value(row, 1)),
              Component.literal("Wanted: " + menu.value(row, 2))),
          x,
          y);
    if (menu.requests
        && x >= leftPos + 249
        && x < leftPos + 301
        && y >= topPos + 67
        && y < topPos + 130)
      g.renderTooltip(font, Component.translatable("menu.autoturgy.request_modifiers"), x, y);
  }
}
