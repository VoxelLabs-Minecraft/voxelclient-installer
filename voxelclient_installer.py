#!/usr/bin/env python3
"""
VoxelClient Installer
==================
Lädt automatisch die neueste VoxelClient .jar von GitHub herunter
und installiert sie in den Minecraft mods-Ordner.

Kompatibel mit: Windows · macOS · Linux
Benötigt: Python 3.9+  (kein pip-Paket nötig – nur stdlib)
"""

import os
import sys
import json
import shutil
import threading
import urllib.request
import urllib.error
from pathlib import Path
from tkinter import (
    Tk, Canvas, Frame, Label, Button, Progressbar,
    StringVar, PhotoImage, messagebox, filedialog
)
import tkinter as tk
import tkinter.ttk as ttk
import tkinter.font as tkfont

# ── Konfiguration ─────────────────────────────────────────────────────────────
GITHUB_OWNER  = "VoxelLabs-Minecraft"
GITHUB_REPO   = "voxelclient"
MOD_NAME      = "VoxelClient"
ACCENT        = "#4a8cff"
ACCENT_DIM    = "#2255bb"
GOLD          = "#ffcc00"
GREEN         = "#2ecc71"
RED           = "#ff4466"
BG            = "#06060f"
SURFACE       = "#0d0d20"
BORDER        = "#1a2044"
TEXT          = "#dde4ff"
TEXT_DIM      = "#667799"

API_URL       = f"https://api.github.com/repos/{GITHUB_OWNER}/{GITHUB_REPO}/releases/latest"
DOWNLOAD_URL_FALLBACK = f"https://github.com/{GITHUB_OWNER}/{GITHUB_REPO}/releases/latest"

# ── Minecraft mods-Ordner ermitteln ──────────────────────────────────────────
def find_minecraft_mods() -> Path:
    system = sys.platform
    home   = Path.home()

    if system == "win32":
        base = Path(os.environ.get("APPDATA", home / "AppData" / "Roaming"))
        return base / ".minecraft" / "mods"
    elif system == "darwin":
        return home / "Library" / "Application Support" / "minecraft" / "mods"
    else:
        return home / ".minecraft" / "mods"

# ── Haupt-App ─────────────────────────────────────────────────────────────────
class InstallerApp(Tk):
    def __init__(self):
        super().__init__()

        self.title(f"{MOD_NAME} Installer")
        self.resizable(False, False)
        self.configure(bg=BG)

        # Fenster zentrieren
        self.update_idletasks()
        w, h = 560, 620
        x = (self.winfo_screenwidth()  - w) // 2
        y = (self.winfo_screenheight() - h) // 2
        self.geometry(f"{w}x{h}+{x}+{y}")

        # State
        self.install_dir  = StringVar(value=str(find_minecraft_mods()))
        self.status_text  = StringVar(value="Bereit zur Installation")
        self.version_text = StringVar(value="Version wird geladen…")
        self.progress_var = tk.DoubleVar(value=0)
        self.phase        = "idle"  # idle | checking | downloading | done | error

        self._setup_style()
        self._build_ui()
        self._start_version_check()

    # ── Style ──────────────────────────────────────────────────────────────────
    def _setup_style(self):
        self.style = ttk.Style(self)
        self.style.theme_use("clam")

        self.style.configure("TProgressbar",
            troughcolor  = "#0e0e22",
            background   = ACCENT,
            bordercolor  = BORDER,
            lightcolor   = ACCENT,
            darkcolor    = ACCENT_DIM,
            thickness    = 6,
        )

        self.style.configure("Dark.TFrame", background=BG)

    # ── UI ─────────────────────────────────────────────────────────────────────
    def _build_ui(self):
        # ── Header ─────────────────────────────────────────────────────────────
        header = Frame(self, bg=SURFACE, height=180)
        header.pack(fill="x")
        header.pack_propagate(False)

        # Top accent line
        accent_line = Frame(header, bg=ACCENT, height=2)
        accent_line.pack(fill="x", side="top")

        # Logo circle
        logo_canvas = Canvas(header, width=72, height=72,
                             bg=SURFACE, highlightthickness=0)
        logo_canvas.pack(pady=(24, 0))
        logo_canvas.create_oval(4, 4, 68, 68,
                                fill="#1a2a66", outline=ACCENT, width=2)
        logo_canvas.create_text(36, 36, text="M",
                                font=("Helvetica", 28, "bold"),
                                fill="white")

        # Mod name
        Label(header, text=MOD_NAME,
              bg=SURFACE, fg=TEXT,
              font=("Helvetica", 20, "bold")).pack(pady=(6, 0))

        # Version
        Label(header, textvariable=self.version_text,
              bg=SURFACE, fg=TEXT_DIM,
              font=("Helvetica", 9)).pack(pady=(2, 0))

        # Bottom separator
        Frame(header, bg=BORDER, height=1).pack(fill="x", side="bottom")

        # ── Body ───────────────────────────────────────────────────────────────
        body = Frame(self, bg=BG, padx=32, pady=20)
        body.pack(fill="both", expand=True)

        # Info boxes
        self._infobox(body, "Minecraft",   "1.21.4")
        self._infobox(body, "Mod Loader",  "Fabric ≥ 0.16")
        self._infobox(body, "Benötigt",    "Fabric API")

        # Spacer
        Frame(body, bg=BG, height=10).pack()

        # Install directory label
        Label(body, text="INSTALLATIONSORDNER",
              bg=BG, fg=TEXT_DIM,
              font=("Helvetica", 8)).pack(anchor="w")

        # Directory row
        dir_row = Frame(body, bg=BG)
        dir_row.pack(fill="x", pady=(4, 16))

        dir_entry = tk.Entry(dir_row, textvariable=self.install_dir,
                             bg=SURFACE, fg=TEXT, insertbackground=TEXT,
                             relief="flat", font=("Courier", 9),
                             highlightthickness=1,
                             highlightbackground=BORDER,
                             highlightcolor=ACCENT)
        dir_entry.pack(side="left", fill="x", expand=True, ipady=7, ipadx=8)

        btn_browse = Button(dir_row, text="…",
                            bg=SURFACE, fg=TEXT_DIM,
                            relief="flat", cursor="hand2",
                            font=("Helvetica", 10),
                            activebackground=BORDER,
                            activeforeground=TEXT,
                            command=self._browse,
                            width=3)
        btn_browse.pack(side="left", padx=(4, 0), ipady=6)

        # Status area
        self.log_frame = Frame(body, bg=SURFACE,
                               highlightthickness=1,
                               highlightbackground=BORDER)
        self.log_frame.pack(fill="x", pady=(0, 12))

        self.log_lines = []
        for _ in range(4):
            lbl = Label(self.log_frame, text="", bg=SURFACE, fg=TEXT_DIM,
                        font=("Courier", 8), anchor="w", padx=10)
            lbl.pack(fill="x", pady=1)
            self.log_lines.append(lbl)

        # Progress bar
        Label(body, textvariable=self.status_text,
              bg=BG, fg=TEXT_DIM,
              font=("Helvetica", 8)).pack(anchor="w")

        self.pbar = ttk.Progressbar(body, variable=self.progress_var,
                                    maximum=100, style="TProgressbar",
                                    length=496, mode="determinate")
        self.pbar.pack(fill="x", pady=(4, 20))

        # Install button
        self.btn_install = Button(body,
            text="⬇  Jetzt installieren",
            bg=ACCENT_DIM, fg="white",
            activebackground=ACCENT, activeforeground="white",
            relief="flat", cursor="hand2",
            font=("Helvetica", 13, "bold"),
            command=self._start_install)
        self.btn_install.pack(fill="x", ipady=12)

        # ── Footer ─────────────────────────────────────────────────────────────
        footer = Frame(self, bg=SURFACE)
        footer.pack(fill="x", side="bottom")
        Frame(footer, bg=BORDER, height=1).pack(fill="x")
        Label(footer,
              text="Plantaria.net  ♥  ave.rip  ·  MIT License",
              bg=SURFACE, fg=TEXT_DIM,
              font=("Helvetica", 8)).pack(pady=8)

    def _infobox(self, parent, label, value):
        row = Frame(parent, bg=SURFACE,
                    highlightthickness=1,
                    highlightbackground=BORDER)
        row.pack(fill="x", pady=3)

        Frame(row, bg=ACCENT, width=3).pack(side="left", fill="y")

        Label(row, text=label, bg=SURFACE, fg=TEXT_DIM,
              font=("Helvetica", 8), width=14, anchor="w",
              padx=10).pack(side="left", pady=6)

        Label(row, text=value, bg=SURFACE, fg=TEXT,
              font=("Helvetica", 9, "bold"), anchor="w").pack(side="left")

    # ── Version check ──────────────────────────────────────────────────────────
    def _start_version_check(self):
        threading.Thread(target=self._check_version, daemon=True).start()

    def _check_version(self):
        try:
            req = urllib.request.Request(
                API_URL,
                headers={"User-Agent": f"{MOD_NAME}-Installer/1.0",
                         "Accept": "application/vnd.github+json"})
            with urllib.request.urlopen(req, timeout=8) as resp:
                data = json.loads(resp.read())

            tag = data.get("tag_name", "")
            normalized = tag.lstrip("v")
            self.latest_tag  = tag
            self.latest_name = data.get("name", f"v{normalized}")

            # Find JAR asset
            self.jar_url  = None
            self.jar_name = None
            for asset in data.get("assets", []):
                if asset["name"].endswith(".jar"):
                    self.jar_url  = asset["browser_download_url"]
                    self.jar_name = asset["name"]
                    break

            self.after(0, lambda: self.version_text.set(
                f"Fabric · Minecraft 1.21.4 · {self.latest_name}"))
            self.after(0, lambda: self._log(f"✔ Gefunden: {self.jar_name or 'release ' + tag}", GREEN))

        except Exception as e:
            self.latest_tag = None
            self.jar_url    = None
            self.jar_name   = None
            self.after(0, lambda: self.version_text.set("Konnte Version nicht laden"))
            self.after(0, lambda: self._log(f"⚠ API nicht erreichbar: {e}", RED))

    # ── Log helper ─────────────────────────────────────────────────────────────
    def _log(self, msg: str, color=None):
        """Add a line to the rolling log (max 4 lines)."""
        for i in range(len(self.log_lines) - 1):
            self.log_lines[i].config(
                text=self.log_lines[i + 1].cget("text"),
                fg=self.log_lines[i + 1].cget("fg")
            )
        self.log_lines[-1].config(text=f"  {msg}", fg=color or TEXT_DIM)

    # ── Browse ─────────────────────────────────────────────────────────────────
    def _browse(self):
        chosen = filedialog.askdirectory(
            initialdir=self.install_dir.get(),
            title=f"{MOD_NAME} – Ordner wählen")
        if chosen:
            self.install_dir.set(chosen)

    # ── Install ────────────────────────────────────────────────────────────────
    def _start_install(self):
        if self.phase in ("downloading", "done"):
            return
        self.phase = "downloading"
        self.btn_install.config(state="disabled", text="Installiere…", bg=BORDER)
        threading.Thread(target=self._do_install, daemon=True).start()

    def _do_install(self):
        try:
            mods_dir = Path(self.install_dir.get())

            # ── Step 1: Ordner erstellen ──────────────────────────────────────
            self.after(0, lambda: self._log("📁 Prüfe mods-Ordner…"))
            self.after(0, lambda: self._set_progress(5))
            mods_dir.mkdir(parents=True, exist_ok=True)
            self.after(0, lambda: self._log(f"✔ Ordner OK: {mods_dir}", GREEN))
            self.after(0, lambda: self._set_progress(15))

            # ── Step 2: Download-URL bestimmen ───────────────────────────────
            jar_url  = getattr(self, "jar_url", None)
            jar_name = getattr(self, "jar_name", None)

            if not jar_url:
                # Kein GitHub-Release gefunden → Fallback-Nachricht
                raise RuntimeError(
                    "Kein JAR-Asset auf GitHub gefunden.\n"
                    "Bitte stelle sicher, dass ein Release mit einer\n"
                    ".jar-Datei auf GitHub existiert.\n\n"
                    f"Releases: {DOWNLOAD_URL_FALLBACK}"
                )

            # ── Step 3: Alte Version entfernen ───────────────────────────────
            self.after(0, lambda: self._log("🗑  Entferne alte Version…"))
            removed = 0
            for f in mods_dir.glob("voxelclient-*.jar"):
                f.unlink()
                removed += 1
            if removed:
                self.after(0, lambda: self._log(f"✔ {removed} alte Datei(en) entfernt", GREEN))
            self.after(0, lambda: self._set_progress(25))

            # ── Step 4: JAR herunterladen ─────────────────────────────────────
            dest = mods_dir / jar_name
            self.after(0, lambda: self._log(f"⬇ Lade {jar_name}…"))
            self.after(0, lambda: self.status_text.set(f"Lade {jar_name}…"))

            def reporthook(block_num, block_size, total_size):
                if total_size > 0:
                    pct = 25 + int((block_num * block_size / total_size) * 65)
                    pct = min(pct, 90)
                    self.after(0, lambda p=pct: self._set_progress(p))
                    kb_done = block_num * block_size // 1024
                    kb_total = total_size // 1024
                    self.after(0, lambda a=kb_done, b=kb_total:
                               self.status_text.set(f"Lade… {a} / {b} KB"))

            req = urllib.request.Request(
                jar_url,
                headers={"User-Agent": f"{MOD_NAME}-Installer/1.0"})
            tmp = dest.with_suffix(".tmp")
            urllib.request.urlretrieve(jar_url, tmp, reporthook)
            tmp.rename(dest)

            # ── Step 5: Verifizieren ──────────────────────────────────────────
            self.after(0, lambda: self._log("🔍 Verifiziere Datei…"))
            if dest.stat().st_size < 1024:
                raise RuntimeError("Heruntergeladene Datei ist zu klein (< 1 KB).")
            self.after(0, lambda: self._set_progress(95))

            # ── Step 6: Fertig ────────────────────────────────────────────────
            self.after(0, self._install_success)

        except Exception as exc:
            self.after(0, lambda e=exc: self._install_error(str(e)))

    def _set_progress(self, value):
        self.progress_var.set(value)

    def _install_success(self):
        self.phase = "done"
        self.progress_var.set(100)
        self.status_text.set("Installation erfolgreich!")
        self._log(f"✔ {self.jar_name} installiert!", GREEN)
        self._log(f"✔ Starte Minecraft und genieße {MOD_NAME}!", GREEN)

        self.btn_install.config(
            state="normal",
            text="✔  Erfolgreich installiert!",
            bg="#1a4a1a",
            fg=GREEN
        )

        # Auto-close nach 4 Sekunden
        self.after(4000, self._ask_close)

    def _install_error(self, msg: str):
        self.phase = "error"
        self.progress_var.set(0)
        self.status_text.set("Fehler bei der Installation")
        self._log(f"✖ Fehler: {msg[:60]}", RED)

        self.btn_install.config(
            state="normal",
            text="↺  Erneut versuchen",
            bg="#4a0a0a",
            fg=RED
        )
        self.phase = "idle"

        messagebox.showerror(
            f"{MOD_NAME} Installer – Fehler",
            f"Installation fehlgeschlagen:\n\n{msg}"
        )

    def _ask_close(self):
        ans = messagebox.askyesno(
            f"{MOD_NAME} Installer",
            f"{MOD_NAME} wurde erfolgreich installiert!\n\n"
            "Starte jetzt Minecraft mit dem Fabric Launcher.\n\n"
            "Installer schließen?"
        )
        if ans:
            self.destroy()


# ── Einstiegspunkt ────────────────────────────────────────────────────────────
if __name__ == "__main__":
    app = InstallerApp()
    app.mainloop()
