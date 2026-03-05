package de.voxellabs.installer;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static de.voxellabs.installer.DownloadWorker.FABRIC_API_MODRINTH;

/**
 * Haupt-GUI des VoxelClient Installers.
 *
 * Design: passt zum In-Game-Stil von VoxelClient
 *   – dunkles Theme (#06060f Hintergrund)
 *   – animierter Canvas-Hintergrund mit Partikeln
 *   – Blau/Lila Glow-Akzente
 *   – Rajdhani-ähnliche Schriftarten via Java2D
 */
public class InstallerApp extends JFrame {

    // ── Farben ────────────────────────────────────────────────────────────────
    private static final Color C_BG         = hex(InstallerConfig.COLOR_BG);
    private static final Color C_SURFACE    = hex(InstallerConfig.COLOR_SURFACE);
    private static final Color C_BORDER     = hex(InstallerConfig.COLOR_BORDER);
    private static final Color C_ACCENT     = hex(InstallerConfig.COLOR_ACCENT);
    private static final Color C_ACCENT_DIM = hex(InstallerConfig.COLOR_ACCENT_DIM);
    private static final Color C_GREEN      = hex(InstallerConfig.COLOR_GREEN);
    private static final Color C_RED        = hex(InstallerConfig.COLOR_RED);
    private static final Color C_GOLD       = hex(InstallerConfig.COLOR_GOLD);
    private static final Color C_TEXT       = hex(InstallerConfig.COLOR_TEXT);
    private static final Color C_TEXT_DIM   = hex(InstallerConfig.COLOR_TEXT_DIM);

    // ── State ─────────────────────────────────────────────────────────────────
    private enum Phase { IDLE, CHECKING, DOWNLOADING, DONE, ERROR }
    private Phase phase = Phase.IDLE;

    // ── Widgets ───────────────────────────────────────────────────────────────
    private JLabel        lblVersion;
    private JTextField    tfDir;
    private JProgressBar  progressBar;
    private JLabel        lblStatus;
    private JButton       btnInstall;
    private JButton       btnBrowse;
    private LogPanel      logPanel;
    private AnimBgPanel   bgPanel;

    public InstallerApp() {
        super(InstallerConfig.MOD_NAME + " Installer");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(580, 640);
        setResizable(false);
        setLocationRelativeTo(null);

        // Undecorated für Custom-Titlebar
        setUndecorated(true);
        setShape(new RoundRectangle2D.Double(0, 0, 580, 640, 16, 16));

        buildUI();
        setVisible(true);

        // Version im Hintergrund laden
        startVersionCheck();
    }

    // ── UI aufbauen ───────────────────────────────────────────────────────────
    private void buildUI() {
        // Animierter Hintergrund als root
        bgPanel = new AnimBgPanel();
        bgPanel.setLayout(new BorderLayout());
        setContentPane(bgPanel);

        // Stack: Titlebar + Header + Body + Footer
        JPanel stack = new JPanel();
        stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
        stack.setOpaque(false);
        bgPanel.add(stack, BorderLayout.CENTER);

        stack.add(buildTitleBar());
        stack.add(buildHeader());
        stack.add(buildBody());
        stack.add(Box.createVerticalGlue());
        stack.add(buildFooter());
    }

    // ── Custom Titlebar (Drag + Close) ────────────────────────────────────────
    private JPanel buildTitleBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setOpaque(false);
        bar.setPreferredSize(new Dimension(580, 32));
        bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        bar.setBorder(new EmptyBorder(0, 16, 0, 8));

        JLabel title = new JLabel(InstallerConfig.MOD_NAME + " Installer");
        title.setForeground(C_TEXT_DIM);
        title.setFont(new Font("Monospaced", Font.PLAIN, 10));
        bar.add(title, BorderLayout.WEST);

        // Close button
        JButton btnClose = new JButton("✕");
        btnClose.setForeground(C_TEXT_DIM);
        btnClose.setBackground(null);
        btnClose.setBorderPainted(false);
        btnClose.setContentAreaFilled(false);
        btnClose.setFocusPainted(false);
        btnClose.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnClose.setFont(new Font("Dialog", Font.PLAIN, 12));
        btnClose.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { btnClose.setForeground(C_RED); }
            public void mouseExited(MouseEvent e)  { btnClose.setForeground(C_TEXT_DIM); }
        });
        btnClose.addActionListener(e -> System.exit(0));
        bar.add(btnClose, BorderLayout.EAST);

        // Drag-to-move
        final Point[] dragStart = {null};
        bar.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) { dragStart[0] = e.getLocationOnScreen(); }
        });
        bar.addMouseMotionListener(new MouseMotionAdapter() {
            public void mouseDragged(MouseEvent e) {
                if (dragStart[0] == null) return;
                Point cur = e.getLocationOnScreen();
                Point loc = getLocation();
                setLocation(loc.x + cur.x - dragStart[0].x,
                            loc.y + cur.y - dragStart[0].y);
                dragStart[0] = cur;
            }
        });

        return bar;
    }

    // ── Header: Logo + Name + Version ────────────────────────────────────────
    private JPanel buildHeader() {
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(8, 0, 24, 0));

        // Accent top bar
        JPanel topBar = new JPanel();
        topBar.setBackground(C_ACCENT);
        topBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 2));
        topBar.setPreferredSize(new Dimension(580, 2));
        header.add(topBar);

        header.add(Box.createVerticalStrut(20));

        // Logo circle
        LogoPanel logo = new LogoPanel();
        logo.setAlignmentX(CENTER_ALIGNMENT);
        header.add(logo);

        header.add(Box.createVerticalStrut(10));

        // Mod name
        JLabel name = new JLabel(InstallerConfig.MOD_NAME);
        name.setForeground(C_TEXT);
        name.setFont(new Font("Dialog", Font.BOLD, 22));
        name.setAlignmentX(CENTER_ALIGNMENT);
        header.add(name);

        header.add(Box.createVerticalStrut(4));

        // Version (wird nach API-Check befüllt)
        lblVersion = new JLabel("Version wird geladen…");
        lblVersion.setForeground(C_TEXT_DIM);
        lblVersion.setFont(new Font("Monospaced", Font.PLAIN, 10));
        lblVersion.setAlignmentX(CENTER_ALIGNMENT);
        header.add(lblVersion);

        header.add(Box.createVerticalStrut(16));

        // Separator
        JSeparator sep = new JSeparator();
        sep.setForeground(C_BORDER);
        sep.setBackground(C_BORDER);
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        header.add(sep);

        return header;
    }

    // ── Body: Info + Pfad + Log + Progress + Button ──────────────────────────
    private JPanel buildBody() {
        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setOpaque(false);
        body.setBorder(new EmptyBorder(20, 32, 20, 32));

        // Info row
        JPanel infoRow = new JPanel(new GridLayout(1, 3, 8, 0));
        infoRow.setOpaque(false);
        infoRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        infoRow.add(infoBox("Minecraft", InstallerConfig.MOD_VERSION_REQ));
        infoRow.add(infoBox("Mod Loader", InstallerConfig.LOADER));
        infoRow.add(infoBox("Benötigt",   InstallerConfig.JAVA_REQ));
        body.add(infoRow);

        body.add(Box.createVerticalStrut(20));

        // Directory label
        JLabel dirLabel = small("INSTALLATIONSORDNER");
        dirLabel.setAlignmentX(LEFT_ALIGNMENT);
        body.add(dirLabel);
        body.add(Box.createVerticalStrut(6));

        // Directory row
        JPanel dirRow = new JPanel(new BorderLayout(6, 0));
        dirRow.setOpaque(false);
        dirRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        dirRow.setAlignmentX(LEFT_ALIGNMENT);

        tfDir = new JTextField(MinecraftPathDetector.getModsFolder().toString());
        tfDir.setBackground(C_SURFACE);
        tfDir.setForeground(C_TEXT);
        tfDir.setCaretColor(C_TEXT);
        tfDir.setFont(new Font("Monospaced", Font.PLAIN, 10));
        tfDir.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(C_BORDER),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        dirRow.add(tfDir, BorderLayout.CENTER);

        btnBrowse = darkButton("…");
        btnBrowse.setPreferredSize(new Dimension(36, 34));
        btnBrowse.addActionListener(e -> browse());
        dirRow.add(btnBrowse, BorderLayout.EAST);

        body.add(dirRow);
        body.add(Box.createVerticalStrut(16));

        // Log panel
        logPanel = new LogPanel();
        logPanel.setAlignmentX(LEFT_ALIGNMENT);
        logPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 96));
        body.add(logPanel);

        body.add(Box.createVerticalStrut(12));

        // Status text
        lblStatus = small("Bereit zur Installation");
        lblStatus.setAlignmentX(LEFT_ALIGNMENT);
        body.add(lblStatus);
        body.add(Box.createVerticalStrut(6));

        // Progress bar
        progressBar = new JProgressBar(0, 100);
        progressBar.setValue(0);
        progressBar.setStringPainted(false);
        progressBar.setBackground(new Color(14, 14, 34));
        progressBar.setForeground(C_ACCENT);
        progressBar.setBorder(BorderFactory.createLineBorder(C_BORDER));
        progressBar.setPreferredSize(new Dimension(0, 6));
        progressBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 6));
        progressBar.setAlignmentX(LEFT_ALIGNMENT);
        body.add(progressBar);

        body.add(Box.createVerticalStrut(20));

        // Install button
        btnInstall = new JButton("⬇  Jetzt installieren");
        btnInstall.setBackground(C_ACCENT_DIM);
        btnInstall.setForeground(Color.WHITE);
        btnInstall.setFont(new Font("Dialog", Font.BOLD, 14));
        btnInstall.setBorder(BorderFactory.createEmptyBorder(14, 0, 14, 0));
        btnInstall.setFocusPainted(false);
        btnInstall.setBorderPainted(false);
        btnInstall.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnInstall.setMaximumSize(new Dimension(Integer.MAX_VALUE, 52));
        btnInstall.setAlignmentX(LEFT_ALIGNMENT);
        btnInstall.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) {
                if (phase == Phase.IDLE || phase == Phase.ERROR)
                    btnInstall.setBackground(C_ACCENT);
            }
            public void mouseExited(MouseEvent e) {
                if (phase == Phase.IDLE || phase == Phase.ERROR)
                    btnInstall.setBackground(C_ACCENT_DIM);
            }
        });
        btnInstall.addActionListener(e -> startInstall());
        body.add(btnInstall);

        return body;
    }

    // ── Footer ────────────────────────────────────────────────────────────────
    private JPanel buildFooter() {
        JPanel footer = new JPanel(new BorderLayout());
        footer.setBackground(C_SURFACE);
        footer.setBorder(new MatteBorder(1, 0, 0, 0, C_BORDER));
        footer.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        footer.setPreferredSize(new Dimension(580, 36));

        JLabel copy = new JLabel("  Plantaria.net  ♥  ave.rip  ·  MIT License");
        copy.setForeground(C_TEXT_DIM);
        copy.setFont(new Font("Monospaced", Font.PLAIN, 9));
        footer.add(copy, BorderLayout.CENTER);

        return footer;
    }

    // ── Version Check ─────────────────────────────────────────────────────────
    private void startVersionCheck() {
        new Thread(() -> {
            try {
                GitHubClient.ReleaseInfo rel = GitHubClient.fetchLatestRelease();
                SwingUtilities.invokeLater(() -> {
                    lblVersion.setText("Fabric · Minecraft "
                            + InstallerConfig.MOD_VERSION_REQ
                            + " · " + rel.releaseName());
                    logPanel.addLine("✔ Verfügbar: " + rel.jarFileName(), C_GREEN);
                });
                try {
                    HttpURLConnection conn = (HttpURLConnection) new URI(FABRIC_API_MODRINTH).toURL().openConnection();
                    conn.setRequestProperty("User-Agent", "FabricAPI-Installer/1.0");
                    conn.setConnectTimeout(8_000);
                    conn.setReadTimeout(8_000);

                    if (conn.getResponseCode() == 200) {
                        JsonArray versions;
                        try (InputStreamReader reader = new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)) {
                            versions = JsonParser.parseReader(reader).getAsJsonArray();
                        }
                        if (!versions.isEmpty()) {
                            JsonArray files = versions.get(0)
                                    .getAsJsonObject()
                                    .getAsJsonArray("files");
                            for (var element : files) {
                                JsonObject file = element.getAsJsonObject();
                                if (file.get("primary").getAsBoolean()) {
                                    String filename = file.get("filename").getAsString();
                                    SwingUtilities.invokeLater(() ->
                                            logPanel.addLine("✔ Verfügbar: " + filename, C_GREEN));
                                    break;
                                }
                            }
                        }
                    }
                } catch (Exception exception) {
                    logPanel.addLine("⚠ Fehler beim Laden der Fabric Version: " + exception.getMessage(), C_RED);
                }
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    lblVersion.setText("Konnte Version nicht laden");
                    logPanel.addLine("⚠ " + ex.getMessage(), C_RED);
                });
            }
        }, "version-check").start();
    }

    // ── Install ───────────────────────────────────────────────────────────────
    private void startInstall() {
        if (phase == Phase.DOWNLOADING || phase == Phase.DONE) return;
        phase = Phase.DOWNLOADING;

        btnInstall.setEnabled(false);
        btnInstall.setText("Installiere…");
        btnInstall.setBackground(C_BORDER);
        progressBar.setValue(0);

        Path dir = Path.of(tfDir.getText().trim());

        DownloadWorker worker = new DownloadWorker(
            dir,
            // onProgress
            (loaded, total) -> SwingUtilities.invokeLater(() -> {
                int pct = total > 0 ? (int)(loaded * 100 / total) : 0;
                progressBar.setValue(pct);
                lblStatus.setText("Lade… " + formatSize(loaded) + " / " + formatSize(total));
            }),
            // onLog
            (msg, color) -> SwingUtilities.invokeLater(() ->
                logPanel.addLine(msg, color != null ? hex(color) : null)),
            // onSuccess
            jarName -> SwingUtilities.invokeLater(() -> installSuccess(jarName)),
            // onError
            msg -> SwingUtilities.invokeLater(() -> installError(msg))
        );

        Thread t = new Thread(worker, "download-worker");
        t.setDaemon(true);
        t.start();
    }

    private void installSuccess(String jarName) {
        phase = Phase.DONE;
        progressBar.setValue(100);
        progressBar.setForeground(C_GREEN);
        lblStatus.setText("Installation erfolgreich!");

        btnInstall.setEnabled(true);
        btnInstall.setText("✔  Erfolgreich installiert!");
        btnInstall.setBackground(new Color(20, 60, 20));
        btnInstall.setForeground(C_GREEN);

        // Auto-close Dialog nach kurzer Pause
        Timer t = new Timer(800, e -> {
            int ans = JOptionPane.showConfirmDialog(this,
                    InstallerConfig.MOD_NAME + " wurde erfolgreich installiert!\n\n"
                    + "Datei: " + jarName + "\n"
                    + "Ordner: " + tfDir.getText() + "\n\n"
                    + "Starte Minecraft mit dem Fabric Launcher.\n\n"
                    + "Installer schließen?",
                    "Installation abgeschlossen",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.INFORMATION_MESSAGE);
            if (ans == JOptionPane.YES_OPTION) System.exit(0);
        });
        t.setRepeats(false);
        t.start();
    }

    private void installError(String msg) {
        phase = Phase.ERROR;
        progressBar.setValue(0);
        progressBar.setForeground(C_RED);
        lblStatus.setText("Fehler bei der Installation");

        btnInstall.setEnabled(true);
        btnInstall.setText("↺  Erneut versuchen");
        btnInstall.setBackground(new Color(60, 10, 20));
        btnInstall.setForeground(C_RED);

        JOptionPane.showMessageDialog(this,
                "Installation fehlgeschlagen:\n\n" + msg,
                InstallerConfig.MOD_NAME + " Installer – Fehler",
                JOptionPane.ERROR_MESSAGE);

        phase = Phase.IDLE;
        btnInstall.setBackground(C_ACCENT_DIM);
        btnInstall.setForeground(Color.WHITE);
    }

    // ── Browse ────────────────────────────────────────────────────────────────
    private void browse() {
        JFileChooser fc = new JFileChooser(tfDir.getText());
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fc.setDialogTitle("Mods-Ordner wählen");
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File f = fc.getSelectedFile();
            if (f != null) tfDir.setText(f.getAbsolutePath());
        }
    }

    // ── Helper Widgets ────────────────────────────────────────────────────────
    private JPanel infoBox(String label, String value) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(C_SURFACE);
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(C_BORDER),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)));

        JPanel accentBar = new JPanel();
        accentBar.setBackground(C_ACCENT);
        accentBar.setPreferredSize(new Dimension(3, 0));
        p.add(accentBar, BorderLayout.WEST);

        JPanel text = new JPanel();
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        text.setBackground(C_SURFACE);
        text.setBorder(new EmptyBorder(0, 8, 0, 0));

        JLabel lbl = new JLabel(label);
        lbl.setForeground(C_TEXT_DIM);
        lbl.setFont(new Font("Monospaced", Font.PLAIN, 9));
        text.add(lbl);

        JLabel val = new JLabel(value);
        val.setForeground(C_TEXT);
        val.setFont(new Font("Dialog", Font.BOLD, 10));
        text.add(val);

        p.add(text, BorderLayout.CENTER);
        return p;
    }

    private JButton darkButton(String text) {
        JButton btn = new JButton(text);
        btn.setBackground(C_SURFACE);
        btn.setForeground(C_TEXT_DIM);
        btn.setBorder(BorderFactory.createLineBorder(C_BORDER));
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setFont(new Font("Dialog", Font.PLAIN, 12));
        return btn;
    }

    private JLabel small(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(C_TEXT_DIM);
        l.setFont(new Font("Monospaced", Font.PLAIN, 9));
        return l;
    }

    // ── Color helpers ─────────────────────────────────────────────────────────
    private static Color hex(String hex) {
        return Color.decode(hex);
    }

    private static String formatSize(long bytes) {
        if (bytes <= 0)          return "0 B";
        if (bytes < 1024)        return bytes + " B";
        if (bytes < 1024*1024)   return (bytes / 1024) + " KB";
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Inner Panels
    // ══════════════════════════════════════════════════════════════════════════

    /** Animierter Partikel-Hintergrund */
    static class AnimBgPanel extends JPanel {
        private static final int PARTICLE_COUNT = 55;
        record Particle(float x, float y, float vx, float vy, float r, float alpha) {}

        private final List<Particle> particles = new ArrayList<>();
        private float time = 0;
        private final Timer animTimer;

        AnimBgPanel() {
            setBackground(C_BG);
            Random rng = new Random();
            for (int i = 0; i < PARTICLE_COUNT; i++) {
                particles.add(new Particle(
                        rng.nextFloat(), rng.nextFloat(),
                        (rng.nextFloat() - .5f) * .0006f,
                        (rng.nextFloat() - .5f) * .0006f,
                        rng.nextFloat() * 1.5f + .4f,
                        rng.nextFloat() * .4f + .1f
                ));
            }
            animTimer = new Timer(32, e -> { time += 0.016f; repaint(); });
            animTimer.start();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth(), h = getHeight();

            // Blobs
            float[][] blobs = {
                {.25f, .3f,  .14f, .10f, 1.0f, 340, 34, 68, 170},
                {.75f, .7f,  .10f, .08f, 0.8f, 280, 68, 17, 102},
                {.50f, .45f, .06f, .05f, 1.3f, 200, 20, 55, 140},
            };
            for (float[] b : blobs) {
                float bx = (b[0] + b[2] * (float)Math.sin(time + b[4])) * w;
                float by = (b[1] + b[3] * (float)Math.cos(time * b[4])) * h;
                int   r  = (int) b[5];
                RadialGradientPaint rg = new RadialGradientPaint(
                        bx, by, r,
                        new float[]{0f, 1f},
                        new Color[]{
                            new Color((int)b[6], (int)b[7], (int)b[8], 22),
                            new Color((int)b[6], (int)b[7], (int)b[8], 0)
                        });
                g2.setPaint(rg);
                g2.fillRect(0, 0, w, h);
            }

            // Particles
            for (int i = 0; i < particles.size(); i++) {
                Particle p = particles.get(i);
                float px = ((p.x + p.vx * time * 60) % 1.0f + 1.0f) % 1.0f;
                float py = ((p.y + p.vy * time * 60) % 1.0f + 1.0f) % 1.0f;
                float a  = p.alpha * (.6f + .4f * (float)Math.sin(time + px * 10));
                g2.setColor(new Color(120, 180, 255, (int)(a * 255)));
                g2.fillOval((int)(px * w), (int)(py * h), (int)p.r, (int)p.r);
            }
        }
    }

    /** Pulsierendes Logo-Circle */
    static class LogoPanel extends JPanel {
        private float pulse = 0f;
        private boolean up  = true;

        LogoPanel() {
            setOpaque(false);
            setPreferredSize(new Dimension(72, 72));
            new Timer(30, e -> {
                if (up) { pulse += .02f; if (pulse > 1) up = false; }
                else    { pulse -= .02f; if (pulse < 0) up = true; }
                repaint();
            }).start();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int cx = getWidth() / 2, cy = getHeight() / 2, r = 30;

            // Glow rings
            for (int i = 3; i >= 1; i--) {
                int gr = r + i * 5;
                int ga = (int)(pulse * 40 / i);
                g2.setColor(new Color(74, 140, 255, ga));
                g2.fillOval(cx - gr, cy - gr, gr * 2, gr * 2);
            }

            // Circle background
            GradientPaint gp = new GradientPaint(
                    cx - r, cy - r, new Color(26, 42, 102),
                    cx + r, cy + r, new Color(51, 102, 204));
            g2.setPaint(gp);
            g2.fillOval(cx - r, cy - r, r * 2, r * 2);

            // Border
            int glowA = (int)(100 + pulse * 155);
            g2.setColor(new Color(74, 140, 255, glowA));
            g2.setStroke(new BasicStroke(2f));
            g2.drawOval(cx - r, cy - r, r * 2, r * 2);

            // "M" letter
            g2.setColor(Color.WHITE);
            g2.setFont(new Font("Dialog", Font.BOLD, 26));
            FontMetrics fm = g2.getFontMetrics();
            String letter = "V";
            g2.drawString(letter,
                    cx - fm.stringWidth(letter) / 2,
                    cy + fm.getAscent() / 2 - 2);
        }
    }

    /** Rolling-Log Panel (letzte 4 Zeilen) */
    static class LogPanel extends JPanel {
        private static final int MAX_LINES = 4;
        private final List<String> texts  = new ArrayList<>();
        private final List<Color>  colors = new ArrayList<>();

        LogPanel() {
            setBackground(C_SURFACE);
            setBorder(BorderFactory.createLineBorder(C_BORDER));
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setPreferredSize(new Dimension(0, 96));
            // Pre-fill with empty lines
            for (int i = 0; i < MAX_LINES; i++) { texts.add(""); colors.add(C_TEXT_DIM); }
        }

        public void addLine(String msg, Color color) {
            texts.add(msg);
            colors.add(color != null ? color : C_TEXT_DIM);
            while (texts.size()  > MAX_LINES) texts.remove(0);
            while (colors.size() > MAX_LINES) colors.remove(0);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setFont(new Font("Monospaced", Font.PLAIN, 10));
            FontMetrics fm = g2.getFontMetrics();
            int lineH = fm.getHeight() + 4;
            int y = 6 + fm.getAscent();
            for (int i = 0; i < texts.size(); i++) {
                g2.setColor(colors.get(i));
                g2.drawString("  " + texts.get(i), 0, y);
                y += lineH;
            }
        }
    }

    // ── main ──────────────────────────────────────────────────────────────────
    public static void main(String[] args) {
        // System Look für Dialoge (JOptionPane etc.)
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
        catch (Exception ignored) {}

        // Dark-Mode Hints für Windows 11
        System.setProperty("sun.java2d.uiScale", "1.0");

        SwingUtilities.invokeLater(InstallerApp::new);
    }
}
