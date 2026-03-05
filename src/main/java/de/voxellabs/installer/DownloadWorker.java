package de.voxellabs.installer;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Führt den eigentlichen Download asynchron durch.
 * Kommuniziert via Callbacks mit der GUI.
 */
public class DownloadWorker implements Runnable {

    /** Callback: (bytesLoaded, totalBytes) → für Fortschrittsanzeige */
    private final BiConsumer<Long, Long>  onProgress;
    /** Callback: Log-Nachricht mit optionaler Farbe */
    private final BiConsumer<String, String> onLog;
    /** Callback: bei erfolgreichem Abschluss mit dem Dateinamen */
    private final Consumer<String>        onSuccess;
    /** Callback: bei Fehler mit Fehlermeldung */
    private final Consumer<String>        onError;

    private final Path targetDir;

    public DownloadWorker(Path targetDir,
                          BiConsumer<Long, Long>    onProgress,
                          BiConsumer<String, String> onLog,
                          Consumer<String>           onSuccess,
                          Consumer<String>           onError) {
        this.targetDir  = targetDir;
        this.onProgress = onProgress;
        this.onLog      = onLog;
        this.onSuccess  = onSuccess;
        this.onError    = onError;
    }

    static final String FABRIC_API_MODRINTH =
            "https://api.modrinth.com/v2/project/fabric-api/version" +
                    "?game_versions=%5B%221.21.4%22%5D&loaders=%5B%22fabric%22%5D";

    /**
     * Lädt die neueste Fabric API für 1.21.4 von Modrinth herunter,
     * falls sie noch nicht im mods-Ordner vorhanden ist.
     */
    private void downloadFabricApi(Path modsDir) {
        try {
            onLog.accept("🔍 Suche Fabric API…", null);

            HttpURLConnection conn = (HttpURLConnection)
                    URI.create(FABRIC_API_MODRINTH).toURL().openConnection();
            conn.setRequestProperty("User-Agent",
                    InstallerConfig.MOD_NAME + "-Installer/1.0");
            conn.setConnectTimeout(8_000);
            conn.setReadTimeout(8_000);

            if (conn.getResponseCode() != 200) {
                onLog.accept("⚠ Fabric API nicht gefunden (HTTP "
                        + conn.getResponseCode() + ")", InstallerConfig.COLOR_GOLD);
                return;
            }

            com.google.gson.JsonArray versions;
            try (java.io.InputStreamReader reader = new java.io.InputStreamReader(
                    conn.getInputStream(), java.nio.charset.StandardCharsets.UTF_8)) {
                versions = com.google.gson.JsonParser.parseReader(reader)
                        .getAsJsonArray();
            }

            if (versions.isEmpty()) {
                onLog.accept("⚠ Keine Fabric API Version gefunden",
                        InstallerConfig.COLOR_GOLD);
                return;
            }

            // Erste (= neueste) Version nehmen
            com.google.gson.JsonArray files = versions.get(0)
                    .getAsJsonObject()
                    .getAsJsonArray("files");

            for (var element : files) {
                com.google.gson.JsonObject file = element.getAsJsonObject();
                if (file.get("primary").getAsBoolean()) {
                    String url      = file.get("url").getAsString();
                    String filename = file.get("filename").getAsString();
                    Path   dest     = modsDir.resolve(filename);

                    // Bereits vorhanden? Überspringen.
                    if (Files.exists(dest)) {
                        onLog.accept("✔ Verfügbar: " + filename,
                                InstallerConfig.COLOR_GREEN);
                        return;
                    }

                    onLog.accept("⬇ Lade Fabric API: " + filename, null);

                    // Herunterladen
                    HttpURLConnection dlConn = (HttpURLConnection)
                            URI.create(url).toURL().openConnection();
                    dlConn.setRequestProperty("User-Agent",
                            InstallerConfig.MOD_NAME + "-Installer/1.0");
                    dlConn.setConnectTimeout(10_000);
                    dlConn.setReadTimeout(30_000);

                    Path tmp = modsDir.resolve(filename + ".tmp");
                    try (java.io.InputStream in =
                                 new java.io.BufferedInputStream(dlConn.getInputStream())) {
                        Files.copy(in, tmp,
                                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                    Files.move(tmp, dest,
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);

                    onLog.accept("✔ Fabric API installiert!", InstallerConfig.COLOR_GREEN);
                    return;
                }
            }

        } catch (Exception ex) {
            onLog.accept("⚠ Fabric API konnte nicht geladen werden: "
                    + ex.getMessage(), InstallerConfig.COLOR_GOLD);
        }
    }

    @Override
    public void run() {
        try {
            // ── Step 1: Version von GitHub holen ────────────────────────────
            log("🔍 Verbinde mit GitHub API…", null);
            GitHubClient.ReleaseInfo release = GitHubClient.fetchLatestRelease();

            log("✔ Gefunden: " + release.releaseName()
                    + "  (" + formatSize(release.jarSizeBytes()) + ")",
                InstallerConfig.COLOR_GREEN);

            // ── Step 2: mods-Ordner anlegen ──────────────────────────────────
            log("📁 Prüfe mods-Ordner…", null);
            Files.createDirectories(targetDir);
            log("✔ Ordner: " + targetDir, InstallerConfig.COLOR_GREEN);

            // ── Step 3: Alte JAR entfernen ───────────────────────────────────
            log("🗑  Entferne alte Version…", null);
            int removed = 0;
            // Suche nach Dateien die mit dem Mod-Namen beginnen
            String prefix = InstallerConfig.GITHUB_REPO.toLowerCase();
            try (DirectoryStream<Path> stream =
                         Files.newDirectoryStream(targetDir, "*.jar")) {
                for (Path file : stream) {
                    String name = file.getFileName().toString().toLowerCase();
                    if (name.startsWith(prefix) || name.startsWith("voxelclient")
                            || name.startsWith("myclient")) {
                        Files.deleteIfExists(file);
                        removed++;
                    }
                }
            }
            if (removed > 0) {
                log("✔ " + removed + " alte Datei(en) entfernt", InstallerConfig.COLOR_GREEN);
            }

            // ── Step 4: Herunterladen ─────────────────────────────────────────
            log("⬇ Lade " + release.jarFileName() + "…", null);
            Path dest = targetDir.resolve(release.jarFileName());
            Path tmp  = targetDir.resolve(release.jarFileName() + ".tmp");

            HttpURLConnection conn = (HttpURLConnection)
                    URI.create(release.jarDownloadUrl()).toURL().openConnection();
            conn.setRequestProperty("User-Agent",
                    InstallerConfig.MOD_NAME + "-Installer/1.0");
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(30_000);

            long totalBytes = release.jarSizeBytes();

            try (InputStream in  = new BufferedInputStream(conn.getInputStream());
                 OutputStream out = Files.newOutputStream(tmp)) {

                byte[] buf       = new byte[8192];
                long   loaded    = 0;
                int    read;

                while ((read = in.read(buf)) != -1) {
                    out.write(buf, 0, read);
                    loaded += read;
                    onProgress.accept(loaded, totalBytes);
                }
            }

            // Temporäre Datei umbenennen
            Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING);

            // ── Step 5: Verifizieren ──────────────────────────────────────────
            log("🔍 Verifiziere…", null);
            long actualSize = Files.size(dest);
            if (actualSize < 1024) {
                throw new IOException("Heruntergeladene Datei zu klein (" + actualSize + " Bytes).");
            }
            log("✔ " + release.jarFileName()
                    + "  (" + formatSize(actualSize) + ")",
                InstallerConfig.COLOR_GREEN);

            downloadFabricApi(targetDir);

            onSuccess.accept(release.jarFileName());

        } catch (Exception ex) {
            // Temp-Datei aufräumen
            try {
                try (DirectoryStream<Path> stream =
                             Files.newDirectoryStream(targetDir, "*.tmp")) {
                    for (Path f : stream) Files.deleteIfExists(f);
                }
            } catch (IOException ignored) {}

            onError.accept(ex.getMessage() != null ? ex.getMessage() : ex.toString());
        }
    }

    private void log(String msg, String color) {
        onLog.accept(msg, color);
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024)       return bytes + " B";
        if (bytes < 1024*1024)  return (bytes / 1024) + " KB";
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }
}
