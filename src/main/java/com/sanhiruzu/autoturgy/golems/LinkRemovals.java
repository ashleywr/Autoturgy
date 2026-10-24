/* Defers unlink and dead-worker metadata cleanup while a device chunk is absent.
 * A rotating loaded-only maintenance queue retires notices after load or replacement. */
package com.sanhiruzu.autoturgy.golems;

import com.sanhiruzu.autoturgy.common.LoadedTargets;
import com.sanhiruzu.autoturgy.inventarium.InventariumModule;
import java.util.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

public final class LinkRemovals extends SavedData {
  private record Notice(LoadedTargets.Target target, Set<UUID> workers) {}

  private record Key(boolean death, UUID device) {}

  private final Map<UUID, Notice> unlinks = new HashMap<>(), deaths = new HashMap<>();
  private final ArrayDeque<Key> queue = new ArrayDeque<>();

  static LinkRemovals get(MinecraftServer server) {
    return server
        .overworld()
        .getDataStorage()
        .computeIfAbsent(
            new SavedData.Factory<>(LinkRemovals::new, LinkRemovals::load), "autoturgy_unlinks");
  }

  private void add(boolean death, UUID device, UUID worker, LoadedTargets.Target at) {
    var map = death ? deaths : unlinks;
    Notice notice = map.get(device);
    if (notice == null) {
      notice = new Notice(at, new HashSet<>());
      map.put(device, notice);
      queue.addLast(new Key(death, device));
    }
    notice.workers.add(worker);
    setDirty();
  }

  void add(UUID device, UUID worker, LoadedTargets.Target at) {
    add(false, device, worker, at);
  }

  void deadWorker(UUID core, UUID worker, LoadedTargets.Target at) {
    add(true, core, worker, at);
  }

  void apply(GolemModule.LinkEntity device) {
    Notice notice = unlinks.remove(device.identity);
    if (notice != null) {
      device.links.removeAll(notice.workers);
      device.changed();
      setDirty();
    }
  }

  private void apply(InventariumModule.CoreEntity core) {
    Notice notice = deaths.remove(core.identity);
    if (notice != null) {
      for (UUID worker : notice.workers) core.orders.workerDied(worker);
      core.changed();
      setDirty();
    }
  }

  public static void coreLoaded(InventariumModule.CoreEntity core) {
    get(core.getLevel().getServer()).apply(core);
  }

  static void maintain(MinecraftServer server) {
    get(server).maintenance(server);
  }

  private void maintenance(MinecraftServer server) {
    for (int i = 0, limit = Math.min(64, queue.size()); i < limit; i++) {
      Key key = queue.removeFirst();
      var map = key.death ? deaths : unlinks;
      Notice notice = map.get(key.device);
      if (notice == null) continue;
      var level = LoadedTargets.level(server, notice.target).orElse(null);
      if (level == null) {
        queue.addLast(key);
        continue;
      }
      var be = level.getBlockEntity(notice.target.pos());
      if (key.death
          && be instanceof InventariumModule.CoreEntity core
          && core.identity.equals(key.device)) apply(core);
      else if (!key.death
          && be instanceof GolemModule.LinkEntity device
          && device.identity.equals(key.device)) apply(device);
      else {
        map.remove(key.device);
        setDirty();
      }
    }
  }

  private static LinkRemovals load(CompoundTag tag, HolderLookup.Provider lookup) {
    var result = new LinkRemovals();
    for (boolean death : new boolean[] {false, true}) {
      ListTag list = tag.getList(death ? "deaths" : "unlinks", Tag.TAG_COMPOUND);
      for (int i = 0; i < list.size(); i++) {
        CompoundTag row = list.getCompound(i);
        var at = LoadedTargets.Target.load(row.getCompound("target")).orElse(null);
        if (at != null && row.hasUUID("device") && row.hasUUID("worker"))
          result.add(death, row.getUUID("device"), row.getUUID("worker"), at);
      }
    }
    return result;
  }

  public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
    for (boolean death : new boolean[] {false, true}) {
      ListTag list = new ListTag();
      (death ? deaths : unlinks)
          .forEach(
              (device, notice) ->
                  notice.workers.forEach(
                      worker -> {
                        CompoundTag row = new CompoundTag();
                        row.putUUID("device", device);
                        row.putUUID("worker", worker);
                        row.put("target", notice.target.save());
                        list.add(row);
                      }));
      tag.put(death ? "deaths" : "unlinks", list);
    }
    return tag;
  }
}
