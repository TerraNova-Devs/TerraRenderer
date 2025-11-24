package de.terranova.terraRenderer.refactor;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Collections;

/**
 * Utility for spawning and removing NMS entities via packets only
 * (entities are NOT added to the world).
 */
public final class DisplayPackets {

    private DisplayPackets() {}

    /**
     * Sends spawn + metadata packets for a given NMS entity to the given players.
     * The entity is NOT registered in the world – this is purely packet-based.
     */
    public static void spawn(Entity nmsEntity, Collection<Player> players) {
        if (nmsEntity == null || players == null || players.isEmpty()) return;

        ServerLevel nmsWorld = (ServerLevel) nmsEntity.level();

        ServerEntity serverEntity = new ServerEntity(
                nmsWorld,
                nmsEntity,
                0,
                false,
                packet -> {},
                Collections.emptySet()
        );

        Packet<?> spawnPacket = nmsEntity.getAddEntityPacket(serverEntity);

        var dataItems = nmsEntity.getEntityData().packAll();
        ClientboundSetEntityDataPacket dataPacket =
                new ClientboundSetEntityDataPacket(nmsEntity.getId(), dataItems);

        for (Player p : players) {
            if (p == null || !p.isOnline()) continue;

            var handle = ((CraftPlayer) p).getHandle();
            handle.connection.send(spawnPacket);
            handle.connection.send(dataPacket);
        }
    }

    /**
     * Sends a remove-entity packet for the given id to the given players.
     */
    public static void remove(int entityId, Collection<Player> players) {
        if (players == null || players.isEmpty()) return;

        ClientboundRemoveEntitiesPacket removePacket =
                new ClientboundRemoveEntitiesPacket(entityId);

        for (Player p : players) {
            if (p == null || !p.isOnline()) continue;

            var handle = ((CraftPlayer) p).getHandle();
            handle.connection.send(removePacket);
        }
    }

    /**
     * Sends a metadata update for an existing packet-only entity.
     * For Display entities this includes transformation + interpolation settings.
     *
     * IMPORTANT: we do NOT send a teleport here, so the client's built-in
     * interpolation can smoothly animate between old and new transforms.
     */
    public static void update(Entity nmsEntity, Collection<Player> players) {
        if (nmsEntity == null || players == null || players.isEmpty()) return;

        var dataItems = nmsEntity.getEntityData().packAll();
        if (dataItems == null || dataItems.isEmpty()) return;

        ClientboundSetEntityDataPacket dataPacket =
                new ClientboundSetEntityDataPacket(nmsEntity.getId(), dataItems);

        for (Player p : players) {
            if (p == null || !p.isOnline()) continue;

            var handle = ((CraftPlayer) p).getHandle();
            handle.connection.send(dataPacket);
        }
    }
}
