/* Autoturgy's server-safe entry point.
 * Registers independent feature families and their shared control menu. */
package com.sanhiruzu.autoturgy;

import com.sanhiruzu.autoturgy.common.ControlMenu;
import com.sanhiruzu.autoturgy.sensing.SensingModule;
import com.sanhiruzu.autoturgy.signal.RedcrystalNetwork;
import com.sanhiruzu.autoturgy.signal.SignalModule;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(Autoturgy.ID)
public final class Autoturgy {
  public static final String ID = "autoturgy";

  public Autoturgy(IEventBus bus) {
    SignalModule.register();
    RedcrystalNetwork.register();
    SensingModule.register();
    com.sanhiruzu.autoturgy.storage.StorageModule.register(bus);
    com.sanhiruzu.autoturgy.inventarium.InventariumModule.register(bus);
    com.sanhiruzu.autoturgy.transport.MirrorModule.register(bus);
    ControlMenu.register();
    Content.register(bus);
  }
}
