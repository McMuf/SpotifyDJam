package com.spotifydjams.client;

import java.io.*;
import java.nio.file.*;

public class OnboardingManager {

    private static final Path FLAG_FILE = Paths.get(
        System.getProperty("user.home"), ".spotifydjams_onboarded"
    );

    public static boolean hasSeenOnboarding() {
        return Files.exists(FLAG_FILE);
    }

    public static void markOnboardingComplete() {
        try {
            Files.createFile(FLAG_FILE);
        } catch (Exception e) {
            System.out.println("[SpotifyDJams] Could not save onboarding flag");
        }
    }
}