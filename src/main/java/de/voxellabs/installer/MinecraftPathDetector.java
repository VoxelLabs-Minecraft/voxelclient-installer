package de.voxellabs.installer;

import java.nio.file.Path;

/**
 * Ermittelt den plattformspezifischen Minecraft mods-Ordner.
 */
public final class MinecraftPathDetector {

    private MinecraftPathDetector() {}

    public static Path getModsFolder() {
        String os = System.getProperty("os.name").toLowerCase();
        Path home = Path.of(System.getProperty("user.home"));

        if (os.contains("win")) {
            // Windows: %APPDATA%\.minecraft\mods
            String appData = System.getenv("APPDATA");
            Path base = appData != null
                    ? Path.of(appData)
                    : home.resolve("AppData").resolve("Roaming");
            return base.resolve(".minecraft").resolve("mods");

        } else if (os.contains("mac")) {
            // macOS: ~/Library/Application Support/minecraft/mods
            return home.resolve("Library")
                       .resolve("Application Support")
                       .resolve("minecraft")
                       .resolve("mods");

        } else {
            // Linux: ~/.minecraft/mods
            return home.resolve(".minecraft").resolve("mods");
        }
    }
}
