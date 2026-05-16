package com.spotifydjams.client;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class SettingsScreen extends Screen {

    private final String playerId;

    private int keyPause;
    private int keySkip;
    private int keyPrev;
    private int keyShuffle;
    private int listeningFor = -1;
    private long lastKeyTime = 0;

    private int particleStyle;
    private int particleType;
    private int bpm;
    private int beatFrequency;

    private int innerRadius;
    private int outerRadius;
    private boolean radiusEnabled;

    private String statusMessage = null;
    private float previewHue = 0f;
    private int currentTab = 0;

    private static final String[] STYLE_NAMES = {"Rings", "Burst", "Spiral", "Fountain"};
    private static final String[] TYPE_NAMES = {"Flame", "Soul Fire", "End Rod", "Witch", "Glow", "RGB"};
    private static final String[] FREQ_NAMES = {"Every Beat", "Every 2nd", "Every 4th"};

    public SettingsScreen(String playerId) {
        super(Component.literal("Settings"));
        this.playerId = playerId;
        this.keyPause = JamState.keyPause;
        this.keySkip = JamState.keySkip;
        this.keyPrev = JamState.keyPrev;
        this.keyShuffle = JamState.keyShuffle;
        this.particleStyle = JamState.particleStyle;
        this.particleType = JamState.particleType;
        this.bpm = JamState.bpm;
        this.beatFrequency = JamState.beatFrequency;
        this.innerRadius = JamState.innerRadius;
        this.outerRadius = JamState.outerRadius;
        this.radiusEnabled = JamState.radiusEnabled;
    }

    @Override
    protected void init() {
        super.init();
        buildButtons();
    }

    private void buildButtons() {
        this.clearWidgets();

        int pw = 300;
        int ph = 210;
        int px = (this.width - pw) / 2;
        int py = (this.height - ph) / 2;

        // Tab buttons
        this.addRenderableWidget(Button.builder(Component.literal("Keybinds"),
            btn -> { currentTab = 0; buildButtons(); }
        ).bounds(px, py - 22, 98, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Particles"),
            btn -> { currentTab = 1; buildButtons(); }
        ).bounds(px + 101, py - 22, 98, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Radius"),
            btn -> { currentTab = 2; buildButtons(); }
        ).bounds(px + 202, py - 22, 98, 20).build());

        int rightCol = px + pw - 90;

        if (currentTab == 0) {
            this.addRenderableWidget(Button.builder(Component.literal("Change"),
                btn -> { listeningFor = 0; statusMessage = "Press any key for PAUSE..."; }
            ).bounds(rightCol, py + 32, 80, 16).build());

            this.addRenderableWidget(Button.builder(Component.literal("Change"),
                btn -> { listeningFor = 1; statusMessage = "Press any key for SKIP..."; }
            ).bounds(rightCol, py + 56, 80, 16).build());

            this.addRenderableWidget(Button.builder(Component.literal("Change"),
                btn -> { listeningFor = 2; statusMessage = "Press any key for PREV..."; }
            ).bounds(rightCol, py + 80, 80, 16).build());

            this.addRenderableWidget(Button.builder(Component.literal("Change"),
                btn -> { listeningFor = 3; statusMessage = "Press any key for SHUFFLE..."; }
            ).bounds(rightCol, py + 104, 80, 16).build());

        } else if (currentTab == 1) {
            int btnRight = px + pw - 30;

            this.addRenderableWidget(Button.builder(Component.literal("◀"),
                btn -> particleStyle = (particleStyle + STYLE_NAMES.length - 1) % STYLE_NAMES.length
            ).bounds(rightCol, py + 32, 20, 16).build());
            this.addRenderableWidget(Button.builder(Component.literal("▶"),
                btn -> particleStyle = (particleStyle + 1) % STYLE_NAMES.length
            ).bounds(btnRight, py + 32, 20, 16).build());

            this.addRenderableWidget(Button.builder(Component.literal("◀"),
                btn -> particleType = (particleType + TYPE_NAMES.length - 1) % TYPE_NAMES.length
            ).bounds(rightCol, py + 56, 20, 16).build());
            this.addRenderableWidget(Button.builder(Component.literal("▶"),
                btn -> particleType = (particleType + 1) % TYPE_NAMES.length
            ).bounds(btnRight, py + 56, 20, 16).build());

            this.addRenderableWidget(Button.builder(Component.literal("-10"),
                btn -> bpm = Math.max(60, bpm - 10)
            ).bounds(px + pw - 120, py + 80, 28, 16).build());
            this.addRenderableWidget(Button.builder(Component.literal("-1"),
                btn -> bpm = Math.max(60, bpm - 1)
            ).bounds(px + pw - 88, py + 80, 22, 16).build());
            this.addRenderableWidget(Button.builder(Component.literal("+1"),
                btn -> bpm = Math.min(220, bpm + 1)
            ).bounds(px + pw - 62, py + 80, 22, 16).build());
            this.addRenderableWidget(Button.builder(Component.literal("+10"),
                btn -> bpm = Math.min(220, bpm + 10)
            ).bounds(px + pw - 36, py + 80, 28, 16).build());

            this.addRenderableWidget(Button.builder(Component.literal("◀"),
                btn -> beatFrequency = (beatFrequency + FREQ_NAMES.length - 1) % FREQ_NAMES.length
            ).bounds(rightCol, py + 104, 20, 16).build());
            this.addRenderableWidget(Button.builder(Component.literal("▶"),
                btn -> beatFrequency = (beatFrequency + 1) % FREQ_NAMES.length
            ).bounds(btnRight, py + 104, 20, 16).build());

        } else if (currentTab == 2) {
            this.addRenderableWidget(Button.builder(
                Component.literal(radiusEnabled ? "ON" : "OFF"),
                btn -> {
                    radiusEnabled = !radiusEnabled;
                    btn.setMessage(Component.literal(radiusEnabled ? "ON" : "OFF"));
                }
            ).bounds(rightCol, py + 32, 80, 16).build());

            this.addRenderableWidget(Button.builder(Component.literal("-5"),
                btn -> innerRadius = Math.max(1, innerRadius - 5)
            ).bounds(px + pw - 120, py + 62, 26, 16).build());
            this.addRenderableWidget(Button.builder(Component.literal("-1"),
                btn -> innerRadius = Math.max(1, innerRadius - 1)
            ).bounds(px + pw - 90, py + 62, 22, 16).build());
            this.addRenderableWidget(Button.builder(Component.literal("+1"),
                btn -> innerRadius = Math.min(outerRadius - 1, innerRadius + 1)
            ).bounds(px + pw - 64, py + 62, 22, 16).build());
            this.addRenderableWidget(Button.builder(Component.literal("+5"),
                btn -> innerRadius = Math.min(outerRadius - 1, innerRadius + 5)
            ).bounds(px + pw - 38, py + 62, 26, 16).build());

            this.addRenderableWidget(Button.builder(Component.literal("-5"),
                btn -> outerRadius = Math.max(innerRadius + 1, outerRadius - 5)
            ).bounds(px + pw - 120, py + 90, 26, 16).build());
            this.addRenderableWidget(Button.builder(Component.literal("-1"),
                btn -> outerRadius = Math.max(innerRadius + 1, outerRadius - 1)
            ).bounds(px + pw - 90, py + 90, 22, 16).build());
            this.addRenderableWidget(Button.builder(Component.literal("+1"),
                btn -> outerRadius = Math.min(200, outerRadius + 1)
            ).bounds(px + pw - 64, py + 90, 22, 16).build());
            this.addRenderableWidget(Button.builder(Component.literal("+5"),
                btn -> outerRadius = Math.min(200, outerRadius + 5)
            ).bounds(px + pw - 38, py + 90, 26, 16).build());
        }

        this.addRenderableWidget(Button.builder(Component.literal("Save"),
            btn -> saveSettings()
        ).bounds(px + 50, py + ph - 26, 80, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("← Back"),
            btn -> Minecraft.getInstance().setScreen(null)
        ).bounds(px + 160, py + ph - 26, 80, 20).build());
    }

    @Override
    public void tick() {
        previewHue = (previewHue + 0.02f) % 1.0f;

        if (listeningFor < 0) return;
        long window = GLFW.glfwGetCurrentContext();
        for (int key = 32; key <= 348; key++) {
            if (GLFW.glfwGetKey(window, key) == GLFW.GLFW_PRESS) {
                if (System.currentTimeMillis() - lastKeyTime < 300) return;
                lastKeyTime = System.currentTimeMillis();
                if (key == GLFW.GLFW_KEY_ESCAPE) {
                    listeningFor = -1;
                    statusMessage = "Cancelled.";
                    return;
                }
                switch (listeningFor) {
                    case 0 -> { keyPause = key; statusMessage = "Pause: [" + keyName(key) + "]"; }
                    case 1 -> { keySkip = key; statusMessage = "Skip: [" + keyName(key) + "]"; }
                    case 2 -> { keyPrev = key; statusMessage = "Prev: [" + keyName(key) + "]"; }
                    case 3 -> { keyShuffle = key; statusMessage = "Shuffle: [" + keyName(key) + "]"; }
                }
                listeningFor = -1;
                return;
            }
        }
    }

    private void saveSettings() {
        boolean bpmChanged = bpm != JamState.bpm;

        JamState.keyPause = keyPause;
        JamState.keySkip = keySkip;
        JamState.keyPrev = keyPrev;
        JamState.keyShuffle = keyShuffle;
        JamState.particleStyle = particleStyle;
        JamState.particleType = particleType;
        JamState.bpm = bpm;
        JamState.beatFrequency = beatFrequency;
        JamState.innerRadius = innerRadius;
        JamState.outerRadius = outerRadius;
        JamState.radiusEnabled = radiusEnabled;

        // Only reset beat engine if BPM changed
        if (bpmChanged) BeatSyncEngine.resetTrack();

        JsonObject body = new JsonObject();
        body.addProperty("playerId", playerId);
        body.addProperty("keyPause", keyPause);
        body.addProperty("keySkip", keySkip);
        body.addProperty("keyPrev", keyPrev);

        BackendClient.post("/settings", body)
            .thenAccept(r -> Minecraft.getInstance().execute(() -> statusMessage = "Saved!"))
            .exceptionally(e -> {
                Minecraft.getInstance().execute(() -> statusMessage = "Save failed.");
                return null;
            });
    }

    private String keyName(int keyCode) {
        String name = GLFW.glfwGetKeyName(keyCode, 0);
        return name != null ? name.toUpperCase() : "Key " + keyCode;
    }

    private int hueToColor(float hue) {
        float h = hue * 6f;
        float r, g, b;
        int i = (int) h;
        float f = h - i;
        switch (i % 6) {
            case 0 -> { r = 1f; g = f; b = 0f; }
            case 1 -> { r = 1f - f; g = 1f; b = 0f; }
            case 2 -> { r = 0f; g = 1f; b = f; }
            case 3 -> { r = 0f; g = 1f - f; b = 1f; }
            case 4 -> { r = f; g = 0f; b = 1f; }
            default -> { r = 1f; g = 0f; b = 1f - f; }
        }
        return (255 << 24) | ((int)(r * 255) << 16) | ((int)(g * 255) << 8) | (int)(b * 255);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0x88000000);

        int pw = 300;
        int ph = 210;
        int px = (this.width - pw) / 2;
        int py = (this.height - ph) / 2;

        graphics.fill(px, py, px + pw, py + ph, 0xFF0a0a0a);
        graphics.fill(px,          py,          px + pw, py + 1,      0xFF1DB954);
        graphics.fill(px,          py + ph - 1, px + pw, py + ph,     0xFF1DB954);
        graphics.fill(px,          py,          px + 1,  py + ph,     0xFF1DB954);
        graphics.fill(px + pw - 1, py,          px + pw, py + ph,     0xFF1DB954);

        if (currentTab == 0) graphics.fill(px,       py - 22, px + 98,  py, 0xFF1a1a1a);
        if (currentTab == 1) graphics.fill(px + 101, py - 22, px + 199, py, 0xFF1a1a1a);
        if (currentTab == 2) graphics.fill(px + 202, py - 22, px + pw,  py, 0xFF1a1a1a);

        graphics.drawString(this.font, "⚙ Settings", px + 10, py + 8, 0xFF1DB954);
        graphics.fill(px + 8, py + 18, px + pw - 8, py + 19, 0xFF222222);

        if (currentTab == 0) {
            graphics.drawString(this.font, "KEYBINDS", px + 10, py + 22, 0xFF888888);

            graphics.drawString(this.font, "Pause / Resume:", px + 10, py + 36, 0xFFFFFFFF);
            graphics.drawString(this.font, "[" + keyName(keyPause) + "]", px + 130, py + 36,
                listeningFor == 0 ? 0xFF1DB954 : 0xFFAAAAAA);

            graphics.drawString(this.font, "Skip Next:", px + 10, py + 60, 0xFFFFFFFF);
            graphics.drawString(this.font, "[" + keyName(keySkip) + "]", px + 130, py + 60,
                listeningFor == 1 ? 0xFF1DB954 : 0xFFAAAAAA);

            graphics.drawString(this.font, "Previous:", px + 10, py + 84, 0xFFFFFFFF);
            graphics.drawString(this.font, "[" + keyName(keyPrev) + "]", px + 130, py + 84,
                listeningFor == 2 ? 0xFF1DB954 : 0xFFAAAAAA);

            graphics.drawString(this.font, "Shuffle:", px + 10, py + 108, 0xFFFFFFFF);
            graphics.drawString(this.font, "[" + keyName(keyShuffle) + "]", px + 130, py + 108,
                listeningFor == 3 ? 0xFF1DB954 : 0xFFAAAAAA);

            graphics.drawString(this.font, "* Shuffle requires Spotify Premium",
                px + 10, py + 132, 0xFF555555);

            if (listeningFor >= 0 && statusMessage != null) {
                graphics.fill(px + 8, py + 148, px + pw - 8, py + 158, 0x441DB954);
                graphics.drawString(this.font, statusMessage, px + 12, py + 150, 0xFF1DB954);
            }

        } else if (currentTab == 1) {
            graphics.drawString(this.font, "PARTICLES", px + 10, py + 22, 0xFF888888);

            graphics.drawString(this.font, "Shape:", px + 10, py + 36, 0xFFFFFFFF);
            graphics.drawString(this.font, STYLE_NAMES[particleStyle], px + 80, py + 36, 0xFF1DB954);

            graphics.drawString(this.font, "Type:", px + 10, py + 60, 0xFFFFFFFF);
            if (particleType == 5) {
                String rgb = "RGB ✦";
                for (int ci = 0; ci < rgb.length(); ci++) {
                    graphics.drawString(this.font, String.valueOf(rgb.charAt(ci)),
                        px + 80 + ci * 7, py + 60, hueToColor((previewHue + ci * 0.1f) % 1.0f));
                }
            } else {
                graphics.drawString(this.font, TYPE_NAMES[particleType], px + 80, py + 60, 0xFF1DB954);
            }

            graphics.drawString(this.font, "BPM:", px + 10, py + 84, 0xFFFFFFFF);
            graphics.drawString(this.font, String.valueOf(bpm), px + 80, py + 84, 0xFF1DB954);

            graphics.drawString(this.font, "Frequency:", px + 10, py + 108, 0xFFFFFFFF);
            graphics.drawString(this.font, FREQ_NAMES[beatFrequency], px + 80, py + 108, 0xFF1DB954);

        } else if (currentTab == 2) {
            graphics.drawString(this.font, "RADIUS / DIRECTIONAL AUDIO", px + 10, py + 22, 0xFF888888);

            graphics.drawString(this.font, "Enabled:", px + 10, py + 36, 0xFFFFFFFF);
            graphics.drawString(this.font, radiusEnabled ? "ON" : "OFF", px + 80, py + 36,
                radiusEnabled ? 0xFF1DB954 : 0xFFFF4444);

            graphics.drawString(this.font, "Full vol within:", px + 10, py + 66, 0xFFFFFFFF);
            graphics.drawString(this.font, innerRadius + " blocks", px + 130, py + 66, 0xFF1DB954);

            graphics.drawString(this.font, "Silent beyond:", px + 10, py + 94, 0xFFFFFFFF);
            graphics.drawString(this.font, outerRadius + " blocks", px + 130, py + 94, 0xFF1DB954);

            graphics.drawString(this.font, "Walk away from jukebox to fade music",
                px + 10, py + 120, 0xFF555555);
        }

        if (statusMessage != null && listeningFor < 0) {
            graphics.drawString(this.font, statusMessage, px + 10, py + 170, 0xFF1DB954);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}