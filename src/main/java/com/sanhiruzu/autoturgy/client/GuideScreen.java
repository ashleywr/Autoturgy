/* Searchable original field notes describe actual supported mechanics and adaptations.
 * Page navigation is local UI state with no gameplay mutations. */
package com.sanhiruzu.autoturgy.client;

import com.sanhiruzu.autoturgy.sensing.GuideItem;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class GuideScreen extends AbstractContainerScreen<GuideItem.GuideMenu> {
  private EditBox search;
  private int page;
  private int scroll;
  private List<Integer> matching = new ArrayList<>();

  public GuideScreen(GuideItem.GuideMenu menu, Inventory inv, Component title) {
    super(menu, inv, title);
    imageWidth = 310;
    imageHeight = 230;
  }

  @Override
  protected void init() {
    super.init();
    search =
        new EditBox(
            font,
            leftPos + 16,
            topPos + 33,
            278,
            18,
            Component.translatable("menu.autoturgy.search"));
    search.setHint(Component.translatable("menu.autoturgy.search"));
    search.setResponder(this::find);
    addRenderableWidget(search);
    find("");
    addRenderableWidget(
        Button.builder(
                Component.literal("←"),
                b -> {
                  if (!matching.isEmpty()) page = Math.floorMod(page - 1, matching.size());
                  scroll = 0;
                })
            .bounds(leftPos + 16, topPos + 201, 40, 20)
            .build());
    addRenderableWidget(
        Button.builder(
                Component.literal("→"),
                b -> {
                  if (!matching.isEmpty()) page = (page + 1) % matching.size();
                  scroll = 0;
                })
            .bounds(leftPos + 254, topPos + 201, 40, 20)
            .build());
  }

  private void find(String text) {
    matching.clear();
    page = 0;
    scroll = 0;
    for (int i = 0; i < GuideItem.PAGES.size(); i++)
      if (Component.translatable(GuideItem.PAGES.get(i))
          .getString()
          .toLowerCase(Locale.ROOT)
          .contains(text.toLowerCase(Locale.ROOT))) matching.add(i);
  }

  @Override
  protected void renderBg(GuiGraphics g, float partial, int mx, int my) {
    DeviceScreens.panel(g, leftPos, topPos, imageWidth, imageHeight);
  }

  @Override
  protected void renderLabels(GuiGraphics g, int mx, int my) {
    g.drawString(font, title, 12, 12, 0xFFF1D89C, false);
    if (!matching.isEmpty()) {
      g.enableScissor(leftPos + 14, topPos + 61, leftPos + 296, topPos + 193);
      g.drawWordWrap(
          font,
          Component.translatable(GuideItem.PAGES.get(matching.get(page))),
          16,
          64 - scroll * 9,
          278,
          0xFFE0E9DF);
      g.disableScissor();
      g.drawCenteredString(
          font,
          (page + 1) + " / " + matching.size() + " • scroll",
          imageWidth / 2,
          207,
          0xFFF1D89C);
    }
  }

  @Override
  public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
    if (!matching.isEmpty()) {
      int lines =
          font.split(Component.translatable(GuideItem.PAGES.get(matching.get(page))), 278).size();
      scroll = Math.clamp(scroll - (int) vertical * 3, 0, Math.max(0, lines - 14));
    }
    return true;
  }
}
