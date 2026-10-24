package com.sanhiruzu.autoturgy;

import java.nio.file.*;
import net.minecraft.gametest.framework.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;

@EventBusSubscriber(modid = Autoturgy.ID)
public final class GameTestReporting {
  @SubscribeEvent
  public static void begin(ServerAboutToStartEvent event) {
    String report = System.getProperty("autoturgy.testReport");
    if (report == null) return;
    // Host 1.0.4 HARNESS_JAR stores a copied ItemStack; production skips IDE validation.
    // Exercise that exact installed-host API without weakening production component checks.
    net.neoforged.neoforge.common.CommonHooks.markComponentClassAsValid(net.minecraft.world.item.ItemStack.class);
    try {
      Path path = Path.of(report);
      Files.createDirectories(path.getParent());
      GlobalTestReporter.replaceWith(new JUnitLikeTestReporter(path.toFile()));
    } catch (Exception failure) {
      throw new IllegalStateException("Cannot prepare GameTest XML report", failure);
    }
  }

  private GameTestReporting() {}
}
