/* Vanilla menu properties travel as signed sixteen-bit words.
 * Split logical integers transparently so device screens retain full numeric ranges. */
package com.sanhiruzu.autoturgy.common;

import net.minecraft.world.inventory.ContainerData;

public final class MenuIntTransport {
  public static ContainerData wire(ContainerData logical) {
    return new ContainerData() {
      public int get(int index) {
        return logical.get(index / 2) >>> ((index & 1) * 16) & 0xFFFF;
      }

      public void set(int index, int value) {
        int shift = (index & 1) * 16;
        int previous = logical.get(index / 2);
        logical.set(index / 2, (previous & ~(0xFFFF << shift)) | ((value & 0xFFFF) << shift));
      }

      public int getCount() {
        return logical.getCount() * 2;
      }
    };
  }

  private MenuIntTransport() {}
}
