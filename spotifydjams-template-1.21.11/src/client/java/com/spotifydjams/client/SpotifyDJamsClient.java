package com.spotifydjams.client;

import com.google.gson.JsonObject;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.JukeboxBlock;
import org.lwjgl.glfw.GLFW;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class SpotifyDJamsClient implements ClientModInitializer {

    private static boolean pauseKeyDown = false;
    private static boolean skipKeyDown = false;
    private static boolean prevKeyDown = false;
    private static boolean shuffleKeyDown = false;
    private static boolean hudKeyDown = false;

    public static String getPlayerId() {
    Minecraft mc = Minecraft.getInstance();
    if (mc.getUser() != null && mc.getUser().getProfileId() != null) {
        return mc.getUser().getProfileId().toString();
    }
    return "unknown-player";
	}

    @Override
    public void onInitializeClient() {
        HudOverlay.register();
        BeatSyncEngine.register();
        DirectionalAudio.register();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[SpotifyDJams] Cleaning up on shutdown...");
            try {
                DirectionalAudio.reset();

                HttpClient client = HttpClient.newHttpClient();

                JsonObject pauseBody = new JsonObject();
                pauseBody.addProperty("playerId", getPlayerId());
                HttpRequest pauseRequest = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:4000/playback/pause"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(pauseBody.toString()))
                    .build();
                client.send(pauseRequest, HttpResponse.BodyHandlers.ofString());
                System.out.println("[SpotifyDJams] Paused Spotify on shutdown");

                JsonObject cleanupBody = new JsonObject();
                cleanupBody.addProperty("playerId", getPlayerId());
                HttpRequest cleanupRequest = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:4000/jams/cleanup"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(cleanupBody.toString()))
                    .build();
                HttpResponse<String> cleanupResponse = client.send(cleanupRequest,
                    HttpResponse.BodyHandlers.ofString());
                System.out.println("[SpotifyDJams] Cleanup: " + cleanupResponse.body());
            } catch (Exception e) {
                System.out.println("[SpotifyDJams] Cleanup failed: " + e.getMessage());
            }
        }));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            if (JamState.isInJam) {
                BlockPos jukeboxPos = new BlockPos(JamState.jukeboxX, JamState.jukeboxY, JamState.jukeboxZ);
                if (client.level != null &&
                    !client.level.getBlockState(jukeboxPos).is(Blocks.JUKEBOX)) {
                    JamState.isInJam = false;
                    JamState.hudVisible = true;
                    BeatSyncEngine.resetTrack();
                    sendPlaybackCommand("/playback/pause");
                    client.player.displayClientMessage(
                        Component.literal("[SpotifyDJams] Jukebox removed — jam ended"), false);
                    return;
                }
            }

            if (!JamState.isInJam) return;
            if (client.screen != null) return;

            long window = GLFW.glfwGetCurrentContext();

            if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_H) == GLFW.GLFW_PRESS) {
                if (!hudKeyDown) {
                    hudKeyDown = true;
                    JamState.hudVisible = !JamState.hudVisible;
                    if (!JamState.hudVisible) HudOverlay.flashHiddenHint();
                }
            } else {
                hudKeyDown = false;
            }

            if (GLFW.glfwGetKey(window, JamState.keyPause) == GLFW.GLFW_PRESS) {
                if (!pauseKeyDown) {
                    pauseKeyDown = true;
                    if (HudOverlay.isPaused) {
                        sendPlaybackCommand("/playback/resume");
                        HudOverlay.isPaused = false;
                        client.player.displayClientMessage(
                            Component.literal("[SpotifyDJams] Resumed"), false);
                    } else {
                        sendPlaybackCommand("/playback/pause");
                        HudOverlay.isPaused = true;
                        client.player.displayClientMessage(
                            Component.literal("[SpotifyDJams] Paused"), false);
                    }
                }
            } else {
                pauseKeyDown = false;
            }

            if (GLFW.glfwGetKey(window, JamState.keySkip) == GLFW.GLFW_PRESS) {
                if (!skipKeyDown) {
                    skipKeyDown = true;
                    sendPlaybackCommand("/playback/skip");
                    client.player.displayClientMessage(
                        Component.literal("[SpotifyDJams] Skipped"), false);
                }
            } else {
                skipKeyDown = false;
            }

            if (GLFW.glfwGetKey(window, JamState.keyPrev) == GLFW.GLFW_PRESS) {
                if (!prevKeyDown) {
                    prevKeyDown = true;
                    sendPlaybackCommand("/playback/prev");
                    client.player.displayClientMessage(
                        Component.literal("[SpotifyDJams] Previous"), false);
                }
            } else {
                prevKeyDown = false;
            }

            if (GLFW.glfwGetKey(window, JamState.keyShuffle) == GLFW.GLFW_PRESS) {
                if (!shuffleKeyDown) {
                    shuffleKeyDown = true;
                    toggleShuffle();
                }
            } else {
                shuffleKeyDown = false;
            }
        });

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (!world.isClientSide()) return InteractionResult.PASS;
            if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;

            if (world.getBlockState(hitResult.getBlockPos()).is(Blocks.JUKEBOX)) {
                BlockPos pos = hitResult.getBlockPos();

                if (player.isShiftKeyDown()) {
                    Minecraft.getInstance().setScreen(
                        new MainMenuScreen(getPlayerId(), pos)
                    );
                    return InteractionResult.SUCCESS;
                }

                boolean hasDisc = world.getBlockState(pos).getValue(JukeboxBlock.HAS_RECORD);
                if (hasDisc) return InteractionResult.PASS;

                boolean holdingDisc = player.getItemInHand(hand)
                    .get(DataComponents.JUKEBOX_PLAYABLE) != null;
                if (holdingDisc) return InteractionResult.PASS;

                if (!OnboardingManager.hasSeenOnboarding()) {
                    Minecraft.getInstance().setScreen(
                        new OnboardingScreen(getPlayerId(), pos)
                    );
                } else {
                    Minecraft.getInstance().setScreen(
                        new MainMenuScreen(getPlayerId(), pos)
                    );
                }
                return InteractionResult.SUCCESS;
            }

            return InteractionResult.PASS;
        });
    }

    private static void toggleShuffle() {
        boolean newState = !JamState.isShuffled;
        JsonObject body = new JsonObject();
        body.addProperty("playerId", getPlayerId());
        body.addProperty("state", newState);

        BackendClient.post("/playback/shuffle", body)
            .thenAccept(response -> {
                Minecraft.getInstance().execute(() -> {
                    if (response != null && response.has("success")) {
                        boolean success = response.get("success").getAsBoolean();
                        boolean premium = !response.has("premium")
                            || response.get("premium").getAsBoolean();
                        if (success) {
                            JamState.isShuffled = newState;
                            JamState.isPremium = true;
                            HudOverlay.showShuffleMessage(newState ? "⇄ Shuffle ON" : "⇄ Shuffle OFF");
                        } else if (!premium) {
                            JamState.isPremium = false;
                            HudOverlay.showShuffleMessage("✗ Requires Spotify Premium");
                        } else {
                            HudOverlay.showShuffleMessage("✗ Shuffle failed");
                        }
                    }
                });
            })
            .exceptionally(e -> null);
    }

    private static void sendPlaybackCommand(String endpoint) {
        JsonObject body = new JsonObject();
        body.addProperty("playerId", getPlayerId());
        BackendClient.post(endpoint, body)
            .thenAccept(r -> System.out.println("[SpotifyDJams] Command: " + endpoint))
            .exceptionally(e -> null);
    }
}