# VoxelClient Installer – Java Projekt

Standalone-Installer für VoxelClient. Lädt automatisch die neueste
`.jar` von GitHub herunter und installiert sie in den Minecraft-Ordner.

---

## 🚀 Starten (direkt)

```bash
mvn clean package
java -jar target/voxelclient-installer-1.0.0.jar
```

---

## 🪟 Als .exe bauen (Windows)

Die `pom.xml` enthält bereits das **Launch4j Maven Plugin**.
Nach einem normalen `mvn package` wird die `.exe` automatisch erzeugt:

```bash
mvn clean package
```

Ausgabe:
```
target/voxelclient-installer.exe   ← fertige Windows-EXE
target/voxelclient-installer-1.0.0.jar  ← fat JAR (plattformübergreifend)
```

> **Hinweis:** Das Launch4j Maven Plugin lädt Launch4j automatisch herunter.
> Kein manuelles Installieren nötig.

---

## ⚙️ Konfiguration

Nur `InstallerConfig.java` anpassen:

```java
public static final String GITHUB_OWNER = "VoxelLabs-Minecraft";    // ← dein GitHub-Name
public static final String GITHUB_REPO  = "voxelclient"; // ← dein Repo-Name
public static final String MOD_NAME     = "VoxelClient";  // ← Anzeigename
```

---

## 📁 Projektstruktur

```
pom.xml
src/main/java/de/voxellabs/installer/
├── InstallerApp.java        ← Haupt-GUI (Swing)
├── InstallerConfig.java     ← Konfiguration (GitHub, Farben, Namen)
├── GitHubClient.java        ← GitHub Releases API
├── DownloadWorker.java      ← Download-Logik (Hintergrund-Thread)
└── MinecraftPathDetector.java ← Automatische Pfad-Erkennung
```

---

## 📦 Abhängigkeiten

| Bibliothek | Zweck                    |
|------------|--------------------------|
| GSON       | GitHub API JSON parsen   |
| Java Swing | GUI (in JDK enthalten)   |
| Launch4j   | JAR → .exe (nur Build)   |

Alle Abhängigkeiten werden von Maven automatisch heruntergeladen.
