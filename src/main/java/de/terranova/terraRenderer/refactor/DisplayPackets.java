package de.terranova.terraRenderer.refactor;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Collections;

public final class DisplayPackets {

    private DisplayPackets() {}

    public static void spawn(Display.BlockDisplay entity, Collection<Player> players) {
        if (entity == null || players == null || players.isEmpty()) return;

        ServerLevel nmsWorld = (ServerLevel) entity.level();

        ServerEntity serverEntity = new ServerEntity(
                nmsWorld,
                entity,
                0,
                false,
                packet -> {},
                Collections.emptySet()
        );

        Packet<?> spawnPacket = entity.getAddEntityPacket(serverEntity);
        var dataItems = entity.getEntityData().packAll();
        ClientboundSetEntityDataPacket dataPacket =
                new ClientboundSetEntityDataPacket(entity.getId(), dataItems);

        sendToPlayers(players, spawnPacket, dataPacket);
    }

    public static void remove(int entityId, Collection<Player> players) {
        if (players == null || players.isEmpty()) return;

        ClientboundRemoveEntitiesPacket removePacket =
                new ClientboundRemoveEntitiesPacket(entityId);

        sendToPlayers(players, removePacket);
    }

    private static void sendToPlayers(Collection<Player> players, Packet<?>... packets) {
        for (Player player : players) {
            if (player == null || !player.isOnline()) continue;

            var handle = ((CraftPlayer) player).getHandle();
            for (Packet<?> packet : packets) {
                handle.connection.send(packet);
            }
        }
    }
}
