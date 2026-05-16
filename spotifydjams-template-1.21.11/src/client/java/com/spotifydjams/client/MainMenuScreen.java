package com.spotifydjams.client;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

public class MainMenuScreen extends Screen {

    private final String playerId;
    private final BlockPos jukeboxPos;
    private String statusMessage = null;

    public MainMenuScreen(String playerId, BlockPos jukeboxPos) {
        super(Component.literal("SpotifyDJams"));
        this.playerId = playerId;
        this.jukeboxPos = jukeboxPos;
    }

    @Override
    protected void init() {
        super.init();

        int panelWidth = 300;
        int panelHeight = 200;
        int panelX = (this.width - panelWidth) / 2;
        int panelY = (this.height - panelHeight) / 2;

        int btnW = 130;
        int btnH = 50;
        int gap = 10;

        int col1 = panelX + 10;
        int col2 = panelX + 10 + btnW + gap;
        int row1 = panelY + 40;
        int row2 = panelY + 40 + btnH + gap;

        this.addRenderableWidget(Button.builder(
            Component.literal("♫ Browse Playlists"),
            btn -> Minecraft.getInstance().setScreen(
                new JukeboxScreen(playerId, jukeboxPos.getX(), jukeboxPos.getY(), jukeboxPos.getZ())
            )
        ).bounds(col1, row1, btnW, btnH).build());

        this.addRenderableWidget(Button.builder(
            Component.literal("✦ Create Jam"),
            btn -> Minecraft.getInstance().setScreen(
                new CreateJamScreen(playerId, jukeboxPos)
            )
        ).bounds(col2, row1, btnW, btnH).build());

        this.addRenderableWidget(Button.builder(
            Component.literal("⇢ Join Jam"),
            btn -> Minecraft.getInstance().setScreen(
                new JoinJamScreen(playerId, jukeboxPos)
            )
        ).bounds(col1, row2, btnW, btnH).build());

        this.addRenderableWidget(Button.builder(
            Component.literal("⚙ Settings"),
            btn -> Minecraft.getInstance().setScreen(
                new SettingsScreen(playerId)
            )
        ).bounds(col2, row2, btnW, btnH).build());

        activateDevice();
    }

    private void activateDevice() {
        // Don't transfer playback if already in a jam — causes stutter
        if (JamState.isInJam) {
            statusMessage = "✓ Jam: " + JamState.currentPlaylistName;
            return;
        }

        JsonObject body = new JsonObject();
        body.addProperty("playerId", playerId);

        BackendClient.post("/playback/activate", body)
            .thenAccept(response -> {
                if (response == null) {
                    Minecraft.getInstance().execute(() ->
                        statusMessage = "⚠ Open Spotify on any device first");
                    return;
                }
                Minecraft.getInstance().execute(() -> {
                    if (response.has("success") && response.get("success").getAsBoolean()) {
                        String deviceName = response.has("deviceName")
                            ? response.get("deviceName").getAsString() : "unknown";
                        statusMessage = "✓ Active on: " + deviceName;
                    } else {
                        statusMessage = "⚠ Open Spotify on any device first";
                        try {
                            java.awt.Desktop.getDesktop().browse(
                                java.net.URI.create("spotify:")
                            );
                        } catch (Exception e) {
                            try {
                                java.awt.Desktop.getDesktop().browse(
                                    java.net.URI.create("https://open.spotify.com")
                                );
                            } catch (Exception e2) {
                                System.out.println("[SpotifyDJams] Could not open Spotify");
                            }
                        }
                    }
                });
            })
            .exceptionally(e -> {
                Minecraft.getInstance().execute(() ->
                    statusMessage = "⚠ Open Spotify on any device first");
                return null;
            });
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0x88000000);

        int panelWidth = 300;
        int panelHeight = 200;
        int panelX = (this.width - panelWidth) / 2;
        int panelY = (this.height - panelHeight) / 2;

        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xFF0a0a0a);
        graphics.fill(panelX,                  panelY,                   panelX + panelWidth, panelY + 1,           0xFF1DB954);
        graphics.fill(panelX,                  panelY + panelHeight - 1, panelX + panelWidth, panelY + panelHeight, 0xFF1DB954);
        graphics.fill(panelX,                  panelY,                   panelX + 1,          panelY + panelHeight, 0xFF1DB954);
        graphics.fill(panelX + panelWidth - 1, panelY,                   panelX + panelWidth, panelY + panelHeight, 0xFF1DB954);

        graphics.drawString(this.font, "SpotifyDJams", panelX + 10, panelY + 10, 0xFF1DB954);
        graphics.drawString(this.font, "What would you like to do?", panelX + 10, panelY + 22, 0xFF888888);

        if (statusMessage != null) {
            int color = statusMessage.startsWith("✓") ? 0xFF1DB954 : 0xFFFFAA00;
            graphics.drawString(this.font, statusMessage, panelX + 10, panelY + panelHeight - 12, color);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}