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
  private String laidOut = "";
  private List<Piece> cachedLayout = List.of();
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
              .contains(text.toLowerCase(Locale.ROOT))
          || searchable(i).toLowerCase(Locale.ROOT).contains(text.toLowerCase(Locale.ROOT)))
        matching.add(i);
  }

  private record Piece(String text, int x, int y, boolean aspect) {}

  private String searchable(int index) {
    String text = Component.translatable(GuideItem.PAGES.get(index)).getString();
    for (String word : text.split("[^A-Za-z]+"))
      if (!word.isEmpty() && AspectUi.resolve(word) != null)
        text += " " + AspectUi.name(word).getString();
    return text;
  }

  private List<Piece> layout() {
    if (matching.isEmpty()) return List.of();
    String text = Component.translatable(GuideItem.PAGES.get(matching.get(page))).getString();
    if (text.equals(laidOut)) return cachedLayout;
    List<Piece> pieces = new ArrayList<>();
    java.util.regex.Matcher tokens =
        java.util.regex.Pattern.compile("\\n|[^\\S\\n]+|[^\\s]+").matcher(text);
    int x = 0, y = 0;
    while (tokens.find()) {
      String token = tokens.group();
      if (token.equals("\n")) {
        x = 0;
        y += 18;
        continue;
      }
      List<Piece> word = new ArrayList<>();
      var parts = java.util.regex.Pattern.compile("[A-Za-z]+|[^A-Za-z]+").matcher(token);
      int width = 0;
      while (parts.find()) {
        String part = parts.group();
        boolean aspect = part.matches("[A-Za-z]+") && AspectUi.resolve(part) != null;
        word.add(new Piece(part, width, 0, aspect));
        width += aspect ? 18 : font.width(part);
      }
      if (x + width > 278 && x > 0) {
        x = 0;
        y += 18;
      }
      if (x == 0 && token.isBlank()) continue;
      for (Piece part : word) pieces.add(new Piece(part.text, x + part.x, y, part.aspect));
      x += width;
    }
    laidOut = text;
    cachedLayout = List.copyOf(pieces);
    return cachedLayout;
  }

  @Override
  public void render(GuiGraphics g, int mx, int my, float partial) {
    super.render(g, mx, my, partial);
    if (my < topPos + 61 || my >= topPos + 193) return;
    for (Piece piece : layout()) {
      int x = leftPos + 16 + piece.x, y = topPos + 64 + piece.y - scroll * 18;
      if (piece.aspect && mx >= x && mx < x + 18 && my >= y && my < y + 16)
        g.renderTooltip(font, AspectUi.name(piece.text), mx, my);
    }
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
      for (Piece piece : layout()) {
        int y = 64 + piece.y - scroll * 18;
        if (piece.aspect) AspectUi.draw(g, font, 16 + piece.x, y, piece.text, -1);
        else g.drawString(font, piece.text, 16 + piece.x, y + 4, 0xFFE0E9DF, false);
      }
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
      int lines = layout().stream().mapToInt(p -> p.y / 18 + 1).max().orElse(0);
      scroll = Math.clamp(scroll - (int) vertical * 3, 0, Math.max(0, lines - 7));
    }
    return true;
  }
}
