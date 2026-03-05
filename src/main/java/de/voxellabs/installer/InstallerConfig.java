package de.voxellabs.installer;

/**
 * Zentrale Konfiguration des Installers.
 * Hier alle Werte anpassen.
 */
public final class InstallerConfig {

    // ── GitHub ────────────────────────────────────────────────────────────────
    public static final String GITHUB_OWNER    = "VoxelLabs-Minecraft";
    public static final String GITHUB_REPO     = "voxelclient";
    public static final String GITHUB_API_URL  =
            "https://api.github.com/repos/" + GITHUB_OWNER + "/" + GITHUB_REPO + "/releases/latest";
    public static final String GITHUB_RELEASES =
            "https://github.com/" + GITHUB_OWNER + "/" + GITHUB_REPO + "/releases/latest";

    // ── Mod-Info ──────────────────────────────────────────────────────────────
    public static final String MOD_NAME        = "VoxelClient";
    public static final String MOD_VERSION_REQ = "1.21.4";
    public static final String LOADER          = "Fabric ≥ 0.16";
    public static final String JAVA_REQ        = "Java 21+";

    // ── Design ────────────────────────────────────────────────────────────────
    public static final String COLOR_BG        = "#06060f";
    public static final String COLOR_SURFACE   = "#0d0d20";
    public static final String COLOR_BORDER    = "#1a2044";
    public static final String COLOR_ACCENT    = "#4a8cff";
    public static final String COLOR_ACCENT_DIM= "#1a3a8a";
    public static final String COLOR_GREEN     = "#2ecc71";
    public static final String COLOR_RED       = "#ff4466";
    public static final String COLOR_GOLD      = "#ffcc00";
    public static final String COLOR_TEXT      = "#dde4ff";
    public static final String COLOR_TEXT_DIM  = "#667799";

    private InstallerConfig() {}
}
