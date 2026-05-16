package com.spotifydjams.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class CreateJamScreen extends Screen {

    private final String playerId;
    private final BlockPos jukeboxPos;
    private EditBox jamNameField;
    private EditBox searchField;
    private List<JsonObject> searchResults = new ArrayList<>();
    private String statusMessage = null;
    private boolean creating = false;
    private String currentJamId = null;

    public CreateJamScreen(String playerId, BlockPos jukeboxPos) {
        super(Component.literal("Create Jam"));
        this.playerId = playerId;
        this.jukeboxPos = jukeboxPos;
    }

    @Override
    protected void init() {
        super.init();

        int panelWidth = 320;
        int panelX = (this.width - panelWidth) / 2;
        int panelY = (this.height - 240) / 2;

        jamNameField = new EditBox(this.font, panelX + 10, panelY + 35, 200, 18,
            Component.literal("Jam Name"));
        jamNameField.setMaxLength(32);
        jamNameField.setValue("My Jam");
        this.addRenderableWidget(jamNameField);

        this.addRenderableWidget(Button.builder(
            Component.literal("Create"),
            btn -> createJam()
        ).bounds(panelX + 220, panelY + 35, 90, 18).build());

        searchField = new EditBox(this.font, panelX + 10, panelY + 80, 220, 18,
            Component.literal("Search songs..."));
        searchField.setMaxLength(64);
        this.addRenderableWidget(searchField);

        this.addRenderableWidget(Button.builder(
            Component.literal("Search"),
            btn -> searchSongs()
        ).bounds(panelX + 240, panelY + 80, 70, 18).build());

        this.addRenderableWidget(Button.builder(
            Component.literal("← Back"),
            btn -> Minecraft.getInstance().setScreen(
                new MainMenuScreen(playerId, jukeboxPos)
            )
        ).bounds(panelX + 10, panelY + 215, 60, 18).build());
    }

    private void createJam() {
        if (creating) return;
        creating = true;
        statusMessage = "Creating jam...";

        JsonObject body = new JsonObject();
        body.addProperty("hostUuid", playerId);
        body.addProperty("jamName", jamNameField.getValue());
        body.addProperty("jukeboxX", jukeboxPos.getX());
        body.addProperty("jukeboxY", jukeboxPos.getY());
        body.addProperty("jukeboxZ", jukeboxPos.getZ());

        BackendClient.post("/jams/create-collab", body)
            .thenAccept(response -> {
                if (response != null && response.has("jamId")) {
                    Minecraft.getInstance().execute(() -> {
                        currentJamId = response.get("jamId").getAsString();
                        JamState.currentJamId = currentJamId;
                        JamState.currentPlaylistName = jamNameField.getValue();
                        JamState.isInJam = true;
                        statusMessage = "Jam created! Search songs and add to queue.";
                        creating = false;
                    });
                } else {
                    Minecraft.getInstance().execute(() -> {
                        statusMessage = "Failed to create jam.";
                        creating = false;
                    });
                }
            })
            .exceptionally(e -> {
                Minecraft.getInstance().execute(() -> {
                    statusMessage = "Error: " + e.getMessage();
                    creating = false;
                });
                return null;
            });
    }

    private void searchSongs() {
        String query = searchField.getValue().trim();
        if (query.isEmpty()) return;
        statusMessage = "Searching...";

        BackendClient.get("/search?playerId=" + playerId + "&query=" + query.replace(" ", "+"))
            .thenAccept(response -> {
                if (response != null && response.has("tracks")) {
                    JsonArray arr = response.getAsJsonArray("tracks");
                    List<JsonObject> results = new ArrayList<>();
                    for (int i = 0; i < arr.size(); i++) {
                        results.add(arr.get(i).getAsJsonObject());
                    }
                    Minecraft.getInstance().execute(() -> {
                        searchResults = results;
                        statusMessage = results.isEmpty() ? "No results." : null;
                        rebuildSearchButtons();
                    });
                }
            })
            .exceptionally(e -> {
                Minecraft.getInstance().execute(() -> statusMessage = "Search failed.");
                return null;
            });
    }

    private void rebuildSearchButtons() {
        String jamName = jamNameField != null ? jamNameField.getValue() : "My Jam";
        String searchQuery = searchField != null ? searchField.getValue() : "";

        this.clearWidgets();

        int panelWidth = 320;
        int panelX = (this.width - panelWidth) / 2;
        int panelY = (this.height - 240) / 2;

        jamNameField = new EditBox(this.font, panelX + 10, panelY + 35, 200, 18,
            Component.literal("Jam Name"));
        jamNameField.setMaxLength(32);
        jamNameField.setValue(jamName);
        this.addRenderableWidget(jamNameField);

        this.addRenderableWidget(Button.builder(
            Component.literal("Create"),
            btn -> createJam()
        ).bounds(panelX + 220, panelY + 35, 90, 18).build());

        searchField = new EditBox(this.font, panelX + 10, panelY + 80, 220, 18,
            Component.literal("Search songs..."));
        searchField.setMaxLength(64);
        searchField.setValue(searchQuery);
        this.addRenderableWidget(searchField);

        this.addRenderableWidget(Button.builder(
            Component.literal("Search"),
            btn -> searchSongs()
        ).bounds(panelX + 240, panelY + 80, 70, 18).build());

        for (int i = 0; i < Math.min(5, searchResults.size()); i++) {
            JsonObject track = searchResults.get(i);
            String trackUri = track.get("uri").getAsString();
            String trackName = track.get("name").getAsString();
            int rowY = panelY + 105 + (i * 20);

            this.addRenderableWidget(Button.builder(
                Component.literal("+"),
                btn -> addSong(trackUri, trackName)
            ).bounds(panelX + 10, rowY, 16, 16).build());
        }

        this.addRenderableWidget(Button.builder(
            Component.literal("← Back"),
            btn -> Minecraft.getInstance().setScreen(
                new MainMenuScreen(playerId, jukeboxPos)
            )
        ).bounds(panelX + 10, panelY + 215, 60, 18).build());
    }

    private void addSong(String trackUri, String trackName) {
        JsonObject body = new JsonObject();
        body.addProperty("playerId", playerId);
        body.addProperty("trackUri", trackUri);

        BackendClient.post("/jams/addsong", body)
            .thenAccept(response -> {
                Minecraft.getInstance().execute(() -> {
                    statusMessage = "Added to queue: " + trackName;
                });
            })
            .exceptionally(e -> {
                Minecraft.getInstance().execute(() -> {
                    statusMessage = "Failed to add song.";
                });
                return null;
            });
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0x88000000);

        int panelWidth = 320;
        int panelHeight = 240;
        int panelX = (this.width - panelWidth) / 2;
        int panelY = (this.height - panelHeight) / 2;

        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xFF0a0a0a);
        graphics.fill(panelX,                  panelY,                   panelX + panelWidth, panelY + 1,           0xFF1DB954);
        graphics.fill(panelX,                  panelY + panelHeight - 1, panelX + panelWidth, panelY + panelHeight, 0xFF1DB954);
        graphics.fill(panelX,                  panelY,                   panelX + 1,          panelY + panelHeight, 0xFF1DB954);
        graphics.fill(panelX + panelWidth - 1, panelY,                   panelX + panelWidth, panelY + panelHeight, 0xFF1DB954);

        graphics.drawString(this.font, "✦ Create Jam", panelX + 10, panelY + 10, 0xFF1DB954);
        graphics.drawString(this.font, "Jam Name:", panelX + 10, panelY + 26, 0xFFAAAAAA);

        graphics.fill(panelX + 8, panelY + 58, panelX + panelWidth - 8, panelY + 59, 0xFF222222);

        if (JamState.isInJam && currentJamId != null) {
            graphics.drawString(this.font, "✓ Jam active — search & queue songs below", panelX + 10, panelY + 64, 0xFF1DB954);
            graphics.drawString(this.font, "Search songs to add to queue:", panelX + 10, panelY + 72, 0xFFAAAAAA);
        } else {
            graphics.drawString(this.font, "Create a jam first, then search for songs", panelX + 10, panelY + 64, 0xFF555555);
            graphics.drawString(this.font, "Search songs to add to queue:", panelX + 10, panelY + 72, 0xFFAAAAAA);
        }

        for (int i = 0; i < Math.min(5, searchResults.size()); i++) {
            JsonObject track = searchResults.get(i);
            String name = track.get("name").getAsString();
            String artist = track.get("artist").getAsString();
            int rowY = panelY + 105 + (i * 20);

            String displayName = name.length() > 30 ? name.substring(0, 27) + "..." : name;
            String displayArtist = artist.length() > 30 ? artist.substring(0, 27) + "..." : artist;
            graphics.drawString(this.font, displayName, panelX + 30, rowY + 1, 0xFFFFFFFF);
            graphics.drawString(this.font, displayArtist, panelX + 30, rowY + 10, 0xFF888888);
        }

        if (statusMessage != null) {
            graphics.drawString(this.font, statusMessage, panelX + 10, panelY + 205, 0xFF1DB954);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}