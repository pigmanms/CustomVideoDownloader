import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.*;
import java.net.URL;

public class VDMasterUI extends JFrame {
    private JTextField urlField;
    private JTextField directoryField;
    private JButton browseButton;
    private JButton startButton;
    private JButton stopButton;
    private JTextArea logArea;
    private Process downloadProcess;

    public VDMasterUI() {
        Image iconImage = null;
        try {
            URL iconUrl = getClass().getResource("/program_icon.png");
            if (iconUrl != null) {
                iconImage = ImageIO.read(iconUrl);
            } else {
                System.err.println("Icon resource not found");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (iconImage != null) {
            setIconImage(iconImage);
        }

        setTitle("VD Build_1.1(Master)");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(800, 600);
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
    }

    private void layoutComponents() {
        JPanel topPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0;
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
        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            directoryField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void onStart(ActionEvent e) {
        if (downloadProcess != null && downloadProcess.isAlive()) {
            appendLog("Download already in progress...\n");
            return;
        }

        String url = urlField.getText().trim();
        String dir = directoryField.getText().trim();
        if (url.isEmpty() || dir.isEmpty()) {
            JOptionPane.showMessageDialog(this, "URL and directory must be specified.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Build the yt-dlp command
        String outputTemplate = dir + File.separator + "%(title)s.%(ext)s";
        ProcessBuilder builder = new ProcessBuilder(
                "yt-dlp",
                "-o", outputTemplate,
                url
        );

        builder.redirectErrorStream(true);

        try {
            downloadProcess = builder.start();
            appendLog("Starting download...\n");

            // Read process output in a background thread
            new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(downloadProcess.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        appendLog(line + "\n");
                    }
                    appendLog("Download process ended.\n");
                } catch (IOException ex) {
                    appendLog("Error reading process output: " + ex.getMessage() + "\n");
                }
            }).start();

        } catch (IOException ex) {
            appendLog("Failed to start download process: " + ex.getMessage() + "\n");
        }
    }

    private void onStop(ActionEvent e) {
        if (downloadProcess != null && downloadProcess.isAlive()) {
            downloadProcess.destroy();
            appendLog("Download process stopped by user.\n");
        } else {
            appendLog("No active download to stop.\n");
        }
    }

    private void appendLog(String text) {
        SwingUtilities.invokeLater(() -> logArea.append(text));
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new VDMasterUI().setVisible(true);
        });
    }
}
