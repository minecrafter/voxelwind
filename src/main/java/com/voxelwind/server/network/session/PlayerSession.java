package com.voxelwind.server.network.session;

import com.flowpowered.math.vector.Vector2i;
import com.flowpowered.math.vector.Vector3f;
import com.flowpowered.math.vector.Vector3i;
import com.google.common.base.Preconditions;
import com.voxelwind.server.level.Level;
import com.voxelwind.server.network.handler.NetworkPacketHandler;
import com.voxelwind.server.network.mcpe.packets.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class PlayerSession {
    private static final Logger LOGGER = LogManager.getLogger(PlayerSession.class);

    private final UserSession session;
    private final Set<Vector2i> sentChunks = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private Vector3f position;
    private Level level;
    private boolean sprinting = false;
    private boolean sneaking = false;

    public PlayerSession(UserSession session) {
        this.session = session;
    }

    public void doInitialSpawn(Level level) {
        Vector3f spawn = level.getSpawnLocation();
        this.level = level;
        this.position = spawn;

        McpeStartGame startGame = new McpeStartGame();
        startGame.setSeed(-1);
        startGame.setDimension((byte) 0);
        startGame.setGenerator(1);
        startGame.setGamemode(0);
        startGame.setEntityId(1);
        startGame.setSpawnLocation(spawn.toInt());
        startGame.setPosition(spawn);
        session.addToSendQueue(startGame);

        McpeAdventureSettings settings = new McpeAdventureSettings();
        settings.setPlayerPermissions(3);
        session.addToSendQueue(settings);

        session.getChannel().eventLoop().schedule(() -> {
            McpePlayStatus status = new McpePlayStatus();
            status.setStatus(McpePlayStatus.Status.PLAYER_SPAWN);
            session.addToSendQueue(status);

            McpeRespawn respawn = new McpeRespawn();
            respawn.setPosition(spawn);
            session.addToSendQueue(respawn);
        }, 1, TimeUnit.SECONDS);
    }

    NetworkPacketHandler getPacketHandler() {
        return new PlayerSessionNetworkPacketHandler();
    }

    private void sendRadius(int radius, boolean updateSent) {
        // Get current player's position in chunks.
        Vector3i positionAsInt = position.toInt();
        int chunkX = positionAsInt.getX() >> 4;
        int chunkZ = positionAsInt.getZ() >> 4;

        // Now get and send chunk data.
        Set<Vector2i> chunksForRadius = new HashSet<>();

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                int newChunkX = chunkX + x, newChunkZ = chunkZ + z;
                Vector2i chunkCoords = new Vector2i(newChunkX, newChunkZ);
                chunksForRadius.add(chunkCoords);

                if (updateSent) {
                    if (!sentChunks.add(chunkCoords)) {
                        // Already sent, don't need to resend.
                        continue;
                    }
                }

                level.getChunk(newChunkX, newChunkZ).whenComplete((chunk, throwable) -> {
                    if (throwable != null) {
                        LOGGER.error("Unable to load chunk", throwable);
                        return;
                    }
                    session.addToSendQueue(chunk.getChunkDataPacket());
                });
            }
        }

        if (updateSent) {
            sentChunks.retainAll(chunksForRadius);
        }
    }

    private class PlayerSessionNetworkPacketHandler implements NetworkPacketHandler {
        @Override
        public void handle(McpeLogin packet) {
            throw new IllegalStateException("Login packet received but player session is currently active!");
        }

        @Override
        public void handle(McpeRequestChunkRadius packet) {
            Preconditions.checkState(level != null, "Player has not been spawned into a level.");
            Preconditions.checkState(position != null, "Player has no set position.");

            int radius = Math.max(5, Math.min(16, packet.getRadius()));
            McpeChunkRadiusUpdated updated = new McpeChunkRadiusUpdated();
            updated.setRadius(radius);
            session.addToSendQueue(updated);

            sendRadius(radius, true);
        }

        @Override
        public void handle(McpePlayerAction packet) {
            switch (packet.getAction()) {
                case ACTION_START_BREAK:
                    // Fire interact
                    break;
                case ACTION_ABORT_BREAK:
                    // No-op
                    break;
                case ACTION_STOP_BREAK:
                    // No-op
                    break;
                case ACTION_RELEASE_ITEM:
                    // Drop item, shoot bow, or dump bucket?
                    break;
                case ACTION_STOP_SLEEPING:
                    // Stop sleeping
                    break;
                case ACTION_SPAWN_SAME_DIMENSION:
                    // Clean up attributes?
                    break;
                case ACTION_JUMP:
                    // No-op
                    break;
                case ACTION_START_SPRINT:
                    sprinting = true;
                    // TODO: Update entity attributes
                    break;
                case ACTION_STOP_SPRINT:
                    sprinting = false;
                    // TODO: Update entity attributes
                    break;
                case ACTION_START_SNEAK:
                    sneaking = true;
                    // TODO: Update entity attributes
                    break;
                case ACTION_STOP_SNEAK:
                    sneaking = false;
                    // TODO: Update entity attributes
                    break;
                case ACTION_SPAWN_OVERWORLD:
                    // Clean up attributes?
                    break;
                case ACTION_SPAWN_NETHER:
                    // Clean up attributes?
                    break;
            }
        }
    }
}
