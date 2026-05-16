package com.spotifydjams.client;

public class JamState {
    public static boolean isInJam = false;
    public static String currentJamId = null;
    public static String currentPlaylistName = null;
    public static String currentTrackName = null;
    public static String currentArtistName = null;
    public static boolean isAd = false;
    public static boolean isShuffled = false;
    public static boolean isPremium = true;
    public static boolean hudVisible = true;

    // Keybinds
    public static int keyPause = 67;
    public static int keySkip = 86;
    public static int keyPrev = 90;
    public static int keyShuffle = 78;

    // Particle settings
    public static int particleStyle = 0;
    public static int particleType = 0;
    public static int bpm = 120;
    public static int beatFrequency = 0;

    // Jukebox position
    public static int jukeboxX = 0;
    public static int jukeboxY = 0;
    public static int jukeboxZ = 0;

    // Radius settings
    public static int innerRadius = 5;
    public static int outerRadius = 20;
    public static boolean radiusEnabled = true;
}