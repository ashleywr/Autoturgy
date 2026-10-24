/* Client registration is isolated from dedicated-server class loading.
 * Original control, ghost-filter and guide screens use vanilla menu transport. */
package com.sanhiruzu.autoturgy.client;

import com.sanhiruzu.autoturgy.Autoturgy;
import com.sanhiruzu.autoturgy.common.ControlMenu;
import com.sanhiruzu.autoturgy.sensing.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = Autoturgy.ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class AutoturgyClient {
  @SubscribeEvent
  public static void screens(RegisterMenuScreensEvent event) {
    event.register(ControlMenu.TYPE.get(), DeviceScreens.ControlScreen::new);
    event.register(FilterMenu.TYPE.get(), DeviceScreens.FilterScreen::new);
    event.register(GuideItem.TYPE.get(), GuideScreen::new);
  }

  private AutoturgyClient() {}
}
