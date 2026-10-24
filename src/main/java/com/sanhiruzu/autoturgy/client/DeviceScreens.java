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
    private int aspectOffset;

    public ControlScreen(ControlMenu menu, Inventory inv, Component title) {
      super(menu, inv, title);
      imageWidth = 310;
      imageHeight = 230;
    }

    @Override
    protected void init() {
      super.init();
      for (int i = 0; i < menu.controls && !readOnly(); i++) {
        if ((menu.visibleMask & 1 << i) == 0 || (menu.editableMask & 1 << i) == 0) continue;
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
              : menu.kind.startsWith("golemic_")
                  ? Component.translatable("menu.autoturgy.golem_status")
                  : Component.translatable(
                      "menu.autoturgy.status", menu.data.get(0), menu.data.get(1)),
          16,
          35,
          0xFFC8E7DC,
          false);
      for (int i = 0; i < menu.controls; i++) {
        if ((menu.visibleMask & 1 << i) == 0) continue;
        String label =
            Component.translatable("control.autoturgy." + menu.kind + "." + i).getString();
        g.drawString(font, font.plainSubstrByWidth(label, 140), 12, 54 + i * 16, 0xFFDDE8E3, false);
        String aspect = aspect(i);
        if (!aspect.isEmpty()) {
          int index = menu.aspects.indexOf(aspect);
          AspectUi.draw(
              g,
              font,
              156,
              50 + i * 16,
              aspect,
              construct() && index >= 0 ? menu.data.get(14 + index * 2) : -1);
        } else
          g.drawString(
              font,
              font.plainSubstrByWidth(value(i).getString(), 89),
              156,
              54 + i * 16,
              0xFFFFD27B,
              false);
      }
      if (menu.kind.equals("thirsty_tank") || menu.kind.equals("alchemical_boiler"))
        g.drawString(
            font,
            font.plainSubstrByWidth(fluidName().getString(), 280),
            12,
            186,
            0xFFC8E7DC,
            false);
      if (menu.kind.equals("essentia_locus") || menu.kind.equals("vis_reader"))
        for (int slot = 0; slot < Math.min(9, menu.aspects.size() - aspectOffset); slot++)
          AspectUi.draw(
              g,
              font,
              12 + slot % 3 * 96,
              140 + slot / 3 * 19,
              menu.aspects.get(slot + aspectOffset),
              menu.data.get(14 + (slot + aspectOffset) * 2));
      if (menu.kind.equals("golemic_inhibitor") || menu.kind.equals("golemic_taskmaster")) {
        g.drawString(
            font,
            Component.translatable("menu.autoturgy.golem_links", menu.data.get(1)),
            12,
            91,
            0xFFC8E7DC,
            false);
        g.drawWordWrap(
            font,
            menu.kind.equals("golemic_inhibitor")
                ? Component.translatable(
                    "menu.autoturgy.inhibitor_radius", menu.data.get(3), menu.data.get(3) / 8)
                : Component.translatable(
                    menu.data.get(4) == 1
                        ? "menu.autoturgy.core_ready"
                        : "menu.autoturgy.core_missing"),
            12,
            112,
            280,
            0xFFC8E7DC);
      }
      if (menu.controls == 0 && !menu.kind.equals("golemic_taskmaster"))
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

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
      if ((menu.kind.equals("essentia_locus") || menu.kind.equals("vis_reader"))
          && y >= topPos + 136
          && y < topPos + 199
          && menu.aspects.size() > 9) {
        int last = Math.max(0, (menu.aspects.size() - 7) / 3 * 3);
        aspectOffset = Math.clamp(aspectOffset - (int) Math.signum(vertical) * 3, 0, last);
        return true;
      }
      return super.mouseScrolled(x, y, horizontal, vertical);
    }

    private Component fluidName() {
      int id = menu.data.get(menu.kind.equals("thirsty_tank") ? 10 : 6);
      var fluid = net.minecraft.core.registries.BuiltInRegistries.FLUID.byId(id);
      return fluid == null || fluid == net.minecraft.world.level.material.Fluids.EMPTY
          ? Component.translatable("menu.autoturgy.empty_fluid")
          : new net.neoforged.neoforge.fluids.FluidStack(fluid, 1).getHoverName();
    }

    private String aspect(int i) {
      int value = menu.data.get(i + 2);
      if (construct())
        return value > 0 && value <= menu.aspects.size() ? menu.aspects.get(value - 1) : "";
      if (menu.kind.equals("vis_reader")
          && i == menu.selectionControl
          && value >= 0
          && value < menu.selections.size()) return menu.selections.get(value);
      return "";
    }

    private Component value(int i) {
      int value = menu.data.get(i + 2);
      if (construct()) {
        String key = aspect(i);
        return key.isEmpty() ? Component.translatable("menu.autoturgy.closed") : AspectUi.name(key);
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
            : AspectUi.name(key);
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
      if (menu.kind.equals("tally_box") && i == 4)
        return Component.translatable("mode.autoturgy.tally_rule." + value);
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
          && row < menu.controls
          && (menu.visibleMask & 1 << row) != 0) {
        g.renderTooltip(
            font,
            Component.translatable("control.autoturgy." + menu.kind + "." + row)
                .append(": ")
                .append(value(row)),
            mx,
            my);
      }
      if (menu.kind.equals("essentia_locus") || menu.kind.equals("vis_reader"))
        for (int slot = 0; slot < Math.min(9, menu.aspects.size() - aspectOffset); slot++) {
          int i = slot + aspectOffset;
          int x = leftPos + 12 + slot % 3 * 96, y = topPos + 140 + slot / 3 * 19;
          if (mx >= x && mx < x + 92 && my >= y && my < y + 17)
            g.renderTooltip(
                font,
                AspectUi.name(menu.aspects.get(i))
                    .copy()
                    .append(": " + menu.data.get(14 + i * 2) + " / " + menu.data.get(15 + i * 2)),
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
