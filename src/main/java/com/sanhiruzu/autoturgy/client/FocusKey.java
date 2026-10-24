/* A configurable key calls the server's real socket/pouch focus workflow.
 * The request contains no trusted client inventory or recipe state. */
package com.sanhiruzu.autoturgy.client;

import com.sanhiruzu.autoturgy.Autoturgy;
import com.sanhiruzu.autoturgy.equipment.CraftingFocus;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = Autoturgy.ID, value = Dist.CLIENT)
public final class FocusKey {
  public static final KeyMapping KEY =
      new KeyMapping(
          "key.autoturgy.crafting_focus",
          org.lwjgl.glfw.GLFW.GLFW_KEY_PERIOD,
          "key.categories.autoturgy");

  @SubscribeEvent
  public static void tick(ClientTickEvent.Post e) {
    while (KEY.consumeClick()) PacketDistributor.sendToServer(new CraftingFocus.Call());
  }

  @EventBusSubscriber(modid = Autoturgy.ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
  public static final class Registration {
    @SubscribeEvent
    public static void key(RegisterKeyMappingsEvent e) {
      e.register(KEY);
    }

    @SubscribeEvent
    public static void render(EntityRenderersEvent.RegisterRenderers e) {
      e.registerEntityRenderer(
          com.sanhiruzu.autoturgy.equipment.CollectingPearls.TYPE.get(),
          net.minecraft.client.renderer.entity.ThrownItemRenderer::new);
    }
  }
}
