package com.spotifydjams.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;


import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class JukeboxScreen extends Screen {

    private List<JsonObject> playlists = new ArrayList<>();
    private boolean loading = true;
    private String errorMessage = null;
    private int scrollOffset = 0;
    private String playerId;
    private int jukeboxX, jukeboxY, jukeboxZ;
    private boolean creatingJam = false;
    private String statusMessage = null;

    private String currentTrack = null;
    private String currentArtist = null;
    private boolean isPlaying = false;
    private int trackPollTimer = 0;

    public JukeboxScreen(String playerId, int x, int y, int z) {
        super(Component.literal("SpotifyDJams"));
        this.playerId = playerId;
        this.jukeboxX = x;
        this.jukeboxY = y;
        this.jukeboxZ = z;
    }

    @Override
    protected void init() {
        super.init();
        fetchPlaylists();
        fetchCurrentTrack();
        rebuildButtons();
    }

    @Override
    public void tick() {
        trackPollTimer++;
        if (trackPollTimer >= 40) {
            trackPollTimer = 0;
            fetchCurrentTrack();
        }
    }

    private void fetchCurrentTrack() {
        BackendClient.get("/playback/current?playerId=" + playerId)
            .thenAccept(response -> {
                if (response != null && response.has("trackName")
                    && !response.get("trackName").isJsonNull()) {
                    String track = response.get("trackName").getAsString();
                    String artist = response.get("artistName").isJsonNull()
                        ? "" : response.get("artistName").getAsString();
                    boolean playing = response.get("isPlaying").getAsBoolean();
                    Minecraft.getInstance().execute(() -> {
                        currentTrack = track;
                        currentArtist = artist;
                        isPlaying = playing;
                        JamState.currentTrackName = track;
                        JamState.currentArtistName = artist;
                        HudOverlay.isPaused = !playing;
                    });
                } else {
                    Minecraft.getInstance().execute(() -> {
                        currentTrack = "Nothing playing";
                        currentArtist = "";
                    });
                }
            })
            .exceptionally(e -> {
                Minecraft.getInstance().execute(() -> currentTrack = "Error fetching track");
                return null;
            });
    }

    private void rebuildButtons() {
        this.clearWidgets();
        if (loading || creatingJam || playlists.isEmpty()) return;

        int panelWidth = 260;
        int panelHeight = 220;
        int panelX = (this.width - panelWidth) / 2;
        int panelY = (this.height - panelHeight) / 2;

        int rowHeight = 22;
        int listStartY = JamState.isInJam ? panelY + 95 : panelY + 40;
        int visibleRows = JamState.isInJam ? 4 : 7;

        for (int i = 0; i < Math.min(visibleRows, playlists.size() - scrollOffset); i++) {
            int index = i + scrollOffset;
            JsonObject playlist = playlists.get(index);
            String name = playlist.get("name").getAsString();
            String playlistId = playlist.get("id").getAsString();
            int rowY = listStartY + (i * rowHeight);

            this.addRenderableWidget(
                net.minecraft.client.gui.components.Button.builder(
                    Component.literal("▶"),
                    btn -> startJam(playlistId, name)
                ).bounds(panelX + 8, rowY, 18, 16).build()
            );
        }
    }

    private void fetchPlaylists() {
        loading = true;
        BackendClient.get("/playlists?playerId=" + playerId)
            .thenAccept(response -> {
                if (response != null && response.has("playlists")) {
                    JsonArray arr = response.getAsJsonArray("playlists");
                    List<JsonObject> result = new ArrayList<>();
                    for (int i = 0; i < arr.size(); i++) {
                        result.add(arr.get(i).getAsJsonObject());
                    }
                    Minecraft.getInstance().execute(() -> {
                        this.playlists = result;
                        this.loading = false;
                        rebuildButtons();
                    });
                } else {
                    Minecraft.getInstance().execute(() -> {
                        this.errorMessage = "Could not load playlists.";
                        this.loading = false;
                    });
                }
            })
            .exceptionally(e -> {
                Minecraft.getInstance().execute(() -> {
                    this.errorMessage = "Backend not running!";
                    this.loading = false;
                });
                return null;
            });
    }

    private void startJam(String playlistId, String playlistName) {
        creatingJam = true;
        statusMessage = "Starting jam...";
        this.clearWidgets();

        JsonObject body = new JsonObject();
        body.addProperty("hostUuid", playerId);
        body.addProperty("jukeboxX", jukeboxX);
        body.addProperty("jukeboxY", jukeboxY);
        body.addProperty("jukeboxZ", jukeboxZ);
        body.addProperty("playlistId", playlistId);

        BackendClient.post("/jams/create", body)
            .thenAccept(response -> {
                if (response != null && response.has("jamId")) {
                    String jamId = response.get("jamId").getAsString();
                    Minecraft.getInstance().execute(() -> {
                        JamState.currentJamId = jamId;
                        JamState.currentPlaylistName = playlistName;
                        JamState.isInJam = true;
                        JamState.jukeboxX = jukeboxX;
                        JamState.jukeboxY = jukeboxY;
                        JamState.jukeboxZ = jukeboxZ;
                        BeatSyncEngine.resetTrack();
                        Minecraft.getInstance().player.displayClientMessage(
                            Component.literal("[SpotifyDJams] Jam started! Playing: " + playlistName),
                            false
                        );
                        this.onClose();
                    });
                } else {
                    Minecraft.getInstance().execute(() -> {
                        statusMessage = "Failed to start jam.";
                        creatingJam = false;
                        rebuildButtons();
                    });
                }
            })
            .exceptionally(e -> {
                Minecraft.getInstance().execute(() -> {
                    statusMessage = "Error connecting to backend.";
                    creatingJam = false;
                    rebuildButtons();
                });
                return null;
            });
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0x88000000);

        int panelWidth = 260;
        int panelHeight = 220;
        int panelX = (this.width - panelWidth) / 2;
        int panelY = (this.height - panelHeight) / 2;

        // Background
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xFF0a0a0a);

        // Border
        graphics.fill(panelX,                  panelY,                   panelX + panelWidth, panelY + 1,           0xFF1DB954);
        graphics.fill(panelX,                  panelY + panelHeight - 1, panelX + panelWidth, panelY + panelHeight, 0xFF1DB954);
        graphics.fill(panelX,                  panelY,                   panelX + 1,          panelY + panelHeight, 0xFF1DB954);
        graphics.fill(panelX + panelWidth - 1, panelY,                   panelX + panelWidth, panelY + panelHeight, 0xFF1DB954);

        graphics.drawString(this.font, "SpotifyDJams", panelX + 8, panelY + 6, 0xFF1DB954);

        if (JamState.isInJam) {
            // Now playing section
            graphics.fill(panelX + 6, panelY + 17, panelX + panelWidth - 6, panelY + 18, 0xFF1DB954);
            graphics.drawString(this.font, "NOW PLAYING", panelX + 8, panelY + 21, 0xFF1DB954);

            String track = currentTrack != null ? currentTrack : "Loading...";
            String artist = currentArtist != null ? currentArtist : "";
            String displayTrack = track.length() > 30 ? track.substring(0, 27) + "..." : track;
            String displayArtist = artist.length() > 30 ? artist.substring(0, 27) + "..." : artist;

            graphics.drawString(this.font, displayTrack, panelX + 8, panelY + 31, 0xFFFFFFFF);
            graphics.drawString(this.font, displayArtist, panelX + 8, panelY + 41, 0xFF888888);

            String status = isPlaying ? "▶ Playing" : "⏸ Paused";
            graphics.drawString(this.font, status, panelX + 8, panelY + 51, isPlaying ? 0xFF1DB954 : 0xFFAAAAAA);

            graphics.fill(panelX + 6, panelY + 63, panelX + panelWidth - 6, panelY + 64, 0xFF222222);
            graphics.drawString(this.font, "SWITCH PLAYLIST", panelX + 8, panelY + 68, 0xFF888888);

            // Playlist rows
            int rowHeight = 22;
            int listStartY = panelY + 95;
            int visibleRows = 4;

            if (loading) {
                graphics.drawString(this.font, "Loading...", panelX + 8, listStartY, 0xFFAAAAAA);
            } else if (creatingJam) {
                graphics.drawString(this.font, statusMessage != null ? statusMessage : "Starting...", panelX + 8, listStartY, 0xFF1DB954);
            } else {
                for (int i = 0; i < Math.min(visibleRows, playlists.size() - scrollOffset); i++) {
                    int index = i + scrollOffset;
                    JsonObject playlist = playlists.get(index);
                    String name = playlist.get("name").getAsString();
                    int trackCount = playlist.getAsJsonObject("items").get("total").getAsInt();
                    int rowY = listStartY + (i * rowHeight);

                    boolean hovered = mouseX >= panelX + 8 && mouseX <= panelX + panelWidth - 8
                        && mouseY >= rowY && mouseY <= rowY + rowHeight - 2;
                    if (hovered) graphics.fill(panelX + 8, rowY, panelX + panelWidth - 8, rowY + rowHeight - 2, 0x221DB954);

                    String displayName = name.length() > 26 ? name.substring(0, 23) + "..." : name;
                    graphics.drawString(this.font, displayName, panelX + 30, rowY + 3, hovered ? 0xFF1DB954 : 0xFFFFFFFF);
                    graphics.drawString(this.font, trackCount + " tracks", panelX + 30, rowY + 12, 0xFF666666);
                }
            }

        } else {
            // No jam — just show playlist picker
            graphics.fill(panelX + 6, panelY + 17, panelX + panelWidth - 6, panelY + 18, 0xFF1DB954);
            graphics.drawString(this.font, "Select a playlist to start a Jam", panelX + 8, panelY + 22, 0xFFAAAAAA);

            int rowHeight = 22;
            int listStartY = panelY + 40;
            int visibleRows = 7;

            if (loading) {
                graphics.drawString(this.font, "Loading playlists...", panelX + 8, listStartY, 0xFFAAAAAA);
            } else if (creatingJam) {
                graphics.drawString(this.font, statusMessage != null ? statusMessage : "Starting...", panelX + 8, listStartY, 0xFF1DB954);
            } else if (errorMessage != null) {
                graphics.drawString(this.font, errorMessage, panelX + 8, listStartY, 0xFFFF4444);
            } else {
                for (int i = 0; i < Math.min(visibleRows, playlists.size() - scrollOffset); i++) {
                    int index = i + scrollOffset;
                    JsonObject playlist = playlists.get(index);
                    String name = playlist.get("name").getAsString();
                    int trackCount = playlist.getAsJsonObject("items").get("total").getAsInt();
                    int rowY = listStartY + (i * rowHeight);

                    boolean hovered = mouseX >= panelX + 8 && mouseX <= panelX + panelWidth - 8
                        && mouseY >= rowY && mouseY <= rowY + rowHeight - 2;
                    if (hovered) graphics.fill(panelX + 8, rowY, panelX + panelWidth - 8, rowY + rowHeight - 2, 0x221DB954);

                    String displayName = name.length() > 26 ? name.substring(0, 23) + "..." : name;
                    graphics.drawString(this.font, displayName, panelX + 30, rowY + 3, hovered ? 0xFF1DB954 : 0xFFFFFFFF);
                    graphics.drawString(this.font, trackCount + " tracks", panelX + 30, rowY + 12, 0xFF666666);
                }
            }
        }

        if (playlists.size() > (JamState.isInJam ? 4 : 7)) {
            graphics.drawString(this.font, "↓ scroll", panelX + panelWidth - 55, panelY + panelHeight - 13, 0xFF555555);
        }
        graphics.drawString(this.font, "ESC to close", panelX + 8, panelY + panelHeight - 13, 0xFF444444);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY < 0 && scrollOffset < playlists.size() - 1) {
            scrollOffset++;
            rebuildButtons();
        } else if (scrollY > 0 && scrollOffset > 0) {
            scrollOffset--;
            rebuildButtons();
        }
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}