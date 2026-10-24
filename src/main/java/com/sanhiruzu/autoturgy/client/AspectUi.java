/* Explicit guide and device choices show native aspect icons and localized names.
 * Rendering known instructions does not change the player's host research knowledge. */
package com.sanhiruzu.autoturgy.client;

import com.leclowndu93150.thaumaturge.api.aspect.*;
import com.leclowndu93150.thaumaturge.api.client.AspectRendering;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class AspectUi {
  public static Holder<IAspect> resolve(String id) {
    var level = Minecraft.getInstance().level;
    ResourceLocation key =
        ResourceLocation.tryParse(
            id.contains(":") ? id : "thaumaturge:" + id.toLowerCase(java.util.Locale.ROOT));
    return level == null || key == null
        ? null
        : level.registryAccess().registryOrThrow(IAspect.REGISTRY_KEY).getHolder(key).orElse(null);
  }

  public static Component name(String id) {
    var holder = resolve(id);
    return holder == null ? Component.literal(id) : AspectComponents.trueName(holder);
  }

  public static void draw(GuiGraphics g, Font font, int x, int y, String id, int amount) {
    var holder = resolve(id);
    if (holder == null) AspectRendering.renderMissingGui(g, x, y);
    else AspectRendering.renderGui(g, font, x, y, holder, 0, AspectKnowledge.KNOWN);
    if (amount >= 0) g.drawString(font, Integer.toString(amount), x + 20, y + 4, 0xFFFFD27B, false);
  }

  private AspectUi() {}
}
