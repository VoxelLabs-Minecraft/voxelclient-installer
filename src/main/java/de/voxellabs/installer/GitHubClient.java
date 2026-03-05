package de.voxellabs.installer;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Kommuniziert mit der GitHub Releases API.
 */
public class GitHubClient {

    private static final int TIMEOUT_MS = 8_000;

    public record ReleaseInfo(
            String tagName,
            String releaseName,
            String jarDownloadUrl,
            String jarFileName,
            long   jarSizeBytes
    ) {}

    /**
     * Holt die neueste Release-Info vom GitHub API.
     *
     * @throws IOException wenn die API nicht erreichbar ist oder kein JAR gefunden wurde
     */
    public static ReleaseInfo fetchLatestRelease() throws IOException {
        URL url = URI.create(InstallerConfig.GITHUB_API_URL).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        conn.setRequestProperty("User-Agent",   InstallerConfig.MOD_NAME + "-Installer/1.0");
        conn.setRequestProperty("Accept",       "application/vnd.github+json");
        conn.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");

        int status = conn.getResponseCode();

        if (status == 404) {
            throw new IOException("Noch keine Releases auf GitHub gefunden.\n"
                    + "Erstelle zuerst ein Release unter:\n" + InstallerConfig.GITHUB_RELEASES);
        }
        if (status != 200) {
            throw new IOException("GitHub API antwortete mit HTTP " + status);
        }

        JsonObject json;
        try (InputStreamReader reader = new InputStreamReader(
                conn.getInputStream(), StandardCharsets.UTF_8)) {
            json = JsonParser.parseReader(reader).getAsJsonObject();
        }

        String tagName     = json.has("tag_name")
                ? json.get("tag_name").getAsString() : "unbekannt";
        String releaseName = json.has("name") && !json.get("name").isJsonNull()
                ? json.get("name").getAsString() : tagName;

        // JAR-Asset suchen
        if (!json.has("assets") || json.get("assets").isJsonNull()) {
            throw new IOException("Release '" + tagName + "' hat keine Assets.\n"
                    + "Bitte lade eine .jar-Datei als Release-Asset hoch.");
        }

        JsonArray assets = json.getAsJsonArray("assets");
        for (var element : assets) {
            JsonObject asset = element.getAsJsonObject();
            String name = asset.get("name").getAsString();
            if (name.endsWith(".jar")) {
                return new ReleaseInfo(
                        tagName,
                        releaseName,
                        asset.get("browser_download_url").getAsString(),
                        name,
                        asset.get("size").getAsLong()
                );
            }
        }

        throw new IOException("Kein .jar-Asset in Release '" + tagName + "' gefunden.\n"
                + "Bitte lade eine .jar-Datei als Release-Asset hoch.");
    }
}
