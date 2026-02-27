# MyClient Installer

Automatischer Installer für den MyClient Fabric Mod.

---

## ✨ Was macht der Installer?

1. Prüft auf die **neueste Version** über die GitHub Releases API
2. Erkennt automatisch den **Minecraft mods-Ordner**
   - Windows: `%APPDATA%\.minecraft\mods`
   - macOS:   `~/Library/Application Support/minecraft/mods`
   - Linux:   `~/.minecraft/mods`
3. Entfernt alte `myclient-*.jar` Dateien
4. Lädt die aktuelle `.jar` direkt von GitHub herunter
5. Zeigt Fortschritt in Echtzeit an

---

## 🚀 Starten (direkt mit Python)

```bash
# Einmalig – Python 3.9+ wird benötigt (keine pip-Pakete nötig!)
python3 myclient_installer.py
```

---

## 📦 Als Standalone-EXE bauen (kein Python nötig für Endnutzer)

```bash
# PyInstaller installieren
pip install pyinstaller

# Windows
pyinstaller --onefile --windowed --name "myclient-installer" myclient_installer.py
# → dist\myclient-installer.exe

# macOS / Linux
bash build_installer.sh
# → dist/myclient-installer
```

---

## ⚙️ Konfiguration

Nur drei Zeilen in `myclient_installer.py` anpassen:

```python
GITHUB_OWNER = "yourname"    # ← dein GitHub-Nutzername
GITHUB_REPO  = "myclient"    # ← dein Repository-Name
MOD_NAME     = "MyClient"    # ← Anzeigename
```

Der Installer lädt dann automatisch das neueste Release mit `.jar`-Asset.

---

## 📋 Voraussetzungen (für Endnutzer)

| Was        | Version    |
|------------|------------|
| Python     | 3.9+ *(nur wenn kein .exe)* |
| Minecraft  | 1.21.4     |
| Fabric Loader | ≥ 0.16  |
| Fabric API | beliebig   |

---

## 🏗️ Projekt-Struktur

```
myclient_installer.py   ← Haupt-Installer (GUI + Logik)
build_installer.sh      ← Build-Skript für Standalone-EXE
README_installer.md     ← Diese Datei
```
