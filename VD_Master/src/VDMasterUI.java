import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class VDMasterUI extends JFrame {
    private JTextField urlField;
    private JTextField directoryField;
    private JButton browseButton;
    private JButton startButton;
    private JButton stopButton;
    private JTextArea logArea;
    private Process downloadProcess;
    private final List<Process> conversionProcesses = Collections.synchronizedList(new ArrayList<>());
    private ExecutorService conversionExecutor = null; // lazily created per batch

    // Conversion UI
    private JCheckBox needConversionCheck;
    private JRadioButton toM4aRadio;
    private JRadioButton toMp3Radio;
    private JRadioButton toWavRadio;
    private JTextField conversionDirField;
    private JButton conversionBrowseButton;
    private JPanel conversionPanel;

    // Quality UI
    private JComboBox<String> qualityCombo;
    private static final String[] QUALITY_OPTIONS = {"Best quality", "8K", "4K", "1080p", "720p"};

    public VDMasterUI() {
        setTitle("VD Build_1.3 (Release)");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(860, 700);
        setLocationRelativeTo(null);

        applyWindowsLookAndFeel();
        initComponents();
        layoutComponents();
        setupShutdownHook();
    }

    private void applyWindowsLookAndFeel() {
        try {
            // Prefer the OS look and feel (Windows on Win11)
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            SwingUtilities.updateComponentTreeUI(this);
        } catch (Exception ignored) {
            // Fallback silently – UI will still work with default L&F
        }
    }

    private void setupShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (downloadProcess != null && downloadProcess.isAlive()) {
                downloadProcess.destroyForcibly();
            }
            synchronized (conversionProcesses) {
                for (Process p : conversionProcesses) {
                    if (p.isAlive()) p.destroyForcibly();
                }
            }
            if (conversionExecutor != null) conversionExecutor.shutdownNow();
        }));
    }

    private void initComponents() {
        urlField = new JTextField();
        directoryField = new JTextField();
        browseButton = new JButton("Browse...");
        startButton = new JButton("Start");
        stopButton = new JButton("Stop");
        logArea = new JTextArea();
        logArea.setEditable(false);

        browseButton.addActionListener(this::onBrowse);
        startButton.addActionListener(this::onStart);
        stopButton.addActionListener(this::onStop);

        // Quality components (default to 1080p; 60fps preferred inside format rules)
        qualityCombo = new JComboBox<>(QUALITY_OPTIONS);
        qualityCombo.setSelectedItem("1080p");

        // Conversion components
        needConversionCheck = new JCheckBox("Need Conversion?");
        needConversionCheck.addItemListener(this::onConversionToggle);

        toM4aRadio = new JRadioButton("Convert all to m4a");
        toMp3Radio = new JRadioButton("Convert all to mp3");
        toWavRadio = new JRadioButton("Convert all to wav");
        ButtonGroup conversionGroup = new ButtonGroup();
        conversionGroup.add(toM4aRadio);
        conversionGroup.add(toMp3Radio);
        conversionGroup.add(toWavRadio);

        conversionDirField = new JTextField();
        conversionBrowseButton = new JButton("Browse...");
        conversionBrowseButton.addActionListener(this::onConversionBrowse);

        conversionPanel = new JPanel(new GridBagLayout());
        conversionPanel.setBorder(BorderFactory.createTitledBorder("Conversion Options"));
        conversionPanel.setVisible(false);
    }

    private void layoutComponents() {
        JPanel topPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbcMaster = new GridBagConstraints();
        gbcMaster.insets = new Insets(5, 5, 5, 5);
        gbcMaster.fill = GridBagConstraints.HORIZONTAL;

        gbcMaster.gridx = 0; gbcMaster.gridy = 0; gbcMaster.weightx = 0;
        topPanel.add(new JLabel("Video URL:"), gbcMaster);
        gbcMaster.gridx = 1; gbcMaster.weightx = 1;
        topPanel.add(urlField, gbcMaster);

        gbcMaster.gridx = 0; gbcMaster.gridy = 1; gbcMaster.weightx = 0;
        topPanel.add(new JLabel("Save Directory:"), gbcMaster);
        gbcMaster.gridx = 1; gbcMaster.weightx = 1;
        topPanel.add(directoryField, gbcMaster);
        gbcMaster.gridx = 2; gbcMaster.weightx = 0;
        topPanel.add(browseButton, gbcMaster);

        // Quality selector row
        gbcMaster.gridx = 0; gbcMaster.gridy = 2; gbcMaster.weightx = 0;
        topPanel.add(new JLabel("Quality(Prioritizing 60FPS):"), gbcMaster);
        gbcMaster.gridx = 1; gbcMaster.weightx = 1;
        topPanel.add(qualityCombo, gbcMaster);

        gbcMaster.gridx = 0; gbcMaster.gridy = 3; gbcMaster.gridwidth = 3;
        topPanel.add(needConversionCheck, gbcMaster);

        GridBagConstraints gbcSaveConvertedFilesAt = new GridBagConstraints();
        gbcSaveConvertedFilesAt.insets = new Insets(3, 3, 3, 3);
        gbcSaveConvertedFilesAt.fill = GridBagConstraints.HORIZONTAL;
        gbcSaveConvertedFilesAt.gridx = 0; gbcSaveConvertedFilesAt.gridy = 0; gbcSaveConvertedFilesAt.gridwidth = 3;
        conversionPanel.add(toM4aRadio, gbcSaveConvertedFilesAt);
        gbcSaveConvertedFilesAt.gridy = 1;
        conversionPanel.add(toMp3Radio, gbcSaveConvertedFilesAt);
        gbcSaveConvertedFilesAt.gridy = 2;
        conversionPanel.add(toWavRadio, gbcSaveConvertedFilesAt);
        gbcSaveConvertedFilesAt.gridy = 3; gbcSaveConvertedFilesAt.gridwidth = 1; gbcSaveConvertedFilesAt.gridx = 0;
        conversionPanel.add(new JLabel("Save converted files at:"), gbcSaveConvertedFilesAt);
        gbcSaveConvertedFilesAt.gridx = 1; gbcSaveConvertedFilesAt.weightx = 1;
        conversionPanel.add(conversionDirField, gbcSaveConvertedFilesAt);
        gbcSaveConvertedFilesAt.gridx = 2; gbcSaveConvertedFilesAt.weightx = 0;
        conversionPanel.add(conversionBrowseButton, gbcSaveConvertedFilesAt);

        gbcMaster.gridy = 4; gbcMaster.gridwidth = 3;
        topPanel.add(conversionPanel, gbcMaster);

        gbcMaster.gridy = 5; gbcMaster.gridwidth = 3;
        JPanel buttonPanel = new JPanel();
        buttonPanel.add(startButton);
        buttonPanel.add(stopButton);
        topPanel.add(buttonPanel, gbcMaster);

        add(topPanel, BorderLayout.NORTH);
        add(new JScrollPane(logArea), BorderLayout.CENTER);
    }

    private void onBrowse(ActionEvent e) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            directoryField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void onConversionBrowse(ActionEvent e) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            conversionDirField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void onConversionToggle(ItemEvent e) {
        boolean selected = e.getStateChange() == ItemEvent.SELECTED;
        conversionPanel.setVisible(selected);
        revalidate();
        repaint();
    }

    private void onStart(ActionEvent e) {
        if (downloadProcess != null && downloadProcess.isAlive()) {
            appendLog("[INFO] Process already running...\n");
            return;
        }

        String url = urlField.getText().trim();
        String dir = directoryField.getText().trim();
        if (url.isEmpty() || dir.isEmpty()) {
            JOptionPane.showMessageDialog(this, "URL and directory must be specified.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        List<String> command = new ArrayList<>();
        command.add("yt-dlp");
        command.add("-o");
        command.add(dir + File.separator + "%(title)s.%(ext)s");

        // Always merge to mp4 when possible – tends to work better on Windows players
        command.add("--merge-output-format");
        command.add("mp4");

        // Prefer 60fps when available, targeting resolution from combo
        String selectedQuality = (String) qualityCombo.getSelectedItem();
        String formatSelector = buildFormatSelector(Objects.requireNonNull(selectedQuality));
        if (formatSelector != null && !formatSelector.isEmpty()) {
            command.add("-f");
            command.add(formatSelector);
        }

        // Workaround: Bilibili sometimes benefits from explicit remux and retries
        if (url.contains("bilibili.com")) {
            command.add("--retries");
            command.add("5");
            command.add("--fragment-retries");
            command.add("5");
            // Note: higher qualities on Bilibili may require login (cookies). Not enforced here.
        }

        command.add(url);

        ProcessBuilder builderMasterProcess = new ProcessBuilder(command);
        builderMasterProcess.redirectErrorStream(true);

        try {
            downloadProcess = builderMasterProcess.start();
            appendLog("[INFO] Starting download...\n");
            appendLog("[CMD] " + String.join(" ", command) + "\n");

            new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(downloadProcess.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        appendLog(line + "\n");
                    }
                    appendLog("[INFO] Download finished.\n");

                    boolean doConvert = needConversionCheck.isSelected();
                    if (doConvert) {
                        String ext;
                        if (toM4aRadio.isSelected()) ext = "m4a";
                        else if (toMp3Radio.isSelected()) ext = "mp3";
                        else if (toWavRadio.isSelected()) ext = "wav";
                        else ext = null;

                        String convDir = conversionDirField.getText().trim();
                        if (ext == null || convDir.isEmpty()) {
                            appendLog("[ERROR] Conversion requested but format/dir not set.\n");
                        } else {
                            convertFiles(dir, ext, convDir);
                        }
                    }
                } catch (IOException ex) {
                    appendLog("[ERROR] General error: " + ex.getMessage() + "\n");
                }
            }, "ydlp-reader").start();

        } catch (IOException ex) {
            appendLog("[ERROR] Failed to start: " + ex.getMessage() + "\n");
        }
    }

    /**
     * Build a yt-dlp format selector string favoring 60fps when available.
     * Uses fallback chains so that if the exact resolution+60fps is unavailable,
     * we still get the closest available variant.
     */
    private String buildFormatSelector(String quality) {
        // Helper templates: prefer video-only + best audio, else fall back to single muxed best
        // 60fps preference is expressed with fps>=?60 (the ? makes it a soft condition).
        return switch (quality) {
            case "Best quality" ->
                // Any site: best video preferring 60fps, plus best audio
                    "bv*[fps>=?60]+ba/bv*+ba/best";
            case "8K" -> // 4320p
                    "bv*[height=4320][fps>=?60]+ba/bv*[height=4320]+ba/b[height=4320]/" +
                            "bv*[height<=4320][fps>=?60]+ba";
            case "4K" -> // 2160p
                    "bv*[height=2160][fps>=?60]+ba/bv*[height=2160]+ba/b[height=2160]/" +
                            "bv*[height<=2160][fps>=?60]+ba";
            case "1080p" -> "bv*[height=1080][fps>=?60]+ba/bv*[height=1080]+ba/b[height=1080]/" +
                    "bv*[height<=1080][fps>=?60]+ba";
            case "720p" -> "bv*[height=720][fps>=?60]+ba/bv*[height=720]+ba/b[height=720]/" +
                    "bv*[height<=720][fps>=?60]+ba";
            default -> "best";
        };
    }

    private void convertFiles(String srcDir, String ext, String destDir) {
        appendLog("[INFO] Starting conversion to " + ext + "...\n");
        File folder = new File(srcDir);
        File[] files = folder.listFiles((d, name) -> name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".webm"));
        if (files == null || files.length == 0) {
            appendLog("[INFO] No files to convert in: " + srcDir + "\n");
            return;
        }

        // Create a fresh executor per conversion batch
        conversionExecutor = Executors.newFixedThreadPool(Math.max(1, Runtime.getRuntime().availableProcessors()));

        for (File f : files) {
            conversionExecutor.submit(() -> {
                String base = f.getName().substring(0, f.getName().lastIndexOf('.'));
                List<String> cmd = new ArrayList<>();
                cmd.add("ffmpeg");
                cmd.add("-y");
                cmd.add("-threads");
                cmd.add(String.valueOf(Runtime.getRuntime().availableProcessors()));
                cmd.add("-i");
                cmd.add(f.getAbsolutePath());
                // Audio-only extraction if target is audio container; keep it simple and fast
                if (ext.equalsIgnoreCase("m4a") || ext.equalsIgnoreCase("mp3") || ext.equalsIgnoreCase("wav")) {
                    cmd.add("-vn"); // drop video
                }
                cmd.add(new File(destDir, base + "." + ext).getAbsolutePath());
                try {
                    Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
                    synchronized (conversionProcesses) {
                        conversionProcesses.add(p);
                    }
                    try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                        String line;
                        while ((line = r.readLine()) != null) {
                            if (line.contains("time=")) { // lightweight progress hint
                                appendLog("[FFMPEG] " + line + "\n");
                            }
                        }
                    }
                    int code = p.waitFor();
                    if (code == 0) appendLog("Converted " + base + "." + ext + "\n");
                    else appendLog("Conversion non-zero exit for " + base + "." + ext + " (code=" + code + ")\n");
                } catch (Exception ex) {
                    appendLog("Conversion error: " + ex.getMessage() + "\n");
                }
            });
        }

        conversionExecutor.shutdown();
        new Thread(() -> {
            try {
                if (!conversionExecutor.awaitTermination(1, TimeUnit.HOURS)) {
                    conversionExecutor.shutdownNow();
                }
            } catch (InterruptedException ie) {
                conversionExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            appendLog("[INFO] All conversions done.\n");
        }, "conv-waiter").start();
    }

    private void onStop(ActionEvent e) {
        if (downloadProcess != null && downloadProcess.isAlive()) {
            downloadProcess.destroyForcibly();
            appendLog("[INFO] Download process stopped.\n");
        } else {
            appendLog("[INFO] No active download.\n");
        }
        synchronized (conversionProcesses) {
            for (Process p : conversionProcesses) {
                if (p.isAlive()) {
                    p.destroyForcibly();
                }
            }
        }
        if (conversionExecutor != null) conversionExecutor.shutdownNow();
    }

    private void appendLog(String text) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(text);
            // auto-scroll to bottom
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    public static void main(String[] args) {
        // Apply Windows theme even before creating UI (best chance for native look)
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        SwingUtilities.invokeLater(() -> new VDMasterUI().setVisible(true));
    }
}
