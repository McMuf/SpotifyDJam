package com.spotifydjams.client;

import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public class DirectionalAudio {

    private static int tickCounter = 0;
    private static int lastVolume = -1;

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!JamState.isInJam) return;
            if (!JamState.radiusEnabled) return;
            if (client.player == null) return;

            tickCounter++;
            if (tickCounter < 20) return;
            tickCounter = 0;

            double px = client.player.getX();
            double py = client.player.getY();
            double pz = client.player.getZ();

            double dx = px - (JamState.jukeboxX + 0.5);
            double dy = py - (JamState.jukeboxY + 0.5);
            double dz = pz - (JamState.jukeboxZ + 0.5);
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

            int targetVolume;
            if (distance <= JamState.innerRadius) {
                targetVolume = 100;
            } else if (distance >= JamState.outerRadius) {
                targetVolume = 0;
            } else {
                double range = JamState.outerRadius - JamState.innerRadius;
                double excess = distance - JamState.innerRadius;
                targetVolume = (int)(100 * (1.0 - (excess / range)));
            }

            if (targetVolume != lastVolume) {
                lastVolume = targetVolume;
                setSpotifyVolume(targetVolume);
            }
        });
    }

    private static void setSpotifyVolume(int volume) {
        JsonObject body = new JsonObject();
        body.addProperty("playerId", SpotifyDJamsClient.getPlayerId());
        body.addProperty("volumePercent", volume);

        BackendClient.post("/playback/volume", body)
            .thenAccept(r -> System.out.println("[SpotifyDJams] Volume: " + volume + "%"))
            .exceptionally(e -> null);
    }

    public static void reset() {
        lastVolume = -1;
        setSpotifyVolume(100);
    }
}