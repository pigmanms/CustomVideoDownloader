import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.io.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class VDMasterUI extends JFrame {
    private JTextField urlField;
    private JTextField directoryField;
    private JButton browseButton;
    private JButton startButton;
    private JButton stopButton;
    private JTextArea logArea;
    private Process downloadProcess;

    // Conversion UI
    private JCheckBox needConversionCheck;
    private JRadioButton toM4aRadio;
    private JRadioButton toMp3Radio;
    private JRadioButton toWavRadio;
    private ButtonGroup conversionGroup;
    private JTextField conversionDirField;
    private JButton conversionBrowseButton;
    private JPanel conversionPanel;

    public VDMasterUI() {
        setTitle("VD Build_1.1(Master)");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(800, 650);
        setLocationRelativeTo(null);

        initComponents();
        layoutComponents();
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
        conversionGroup = new ButtonGroup();
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
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        topPanel.add(new JLabel("Video URL:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        topPanel.add(urlField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        topPanel.add(new JLabel("Save Directory:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        topPanel.add(directoryField, gbc);
        gbc.gridx = 2; gbc.weightx = 0;
        topPanel.add(browseButton, gbc);

        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 3;
        topPanel.add(needConversionCheck, gbc);

        GridBagConstraints cgbc = new GridBagConstraints();
        cgbc.insets = new Insets(3, 3, 3, 3);
        cgbc.fill = GridBagConstraints.HORIZONTAL;
        cgbc.gridx = 0; cgbc.gridy = 0; cgbc.gridwidth = 3;
        conversionPanel.add(toM4aRadio, cgbc);
        cgbc.gridy = 1;
        conversionPanel.add(toMp3Radio, cgbc);
        cgbc.gridy = 2;
        conversionPanel.add(toWavRadio, cgbc);
        cgbc.gridy = 3; cgbc.gridwidth = 1; cgbc.gridx = 0;
        conversionPanel.add(new JLabel("Save converted files at:"), cgbc);
        cgbc.gridx = 1; cgbc.weightx = 1;
        conversionPanel.add(conversionDirField, cgbc);
        cgbc.gridx = 2; cgbc.weightx = 0;
        conversionPanel.add(conversionBrowseButton, cgbc);

        gbc.gridy = 3; gbc.gridwidth = 3;
        topPanel.add(conversionPanel, gbc);

        gbc.gridy = 4; gbc.gridwidth = 3;
        JPanel buttonPanel = new JPanel();
        buttonPanel.add(startButton);
        buttonPanel.add(stopButton);
        topPanel.add(buttonPanel, gbc);

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
            appendLog("Process already running...\n");
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

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);

        try {
            downloadProcess = builder.start();
            appendLog("Starting download...\n");

            new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(downloadProcess.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        appendLog(line + "\n");
                    }
                    appendLog("Download finished.\n");

                    if (doConvert) {
                        convertFiles(dir, ext, convDir);
                    }
                } catch (IOException ex) {
                    appendLog("Error: " + ex.getMessage() + "\n");
                }
            }).start();

        } catch (IOException ex) {
            appendLog("Failed to start: " + ex.getMessage() + "\n");
        }
    }

    private void convertFiles(String srcDir, String ext, String destDir) {
        appendLog("Starting conversion to " + ext + "...\n");
        File folder = new File(srcDir);
        File[] files = folder.listFiles((d, name) -> name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".webm"));
        if (files == null) return;
        for (File f : files) {
            String base = f.getName().substring(0, f.getName().lastIndexOf('.'));
            List<String> cmd = new ArrayList<>();
            cmd.add("ffmpeg");
            cmd.add("-i");
            cmd.add(f.getAbsolutePath());
            cmd.add(destDir + File.separator + base + "." + ext);
            try {
                Process p = new ProcessBuilder(cmd).inheritIO().start();
                p.waitFor();
                appendLog("Converted " + base + "." + ext + "\n");
            } catch (Exception ex) {
                appendLog("Conversion error: " + ex.getMessage() + "\n");
            }
        }
        appendLog("All conversions done.\n");
    }

    private void onStop(ActionEvent e) {
        if (downloadProcess != null && downloadProcess.isAlive()) {
            downloadProcess.destroy();
            appendLog("Process stopped.\n");
        } else {
            appendLog("No active process.\n");
        }
    }

    private void appendLog(String text) {
        SwingUtilities.invokeLater(() -> logArea.append(text));
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new VDMasterUI().setVisible(true));
    }
}