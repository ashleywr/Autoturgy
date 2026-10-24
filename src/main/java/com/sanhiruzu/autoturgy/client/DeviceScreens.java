/* Readable device controls and a clearly marked non-consuming filter editor.
 * Each button submits one bounded intent through its current server menu. */
package com.sanhiruzu.autoturgy.client;

import com.sanhiruzu.autoturgy.common.ControlMenu;
import com.sanhiruzu.autoturgy.sensing.FilterMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class DeviceScreens {
  static void panel(GuiGraphics g, int x, int y, int w, int h) {
    g.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0xFF9D7A45);
    g.fill(x, y, x + w, y + h, 0xFF172A31);
    g.fill(x + 5, y + 5, x + w - 5, y + 26, 0xFF28454A);
  }

  public static final class ControlScreen extends AbstractContainerScreen<ControlMenu> {
    public ControlScreen(ControlMenu menu, Inventory inv, Component title) {
      super(menu, inv, title);
      imageWidth = 310;
      imageHeight = 230;
    }

    @Override
    protected void init() {
      super.init();
      for (int i = 0; i < menu.controls && !readOnly(); i++) {
        final int control = i;
        addRenderableWidget(
            Button.builder(Component.literal("−"), b -> send(control * 2))
                .bounds(leftPos + 249, topPos + 50 + i * 16, 22, 15)
                .build());
        addRenderableWidget(
            Button.builder(Component.literal("+"), b -> send(control * 2 + 1))
                .bounds(leftPos + 277, topPos + 50 + i * 16, 22, 15)
                .build());
      }
      if (menu.kind.contains("hourglass"))
        addRenderableWidget(
            Button.builder(Component.translatable("menu.autoturgy.start"), b -> send(80))
                .bounds(leftPos + 16, topPos + 201, 130, 20)
                .build());
      if (construct())
        addRenderableWidget(
            Button.builder(Component.translatable("menu.autoturgy.cancel"), b -> send(81))
                .bounds(leftPos + 160, topPos + 201, 130, 20)
                .build());
      if (menu.kind.contains("hourglass"))
        addRenderableWidget(
            Button.builder(Component.translatable("menu.autoturgy.cancel"), b -> send(81))
                .bounds(leftPos + 160, topPos + 201, 130, 20)
                .build());
    }

    private void send(int id) {
      minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    private boolean readOnly() {
      return menu.kind.equals("thirsty_tank")
          || menu.kind.equals("alchemical_boiler")
          || menu.kind.equals("essentia_locus");
    }

    private boolean construct() {
      return java.util.Set.of("thaumostatic_spire", "nethermind", "dimensional_lure", "sirens_lure")
          .contains(menu.kind);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY) {
      panel(g, leftPos, topPos, imageWidth, imageHeight);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
      g.drawString(font, title, 12, 12, 0xFFF1D89C, false);
      g.drawString(
          font,
          construct()
              ? Component.translatable(
                  "menu.autoturgy.construct_status",
                  menu.data.get(8) == 1 ? "Active" : "Stopped",
                  menu.data.get(9),
                  menu.data.get(10))
              : Component.translatable("menu.autoturgy.status", menu.data.get(0), menu.data.get(1)),
          16,
          35,
          0xFFC8E7DC,
          false);
      for (int i = 0; i < menu.controls; i++) {
        String label =
            Component.translatable("control.autoturgy." + menu.kind + "." + i).getString();
        g.drawString(font, font.plainSubstrByWidth(label, 140), 12, 54 + i * 16, 0xFFDDE8E3, false);
        g.drawString(
            font,
            font.plainSubstrByWidth(value(i).getString(), 89),
            156,
            54 + i * 16,
            0xFFFFD27B,
            false);
      }
      if (menu.controls == 0)
        g.drawWordWrap(
            font, Component.translatable("menu.autoturgy.reader_hint"), 16, 66, 278, 0xFFDDE8E3);
      if (construct()) {
        String key =
            menu.kind.equals("nethermind")
                ? "nether"
                : menu.kind.equals("dimensional_lure") ? "stability" : "radius";
        g.drawString(
            font,
            Component.translatable(
                "menu.autoturgy.construct_" + key, menu.data.get(11), menu.data.get(12)),
            12,
            159,
            0xFFC8E7DC,
            false);
        g.drawWordWrap(
            font,
            Component.translatable("menu.autoturgy.construct_hint"),
            12,
            177,
            282,
            0xFFDDE8E3);
      }
    }

    private Component value(int i) {
      int value = menu.data.get(i + 2);
      if (construct()) {
        java.util.List<String> names =
            switch (menu.kind) {
              case "thaumostatic_spire" -> java.util.List.of("Potentia", "Aer", "Volatus");
              case "nethermind" ->
                  java.util.List.of(
                      "Ignis",
                      "Terra",
                      "Vitreus",
                      "Lux",
                      "Spiritus",
                      "Bestia",
                      "Exanimis",
                      "Volatus",
                      "Auram");
              case "dimensional_lure" -> java.util.List.of("Alienis", "Potentia");
              default -> java.util.List.of("Potentia", "Desiderium");
            };
        return value > 0 && value <= names.size()
            ? Component.literal(names.get(value - 1))
            : Component.translatable("menu.autoturgy.closed");
      }
      if (menu.kind.equals("thirsty_tank") && i < 6)
        return Component.translatable("glyph.autoturgy." + value);
      if (menu.kind.equals("greedy_chest")) {
        if (i < 6) return onOff((menu.data.get(2) & 1 << i) != 0);
        return i == 6
            ? Component.translatable("mode.autoturgy.greedy." + menu.data.get(3))
            : Component.literal(Integer.toString(menu.data.get(4)));
      }
      if (menu.kind.equals("inventarium_scribe"))
        return Component.literal(net.minecraft.core.Direction.from3DDataValue(value).getName());
      if (menu.kind.equals("inventarium_oculus"))
        return Component.translatable("mode.autoturgy.oculus." + value);
      if (i == menu.selectionControl && value >= 0 && value < menu.selections.size()) {
        String key = menu.selections.get(value);
        return key.isEmpty()
            ? Component.translatable("menu.autoturgy.all_aspects")
            : Component.literal(key.substring(key.indexOf(':') + 1));
      }
      if (menu.kind.equals("redcrystal")) {
        if (i < 6)
          return Component.translatable(
              "mode.autoturgy.connection." + ((menu.data.get(2) >> (i * 2)) & 3));
        value = menu.data.get(i == 6 ? 4 : i == 7 ? 5 : 3);
        if (i == 8) return onOff(value != 0);
      }
      if (menu.kind.equals("magic_hourglass") && i == 1)
        return Component.translatable("mode.autoturgy.output." + value);
      if (menu.kind.equals("magic_hourglass") && i == 2)
        return Component.translatable("mode.autoturgy.trigger." + value);
      if (menu.kind.equals("vis_reader") && i == 0)
        return Component.translatable("mode.autoturgy.vis." + value);
      if (menu.kind.equals("tally_box") && i == 2)
        return Component.translatable("mode.autoturgy.lens." + value);
      if (menu.kind.equals("signum_entia") && i == 1)
        return Component.translatable("mode.autoturgy.age." + value);
      if (menu.kind.equals("magic_hourglass") && i >= 3
          || menu.kind.equals("vis_reader") && i == 1
          || menu.kind.equals("tally_box") && i == 3
          || menu.kind.equals("signum_entia") && i == 3) return onOff(value != 0);
      return Component.literal(Integer.toString(value));
    }

    private Component onOff(boolean enabled) {
      return Component.translatable(enabled ? "menu.autoturgy.on" : "menu.autoturgy.off");
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
      super.render(g, mx, my, partial);
      renderTooltip(g, mx, my);
      int row = (my - topPos - 50) / 16;
      if (mx >= leftPos + 10
          && mx < leftPos + 245
          && my >= topPos + 50
          && row >= 0
          && row < menu.controls) {
        g.renderTooltip(
            font,
            Component.translatable("control.autoturgy." + menu.kind + "." + row)
                .append(": ")
                .append(value(row)),
            mx,
            my);
      }
    }
  }

  public static final class FilterScreen extends AbstractContainerScreen<FilterMenu> {
    private final Button[] options = new Button[5];

    public FilterScreen(FilterMenu menu, Inventory inv, Component title) {
      super(menu, inv, title);
      imageWidth = 196;
      imageHeight = 211;
      inventoryLabelY = 113;
      inventoryLabelX = 17;
    }

    @Override
    protected void init() {
      super.init();
      for (int i = 0; i < 5; i++) {
        final int flag = i;
        options[i] =
            addRenderableWidget(
                Button.builder(
                        Component.empty(),
                        b -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, flag))
                    .bounds(leftPos + 9 + (i % 2) * 90, topPos + 29 + (i / 2) * 18, 87, 17)
                    .build());
      }
    }

    @Override
    protected void containerTick() {
      for (int i = 0; i < 5; i++)
        options[i].setMessage(
            Component.translatable("filter.autoturgy." + i, menu.flags.get(i) == 1 ? "✓" : "×"));
    }

    @Override
    protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY) {
      panel(g, leftPos, topPos, imageWidth, imageHeight);
      for (var slot : menu.slots) {
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
            slot.index < 9 ? 0xFF416E73 : 0xFF53605E);
      }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int x, int y) {
      g.drawString(font, title, 12, 12, 0xFFF1D89C, false);
      g.drawString(
          font, Component.translatable("menu.autoturgy.ghosts"), 16, 108, 0xFFC8E7DC, false);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
      super.render(g, mx, my, partial);
      renderTooltip(g, mx, my);
    }
  }

  private DeviceScreens() {}
}
