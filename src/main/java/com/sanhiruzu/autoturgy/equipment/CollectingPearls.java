/* Thrown collecting pearls move only actual loose stacks into physical inventories.
 * Finite flight and once-only return make dispenser and player throws conservative. */
package com.sanhiruzu.autoturgy.equipment;

import com.leclowndu93150.thaumaturge.api.aspect.TTAspects;
import com.sanhiruzu.autoturgy.Content;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.dispenser.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.*;
import net.neoforged.neoforge.registries.*;

public final class CollectingPearls {
  public static final DeferredRegister<EntityType<?>> ENTITIES =
      DeferredRegister.create(net.minecraft.core.registries.Registries.ENTITY_TYPE, "autoturgy");
  public static final DeferredHolder<EntityType<?>, EntityType<Pearl>> TYPE =
      ENTITIES.register(
          "collecting_pearl",
          () ->
              EntityType.Builder.<Pearl>of(Pearl::new, MobCategory.MISC)
                  .sized(.25F, .25F)
                  .clientTrackingRange(8)
                  .updateInterval(10)
                  .build("autoturgy:collecting_pearl"));
  public static DeferredItem<Item> AVARICE, MISER, FRAGMENT;

  public static void register(IEventBus bus) {
    AVARICE = Content.item("avarice_pearl", () -> new PearlItem(false));
    MISER = Content.item("miser_pearl", () -> new PearlItem(true));
    FRAGMENT = Content.item("shattered_avarice", () -> new Item(new Item.Properties()));
    ENTITIES.register(bus);
    bus.addListener(
        (net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent e) ->
            e.enqueueWork(
                () -> {
                  for (var item : List.of(AVARICE, MISER))
                    DispenserBlock.registerBehavior(
                        item.get(),
                        new DefaultDispenseItemBehavior() {
                          protected ItemStack execute(BlockSource source, ItemStack stack) {
                            Direction d = source.state().getValue(DispenserBlock.FACING);
                            Pearl pearl = new Pearl(TYPE.get(), source.level());
                            pearl.machine = source.pos().immutable();
                            pearl.setItem(stack.copyWithCount(1));
                            pearl.setPos(
                                source
                                    .center()
                                    .add(d.getStepX() * .7, d.getStepY() * .7, d.getStepZ() * .7));
                            pearl.shoot(d.getStepX(), d.getStepY() + .1, d.getStepZ(), 1.2F, 3);
                            if (source.level().addFreshEntity(pearl)) stack.shrink(1);
                            return stack;
                          }
                        });
                }));
  }

  private static final class PearlItem extends Item {
    final boolean miser;

    PearlItem(boolean miser) {
      super(new Properties().stacksTo(16));
      this.miser = miser;
    }

    public InteractionResultHolder<ItemStack> use(Level l, Player p, InteractionHand hand) {
      ItemStack stack = p.getItemInHand(hand);
      if (!l.isClientSide) {
        if (miser && !EquipmentModule.canPay(p, TTAspects.ORDO, 25))
          return InteractionResultHolder.fail(stack);
        Pearl pearl = new Pearl(TYPE.get(), l);
        pearl.setOwner(p);
        pearl.setItem(stack.copyWithCount(1));
        pearl.setPos(p.getX(), p.getEyeY() - .1, p.getZ());
        pearl.shootFromRotation(p, p.getXRot(), p.getYRot(), 0, 1.5F, 1);
        if (l.addFreshEntity(pearl)) {
          if (miser && !EquipmentModule.pay(p, TTAspects.ORDO, 25)) {
            pearl.discard();
            return InteractionResultHolder.fail(stack);
          }
          stack.shrink(1);
          p.getCooldowns().addCooldown(this, 10);
        }
      }
      return InteractionResultHolder.sidedSuccess(stack, l.isClientSide);
    }
  }

  public static final class Pearl extends ThrowableItemProjectile {
    public BlockPos machine;
    public int stacks, quantity;
    private int age;
    private boolean finished;

    public Pearl(EntityType<? extends Pearl> type, Level level) {
      super(type, level);
    }

    protected Item getDefaultItem() {
      return AVARICE.get();
    }

    public boolean miser() {
      return getItem().is(MISER.get());
    }

    private ItemStack insert(ItemStack stack) {
      if (getOwner() instanceof Player p
          && p.isAlive()
          && p.level() == level()
          && level().hasChunkAt(p.blockPosition())) {
        ItemStack copy = stack.copy();
        p.getInventory().add(copy);
        return copy;
      }
      if (machineAvailable()) {
        var inv = level().getCapability(Capabilities.ItemHandler.BLOCK, machine, null);
        if (inv != null) return ItemHandlerHelper.insertItemStacked(inv, stack, false);
      }
      return stack;
    }

    private boolean machineAvailable() {
      return machine != null
          && level().hasChunkAt(machine)
          && level().getBlockEntity(machine)
              instanceof net.minecraft.world.level.block.entity.DispenserBlockEntity;
    }

    public void tick() {
      if (!level().isClientSide
          && !level().hasChunkAt(BlockPos.containing(position().add(getDeltaMovement())))) {
        finish();
        return;
      }
      super.tick();
      if (level().isClientSide || finished) return;
      int examined = 0;
      for (ItemEntity item :
          level()
              .getEntitiesOfClass(
                  ItemEntity.class,
                  getBoundingBox().inflate(2.5),
                  e ->
                      e.isAlive()
                          && e.getPersistentData().getLong("autoturgyPearlReturn")
                              < level().getGameTime())) {
        if (++examined > 128 || stacks >= (miser() ? 10 : 64)) break;
        ItemStack original = item.getItem();
        ItemStack left = insert(original.copy());
        int moved = original.getCount() - left.getCount();
        if (moved > 0) {
          stacks++;
          quantity += moved;
          if (left.isEmpty()) item.discard();
          else item.setItem(left);
        }
      }
      if (++age >= 80) finish();
    }

    protected void onHit(HitResult hit) {
      super.onHit(hit);
      if (!level().isClientSide) finish();
    }

    public void finish() {
      if (finished) return;
      finished = true;
      boolean shattered =
          !miser() && quantity > 0 && random.nextInt(2048) < Math.min(1536, quantity + stacks * 16);
      if (!miser()
          && quantity > 0
          && getOwner() instanceof Player p
          && random.nextInt(1024) < Math.min(768, quantity + stacks * 8))
        p.hurt(damageSources().magic(), Math.min(8, 1 + stacks / 4F));
      ItemStack result = shattered ? new ItemStack(FRAGMENT.get()) : getItem().copyWithCount(1);
      ItemStack left = insert(result);
      if (!left.isEmpty()) {
        Vec3 at = position();
        if (getOwner() instanceof Player p
            && p.isAlive()
            && p.level() == level()
            && level().hasChunkAt(p.blockPosition())) at = p.position();
        else if (machineAvailable()) at = Vec3.atCenterOf(machine);
        ItemEntity item = new ItemEntity(level(), at.x, at.y, at.z, left);
        item.setDefaultPickUpDelay();
        item.getPersistentData().putLong("autoturgyPearlReturn", level().getGameTime() + 100);
        level().addFreshEntity(item);
      }
      discard();
    }

    public void addAdditionalSaveData(CompoundTag t) {
      super.addAdditionalSaveData(t);
      if (machine != null) t.putLong("Machine", machine.asLong());
      t.putInt("Age", age);
      t.putInt("Stacks", stacks);
      t.putInt("Quantity", quantity);
    }

    public void readAdditionalSaveData(CompoundTag t) {
      super.readAdditionalSaveData(t);
      machine = t.contains("Machine") ? BlockPos.of(t.getLong("Machine")) : null;
      age = Math.clamp(t.getInt("Age"), 0, 80);
      stacks = Math.clamp(t.getInt("Stacks"), 0, 64);
      quantity = Math.max(0, t.getInt("Quantity"));
    }
  }

  private CollectingPearls() {}
}
