package com.spotifydjams.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

public class BeatSyncEngine {

    private static List<Long> beatTimestamps = new ArrayList<>();
    private static long currentPositionMs = 0;
    private static long lastFetchTimeMs = 0;
    private static long lastFetchPositionMs = 0;
    private static boolean isPlaying = false;
    private static String currentTrackId = null;
    private static boolean needsReset = false;

    private static int nextBeatIndex = 0;
    private static int tickCounter = 0;
    private static int analysisPollCounter = 0;
    private static int beatCount = 0;
    private static float hue = 0f;

    public static void resetTrack() {
        needsReset = true;
        System.out.println("[SpotifyDJams] Beat engine reset requested");
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!JamState.isInJam) return;
            if (client.player == null) return;
            if (client.level == null) return;

            if (needsReset) {
                needsReset = false;
                currentTrackId = null;
                beatTimestamps.clear();
                nextBeatIndex = 0;
                beatCount = 0;
                System.out.println("[SpotifyDJams] Beat engine reset applied");
            }

            tickCounter++;
            analysisPollCounter++;

            if (tickCounter >= 20) {
                tickCounter = 0;
                fetchPlaybackPosition();
            }

            if (analysisPollCounter >= 600) {
                analysisPollCounter = 0;
                if (currentTrackId != null) fetchBeatAnalysis(currentTrackId);
            }

            if (isPlaying && lastFetchTimeMs > 0) {
                long elapsed = System.currentTimeMillis() - lastFetchTimeMs;
                currentPositionMs = lastFetchPositionMs + elapsed;
            }

            if (!beatTimestamps.isEmpty() && nextBeatIndex < beatTimestamps.size()) {
                long nextBeat = beatTimestamps.get(nextBeatIndex);
                if (currentPositionMs >= nextBeat) {
                    nextBeatIndex++;
                    beatCount++;
                    int freq = getFrequencyValue();
                    if (isPlaying && beatCount % freq == 0) {
                        spawnBeatParticles(client.level);
                    }
                }
            }
        });
    }

    private static int getFrequencyValue() {
        return switch (JamState.beatFrequency) {
            case 1 -> 2;
            case 2 -> 4;
            default -> 1;
        };
    }

    private static void fetchPlaybackPosition() {
        String playerId = SpotifyDJamsClient.getPlayerId();
        BackendClient.get("/playback/position?playerId=" + playerId)
            .thenAccept(response -> {
                if (response == null) return;
                Minecraft.getInstance().execute(() -> {
                    if (!response.has("trackId")) return;

                    boolean isAd = response.has("isAd") && response.get("isAd").getAsBoolean();
                    JamState.isAd = isAd;

                    if (isAd) {
                        isPlaying = false;
                        HudOverlay.isPaused = false;
                        return;
                    }

                    if (response.get("trackId").isJsonNull()) return;

                    String newTrackId = response.get("trackId").getAsString();
                    long newPosition = response.get("positionMs").getAsLong();
                    boolean playing = response.get("isPlaying").getAsBoolean();

                    if (currentTrackId == null || !newTrackId.equals(currentTrackId)) {
                        System.out.println("[SpotifyDJams] Track changed to: " + newTrackId);
                        currentTrackId = newTrackId;
                        beatTimestamps.clear();
                        nextBeatIndex = 0;
                        beatCount = 0;
                        fetchBeatAnalysis(newTrackId);

                        if (response.has("trackName") && !response.get("trackName").isJsonNull()) {
                            JamState.currentTrackName = response.get("trackName").getAsString();
                        }
                        if (response.has("artistName") && !response.get("artistName").isJsonNull()) {
                            JamState.currentArtistName = response.get("artistName").getAsString();
                        }
                    }

                    lastFetchPositionMs = newPosition;
                    lastFetchTimeMs = System.currentTimeMillis();
                    isPlaying = playing;
                    HudOverlay.isPaused = !playing;

                    resyncBeatIndex();
                });
            })
            .exceptionally(e -> {
                System.out.println("[SpotifyDJams] Position fetch error: " + e.getMessage());
                return null;
            });
    }

    private static void fetchBeatAnalysis(String trackId) {
        System.out.println("[SpotifyDJams] Fetching beat analysis for: " + trackId);
        String playerId = SpotifyDJamsClient.getPlayerId();
        int bpm = JamState.bpm;
        int beatIntervalMs = Math.round(60000f / bpm);

        BackendClient.get("/playback/analysis?playerId=" + playerId + "&trackId=" + trackId)
            .thenAccept(response -> {
                Minecraft.getInstance().execute(() -> {
                    if (response != null && response.has("beats")) {
                        JsonArray beats = response.getAsJsonArray("beats");
                        beatTimestamps.clear();
                        for (int i = 0; i < beats.size(); i++) {
                            JsonObject beat = beats.get(i).getAsJsonObject();
                            beatTimestamps.add(beat.get("start").getAsLong());
                        }
                    } else {
                        beatTimestamps.clear();
                        for (long t = 0; t < 600000; t += beatIntervalMs) {
                            beatTimestamps.add(t);
                        }
                    }
                    resyncBeatIndex();
                    System.out.println("[SpotifyDJams] Loaded " + beatTimestamps.size() + " beats at " + bpm + " BPM");
                });
            })
            .exceptionally(e -> {
                Minecraft.getInstance().execute(() -> {
                    beatTimestamps.clear();
                    for (long t = 0; t < 600000; t += beatIntervalMs) {
                        beatTimestamps.add(t);
                    }
                    resyncBeatIndex();
                });
                return null;
            });
    }

    private static void resyncBeatIndex() {
        nextBeatIndex = 0;
        for (int i = 0; i < beatTimestamps.size(); i++) {
            if (beatTimestamps.get(i) > currentPositionMs) {
                nextBeatIndex = i;
                break;
            }
        }
    }

    private static SimpleParticleType getParticleType() {
        return switch (JamState.particleType) {
            case 1 -> ParticleTypes.SOUL_FIRE_FLAME;
            case 2 -> ParticleTypes.END_ROD;
            case 3 -> ParticleTypes.WITCH;
            case 4 -> ParticleTypes.GLOW;
            default -> ParticleTypes.FLAME;
        };
    }

    private static void spawnBeatParticles(Level level) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        BlockPos playerPos = mc.player.blockPosition();
        BlockPos jukeboxPos = findNearestJukebox(level, playerPos, 32);
        if (jukeboxPos == null) return;

        double cx = jukeboxPos.getX() + 0.5;
        double cy = jukeboxPos.getY() + 0.05;
        double cz = jukeboxPos.getZ() + 0.5;

        hue = (hue + 0.1f) % 1.0f;

        SimpleParticleType particle;
        if (JamState.particleType == 5) {
            int rgbIndex = (int)(hue * 5) % 5;
            particle = switch (rgbIndex) {
                case 1 -> ParticleTypes.SOUL_FIRE_FLAME;
                case 2 -> ParticleTypes.END_ROD;
                case 3 -> ParticleTypes.WITCH;
                case 4 -> ParticleTypes.GLOW;
                default -> ParticleTypes.FLAME;
            };
        } else {
            particle = getParticleType();
        }

        switch (JamState.particleStyle) {
            case 0 -> spawnRings(level, particle, cx, cy, cz);
            case 1 -> spawnBurst(level, particle, cx, cy, cz);
            case 2 -> spawnSpiral(level, particle, cx, cy, cz);
            case 3 -> spawnFountain(level, particle, cx, cy, cz);
        }
    }

    private static void spawnRings(Level level, SimpleParticleType particle,
                                    double cx, double cy, double cz) {
        for (int ring = 0; ring < 4; ring++) {
            double radius = 0.2 + (ring * 0.35);
            int count = 12 + (ring * 4);
            for (int i = 0; i < count; i++) {
                double angle = (Math.PI * 2.0 / count) * i;
                double vx = Math.cos(angle) * 0.05;
                double vz = Math.sin(angle) * 0.05;
                level.addParticle(particle,
                    cx + Math.cos(angle) * radius, cy,
                    cz + Math.sin(angle) * radius, vx, 0.01, vz);
            }
        }
    }

    private static void spawnBurst(Level level, SimpleParticleType particle,
                                    double cx, double cy, double cz) {
        int count = 32;
        for (int i = 0; i < count; i++) {
            double angle = (Math.PI * 2.0 / count) * i;
            double vy = Math.random() * 0.15;
            level.addParticle(particle, cx, cy, cz,
                Math.cos(angle) * 0.2, vy, Math.sin(angle) * 0.2);
        }
    }

    private static void spawnSpiral(Level level, SimpleParticleType particle,
                                     double cx, double cy, double cz) {
        float spiralOffset = (beatCount * 0.5f) % (float)(Math.PI * 2);
        int count = 20;
        for (int i = 0; i < count; i++) {
            double t = (double) i / count;
            double angle = spiralOffset + t * Math.PI * 4;
            double radius = t * 1.5;
            level.addParticle(particle,
                cx + Math.cos(angle) * radius, cy,
                cz + Math.sin(angle) * radius,
                Math.cos(angle) * 0.04, 0.02, Math.sin(angle) * 0.04);
        }
    }

    private static void spawnFountain(Level level, SimpleParticleType particle,
                                       double cx, double cy, double cz) {
        for (int i = 0; i < 20; i++) {
            double angle = Math.random() * Math.PI * 2;
            double speed = 0.05 + Math.random() * 0.1;
            level.addParticle(particle, cx, cy + 0.5, cz,
                Math.cos(angle) * speed, 0.2 + Math.random() * 0.2,
                Math.sin(angle) * speed);
        }
    }

    private static BlockPos findNearestJukebox(Level level, BlockPos center, int radius) {
        BlockPos nearest = null;
        double nearestDist = Double.MAX_VALUE;

        for (int x = -radius; x <= radius; x++) {
            for (int y = -4; y <= 4; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = center.offset(x, y, z);
                    if (level.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.JUKEBOX)) {
                        double dist = center.distSqr(pos);
                        if (dist < nearestDist) {
                            nearestDist = dist;
                            nearest = pos;
                        }
                    }
                }
            }
        }
        return nearest;
    }
}