#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# build_installer.sh
# Baut den VoxelClient Installer als plattformspezifische Standalone-Datei.
#
# Windows  → voxelclient-installer.exe  (mit PyInstaller auf Windows ausführen)
# macOS    → voxelclient-installer       (mit PyInstaller auf macOS ausführen)
# Linux    → voxelclient-installer       (mit PyInstaller auf Linux ausführen)
#
# Voraussetzungen:
#   pip install pyinstaller
# ─────────────────────────────────────────────────────────────────────────────

set -e

echo "=== VoxelClient Installer Builder ==="
echo ""

# PyInstaller installieren falls nicht vorhanden
if ! command -v pyinstaller &>/dev/null; then
    echo "▸ Installiere PyInstaller…"
    pip install pyinstaller --break-system-packages
fi

echo "▸ Baue Installer…"
pyinstaller \
    --onefile \
    --windowed \
    --name "voxelclient-installer" \
    --clean \
    voxelclient_installer.py

echo ""
echo "✔ Fertig! Datei liegt in: dist/voxelclient-installer"
