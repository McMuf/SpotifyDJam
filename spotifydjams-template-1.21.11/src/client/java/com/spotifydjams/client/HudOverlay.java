package com.spotifydjams.client;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

public class HudOverlay {

    public static boolean isPaused = false;
    private static String shuffleMessage = null;
    private static int shuffleMessageTimer = 0;

    // Small flash message when HUD is hidden
    private static int hiddenFlashTimer = 0;

    public static void showShuffleMessage(String msg) {
        shuffleMessage = msg;
        shuffleMessageTimer = 60;
    }

    public static void register() {
        HudRenderCallback.EVENT.register((guiGraphics, tickDelta) -> {
            if (!JamState.isInJam) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen != null) return;
            render(guiGraphics, mc);
        });
    }

    private static void render(GuiGraphics graphics, Minecraft mc) {
        int screenWidth = mc.getWindow().getGuiScaledWidth();

        // Tick timers
        if (shuffleMessageTimer > 0) shuffleMessageTimer--;
        else shuffleMessage = null;
        if (hiddenFlashTimer > 0) hiddenFlashTimer--;

        // If HUD hidden — just show a tiny indicator so user knows how to get it back
        if (!JamState.hudVisible) {
            if (hiddenFlashTimer > 0) {
                graphics.drawString(mc.font, "[H] Show HUD",
                    screenWidth - 90, 5, 0xFF444444);
            }
            return;
        }

        int panelWidth = 185;
        int panelX = screenWidth - panelWidth - 5;
        int panelY = 5;

        // Ad mode
        if (JamState.isAd) {
            int adHeight = 40;
            graphics.fill(panelX, panelY, panelX + panelWidth, panelY + adHeight, 0xCC0a0a0a);
            graphics.fill(panelX, panelY, panelX + panelWidth, panelY + 1, 0xFFFFAA00);
            graphics.fill(panelX, panelY, panelX + 2, panelY + adHeight, 0xFFFFAA00);
            graphics.drawString(mc.font, "⚠ Ad playing on Spotify", panelX + 8, panelY + 6, 0xFFFFAA00);
            graphics.drawString(mc.font, "Music resumes after ad", panelX + 8, panelY + 18, 0xFF888888);
            graphics.drawString(mc.font, "Consider Spotify Premium!", panelX + 8, panelY + 28, 0xFF555555);
            // X button
            graphics.drawString(mc.font, "✕", panelX + panelWidth - 10, panelY + 2, 0xFF555555);
            return;
        }

        // Normal HUD
        int panelHeight = 76;
        if (shuffleMessage != null) panelHeight += 14;

        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xCC0a0a0a);
        graphics.fill(panelX, panelY, panelX + 2, panelY + panelHeight, 0xFF1DB954);
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + 1, 0xFF1DB954);

        // X button top right
        graphics.drawString(mc.font, "✕", panelX + panelWidth - 10, panelY + 3, 0xFF444444);

        // Track name
        String track = JamState.currentTrackName != null ? JamState.currentTrackName : "Starting...";
        String displayTrack = track.length() > 22 ? track.substring(0, 19) + "..." : track;
        graphics.drawString(mc.font, displayTrack, panelX + 8, panelY + 6, 0xFFFFFFFF);

        // Artist
        String artist = JamState.currentArtistName != null ? JamState.currentArtistName : "";
        String displayArtist = artist.length() > 22 ? artist.substring(0, 19) + "..." : artist;
        graphics.drawString(mc.font, displayArtist, panelX + 8, panelY + 17, 0xFF888888);

        // Playlist + shuffle icon
        String playlist = JamState.currentPlaylistName != null ? JamState.currentPlaylistName : "";
        String displayPlaylist = playlist.length() > 18 ? playlist.substring(0, 15) + "..." : playlist;
        String shuffleIcon = JamState.isShuffled ? " ⇄" : "";
        graphics.drawString(mc.font, "♫ " + displayPlaylist + shuffleIcon,
            panelX + 8, panelY + 28, 0xFF1DB954);

        // Status
        String status = isPaused ? "⏸ Paused" : "▶ Playing";
        graphics.drawString(mc.font, status, panelX + 8, panelY + 39,
            isPaused ? 0xFF888888 : 0xFF1DB954);

        // Keybind hints
        String pauseKey = keyName(JamState.keyPause);
        String skipKey = keyName(JamState.keySkip);
        String prevKey = keyName(JamState.keyPrev);
        String shuffleKey = keyName(JamState.keyShuffle);
        graphics.drawString(mc.font,
            "[" + pauseKey + "][" + prevKey + "][" + skipKey + "][" + shuffleKey + "] [H]hide",
            panelX + 8, panelY + 50, 0xFF333333);

        // Shuffle message
        if (shuffleMessage != null) {
            boolean isError = shuffleMessage.startsWith("✗");
            graphics.fill(panelX + 4, panelY + 62, panelX + panelWidth - 4, panelY + 74, 0x33FFFFFF);
            graphics.drawString(mc.font, shuffleMessage, panelX + 8, panelY + 64,
                isError ? 0xFFFF4444 : 0xFF1DB954);
        }
    }

    // Call this from the click handler to check if X was clicked
    public static boolean handleClick(double mouseX, double mouseY, int screenWidth) {
        if (!JamState.isInJam || !JamState.hudVisible) return false;
        int panelWidth = 185;
        int panelX = screenWidth - panelWidth - 5;
        int panelY = 5;
        // X button is at top right corner
        if (mouseX >= panelX + panelWidth - 14 && mouseX <= panelX + panelWidth
            && mouseY >= panelY && mouseY <= panelY + 12) {
            JamState.hudVisible = false;
            hiddenFlashTimer = 80;
            return true;
        }
        return false;
    }

    public static void flashHiddenHint() {
        hiddenFlashTimer = 80;
    }

    private static String keyName(int keyCode) {
        String name = GLFW.glfwGetKeyName(keyCode, 0);
        return name != null ? name.toUpperCase() : "?" + keyCode;
    }
}