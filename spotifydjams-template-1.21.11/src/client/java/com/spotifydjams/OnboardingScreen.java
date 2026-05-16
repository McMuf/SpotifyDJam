package com.spotifydjams.client;

import com.google.gson.JsonObject;
import com.spotifydjams.client.BackendClient;
import com.spotifydjams.client.MainMenuScreen;
import com.spotifydjams.client.OnboardingManager;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

public class OnboardingScreen extends Screen {

    private final String playerId;
    private final BlockPos jukeboxPos;
    private int currentSlide = 0;
    private boolean spotifyLinked = false;
    private boolean checkingLink = false;
    private boolean linkOpened = false;
    private int linkCheckTimer = 0;

    private static final String[][] SLIDES = {
        {
            "Welcome to SpotifyDJams! ♫",
            "Play Spotify music directly from",
            "jukeboxes in Minecraft.",
            "",
            "Let's get you set up!"
        },
        {
            "Step 1: Link Your Spotify",
            "You need a Spotify account to use",
            "this mod. Click the button below",
            "to open your browser and authorize.",
            "Then come back to Minecraft!"
        },
        {
            "Beat-Synced Particles ✦",
            "Watch colorful particles pulse",
            "from the jukebox in sync with",
            "the beat of your music.",
            "Customize them in Settings!"
        },
        {
            "Directional Audio",
            "Walk away from the jukebox",
            "and the music fades out.",
            "Set your preferred radius",
            "in Settings → Radius."
        },
        {
            "Controls",
            "C  →  Pause / Resume",
            "V  →  Skip to next track",
            "Z  →  Previous track",
            "N  →  Shuffle (Premium only)"
        },
        {
            "Jukebox Tips",
            "Shift + Right-click → always",
            "opens SpotifyDJams menu.",
            "Break the jukebox to end",
            "the jam and hide the HUD."
        },
        {
            "You're all set! 🎉",
            "Right-click any jukebox to",
            "browse playlists and start a Jam.",
            "",
            "Enjoy the music!"
        }
    };

    private static final int[] SLIDE_COLORS = {
        0xFF1DB954,
        0xFF1DB954,
        0xFFCE93D8,
        0xFF80DEEA,
        0xFFFFCC80,
        0xFF4FC3F7,
        0xFF1DB954,
    };

    public OnboardingScreen(String playerId, BlockPos jukeboxPos) {
        super(Component.literal("Welcome to SpotifyDJams"));
        this.playerId = playerId;
        this.jukeboxPos = jukeboxPos;
    }

    @Override
    protected void init() {
        super.init();
        checkSpotifyLinked();
        buildButtons();
    }

    private void checkSpotifyLinked() {
        checkingLink = true;
        BackendClient.get("/auth-status?playerId=" + playerId)
            .thenAccept(response -> {
                Minecraft.getInstance().execute(() -> {
                    checkingLink = false;
                    if (response != null && response.has("linked")) {
                        spotifyLinked = response.get("linked").getAsBoolean();
                    }
                    buildButtons();
                });
            })
            .exceptionally(e -> {
                Minecraft.getInstance().execute(() -> {
                    checkingLink = false;
                    buildButtons();
                });
                return null;
            });
    }

    @Override
    public void tick() {
        // Poll auth status every 2 seconds after link opened
        if (linkOpened && !spotifyLinked) {
            linkCheckTimer++;
            if (linkCheckTimer >= 40) {
                linkCheckTimer = 0;
                checkSpotifyLinked();
            }
        }
    }

    private void buildButtons() {
        this.clearWidgets();

        int pw = 320;
        int ph = 230;
        int px = (this.width - pw) / 2;
        int py = (this.height - ph) / 2;

        boolean isLinkSlide = currentSlide == 1;

        // Link Spotify button on slide 1
        if (isLinkSlide) {
            if (!spotifyLinked) {
                this.addRenderableWidget(Button.builder(
                    Component.literal("🔗 Link Spotify Account"),
                    btn -> openSpotifyLink()
                ).bounds(px + pw / 2 - 90, py + ph - 70, 180, 20).build());

                // Check again button if link was opened
                if (linkOpened) {
                    this.addRenderableWidget(Button.builder(
                        Component.literal("✓ I've authorized, check again"),
                        btn -> checkSpotifyLinked()
                    ).bounds(px + pw / 2 - 90, py + ph - 45, 180, 18).build());
                }
            } else {
                // Linked — show continue button
                this.addRenderableWidget(Button.builder(
                    Component.literal("✓ Linked! Continue →"),
                    btn -> { currentSlide++; buildButtons(); }
                ).bounds(px + pw / 2 - 80, py + ph - 55, 160, 20).build());
            }
        } else {
            // Normal navigation
            if (currentSlide > 0) {
                this.addRenderableWidget(Button.builder(
                    Component.literal("← Back"),
                    btn -> { currentSlide--; buildButtons(); }
                ).bounds(px + 10, py + ph - 28, 70, 18).build());
            }

            if (currentSlide < SLIDES.length - 1) {
                // Only allow next on slide 1 if linked
                boolean canNext = currentSlide != 1 || spotifyLinked;
                this.addRenderableWidget(Button.builder(
                    Component.literal("Next →"),
                    btn -> { if (canNext) { currentSlide++; buildButtons(); } }
                ).bounds(px + pw - 85, py + ph - 28, 75, 18).build());
            } else {
                this.addRenderableWidget(Button.builder(
                    Component.literal("✦ Get Started!"),
                    btn -> finishOnboarding()
                ).bounds(px + pw - 120, py + ph - 28, 110, 18).build());
            }

            if (currentSlide < SLIDES.length - 1 && currentSlide != 1) {
                this.addRenderableWidget(Button.builder(
                    Component.literal("Skip"),
                    btn -> finishOnboarding()
                ).bounds(px + pw / 2 - 25, py + ph - 28, 50, 18).build());
            }
        }
    }

    private void openSpotifyLink() {
    String url = "http://127.0.0.1:4000/login?playerId=" + playerId;
    try {
        String os = System.getProperty("os.name").toLowerCase();
        ProcessBuilder pb;
        if (os.contains("mac")) {
            pb = new ProcessBuilder("open", url);
        } else if (os.contains("win")) {
            pb = new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url);
        } else {
            pb = new ProcessBuilder("xdg-open", url);
        }
        pb.start();
        linkOpened = true;
        buildButtons();
        System.out.println("[SpotifyDJams] Opened browser: " + url);
    } catch (Exception e) {
        System.out.println("[SpotifyDJams] Could not open browser: " + e.getMessage());
        // Show URL in chat so user can copy it
        Minecraft.getInstance().player.displayClientMessage(
            net.minecraft.network.chat.Component.literal(
                "[SpotifyDJams] Open this URL to link Spotify: " + url), false);
    }
}

    private void finishOnboarding() {
        OnboardingManager.markOnboardingComplete();
        Minecraft.getInstance().setScreen(
            new MainMenuScreen(playerId, jukeboxPos)
        );
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0x88000000);

        int pw = 320;
        int ph = 230;
        int px = (this.width - pw) / 2;
        int py = (this.height - ph) / 2;

        graphics.fill(px, py, px + pw, py + ph, 0xFF0a0a0a);

        int accentColor = SLIDE_COLORS[currentSlide];
        graphics.fill(px,          py,          px + pw, py + 3,      accentColor);
        graphics.fill(px,          py + ph - 1, px + pw, py + ph,     accentColor);
        graphics.fill(px,          py,          px + 1,  py + ph,     accentColor);
        graphics.fill(px + pw - 1, py,          px + pw, py + ph,     accentColor);

        // Slide dots
        int totalSlides = SLIDES.length;
        int dotSpacing = 14;
        int dotsWidth = totalSlides * dotSpacing;
        int dotsX = px + (pw - dotsWidth) / 2;
        int dotsY = py + 10;
        for (int i = 0; i < totalSlides; i++) {
            int dotColor = i == currentSlide ? accentColor : 0xFF333333;
            int dotX = dotsX + i * dotSpacing;
            graphics.fill(dotX, dotsY, dotX + 8, dotsY + 8, dotColor);
        }

        // Slide content
        String[] slide = SLIDES[currentSlide];
        graphics.drawString(this.font, slide[0], px + 15, py + 28, accentColor);
        graphics.fill(px + 12, py + 40, px + pw - 12, py + 41, 0xFF222222);

        for (int i = 1; i < slide.length; i++) {
            if (!slide[i].isEmpty()) {
                graphics.drawString(this.font, slide[i], px + 15, py + 48 + (i - 1) * 16, 0xFFCCCCCC);
            }
        }

        // Link slide special UI
        if (currentSlide == 1) {
            if (checkingLink) {
                graphics.drawString(this.font, "Checking Spotify link...",
                    px + 15, py + 140, 0xFF888888);
            } else if (spotifyLinked) {
                graphics.fill(px + 10, py + 135, px + pw - 10, py + 155, 0x331DB954);
                graphics.drawString(this.font, "✓ Spotify account linked successfully!",
                    px + 15, py + 142, 0xFF1DB954);
            } else if (linkOpened) {
                graphics.drawString(this.font, "Waiting for Spotify authorization...",
                    px + 15, py + 135, 0xFFFFAA00);
                graphics.drawString(this.font, "Authorize in your browser then click",
                    px + 15, py + 147, 0xFF888888);
                graphics.drawString(this.font, "the check button below.",
                    px + 15, py + 158, 0xFF888888);
            } else {
                graphics.drawString(this.font, "Click the button below to open your",
                    px + 15, py + 135, 0xFF888888);
                graphics.drawString(this.font, "browser and link your Spotify account.",
                    px + 15, py + 147, 0xFF888888);
            }
        }

        // Slide counter
        String counter = (currentSlide + 1) + " / " + totalSlides;
        int counterX = px + pw / 2 - (counter.length() * 3);
        graphics.drawString(this.font, counter, counterX, py + ph - 14, 0xFF444444);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}