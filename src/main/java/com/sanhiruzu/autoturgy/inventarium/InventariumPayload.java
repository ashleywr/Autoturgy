/* Bounded catalogue intents carry a menu identity and display revision.
 * The server resolves the active menu and validates every query and request there. */
package com.sanhiruzu.autoturgy.inventarium;

import com.sanhiruzu.autoturgy.Content;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public record InventariumPayload(
    int menu, int revision, int action, String query, int page, boolean byCount)
    implements CustomPacketPayload {
  public static final Type<InventariumPayload> TYPE = new Type<>(Content.id("inventarium_intent"));
  public static final StreamCodec<RegistryFriendlyByteBuf, InventariumPayload> CODEC =
      StreamCodec.of(
          (buf, p) -> {
            buf.writeVarInt(p.menu);
            buf.writeVarInt(p.revision);
            buf.writeVarInt(p.action);
            buf.writeUtf(p.query, 64);
            buf.writeVarInt(p.page);
            buf.writeBoolean(p.byCount);
          },
          buf ->
              new InventariumPayload(
                  buf.readVarInt(),
                  buf.readVarInt(),
                  buf.readVarInt(),
                  buf.readUtf(64),
                  buf.readVarInt(),
                  buf.readBoolean()));

  public Type<InventariumPayload> type() {
    return TYPE;
  }

  public static void register(IEventBus bus) {
    bus.addListener(
        (RegisterPayloadHandlersEvent event) ->
            event
                .registrar("1")
                .playToServer(
                    TYPE,
                    CODEC,
                    (payload, context) ->
                        context.enqueueWork(
                            () -> {
                              var player = context.player();
                              if (player.containerMenu instanceof InventariumMenu menu
                                  && menu.containerId == payload.menu)
                                menu.intent(
                                    player,
                                    payload.revision,
                                    payload.action,
                                    payload.query,
                                    payload.page,
                                    payload.byCount);
                            })));
  }
}
