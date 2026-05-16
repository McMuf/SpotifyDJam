package com.spotifydjams.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class JoinJamScreen extends Screen {

    private final String playerId;
    private final BlockPos jukeboxPos;
    private List<JsonObject> availableJams = new ArrayList<>();
    private boolean loading = true;
    private String statusMessage = null;

    public JoinJamScreen(String playerId, BlockPos jukeboxPos) {
        super(Component.literal("Join Jam"));
        this.playerId = playerId;
        this.jukeboxPos = jukeboxPos;
    }

    @Override
    protected void init() {
        super.init();
        fetchJams();
    }

    private void fetchJams() {
        loading = true;
        BackendClient.get("/jams/list")
            .thenAccept(response -> {
                if (response != null && response.has("jams")) {
                    JsonArray arr = response.getAsJsonArray("jams");
                    List<JsonObject> result = new ArrayList<>();
                    for (int i = 0; i < arr.size(); i++) {
                        result.add(arr.get(i).getAsJsonObject());
                    }
                    Minecraft.getInstance().execute(() -> {
                        availableJams = result;
                        loading = false;
                        buildButtons();
                    });
                } else {
                    Minecraft.getInstance().execute(() -> {
                        loading = false;
                        buildButtons();
                    });
                }
            })
            .exceptionally(e -> {
                Minecraft.getInstance().execute(() -> {
                    loading = false;
                    statusMessage = "Could not load jams.";
                    buildButtons();
                });
                return null;
            });
    }

    private void buildButtons() {
        this.clearWidgets();

        int panelWidth = 300;
        int panelX = (this.width - panelWidth) / 2;
        int panelY = (this.height - 220) / 2;
        int rowHeight = 28;
        int listStartY = panelY + 45;

        for (int i = 0; i < Math.min(5, availableJams.size()); i++) {
            JsonObject jam = availableJams.get(i);
            String jamId = jam.get("id").getAsString();
            String jamName = jam.has("name") && !jam.get("name").isJsonNull()
                ? jam.get("name").getAsString() : "Untitled Jam";
            int rowY = listStartY + (i * rowHeight);

            this.addRenderableWidget(Button.builder(
                Component.literal("Join"),
                btn -> joinJam(jam, jamName)
            ).bounds(panelX + panelWidth - 65, rowY, 55, 20).build());
        }

        this.addRenderableWidget(Button.builder(
            Component.literal("← Back"),
            btn -> Minecraft.getInstance().setScreen(
                new MainMenuScreen(playerId, jukeboxPos)
            )
        ).bounds(panelX + 10, panelY + 195, 60, 18).build());

        this.addRenderableWidget(Button.builder(
            Component.literal("↻ Refresh"),
            btn -> {
                availableJams.clear();
                loading = true;
                this.clearWidgets();
                fetchJams();
            }
        ).bounds(panelX + panelWidth - 80, panelY + 195, 70, 18).build());
    }

    private void joinJam(JsonObject jamData, String jamName) {
        // Use collaborative playlist if available, otherwise use regular playlist
        String playlistId;
        if (jamData.has("collaborative_playlist_id")
            && !jamData.get("collaborative_playlist_id").isJsonNull()) {
            playlistId = jamData.get("collaborative_playlist_id").getAsString();
        } else {
            playlistId = jamData.get("playlist_id").getAsString();
        }

        String jamId = jamData.get("id").getAsString();

        JsonObject body = new JsonObject();
        body.addProperty("playerId", playerId);
        body.addProperty("playlistId", playlistId);

        BackendClient.post("/playback/start", body)
            .thenAccept(response -> {
                Minecraft.getInstance().execute(() -> {
                    JamState.currentJamId = jamId;
                    JamState.currentPlaylistName = jamName;
                    JamState.isInJam = true;
                    Minecraft.getInstance().player.displayClientMessage(
                        Component.literal("[SpotifyDJams] Joined jam: " + jamName), false
                    );
                    Minecraft.getInstance().setScreen(null);
                });
            })
            .exceptionally(e -> {
                Minecraft.getInstance().execute(() ->
                    statusMessage = "Failed to join jam.");
                return null;
            });
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0x88000000);

        int panelWidth = 300;
        int panelHeight = 220;
        int panelX = (this.width - panelWidth) / 2;
        int panelY = (this.height - panelHeight) / 2;

        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xFF0a0a0a);
        graphics.fill(panelX,                  panelY,                   panelX + panelWidth, panelY + 1,           0xFF1DB954);
        graphics.fill(panelX,                  panelY + panelHeight - 1, panelX + panelWidth, panelY + panelHeight, 0xFF1DB954);
        graphics.fill(panelX,                  panelY,                   panelX + 1,          panelY + panelHeight, 0xFF1DB954);
        graphics.fill(panelX + panelWidth - 1, panelY,                   panelX + panelWidth, panelY + panelHeight, 0xFF1DB954);

        graphics.drawString(this.font, "⇢ Join a Jam", panelX + 10, panelY + 10, 0xFF1DB954);
        graphics.drawString(this.font, "Active jams:", panelX + 10, panelY + 26, 0xFF888888);
        graphics.fill(panelX + 8, panelY + 36, panelX + panelWidth - 8, panelY + 37, 0xFF222222);

        if (loading) {
            graphics.drawString(this.font, "Loading jams...", panelX + 10, panelY + 50, 0xFFAAAAAA);
        } else if (availableJams.isEmpty()) {
            graphics.drawString(this.font, "No active jams found.", panelX + 10, panelY + 50, 0xFFAAAAAA);
            graphics.drawString(this.font, "Create one from the main menu!", panelX + 10, panelY + 62, 0xFF555555);
        } else {
            int rowHeight = 28;
            int listStartY = panelY + 45;

            for (int i = 0; i < Math.min(5, availableJams.size()); i++) {
                JsonObject jam = availableJams.get(i);
                String jamName = jam.has("name") && !jam.get("name").isJsonNull()
                    ? jam.get("name").getAsString() : "Untitled Jam";
                String host = jam.get("host_uuid").getAsString();
                String shortHost = host.length() > 12 ? host.substring(0, 12) : host;

                boolean isCollab = jam.has("collaborative_playlist_id")
                    && !jam.get("collaborative_playlist_id").isJsonNull();

                int rowY = listStartY + (i * rowHeight);

                boolean hovered = mouseX >= panelX + 10 && mouseX <= panelX + panelWidth - 10
                    && mouseY >= rowY && mouseY <= rowY + rowHeight - 2;

                if (hovered) {
                    graphics.fill(panelX + 10, rowY, panelX + panelWidth - 10, rowY + rowHeight - 2, 0x221DB954);
                }

                String displayName = jamName.length() > 25 ? jamName.substring(0, 22) + "..." : jamName;
                String typeLabel = isCollab ? " ✦" : "";
                graphics.drawString(this.font, displayName + typeLabel, panelX + 15, rowY + 4, 0xFFFFFFFF);
                graphics.drawString(this.font, "Host: " + shortHost + "...", panelX + 15, rowY + 14, 0xFF666666);
            }
        }

        if (statusMessage != null) {
            graphics.drawString(this.font, statusMessage, panelX + 10, panelY + 178, 0xFFFF4444);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}