/* Curios rings react once to both endpoints of genuine ender teleports.
 * Every damaged target pays actual wand Perditio; fragments attract idle Endermen. */
package com.sanhiruzu.autoturgy.equipment;

import com.leclowndu93150.thaumaturge.api.aspect.*;
import com.leclowndu93150.thaumaturge.api.wands.WandAccess;
import com.leclowndu93150.thaumaturge.content.wands.ItemWand;
import com.sanhiruzu.autoturgy.Content;
import java.util.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

public final class EquipmentModule {
  public static DeferredItem<Item> LESSER_RING, RING;
  private static final Set<UUID> TELEPORTING = new HashSet<>();

  public static void register(IEventBus bus) {
    LESSER_RING = Content.item("lesser_ender_disjunction_ring", () -> new Ring());
    RING = Content.item("ender_disjunction_ring", () -> new Ring());
    AuxiliaryContent.register();
    CollectingPearls.register(bus);
    CraftingFocus.register(bus);
    NodeJarring.register(bus);
    NeoForge.EVENT_BUS.addListener(
        net.neoforged.bus.api.EventPriority.LOWEST, EquipmentModule::teleport);
    NeoForge.EVENT_BUS.addListener(EquipmentModule::fragment);
  }

  public static boolean canPay(Player p, ResourceKey<IAspect> aspect, int n) {
    for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
      ItemStack s = p.getInventory().getItem(i);
      if (s.getItem() instanceof ItemWand && WandAccess.getVis(s, aspect) >= n) return true;
    }
    return false;
  }

  public static boolean pay(Player p, ResourceKey<IAspect> aspect, int n) {
    for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
      ItemStack s = p.getInventory().getItem(i);
      if (s.getItem() instanceof ItemWand && WandAccess.getVis(s, aspect) >= n) {
        p.getInventory()
            .setItem(i, WandAccess.withVis(s, aspect, WandAccess.getVis(s, aspect) - n));
        return true;
      }
    }
    return false;
  }

  public static final class Ring extends Item implements ICurioItem {
    Ring() {
      super(new Properties().stacksTo(1));
    }
  }

  private static void teleport(EntityTeleportEvent.EnderEntity event) {
    var entity = event.getEntity();
    if (!(entity.level() instanceof ServerLevel level)
        || event.isCanceled()
        || !TELEPORTING.add(entity.getUUID())) return;
    try {
      Vec3 from = entity.position(),
          to = new Vec3(event.getTargetX(), event.getTargetY(), event.getTargetZ());
      for (Player p : level.players()) {
        List<Integer> powers = new ArrayList<>();
        CuriosApi.getCuriosInventory(p)
            .ifPresent(
                inv -> {
                  var all = inv.getEquippedCurios();
                  for (int i = 0; i < all.getSlots(); i++) {
                    ItemStack s = all.getStackInSlot(i);
                    if (s.is(RING.get())) powers.add(6);
                    else if (s.is(LESSER_RING.get())) powers.add(3);
                  }
                });
        if (powers.isEmpty()) continue;
        powers.sort(Comparator.reverseOrder());
        int range = powers.getFirst() == 6 ? 12 : 6;
        if (p.position().distanceToSqr(from) > range * range
            && p.position().distanceToSqr(to) > range * range) continue;
        float damage = powers.getFirst() + (powers.size() > 1 ? powers.get(1) * .5F : 0);
        if (pay(p, TTAspects.PERDITIO, 25)) entity.hurt(level.damageSources().magic(), damage);
      }
    } finally {
      TELEPORTING.remove(entity.getUUID());
    }
  }

  private static void fragment(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post e) {
    Player p = e.getEntity();
    if (!(p.level() instanceof ServerLevel l)
        || l.getGameTime() % 100 != 0
        || !p.getInventory().contains(new ItemStack(CollectingPearls.FRAGMENT.get()))) return;
    for (EnderMan end : l.getEntitiesOfClass(EnderMan.class, p.getBoundingBox().inflate(24)))
      if (end.getTarget() == null
          && end.getNavigation().isDone()
          && !end.isLeashed()
          && !end.isNoAi()
          && !end.isPassenger()
          && end.getLastHurtByMob() == null) end.getNavigation().moveTo(p, 0.7);
  }

  private EquipmentModule() {}
}
