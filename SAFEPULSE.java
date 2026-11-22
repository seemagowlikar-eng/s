import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.io.*;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import javax.swing.*;
import javax.swing.border.*;

/**
 * ============================================================
 *  SAFEPULSE - Smart Women Safety System  (College Mini Project)
 * ============================================================
 *
 *  FEATURES
 *   1.  Animated SOS button + F1 keyboard shortcut (TEST SMS MODE)
 *   2.  Emergency contacts (add / delete, saved in contacts.txt)
 *   3.  Safe Journey check-in timer (auto-SOS if user does not confirm)
 *   4.  Live approximate location (IP based) + Google Maps link
 *   5.  Nearby Help finder (police / hospital / pharmacy / fire station)
 *   6.  Fake Call (incoming call simulation to escape a situation)
 *   7.  Siren Alarm (loud generated siren sound + flashing screen)
 *   8.  Medical Profile (blood group, allergies - added to SOS message)
 *   9.  SOS result: Copy message / Send via WhatsApp
 *  10.  Live dashboard: contacts, SOS count, check-ins, readiness score
 *  11.  Alert history with colour coding (saved in history.txt)
 *  12.  Safety tips, emergency helplines, splash screen
 *
 *  CONCEPTS USED
 *   Java Swing (GUI), Event handling, File handling, Collections,
 *   Multithreading (SwingWorker + audio thread), HTTP API call,
 *   Regular expressions, Custom painting (Graphics2D), Timers.
 */
public class SafePulse extends JFrame {

    // ---------- DATA ----------
    ArrayList<String> contacts = new ArrayList<>();
    Properties profile = new Properties();
    String currentLatitude = null;
    String currentLongitude = null;
    String currentMapsLink = null;

    // ---------- UI REFERENCES ----------
    private JLabel locationStatus;
    private JLabel statContacts;
    private JLabel statSos;
    private JLabel statCheckins;
    private JLabel statReady;

    private final Siren siren = new Siren();

    // ---------- THEME ----------
    static final Color BG_TOP     = new Color(14, 16, 30);
    static final Color BG_BOTTOM  = new Color(40, 22, 62);
    static final Color CARD       = new Color(32, 35, 56);
    static final Color CARD_ALT   = new Color(38, 42, 66);
    static final Color CARD_HOVER = new Color(54, 59, 92);
    static final Color FIELD      = new Color(44, 48, 74);
    static final Color ACCENT     = new Color(255, 64, 96);
    static final Color VIOLET     = new Color(124, 92, 255);
    static final Color GREEN      = new Color(46, 204, 113);
    static final Color YELLOW     = new Color(255, 200, 60);
    static final Color MUTED      = new Color(160, 165, 190);

    private static final DateTimeFormatter TIME_FORMAT =
        DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

    private static final String[] TIPS = {
        "Share your live location with a trusted person when travelling alone.",
        "Keep your phone charged above 30% before leaving home.",
        "Trust your instincts - if a place feels unsafe, leave.",
        "Note the vehicle number before boarding a cab and share it.",
        "Keep emergency numbers on speed dial: 112, 100, 1091.",
        "Walk in well-lit and busy areas, especially at night.",
        "Avoid wearing headphones at full volume while walking alone."
    };

    // =====================================================
    //  CUSTOM UI COMPONENTS
    // =====================================================

    /** Panel with a vertical gradient background. */
    static class GradientPanel extends JPanel {
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setPaint(new GradientPaint(0, 0, BG_TOP, 0, getHeight(), BG_BOTTOM));
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.dispose();
        }
    }

    /** Transparent panel that paints a rounded filled rectangle. */
    static class RoundPanel extends JPanel {
        private final Color fill;
        private final int arc;

        RoundPanel(Color fill, int arc) {
            this.fill = fill;
            this.arc = arc;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** Rounded button with hover and press effects. */
    static class RoundButton extends JButton {
        private final Color base;
        private final Color hoverColor;
        private boolean hover = false;
        private boolean press = false;

        RoundButton(String text, Color base) {
            this(text, base, base.brighter());
        }

        RoundButton(String text, Color base, Color hoverColor) {
            super(text);
            this.base = base;
            this.hoverColor = hoverColor;
            setForeground(Color.WHITE);
            setFont(new Font("Arial", Font.BOLD, 15));
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true;  repaint(); }
                @Override public void mouseExited(MouseEvent e)  { hover = false; repaint(); }
                @Override public void mousePressed(MouseEvent e) { press = true;  repaint(); }
                @Override public void mouseReleased(MouseEvent e){ press = false; repaint(); }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                RenderingHints.VALUE_ANTIALIAS_ON);
            Color c;
            if (!isEnabled())   c = new Color(64, 66, 86);
            else if (press)     c = base.darker();
            else if (hover)     c = hoverColor;
            else                c = base;
            g2.setColor(c);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 22, 22);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** Big round pulsing SOS button. */
    static class SosButton extends JComponent {
        private float phase = 0f;
        private boolean pressed = false;
        private boolean hover = false;
        private final Runnable action;

        SosButton(Runnable action) {
            this.action = action;
            Dimension d = new Dimension(250, 250);
            setPreferredSize(d);
            setMaximumSize(d);
            setMinimumSize(d);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            new javax.swing.Timer(40, e -> {
                phase += 0.018f;
                if (phase > 1f) phase = 0f;
                repaint();
            }).start();

            addMouseListener(new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) { pressed = true; repaint(); }
                @Override public void mouseReleased(MouseEvent e) {
                    boolean fire = pressed && contains(e.getPoint());
                    pressed = false;
                    repaint();
                    if (fire) SosButton.this.action.run();
                }
                @Override public void mouseEntered(MouseEvent e) { hover = true; }
                @Override public void mouseExited(MouseEvent e)  { hover = false; }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int cx = getWidth() / 2;
            int cy = getHeight() / 2;
            int base = hover ? 168 : 160;

            // Expanding pulse rings
            for (int i = 0; i < 2; i++) {
                float p = (phase + i * 0.5f) % 1f;
                int d = (int) (base + p * 80);
                int alpha = (int) (120 * (1f - p));
                g2.setColor(new Color(255, 64, 96, Math.max(0, alpha)));
                g2.setStroke(new BasicStroke(3f));
                g2.drawOval(cx - d / 2, cy - d / 2, d, d);
            }

            // Soft glow
            g2.setColor(new Color(255, 64, 96, 45));
            g2.fillOval(cx - base / 2 - 10, cy - base / 2 - 10, base + 20, base + 20);

            // Main circle
            int d = pressed ? base - 8 : base;
            g2.setPaint(new GradientPaint(cx, cy - d / 2f, new Color(255, 95, 120),
                                          cx, cy + d / 2f, new Color(205, 25, 55)));
            g2.fillOval(cx - d / 2, cy - d / 2, d, d);

            g2.setColor(new Color(255, 255, 255, 90));
            g2.setStroke(new BasicStroke(3f));
            g2.drawOval(cx - d / 2, cy - d / 2, d, d);

            // Text
            g2.setColor(Color.WHITE);
            g2.setFont(new Font("Arial", Font.BOLD, 50));
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString("SOS", cx - fm.stringWidth("SOS") / 2, cy + 8);

            g2.setFont(new Font("Arial", Font.BOLD, 12));
            fm = g2.getFontMetrics();
            String sub = "TAP FOR HELP";
            g2.drawString(sub, cx - fm.stringWidth(sub) / 2, cy + 34);

            g2.dispose();
        }
    }

    /** Circle avatar with the first letter of the caller's name. */
    static class Avatar extends JComponent {
        private final String letter;

        Avatar(String name) {
            letter = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase();
            Dimension d = new Dimension(150, 150);
            setPreferredSize(d);
            setMaximumSize(d);
            setMinimumSize(d);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setPaint(new GradientPaint(0, 0, VIOLET, 0, getHeight(), new Color(80, 50, 200)));
            g2.fillOval(5, 5, getWidth() - 10, getHeight() - 10);
            g2.setColor(Color.WHITE);
            g2.setFont(new Font("Arial", Font.BOLD, 64));
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(letter,
                getWidth() / 2 - fm.stringWidth(letter) / 2,
                getHeight() / 2 + fm.getAscent() / 2 - 8);
            g2.dispose();
        }
    }

    /** Panel whose colour and text can change (used by the siren window). */
    static class FlashPanel extends JPanel {
        private Color fill = CARD;
        private String text = "SIREN OFF";

        FlashPanel() {
            setOpaque(false);
        }

        void set(Color c, String t) {
            fill = c;
            text = t;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 30, 30);
            g2.setColor(Color.WHITE);
            g2.setFont(new Font("Arial", Font.BOLD, 34));
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(text,
                getWidth() / 2 - fm.stringWidth(text) / 2,
                getHeight() / 2 + fm.getAscent() / 3);
            g2.dispose();
        }
    }

    /** Generates and plays a wailing siren sound on a background thread. */
    static class Siren {
        private volatile boolean running = false;

        boolean isRunning() {
            return running;
        }

        void start() {
            if (running) return;
            running = true;

            Thread t = new Thread(() -> {
                try {
                    AudioFormat fmt = new AudioFormat(8000f, 8, 1, true, false);
                    SourceDataLine line = AudioSystem.getSourceDataLine(fmt);
                    line.open(fmt, 4000);
                    line.start();

                    byte[] buf = new byte[800];
                    double phase = 0;
                    double time = 0;

                    while (running) {
                        for (int i = 0; i < buf.length; i++) {
                            double freq = 900 + 400 * Math.sin(2 * Math.PI * 0.8 * time);
                            phase += 2 * Math.PI * freq / 8000.0;
                            buf[i] = (byte) (Math.sin(phase) * 110);
                            time += 1.0 / 8000.0;
                        }
                        line.write(buf, 0, buf.length);
                    }

                    line.stop();
                    line.close();

                } catch (Exception ex) {
                    Toolkit.getDefaultToolkit().beep();
                } finally {
                    running = false;
                }
            });
            t.setDaemon(true);
            t.start();
        }

        void stop() {
            running = false;
        }
    }

    // =====================================================
    //  UI HELPERS
    // =====================================================
    private JFrame createWindow(String title, int w, int h) {
        JFrame f = new JFrame(title);
        f.setSize(w, h);
        f.setLocationRelativeTo(this);
        GradientPanel root = new GradientPanel();
        root.setLayout(new BorderLayout(0, 14));
        root.setBorder(new EmptyBorder(18, 22, 18, 22));
        f.setContentPane(root);
        return f;
    }

    private JPanel windowHeader(String title, String subtitle, Color titleColor) {
        JPanel box = new JPanel();
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setOpaque(false);

        JLabel t = new JLabel(title);
        t.setFont(new Font("Arial", Font.BOLD, 24));
        t.setForeground(titleColor);
        t.setAlignmentX(Component.LEFT_ALIGNMENT);
        box.add(t);

        JLabel s = new JLabel(subtitle);
        s.setFont(new Font("Arial", Font.PLAIN, 13));
        s.setForeground(MUTED);
        s.setAlignmentX(Component.LEFT_ALIGNMENT);
        box.add(Box.createVerticalStrut(4));
        box.add(s);
        return box;
    }

    private void styleField(JTextField t) {
        t.setBackground(FIELD);
        t.setForeground(Color.WHITE);
        t.setCaretColor(Color.WHITE);
        t.setFont(new Font("Arial", Font.PLAIN, 15));
        t.setBorder(new CompoundBorder(
            new LineBorder(new Color(70, 75, 110), 1, true),
            new EmptyBorder(8, 10, 8, 10)));
    }

    private void styleCombo(JComboBox<String> c) {
        c.setBackground(FIELD);
        c.setForeground(Color.WHITE);
        c.setFont(new Font("Arial", Font.BOLD, 14));
    }

    private JLabel smallLabel(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(MUTED);
        l.setFont(new Font("Arial", Font.BOLD, 12));
        return l;
    }

    private JList<String> styledList(DefaultListModel<String> model) {
        JList<String> list = new JList<>(model);
        list.setBackground(CARD);
        list.setForeground(Color.WHITE);
        list.setFont(new Font("Arial", Font.PLAIN, 14));
        list.setSelectionBackground(VIOLET);
        list.setSelectionForeground(Color.WHITE);
        list.setFixedCellHeight(36);
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object value,
                    int index, boolean isSelected, boolean cellHasFocus) {
                JLabel lab = (JLabel) super.getListCellRendererComponent(
                    l, value, index, isSelected, false);
                lab.setBorder(new EmptyBorder(4, 14, 4, 14));
                if (!isSelected) {
                    lab.setBackground(index % 2 == 0 ? CARD : CARD_ALT);
                    String s = String.valueOf(value);
                    if (s.contains("SOS"))                 lab.setForeground(new Color(255, 130, 155));
                    else if (s.contains("Completed"))      lab.setForeground(new Color(120, 230, 160));
                    else if (s.contains("Deadline") || s.contains("Cancelled"))
                                                           lab.setForeground(YELLOW);
                }
                return lab;
            }
        });
        return list;
    }

    private JScrollPane styledScroll(Component c) {
        JScrollPane sp = new JScrollPane(c);
        sp.setBorder(null);
        sp.getViewport().setBackground(CARD);
        return sp;
    }

    private RoundPanel chip(String text, Color color) {
        RoundPanel p = new RoundPanel(new Color(
            color.getRed(), color.getGreen(), color.getBlue(), 45), 24);
        p.setLayout(new FlowLayout(FlowLayout.CENTER, 12, 5));
        JLabel l = new JLabel(text);
        l.setFont(new Font("Arial", Font.BOLD, 12));
        l.setForeground(color);
        p.add(l);
        return p;
    }

    private RoundPanel statCard(String caption, JLabel valueLabel, Color color) {
        RoundPanel p = new RoundPanel(new Color(255, 255, 255, 20), 22);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(new EmptyBorder(10, 16, 10, 16));

        valueLabel.setFont(new Font("Arial", Font.BOLD, 26));
        valueLabel.setForeground(color);
        valueLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel cap = new JLabel(caption);
        cap.setFont(new Font("Arial", Font.BOLD, 11));
        cap.setForeground(MUTED);
        cap.setAlignmentX(Component.LEFT_ALIGNMENT);

        p.add(valueLabel);
        p.add(cap);
        return p;
    }

    private RoundButton featureCard(String title, String sub, String hex, Runnable action) {
        RoundButton b = new RoundButton(
            "<html><center><span style='font-size:13px;color:" + hex + "'><b>"
            + title + "</b></span><br><span style='font-size:10px;color:#A0A5BE'>"
            + sub + "</span></center></html>",
            CARD, CARD_HOVER);
        b.addActionListener(e -> action.run());
        return b;
    }

    private void openUrl(String url) {
        try {
            if (Desktop.isDesktopSupported()
                    && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
            } else {
                JOptionPane.showMessageDialog(this, "Cannot open browser.\nLink: " + url);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Unable to open link.\n" + url);
        }
    }

    // =====================================================
    //  SPLASH SCREEN
    // =====================================================
    static void showSplash(Runnable onDone) {

        JWindow w = new JWindow();
        w.setSize(520, 320);
        w.setLocationRelativeTo(null);

        GradientPanel p = new GradientPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(new CompoundBorder(
            new LineBorder(VIOLET, 2), new EmptyBorder(40, 40, 30, 40)));

        JLabel logo = new JLabel(
            "<html><span style='color:white'>SAFE</span>"
            + "<span style='color:#FF4060'>PULSE</span></html>");
        logo.setFont(new Font("Arial", Font.BOLD, 52));
        logo.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel tag = new JLabel("SMART WOMEN SAFETY SYSTEM");
        tag.setFont(new Font("Arial", Font.BOLD, 13));
        tag.setForeground(MUTED);
        tag.setAlignmentX(Component.CENTER_ALIGNMENT);

        JProgressBar bar = new JProgressBar(0, 100);
        bar.setForeground(ACCENT);
        bar.setBackground(FIELD);
        bar.setBorderPainted(false);
        bar.setMaximumSize(new Dimension(380, 10));

        JLabel msg = new JLabel("Starting...");
        msg.setFont(new Font("Arial", Font.PLAIN, 13));
        msg.setForeground(MUTED);
        msg.setAlignmentX(Component.CENTER_ALIGNMENT);

        p.add(Box.createVerticalGlue());
        p.add(logo);
        p.add(Box.createVerticalStrut(6));
        p.add(tag);
        p.add(Box.createVerticalStrut(40));
        p.add(bar);
        p.add(Box.createVerticalStrut(12));
        p.add(msg);
        p.add(Box.createVerticalGlue());

        w.setContentPane(p);
        w.setVisible(true);

        javax.swing.Timer t = new javax.swing.Timer(30, null);
        t.addActionListener(e -> {
            int v = bar.getValue() + 2;
            bar.setValue(v);

            if (v < 30)       msg.setText("Loading contacts...");
            else if (v < 60)  msg.setText("Starting location services...");
            else if (v < 90)  msg.setText("Preparing SOS module...");
            else              msg.setText("Ready!");

            if (v >= 100) {
                t.stop();
                w.dispose();
                onDone.run();
            }
        });
        t.start();
    }

    // =====================================================
    //  MAIN WINDOW
    // =====================================================
    SafePulse() {

        setTitle("SAFEPULSE - Women Safety System");
        setSize(1060, 760);
        setMinimumSize(new Dimension(1000, 700));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        loadContacts();
        loadProfile();

        GradientPanel root = new GradientPanel();
        root.setLayout(new BorderLayout(0, 8));
        root.setBorder(new EmptyBorder(22, 32, 16, 32));

        // ----- Header -----
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JPanel left = new JPanel();
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.setOpaque(false);

        JLabel logo = new JLabel(
            "<html><span style='color:white'>SAFE</span>"
            + "<span style='color:#FF4060'>PULSE</span></html>");
        logo.setFont(new Font("Arial", Font.BOLD, 36));
        logo.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel subtitle = new JLabel("SMART WOMEN SAFETY SYSTEM");
        subtitle.setFont(new Font("Arial", Font.BOLD, 12));
        subtitle.setForeground(MUTED);
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        left.add(logo);
        left.add(Box.createVerticalStrut(2));
        left.add(subtitle);
        header.add(left, BorderLayout.WEST);

        RoundPanel pill = new RoundPanel(new Color(255, 255, 255, 22), 30);
        pill.setLayout(new FlowLayout(FlowLayout.CENTER, 16, 8));
        locationStatus = new JLabel("Location: Detecting...");
        locationStatus.setFont(new Font("Arial", Font.BOLD, 13));
        locationStatus.setForeground(YELLOW);
        pill.add(locationStatus);

        JPanel pillWrap = new JPanel(new GridBagLayout());
        pillWrap.setOpaque(false);
        pillWrap.add(pill);
        header.add(pillWrap, BorderLayout.EAST);

        // ----- Dashboard stats -----
        statContacts = new JLabel("0");
        statSos      = new JLabel("0");
        statCheckins = new JLabel("0");
        statReady    = new JLabel("0%");

        JPanel stats = new JPanel(new GridLayout(1, 4, 12, 0));
        stats.setOpaque(false);
        stats.setPreferredSize(new Dimension(0, 70));
        stats.add(statCard("EMERGENCY CONTACTS", statContacts, Color.WHITE));
        stats.add(statCard("SOS ALERTS SENT", statSos, new Color(255, 130, 155)));
        stats.add(statCard("CHECK-INS COMPLETED", statCheckins, GREEN));
        stats.add(statCard("SAFETY READINESS", statReady, YELLOW));

        JPanel top = new JPanel(new BorderLayout(0, 14));
        top.setOpaque(false);
        top.add(header, BorderLayout.NORTH);
        top.add(stats, BorderLayout.CENTER);
        root.add(top, BorderLayout.NORTH);

        // ----- Center : SOS -----
        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.setOpaque(false);

        SosButton sos = new SosButton(this::runTestSmsMode);
        sos.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel caption = new JLabel(
            "Tap SOS to alert your emergency contacts   |   Shortcut: F1");
        caption.setFont(new Font("Arial", Font.PLAIN, 14));
        caption.setForeground(MUTED);
        caption.setAlignmentX(Component.CENTER_ALIGNMENT);

        center.add(Box.createVerticalGlue());
        center.add(sos);
        center.add(caption);
        center.add(Box.createVerticalGlue());
        root.add(center, BorderLayout.CENTER);

        // ----- Bottom : feature cards (2 x 5) -----
        JPanel south = new JPanel(new BorderLayout(0, 10));
        south.setOpaque(false);

        JPanel features = new JPanel(new GridLayout(2, 5, 12, 12));
        features.setOpaque(false);
        features.setPreferredSize(new Dimension(0, 150));

        features.add(featureCard("Emergency Contacts", "Manage trusted people", "#FF6B8A", this::contactWindow));
        features.add(featureCard("Safety Check-in",    "Safe journey timer",    "#9C86FF", this::safetyTimer));
        features.add(featureCard("Current Location",   "Find where you are",    "#2ECC71", () -> getCurrentLocation(false)));
        features.add(featureCard("Nearby Help",        "Police, hospitals",     "#4FC3F7", this::showNearbyHelp));
        features.add(featureCard("Fake Call",          "Escape a situation",    "#FFC83C", this::fakeCallSetup));
        features.add(featureCard("Siren Alarm",        "Loud alarm + flash",    "#FF5252", this::showSirenWindow));
        features.add(featureCard("Medical Profile",    "Blood group, allergies","#F48FB1", this::showProfileWindow));
        features.add(featureCard("Alert History",      "Past alerts and logs",  "#80CBC4", this::showHistory));
        features.add(featureCard("Safety Tips",        "Stay safe every day",   "#AED581", this::showSafetyTips));
        features.add(featureCard("Helplines",          "Important numbers",     "#FF8A65", this::showHelplines));

        String tip = TIPS[(int) (Math.random() * TIPS.length)];
        JLabel footer = new JLabel(
            "TIP: " + tip + "     |     TEST MODE - no real SMS is sent",
            JLabel.CENTER);
        footer.setFont(new Font("Arial", Font.PLAIN, 12));
        footer.setForeground(MUTED);

        south.add(features, BorderLayout.CENTER);
        south.add(footer, BorderLayout.SOUTH);
        root.add(south, BorderLayout.SOUTH);

        setContentPane(root);

        // F1 = SOS shortcut
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
            .put(KeyStroke.getKeyStroke("F1"), "sos");
        getRootPane().getActionMap().put("sos", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                runTestSmsMode();
            }
        });

        refreshStats();
        setVisible(true);

        // Fetch location in the background so SOS messages include it
        getCurrentLocation(true);
    }

    // =====================================================
    //  DASHBOARD STATS
    // =====================================================
    private int countHistory(String keyword) {

        File file = new File("history.txt");
        int count = 0;

        if (!file.exists()) return 0;

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains(keyword)) count++;
            }
        } catch (IOException e) {
            // ignore - stats are optional
        }
        return count;
    }

    private boolean isProfileComplete() {
        return !profile.getProperty("name", "").trim().isEmpty()
            && !profile.getProperty("blood", "").trim().isEmpty();
    }

    private int readinessScore() {
        int score = 0;
        if (contactsCount() > 0)        score += 40;
        if (isProfileComplete())        score += 30;
        if (currentLatitude != null)    score += 30;
        return score;
    }

    public void refreshStats() {

        if (statContacts == null) return;

        statContacts.setText(String.valueOf(contactsCount()));
        statSos.setText(String.valueOf(countHistory("SOS Triggered (Test SMS Mode)")));
        statCheckins.setText(String.valueOf(countHistory("Safety Check-in Completed")));

        int score = readinessScore();
        statReady.setText(score + "%");
        statReady.setForeground(score >= 100 ? GREEN : (score >= 60 ? YELLOW : ACCENT));
    }

    // =====================================================
    //  SAFETY TIPS & HELPLINES
    // =====================================================
    void showSafetyTips() {

        JFrame f = createWindow("Personal Safety Tips", 560, 560);
        f.add(windowHeader("Safety Tips", "Small habits that keep you safe", Color.WHITE),
              BorderLayout.NORTH);

        DefaultListModel<String> model = new DefaultListModel<>();
        model.addElement("1.  Always save emergency contact numbers.");
        model.addElement("2.  Share your travel plans with trusted people.");
        model.addElement("3.  Stay alert and aware of your surroundings.");
        model.addElement("4.  Avoid isolated places whenever possible.");
        model.addElement("5.  Keep your phone charged while travelling.");
        model.addElement("6.  Use trusted transportation services.");
        model.addElement("7.  Note cab number and share it before the ride.");
        model.addElement("8.  In an emergency, call 112 (India).");

        JList<String> list = styledList(model);
        list.setFixedCellHeight(44);
        f.add(styledScroll(list), BorderLayout.CENTER);

        RoundButton close = new RoundButton("CLOSE", VIOLET);
        close.setPreferredSize(new Dimension(0, 42));
        close.addActionListener(e -> f.dispose());
        f.add(close, BorderLayout.SOUTH);

        f.setVisible(true);
    }

    void showHelplines() {

        JFrame f = createWindow("Emergency Helplines", 520, 470);
        f.add(windowHeader("Emergency Helplines", "India - save these numbers", ACCENT),
              BorderLayout.NORTH);

        DefaultListModel<String> model = new DefaultListModel<>();
        model.addElement("112      National Emergency Number");
        model.addElement("100      Police");
        model.addElement("1091     Women Helpline");
        model.addElement("181      Women Helpline (Domestic Abuse)");
        model.addElement("102/108  Ambulance");
        model.addElement("1930     Cyber Crime Helpline");

        JList<String> list = styledList(model);
        list.setFont(new Font("Monospaced", Font.BOLD, 14));
        list.setFixedCellHeight(46);
        f.add(styledScroll(list), BorderLayout.CENTER);

        RoundButton close = new RoundButton("CLOSE", VIOLET);
        close.setPreferredSize(new Dimension(0, 42));
        close.addActionListener(e -> f.dispose());
        f.add(close, BorderLayout.SOUTH);

        f.setVisible(true);
    }

    // =====================================================
    //  NEARBY HELP  (opens Google Maps search)
    // =====================================================
    void showNearbyHelp() {

        JFrame f = createWindow("Nearby Help", 480, 540);
        f.add(windowHeader("Nearby Help",
              "Find the closest help on Google Maps", Color.WHITE),
              BorderLayout.NORTH);

        RoundPanel card = new RoundPanel(new Color(255, 255, 255, 18), 26);
        card.setLayout(new GridLayout(0, 1, 0, 12));
        card.setBorder(new EmptyBorder(18, 18, 18, 18));

        String locText = (currentLatitude != null)
            ? "Using your detected location"
            : "Location not detected - using 'near me'";
        JLabel info = new JLabel(locText, JLabel.CENTER);
        info.setForeground(currentLatitude != null ? GREEN : YELLOW);
        info.setFont(new Font("Arial", Font.BOLD, 13));
        card.add(info);

        String[] labels = {"Police Stations", "Hospitals", "Pharmacies", "Fire Stations"};
        String[] queries = {"police station", "hospital", "pharmacy", "fire station"};
        Color[] colors = {VIOLET, ACCENT.darker(), GREEN.darker(), new Color(230, 126, 34)};

        for (int i = 0; i < labels.length; i++) {
            final String query = queries[i];
            RoundButton b = new RoundButton(labels[i].toUpperCase() + " NEAR ME", colors[i]);
            b.addActionListener(e -> openNearby(query));
            card.add(b);
        }

        f.add(card, BorderLayout.CENTER);

        RoundButton close = new RoundButton("CLOSE", CARD_HOVER);
        close.setPreferredSize(new Dimension(0, 42));
        close.addActionListener(e -> f.dispose());
        f.add(close, BorderLayout.SOUTH);

        f.setVisible(true);
    }

    private void openNearby(String query) {

        String q = query.replace(" ", "+");
        String url;

        if (currentLatitude != null && currentLongitude != null) {
            url = "https://www.google.com/maps/search/" + q
                + "/@" + currentLatitude + "," + currentLongitude + ",14z";
        } else {
            url = "https://www.google.com/maps/search/" + q + "+near+me";
        }

        addHistory("Nearby Help Searched - " + query);
        openUrl(url);
    }

    // =====================================================
    //  FAKE CALL
    // =====================================================
    void fakeCallSetup() {

        JFrame f = createWindow("Fake Call", 440, 470);
        f.add(windowHeader("Fake Call",
              "Get a realistic incoming call to leave safely", Color.WHITE),
              BorderLayout.NORTH);

        RoundPanel card = new RoundPanel(new Color(255, 255, 255, 18), 26);
        card.setLayout(new GridLayout(0, 1, 0, 8));
        card.setBorder(new EmptyBorder(18, 18, 18, 18));

        JTextField caller = new JTextField("Mom");
        styleField(caller);

        String[] delays = {"Immediately", "5 seconds", "10 seconds", "30 seconds"};
        int[] delaySecs = {0, 5, 10, 30};
        JComboBox<String> delayChoice = new JComboBox<>(delays);
        styleCombo(delayChoice);

        card.add(smallLabel("CALLER NAME"));
        card.add(caller);
        card.add(smallLabel("RING AFTER"));
        card.add(delayChoice);
        f.add(card, BorderLayout.CENTER);

        RoundButton start = new RoundButton("START FAKE CALL", VIOLET);
        start.setPreferredSize(new Dimension(0, 46));
        start.addActionListener(e -> {

            String name = caller.getText().trim();

            if (name.isEmpty()) {
                JOptionPane.showMessageDialog(f, "Enter a caller name.");
                return;
            }

            int wait = delaySecs[delayChoice.getSelectedIndex()];
            addHistory("Fake Call Scheduled - " + name + " (" + wait + "s)");
            f.dispose();

            javax.swing.Timer delay = new javax.swing.Timer(wait * 1000,
                ev -> showIncomingCall(name));
            delay.setRepeats(false);
            delay.start();
        });
        f.add(start, BorderLayout.SOUTH);

        f.setVisible(true);
    }

    void showIncomingCall(String caller) {

        JFrame f = new JFrame("Incoming Call");
        f.setSize(380, 640);
        f.setLocationRelativeTo(this);
        f.setAlwaysOnTop(true);
        f.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        GradientPanel p = new GradientPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(new EmptyBorder(50, 30, 40, 30));
        f.setContentPane(p);

        JLabel status = new JLabel("Incoming call...");
        status.setFont(new Font("Arial", Font.BOLD, 16));
        status.setForeground(MUTED);
        status.setAlignmentX(Component.CENTER_ALIGNMENT);

        Avatar avatar = new Avatar(caller);
        avatar.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel nameLabel = new JLabel(caller);
        nameLabel.setFont(new Font("Arial", Font.BOLD, 32));
        nameLabel.setForeground(Color.WHITE);
        nameLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel sub = new JLabel("Mobile");
        sub.setFont(new Font("Arial", Font.PLAIN, 15));
        sub.setForeground(MUTED);
        sub.setAlignmentX(Component.CENTER_ALIGNMENT);

        RoundButton decline = new RoundButton("DECLINE", ACCENT.darker());
        RoundButton accept = new RoundButton("ACCEPT", GREEN.darker());
        decline.setPreferredSize(new Dimension(130, 56));
        accept.setPreferredSize(new Dimension(130, 56));

        JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 0));
        row.setOpaque(false);
        row.add(decline);
        row.add(accept);
        row.setAlignmentX(Component.CENTER_ALIGNMENT);
        row.setMaximumSize(new Dimension(400, 70));

        p.add(status);
        p.add(Box.createVerticalStrut(30));
        p.add(avatar);
        p.add(Box.createVerticalStrut(24));
        p.add(nameLabel);
        p.add(Box.createVerticalStrut(6));
        p.add(sub);
        p.add(Box.createVerticalGlue());
        p.add(row);

        final int[] secs = {0};
        final boolean[] answered = {false};

        javax.swing.Timer ring = new javax.swing.Timer(1200,
            e -> Toolkit.getDefaultToolkit().beep());
        ring.setInitialDelay(0);

        javax.swing.Timer clock = new javax.swing.Timer(1000, e -> {
            secs[0]++;
            status.setText(String.format("%02d:%02d", secs[0] / 60, secs[0] % 60));
        });

        accept.addActionListener(e -> {
            answered[0] = true;
            ring.stop();
            status.setText("00:00");
            status.setForeground(GREEN);
            sub.setText("Call in progress");
            accept.setVisible(false);
            decline.setText("END CALL");
            clock.start();
        });

        decline.addActionListener(e -> f.dispose());

        f.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                ring.stop();
                clock.stop();
                addHistory(answered[0]
                    ? "Fake Call Answered - " + caller + " (" + secs[0] + "s)"
                    : "Fake Call Declined - " + caller);
            }
        });

        f.setVisible(true);
        ring.start();
    }

    // =====================================================
    //  SIREN ALARM
    // =====================================================
    void showSirenWindow() {

        JFrame f = createWindow("Siren Alarm", 460, 520);
        f.add(windowHeader("Siren Alarm",
              "Loud alarm to attract attention nearby", ACCENT),
              BorderLayout.NORTH);

        FlashPanel panel = new FlashPanel();
        f.add(panel, BorderLayout.CENTER);

        RoundButton toggle = new RoundButton("START SIREN", ACCENT.darker());
        toggle.setPreferredSize(new Dimension(0, 52));
        f.add(toggle, BorderLayout.SOUTH);

        final boolean[] flip = {false};

        javax.swing.Timer flash = new javax.swing.Timer(400, e -> {
            flip[0] = !flip[0];
            panel.set(flip[0] ? new Color(210, 30, 60) : new Color(70, 10, 28),
                      "SIREN ACTIVE");
        });

        toggle.addActionListener(e -> {
            if (!siren.isRunning()) {
                siren.start();
                flash.start();
                toggle.setText("STOP SIREN");
                addHistory("Siren Alarm Activated");
            } else {
                siren.stop();
                flash.stop();
                panel.set(CARD, "SIREN OFF");
                toggle.setText("START SIREN");
                addHistory("Siren Alarm Stopped");
            }
        });

        // Always stop the siren when this window is closed
        f.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                siren.stop();
                flash.stop();
            }
        });

        f.setVisible(true);
    }

    // =====================================================
    //  MEDICAL PROFILE
    // =====================================================
    void loadProfile() {

        File file = new File("profile.txt");

        if (!file.exists()) return;

        try (FileReader reader = new FileReader(file)) {
            profile.load(reader);
        } catch (IOException e) {
            System.out.println("Unable to load profile.");
        }
    }

    void saveProfile() {

        try (FileWriter writer = new FileWriter("profile.txt")) {
            profile.store(writer, "SafePulse Medical Profile");
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Unable to save profile.");
        }
    }

    void showProfileWindow() {

        JFrame f = createWindow("Medical Profile", 500, 680);
        f.add(windowHeader("Medical Profile",
              "Shared with your contacts inside the SOS message", Color.WHITE),
              BorderLayout.NORTH);

        JTextField name = new JTextField(profile.getProperty("name", ""));
        JTextField age = new JTextField(profile.getProperty("age", ""));
        JTextField medical = new JTextField(profile.getProperty("medical", ""));
        JTextField address = new JTextField(profile.getProperty("address", ""));
        styleField(name);
        styleField(age);
        styleField(medical);
        styleField(address);

        String[] groups = {"Unknown", "A+", "A-", "B+", "B-", "O+", "O-", "AB+", "AB-"};
        JComboBox<String> blood = new JComboBox<>(groups);
        styleCombo(blood);
        blood.setSelectedItem(profile.getProperty("blood", "Unknown"));

        RoundPanel card = new RoundPanel(new Color(255, 255, 255, 18), 26);
        card.setLayout(new GridLayout(0, 1, 0, 6));
        card.setBorder(new EmptyBorder(16, 18, 16, 18));
        card.add(smallLabel("FULL NAME"));
        card.add(name);
        card.add(smallLabel("AGE"));
        card.add(age);
        card.add(smallLabel("BLOOD GROUP"));
        card.add(blood);
        card.add(smallLabel("ALLERGIES / MEDICAL CONDITIONS"));
        card.add(medical);
        card.add(smallLabel("HOME ADDRESS"));
        card.add(address);
        f.add(card, BorderLayout.CENTER);

        RoundButton save = new RoundButton("SAVE PROFILE", GREEN.darker());
        save.setPreferredSize(new Dimension(0, 46));
        save.addActionListener(e -> {

            String n = name.getText().trim();
            String a = age.getText().trim();

            if (n.isEmpty()) {
                JOptionPane.showMessageDialog(f, "Please enter your name.");
                return;
            }

            if (!a.isEmpty() && !a.matches("[0-9]{1,3}")) {
                JOptionPane.showMessageDialog(f, "Enter a valid age.");
                return;
            }

            profile.setProperty("name", n);
            profile.setProperty("age", a);
            profile.setProperty("blood", String.valueOf(blood.getSelectedItem()));
            profile.setProperty("medical", medical.getText().trim());
            profile.setProperty("address", address.getText().trim());

            saveProfile();
            addHistory("Medical Profile Updated");
            refreshStats();

            JOptionPane.showMessageDialog(f, "Profile saved successfully!");
            f.dispose();
        });
        f.add(save, BorderLayout.SOUTH);

        f.setVisible(true);
    }

    // =====================================================
    //  EMERGENCY CONTACTS
    // =====================================================
    void contactWindow() {

        JFrame frame = createWindow("Emergency Contacts", 480, 600);
        frame.add(windowHeader("Emergency Contacts",
                  "These people receive your SOS alert", Color.WHITE),
                  BorderLayout.NORTH);

        DefaultListModel<String> model = new DefaultListModel<>();
        for (String contact : contacts) {
            model.addElement(contact);
        }

        JList<String> list = styledList(model);
        frame.add(styledScroll(list), BorderLayout.CENTER);

        JTextField name = new JTextField();
        JTextField phone = new JTextField();
        styleField(name);
        styleField(phone);

        RoundButton add = new RoundButton("ADD CONTACT", GREEN.darker());
        RoundButton delete = new RoundButton("DELETE SELECTED", ACCENT.darker());

        JPanel buttons = new JPanel(new GridLayout(1, 2, 10, 0));
        buttons.setOpaque(false);
        buttons.add(add);
        buttons.add(delete);
        buttons.setPreferredSize(new Dimension(0, 42));

        RoundPanel form = new RoundPanel(new Color(255, 255, 255, 18), 22);
        form.setLayout(new GridLayout(0, 1, 0, 6));
        form.setBorder(new EmptyBorder(14, 14, 14, 14));
        form.add(smallLabel("NAME"));
        form.add(name);
        form.add(smallLabel("PHONE (10 DIGITS)"));
        form.add(phone);
        form.add(buttons);
        frame.add(form, BorderLayout.SOUTH);

        add.addActionListener(e -> {

            String n = name.getText().trim();
            String p = phone.getText().trim();

            if (n.isEmpty() || !p.matches("[0-9]{10}")) {
                JOptionPane.showMessageDialog(frame,
                    "Enter a name and valid 10-digit phone number.");
                return;
            }

            String contact = n + " - " + p;

            if (contacts.contains(contact)) {
                JOptionPane.showMessageDialog(frame, "This contact already exists.");
                return;
            }

            contacts.add(contact);
            model.addElement(contact);
            saveContacts();
            refreshStats();

            name.setText("");
            phone.setText("");
        });

        delete.addActionListener(e -> {

            int index = list.getSelectedIndex();

            if (index >= 0) {
                contacts.remove(index);
                model.remove(index);
                saveContacts();
                refreshStats();
            } else {
                JOptionPane.showMessageDialog(frame,
                    "Select a contact to delete.");
            }
        });

        frame.setVisible(true);
    }

    void saveContacts() {

        try (PrintWriter writer =
             new PrintWriter(new FileWriter("contacts.txt"))) {

            for (String contact : contacts) {
                writer.println(contact);
            }

        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Unable to save contacts.");
        }
    }

    void loadContacts() {

        File file = new File("contacts.txt");

        if (!file.exists()) return;

        try (BufferedReader reader =
             new BufferedReader(new FileReader(file))) {

            String line;

            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    contacts.add(line);
                }
            }

        } catch (IOException e) {
            System.out.println("Unable to load contacts.");
        }
    }

    public ArrayList<String> getEmergencyPhoneNumbers() {

        ArrayList<String> numbers = new ArrayList<>();

        for (String contact : contacts) {

            int separator = contact.lastIndexOf(" - ");

            if (separator != -1) {

                String phone = contact.substring(separator + 3).trim();

                if (phone.matches("[0-9]{10}")) {
                    numbers.add("+91" + phone);
                }
            }
        }

        return numbers;
    }

    public int contactsCount() {
        return getEmergencyPhoneNumbers().size();
    }

    // =====================================================
    //  SAFE JOURNEY CHECK-IN TIMER
    //  Phase 0 : journey timer counting down
    //  Phase 1 : 10 second grace period to press "I AM SAFE"
    //            -> if not pressed, SOS is triggered automatically
    // =====================================================
    public void safetyTimer() {

        final int GRACE_SECONDS = 10;

        JFrame frame = createWindow("Safe Journey Mode", 440, 520);
        frame.add(windowHeader("Safe Journey Check-in",
                  "Auto-SOS if you do not confirm in time", Color.WHITE),
                  BorderLayout.NORTH);

        RoundPanel card = new RoundPanel(new Color(255, 255, 255, 18), 26);
        card.setLayout(new GridLayout(0, 1, 0, 12));
        card.setBorder(new EmptyBorder(20, 20, 20, 20));

        JLabel countdown = new JLabel("--:--", JLabel.CENTER);
        countdown.setFont(new Font("Arial", Font.BOLD, 56));
        countdown.setForeground(Color.WHITE);

        JLabel status = new JLabel("Select your timer and start", JLabel.CENTER);
        status.setFont(new Font("Arial", Font.BOLD, 14));
        status.setForeground(MUTED);

        JProgressBar progress = new JProgressBar(0, 100);
        progress.setValue(100);
        progress.setForeground(VIOLET);
        progress.setBackground(FIELD);
        progress.setBorderPainted(false);

        String[] options = {"1 Minute", "2 Minutes", "5 Minutes"};
        int[] durations = {60, 120, 300};

        JComboBox<String> timerChoice = new JComboBox<>(options);
        styleCombo(timerChoice);

        card.add(countdown);
        card.add(status);
        card.add(progress);
        card.add(timerChoice);
        frame.add(card, BorderLayout.CENTER);

        RoundButton start = new RoundButton("START JOURNEY", VIOLET);
        RoundButton safe = new RoundButton("I AM SAFE", GREEN.darker());
        safe.setEnabled(false);

        JPanel buttons = new JPanel(new GridLayout(1, 2, 12, 0));
        buttons.setOpaque(false);
        buttons.setPreferredSize(new Dimension(0, 48));
        buttons.add(start);
        buttons.add(safe);
        frame.add(buttons, BorderLayout.SOUTH);

        final javax.swing.Timer[] timer = new javax.swing.Timer[1];
        final int[] seconds = {0};
        final int[] phase = {0};
        final int[] total = {0};

        start.addActionListener(e -> {

            if (timer[0] != null && timer[0].isRunning()) {
                return;
            }

            total[0] = durations[timerChoice.getSelectedIndex()];
            seconds[0] = total[0];
            phase[0] = 0;

            start.setEnabled(false);
            safe.setEnabled(true);   // user may confirm safe any time
            timerChoice.setEnabled(false);

            progress.setMaximum(total[0]);
            progress.setValue(total[0]);
            progress.setForeground(VIOLET);
            countdown.setForeground(Color.WHITE);
            status.setForeground(GREEN);
            status.setText("Journey in progress...");

            addHistory("Safe Journey Started - " + timerChoice.getSelectedItem());

            countdown.setText(String.format("%02d:%02d",
                seconds[0] / 60, seconds[0] % 60));

            timer[0] = new javax.swing.Timer(1000, event -> {

                seconds[0]--;
                progress.setValue(Math.max(0, seconds[0]));
                countdown.setText(String.format("%02d:%02d",
                    Math.max(0, seconds[0]) / 60, Math.max(0, seconds[0]) % 60));

                if (phase[0] == 0) {

                    if (seconds[0] <= 0) {
                        phase[0] = 1;
                        seconds[0] = GRACE_SECONDS;
                        progress.setMaximum(GRACE_SECONDS);
                        progress.setValue(GRACE_SECONDS);
                        progress.setForeground(ACCENT);
                        countdown.setForeground(ACCENT);
                        status.setForeground(ACCENT);
                        status.setText("CONFIRM YOU ARE SAFE!");
                        addHistory("Safety Check-in Deadline Reached");
                        Toolkit.getDefaultToolkit().beep();
                    }

                } else {

                    if (seconds[0] <= 0) {
                        timer[0].stop();
                        countdown.setText("SOS!");
                        status.setText("No response - SOS triggered!");
                        safe.setEnabled(false);
                        start.setEnabled(true);
                        timerChoice.setEnabled(true);
                        addHistory("No Check-in Response - SOS Triggered");
                        runTestSmsMode();
                    }
                }
            });

            timer[0].start();
        });

        safe.addActionListener(e -> {

            if (timer[0] != null && timer[0].isRunning()) {
                timer[0].stop();
            }

            addHistory("Safety Check-in Completed");
            refreshStats();

            countdown.setText("SAFE");
            countdown.setForeground(GREEN);
            status.setForeground(GREEN);
            status.setText("You confirmed that you are safe!");
            progress.setValue(progress.getMaximum());
            progress.setForeground(GREEN);

            safe.setEnabled(false);
            start.setEnabled(true);
            timerChoice.setEnabled(true);
        });

        // Stop the timer if the window is closed
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (timer[0] != null && timer[0].isRunning()) {
                    timer[0].stop();
                    addHistory("Safe Journey Window Closed - Timer Cancelled");
                }
            }
        });

        frame.setVisible(true);
    }

    // =====================================================
    //  EMERGENCY MESSAGE + SOS (TEST SMS MODE)
    // =====================================================
    public String buildEmergencyMessage() {

        String time = LocalDateTime.now().format(TIME_FORMAT);

        String message =
            "EMERGENCY ALERT - SAFEPULSE\n\n" +
            "I need immediate assistance.\n" +
            "Please contact me immediately.\n\n" +
            "Time: " + time;

        if (currentLatitude != null && currentLongitude != null) {

            message +=
                "\n\nCURRENT LOCATION:\n" +
                "Latitude: " + currentLatitude + "\n" +
                "Longitude: " + currentLongitude + "\n" +
                "Google Maps: " + currentMapsLink;

        } else {

            message += "\n\nLocation: Not Available";
        }

        // Medical profile details (if saved)
        String pn = profile.getProperty("name", "").trim();
        String pb = profile.getProperty("blood", "").trim();
        String pm = profile.getProperty("medical", "").trim();
        String pa = profile.getProperty("address", "").trim();

        if (!pn.isEmpty()) {
            message += "\n\nPERSON DETAILS:\nName: " + pn;
            if (!pb.isEmpty() && !pb.equals("Unknown")) message += "\nBlood Group: " + pb;
            if (!pm.isEmpty()) message += "\nMedical Info: " + pm;
            if (!pa.isEmpty()) message += "\nHome Address: " + pa;
        }

        return message;
    }

    public void runTestSmsMode() {

        ArrayList<String> numbers = getEmergencyPhoneNumbers();

        if (numbers.isEmpty()) {

            JOptionPane.showMessageDialog(
                this,
                "No emergency contacts found!\nAdd contacts first.",
                "SAFEPULSE",
                JOptionPane.WARNING_MESSAGE
            );

            return;
        }

        String message = buildEmergencyMessage();

        addHistory(
            "SOS Triggered (Test SMS Mode) - " +
            numbers.size() + " contacts - Not Sent"
        );
        refreshStats();

        showSosResult(numbers, message);
    }

    /** One combined window: status chips + recipients + message + actions. */
    public void showSosResult(ArrayList<String> numbers, String message) {

        JFrame frame = createWindow("SAFEPULSE - SOS Alert", 580, 740);

        // Header + chips
        JPanel north = new JPanel();
        north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));
        north.setOpaque(false);

        JLabel title = new JLabel("SOS ALERT PREPARED");
        title.setFont(new Font("Arial", Font.BOLD, 26));
        title.setForeground(ACCENT);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel chips = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        chips.setOpaque(false);
        chips.setAlignmentX(Component.LEFT_ALIGNMENT);
        chips.add(chip("MODE: TEST SMS", YELLOW));
        chips.add(chip("STATUS: SIMULATED", GREEN));
        chips.add(chip("REAL SMS SENT: NO", ACCENT));

        north.add(title);
        north.add(Box.createVerticalStrut(10));
        north.add(chips);
        frame.add(north, BorderLayout.NORTH);

        // Recipients card
        DefaultListModel<String> model = new DefaultListModel<>();
        for (String number : numbers) {
            model.addElement(number + "   -   SIMULATED, NOT SENT");
        }
        JList<String> recipients = styledList(model);
        recipients.setSelectedIndex(0);

        RoundPanel recipientsCard = new RoundPanel(CARD, 22);
        recipientsCard.setLayout(new BorderLayout(0, 8));
        recipientsCard.setBorder(new EmptyBorder(12, 12, 12, 12));
        recipientsCard.add(smallLabel("RECIPIENTS (" + numbers.size() + ")  -  select one for WhatsApp"),
                           BorderLayout.NORTH);
        recipientsCard.add(styledScroll(recipients), BorderLayout.CENTER);

        // Message card
        JTextArea text = new JTextArea(message);
        text.setEditable(false);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setBackground(CARD);
        text.setForeground(Color.WHITE);
        text.setFont(new Font("Arial", Font.PLAIN, 13));
        text.setBorder(new EmptyBorder(8, 10, 8, 10));

        RoundPanel messageCard = new RoundPanel(CARD, 22);
        messageCard.setLayout(new BorderLayout(0, 8));
        messageCard.setBorder(new EmptyBorder(12, 12, 12, 12));
        messageCard.add(smallLabel("EMERGENCY MESSAGE"), BorderLayout.NORTH);
        messageCard.add(styledScroll(text), BorderLayout.CENTER);

        JPanel middle = new JPanel(new GridLayout(2, 1, 0, 12));
        middle.setOpaque(false);
        middle.add(recipientsCard);
        middle.add(messageCard);
        frame.add(middle, BorderLayout.CENTER);

        // Action buttons
        RoundButton copy = new RoundButton("COPY MESSAGE", VIOLET);
        RoundButton whatsapp = new RoundButton("SEND VIA WHATSAPP", GREEN.darker());
        RoundButton close = new RoundButton("CLOSE", CARD_HOVER);

        copy.addActionListener(e -> {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(message), null);
            JOptionPane.showMessageDialog(frame, "Emergency message copied to clipboard!");
        });

        whatsapp.addActionListener(e -> {
            int idx = Math.max(0, recipients.getSelectedIndex());
            String number = numbers.get(idx).replace("+", "");
            String encoded = URLEncoder.encode(message, StandardCharsets.UTF_8)
                                       .replace("+", "%20");
            addHistory("WhatsApp Link Opened - " + numbers.get(idx));
            openUrl("https://wa.me/" + number + "?text=" + encoded);
        });

        close.addActionListener(e -> frame.dispose());

        JPanel actions = new JPanel(new GridLayout(1, 3, 10, 0));
        actions.setOpaque(false);
        actions.setPreferredSize(new Dimension(0, 46));
        actions.add(copy);
        actions.add(whatsapp);
        actions.add(close);
        frame.add(actions, BorderLayout.SOUTH);

        frame.setVisible(true);
    }

    // =====================================================
    //  HISTORY
    // =====================================================
    public void addHistory(String activity) {

        String time = LocalDateTime.now().format(TIME_FORMAT);
        String record = time + " - " + activity;

        try (PrintWriter writer = new PrintWriter(
                new FileWriter("history.txt", true))) {

            writer.println(record);

        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Unable to save history!");
        }
    }

    public void showHistory() {

        JFrame frame = createWindow("SafePulse - Alert History", 680, 560);
        frame.add(windowHeader("Alert History",
                  "Everything SafePulse has recorded", Color.WHITE),
                  BorderLayout.NORTH);

        DefaultListModel<String> model = new DefaultListModel<>();

        File file = new File("history.txt");

        if (file.exists()) {

            try (BufferedReader reader =
                    new BufferedReader(new FileReader(file))) {

                String line;

                // newest first
                ArrayList<String> lines = new ArrayList<>();
                while ((line = reader.readLine()) != null) {
                    lines.add(line);
                }
                for (int i = lines.size() - 1; i >= 0; i--) {
                    model.addElement(lines.get(i));
                }

            } catch (IOException e) {
                JOptionPane.showMessageDialog(frame, "Unable to load history!");
            }
        }

        if (model.isEmpty()) {
            model.addElement("No alert history yet.");
        }

        JList<String> list = styledList(model);
        frame.add(styledScroll(list), BorderLayout.CENTER);

        RoundButton clear = new RoundButton("CLEAR HISTORY", ACCENT.darker());
        clear.setPreferredSize(new Dimension(0, 44));
        clear.addActionListener(e -> {
            int ok = JOptionPane.showConfirmDialog(
                frame, "Delete all history?", "Confirm",
                JOptionPane.YES_NO_OPTION);

            if (ok == JOptionPane.YES_OPTION) {
                new File("history.txt").delete();
                model.clear();
                model.addElement("No alert history yet.");
                refreshStats();
            }
        });
        frame.add(clear, BorderLayout.SOUTH);

        frame.setVisible(true);
    }

    // =====================================================
    //  LOCATION (IP based, approximate)
    //  silent = true  -> background fetch at startup (no popups)
    //  silent = false -> user clicked the button (show popups)
    // =====================================================
    public void getCurrentLocation(boolean silent) {

        if (!silent) {
            locationStatus.setText("Location: Detecting...");
            locationStatus.setForeground(YELLOW);
        }

        new SwingWorker<String, Void>() {

            @Override
            protected String doInBackground() throws Exception {

                HttpClient client = HttpClient.newHttpClient();

                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://ipapi.co/json/"))
                    .header("User-Agent", "SafePulse/1.0")
                    .GET()
                    .build();

                HttpResponse<String> response = client.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
                );

                if (response.statusCode() != 200) {
                    throw new IOException("Location service unavailable.");
                }

                return response.body();
            }

            @Override
            protected void done() {

                try {
                    String data = get();

                    String city = extractField(data, "city");
                    String state = extractField(data, "region");
                    String country = extractField(data, "country_name");

                    String latitude = extractNumber(data, "latitude");
                    String longitude = extractNumber(data, "longitude");

                    if (latitude == null || longitude == null) {
                        throw new Exception("Location details unavailable.");
                    }

                    String mapsLink =
                        "https://www.google.com/maps?q="
                        + latitude + "," + longitude;

                    currentLatitude = latitude;
                    currentLongitude = longitude;
                    currentMapsLink = mapsLink;

                    locationStatus.setText("Location: " + city + ", " + state);
                    locationStatus.setToolTipText(latitude + ", " + longitude);
                    locationStatus.setForeground(GREEN);
                    refreshStats();

                    if (silent) return;

                    int choice = JOptionPane.showConfirmDialog(
                        SafePulse.this,
                        "CURRENT LOCATION\n\n" +
                        "City: " + city + "\n" +
                        "State: " + state + "\n" +
                        "Country: " + country + "\n" +
                        "Latitude: " + latitude + "\n" +
                        "Longitude: " + longitude +
                        "\n\nOpen Google Maps?",
                        "SAFEPULSE - LOCATION",
                        JOptionPane.YES_NO_OPTION
                    );

                    if (choice == JOptionPane.YES_OPTION) {
                        openUrl(mapsLink);
                    }

                } catch (Exception e) {

                    locationStatus.setText("Location: Not available");
                    locationStatus.setForeground(ACCENT);

                    if (!silent) {
                        JOptionPane.showMessageDialog(
                            SafePulse.this,
                            "Unable to fetch location.\n" +
                            "Check your internet connection.",
                            "Location Error",
                            JOptionPane.ERROR_MESSAGE
                        );
                    }
                }
            }

        }.execute();
    }

    private String extractField(String data, String key) {

        Pattern pattern = Pattern.compile(
            "\"" + key + "\"\\s*:\\s*\"([^\"]*)\""
        );

        Matcher matcher = pattern.matcher(data);

        if (matcher.find()) {
            return matcher.group(1);
        }

        return "Unavailable";
    }

    private String extractNumber(String data, String key) {

        Pattern pattern = Pattern.compile(
            "\"" + key + "\"\\s*:\\s*(-?[0-9.]+)"
        );

        Matcher matcher = pattern.matcher(data);

        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
    }

    // =====================================================
    //  MAIN
    // =====================================================
    public static void main(String[] args) {

        // Dark theme for popup dialogs (JOptionPane)
        UIManager.put("OptionPane.background", new Color(30, 32, 52));
        UIManager.put("Panel.background", new Color(30, 32, 52));
        UIManager.put("OptionPane.messageForeground", Color.WHITE);
        UIManager.put("OptionPane.foreground", Color.WHITE);

        SwingUtilities.invokeLater(() -> showSplash(SafePulse::new));
    }
}
