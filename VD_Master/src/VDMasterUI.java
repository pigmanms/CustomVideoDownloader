import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
    private final ExecutorService conversionExecutor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    // Conversion UI
    private JCheckBox needConversionCheck;
    private JRadioButton toM4aRadio;
    private JRadioButton toMp3Radio;
    private JRadioButton toWavRadio;
    private JTextField conversionDirField;
    private JButton conversionBrowseButton;
    private JPanel conversionPanel;

    public VDMasterUI() {
        setTitle("VD Build_1.2 (Release)");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(800, 650);
        setLocationRelativeTo(null);

        initComponents();
        layoutComponents();
        setupShutdownHook();
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
            conversionExecutor.shutdownNow();
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

        gbcMaster.gridx = 0; gbcMaster.gridy = 2; gbcMaster.gridwidth = 3;
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

        gbcMaster.gridy = 3; gbcMaster.gridwidth = 3;
        topPanel.add(conversionPanel, gbcMaster);

        gbcMaster.gridy = 4; gbcMaster.gridwidth = 3;
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
        command.add(url);

        boolean doConvert = needConversionCheck.isSelected();
        String ext;
        String convDir;
        if (doConvert) {
            if (toM4aRadio.isSelected()) ext = "m4a";
            else if (toMp3Radio.isSelected()) ext = "mp3";
            else if (toWavRadio.isSelected()) ext = "wav";
            else {
                ext = null;
            }
            convDir = conversionDirField.getText().trim();
            if (ext == null || convDir.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Select conversion format and directory.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
        } else {
            convDir = null;
            ext = null;
        }

        ProcessBuilder builderMasterProcess = new ProcessBuilder(command);
        builderMasterProcess.redirectErrorStream(true);

        try {
            downloadProcess = builderMasterProcess.start();
            appendLog("[INFO] Starting download...\n");

            new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(downloadProcess.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        appendLog(line + "\n");
                    }
                    appendLog("[INFO] Download finished.\n");

                    if (doConvert) {
                        convertFiles(dir, ext, convDir);
                    }
                } catch (IOException ex) {
                    appendLog("[ERROR] General error: " + ex.getMessage() + "\n");
                }
            }).start();

        } catch (IOException ex) {
            appendLog("[ERROR] Failed to start: " + ex.getMessage() + "\n");
        }
    }

    private void convertFiles(String srcDir, String ext, String destDir) {
        appendLog("[INFO] Starting conversion to " + ext + "...\n");
        File folder = new File(srcDir);
        File[] files = folder.listFiles((d, name) -> name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".webm"));
        if (files == null) return;

        for (File f : files) {
            conversionExecutor.submit(() -> {
                String base = f.getName().substring(0, f.getName().lastIndexOf('.'));
                List<String> cmd = new ArrayList<>();
                cmd.add("ffmpeg");
                cmd.add("-threads");
                cmd.add(String.valueOf(Runtime.getRuntime().availableProcessors()));
                cmd.add("-i");
                cmd.add(f.getAbsolutePath());
                cmd.add(destDir + File.separator + base + "." + ext);
                try {
                    Process p = new ProcessBuilder(cmd).inheritIO().start();
                    synchronized (conversionProcesses) {
                        conversionProcesses.add(p);
                    }
                    p.waitFor();
                    appendLog("Converted " + base + "." + ext + "\n");
                } catch (Exception ex) {
                    appendLog("Conversion error: " + ex.getMessage() + "\n");
                }
            });
        }
        conversionExecutor.shutdown();
        try {
            if (!conversionExecutor.awaitTermination(1, TimeUnit.HOURS)) {
                conversionExecutor.shutdownNow();
            }
        } catch (InterruptedException ie) {
            conversionExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        appendLog("[INFO] All conversions done.\n");
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
        conversionExecutor.shutdownNow();
    }

    private void appendLog(String text) {
        SwingUtilities.invokeLater(() -> logArea.append(text));
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new VDMasterUI().setVisible(true));
    }
}
