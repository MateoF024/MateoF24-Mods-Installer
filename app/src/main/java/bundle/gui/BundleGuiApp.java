package bundle.gui;

import bundle.download.DownloadException;
import bundle.download.DownloadManager;
import bundle.download.ProgressCallback;
import bundle.installer.BundleInstaller;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.io.IOException;

public class BundleGuiApp extends JFrame {

    private static final Font FONT_NORMAL = new Font("Segoe UI", Font.PLAIN, 14);
    private static final Font FONT_BOLD = new Font("Segoe UI", Font.BOLD, 14);
    private static final Font FONT_SMALL = new Font("Segoe UI", Font.PLAIN, 12);
    private static final Color COLOR_BG = new Color(40, 42, 43);
    private static final Color COLOR_TITLE = new Color(35, 38, 40);
    private static final Color COLOR_TEXT = new Color(230, 230, 230);
    private static final Color COLOR_PRIMARY = new Color(30, 136, 229);
    private static final Color COLOR_FIELD_BG = new Color(58, 60, 62);
    private static final Color COLOR_SUCCESS = new Color(76, 175, 80);
    private static final Color COLOR_PROGRESS_BG = new Color(70, 72, 74);
    private static final Insets DEFAULT_INSETS = new Insets(8, 8, 8, 8);
    private static final int CORNER_RADIUS = 21;

    private final BundleInstaller installer;
    private final CardLayout cards = new CardLayout();
    private final JPanel cardsContainer = new JPanel(cards);
    private final JLabel finishLabel = new JLabel();
    private static final String INSTALL_PANEL = "install";
    private static final String FINISH_INSTALL_PANEL = "finish_install";

    // Referencias para manipular visibilidad y progreso
    private JComboBox<String> combo;
    private JButton btnBuscar;
    private JTextField filePath;
    private JButton btnInstall;
    private JProgressBar progressBar;
    private JLabel statusLabel;
    private JLabel progressLabel;
    private JPanel progressPanel;
    private JLabel lblModpacks;

    public BundleGuiApp(BundleInstaller installer) {
        super(installer.installerProperties.getProperty("window_title"));

        setUndecorated(true);
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        this.installer = installer;

        int w = Integer.parseInt(installer.installerProperties.getProperty("width"));
        int h = Integer.parseInt(installer.installerProperties.getProperty("height"));
        setSize(w, h);
        setResizable(Boolean.parseBoolean(installer.installerProperties.getProperty("resizable")));

        setShape(new RoundRectangle2D.Double(0, 0, w, h, CORNER_RADIUS, CORNER_RADIUS));
        getRootPane().setBorder(BorderFactory.createLineBorder(new Color(0, 0, 0,100)));

        setLayout(new BorderLayout());
        add(createTitleBar(), BorderLayout.NORTH);

        cardsContainer.setBackground(COLOR_BG);
        cardsContainer.add(buildInstallPanel(), INSTALL_PANEL);
        cardsContainer.add(buildFinishPanel(), FINISH_INSTALL_PANEL);
        add(wrapCenter(cardsContainer), BorderLayout.CENTER);

        centerWindow();
    }

    private JPanel wrapCenter(JComponent inner) {
        JPanel wrapper = new JPanel(new GridBagLayout());
        wrapper.setBackground(COLOR_BG);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0; gbc.gridy = 0;
        gbc.weightx = 1; gbc.weighty = 1;
        gbc.fill = GridBagConstraints.BOTH;
        wrapper.add(inner, gbc);
        return wrapper;
    }

    private JComponent createTitleBar() {
        JPanel titleBar = new JPanel(new BorderLayout());
        titleBar.setBackground(COLOR_TITLE);
        titleBar.setPreferredSize(new Dimension(getWidth(), 36));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        left.setOpaque(false);

        JLabel titleLabel = new JLabel(getTitle());
        titleLabel.setFont(FONT_BOLD);
        titleLabel.setForeground(COLOR_TEXT);
        left.add(titleLabel);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 4));
        right.setOpaque(false);
        JButton btnMin = createTitleButton("\u2014");
        JButton btnMax = createTitleButton("\u25A1");
        JButton btnClose = createTitleButton("\u2715");

        btnMin.addActionListener(e -> setState(Frame.ICONIFIED));

        btnMax.setEnabled(false);
        btnMax.setForeground(new Color(100, 100, 100));
        btnMax.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));

        btnClose.addActionListener(e -> {
            dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING));
        });

        right.add(btnMin);
        right.add(btnMax);
        right.add(btnClose);

        Point dragStart = new Point();
        MouseAdapter ma = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                dragStart.x = e.getX();
                dragStart.y = e.getY();
            }
            @Override
            public void mouseDragged(MouseEvent e) {
                Point p = getLocation();
                setLocation(p.x + e.getX() - dragStart.x, p.y + e.getY() - dragStart.y);
            }
        };
        titleBar.addMouseListener(ma);
        titleBar.addMouseMotionListener(ma);
        titleLabel.addMouseListener(ma);
        titleLabel.addMouseMotionListener(ma);

        titleBar.add(left, BorderLayout.WEST);
        titleBar.add(right, BorderLayout.EAST);

        return titleBar;
    }

    private JButton createTitleButton(String text) {
        JButton b = new JButton(text);
        b.setFont(new Font("Dialog", Font.PLAIN, 12));
        b.setForeground(COLOR_TEXT);
        b.setBackground(Color.CYAN);
        b.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        b.setFocusPainted(false);
        b.setOpaque(false);
        b.setContentAreaFilled(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) {
                if (b.isEnabled()) {
                    b.setOpaque(true);
                    b.setBackground(new Color(80,80,80));
                }
            }
            @Override public void mouseExited(MouseEvent e) {
                b.setOpaque(false);
                b.setBackground(Color.CYAN);
            }
        });
        return b;
    }

    private JPanel buildInstallPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(true);
        panel.setBackground(COLOR_BG);
        panel.setBorder(new EmptyBorder(16, 18, 18, 18));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = DEFAULT_INSETS;
        gbc.fill = GridBagConstraints.CENTER;

        // Row 0: Modpacks label + combo
        gbc.gridy = 0;
        gbc.gridx = 0;
        gbc.weightx = 0;
        gbc.anchor = GridBagConstraints.CENTER;
        lblModpacks = new JLabel("Modpacks:");
        lblModpacks.setFont(FONT_BOLD);
        lblModpacks.setForeground(COLOR_TEXT);
        panel.add(lblModpacks, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        String[] names = installer.installerConfig.configNames.toArray(new String[0]);
        combo = createModernComboBox(names);
        combo.addActionListener(e -> installer.selectedInstall = (String) combo.getSelectedItem());
        panel.add(combo, gbc);

        gbc.gridx = 2;
        gbc.weightx = 0;
        panel.add(new JLabel(""), gbc);

        gbc.gridy = 1;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        filePath = new JTextField(installer.gameDir.toString());
        styleTextField(filePath);
        panel.add(filePath, gbc);

        gbc.gridwidth = 1;
        gbc.gridx = 2;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        btnBuscar = createAccentButton("Buscar", 92, 34);
        btnBuscar.addActionListener(e -> {
            changeGameDir();
            filePath.setText(installer.gameDir.toString());
        });
        panel.add(btnBuscar, gbc);

        // Panel de progreso
        gbc.gridy = 2;
        gbc.gridx = 0;
        gbc.gridwidth = 3;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        progressPanel = createProgressPanel();
        progressPanel.setVisible(false);
        panel.add(progressPanel, gbc);

        gbc.gridy = 3;
        gbc.gridx = 0;
        gbc.gridwidth = 3;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.weighty = 1.0;
        btnInstall = createAccentButton("Instalar", 140, 40);
        btnInstall.addActionListener(e -> {
            lblModpacks.setVisible(false);
            combo.setVisible(false);
            btnBuscar.setVisible(false);
            filePath.setVisible(false);
            btnInstall.setVisible(false);
            progressPanel.setVisible(true);
            resetProgress();
            performInstall();
        });
        panel.add(btnInstall, gbc);

        return panel;
    }

    private JPanel createProgressPanel() {
        JPanel mainPanel = new JPanel(new GridBagLayout());
        mainPanel.setOpaque(false);
        mainPanel.setBorder(new EmptyBorder(20, 20, 20, 20));

        JPanel progressContainer = new JPanel();
        progressContainer.setLayout(new BoxLayout(progressContainer, BoxLayout.Y_AXIS));
        progressContainer.setOpaque(false);
        progressContainer.setBorder(new EmptyBorder(15, 15, 15, 15));

        statusLabel = new JLabel("Preparando instalación...", SwingConstants.CENTER);
        statusLabel.setFont(FONT_NORMAL);
        statusLabel.setForeground(COLOR_TEXT);
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        statusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        progressContainer.add(statusLabel);

        progressContainer.add(Box.createVerticalStrut(12));

        progressBar = createModernProgressBar();
        progressBar.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPanel progressBarWrapper = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        progressBarWrapper.setOpaque(false);
        progressBarWrapper.add(progressBar);
        progressContainer.add(progressBarWrapper);

        progressContainer.add(Box.createVerticalStrut(8));

        progressLabel = new JLabel("0%", SwingConstants.CENTER);
        progressLabel.setFont(FONT_SMALL);
        progressLabel.setForeground(new Color(180, 180, 180));
        progressLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        progressLabel.setHorizontalAlignment(SwingConstants.CENTER);
        progressContainer.add(progressLabel);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.fill = GridBagConstraints.NONE;

        mainPanel.add(progressContainer, gbc);
        return mainPanel;
    }

    private JProgressBar createModernProgressBar() {
        JProgressBar bar = new JProgressBar(0, 100) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int width = getWidth();
                int height = getHeight();
                int progressWidth = (int) ((double) getValue() / getMaximum() * width);

                g2.setColor(COLOR_PROGRESS_BG);
                g2.fillRoundRect(0, 0, width, height, 6, 6);

                if (progressWidth > 0) {
                    g2.setColor(COLOR_PRIMARY);
                    g2.fillRoundRect(0, 0, progressWidth, height, 6, 6);

                    g2.setColor(new Color(255, 255, 255, 30));
                    g2.fillRoundRect(0, 0, progressWidth, height / 2, 6, 6);
                }

                g2.dispose();
            }
        };

        bar.setValue(0);
        bar.setPreferredSize(new Dimension(350, 14));
        bar.setMinimumSize(new Dimension(350, 14));
        bar.setMaximumSize(new Dimension(350, 14));
        bar.setBorderPainted(false);
        bar.setOpaque(false);

        return bar;
    }

    private void resetProgress() {
        progressBar.setValue(0);
        progressLabel.setText("0%");
        statusLabel.setText("Preparando instalación...");
    }

    private void updateProgress(int value, String status) {
        SwingUtilities.invokeLater(() -> {
            progressBar.setValue(value);
            progressLabel.setText(value + "%");
            statusLabel.setText(status);
        });
    }

    private JPanel buildFinishPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(true);
        panel.setBackground(COLOR_BG);
        panel.setBorder(new EmptyBorder(16, 18, 18, 18));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = DEFAULT_INSETS;
        gbc.gridx = 0; gbc.gridy = 0; gbc.anchor = GridBagConstraints.CENTER;

        finishLabel.setFont(FONT_BOLD);
        finishLabel.setForeground(COLOR_SUCCESS);
        finishLabel.setText("¡La instalación ha sido exitosa!");
        panel.add(finishLabel, gbc);

        gbc.gridy = 1;
        JButton done = createAccentButton("Hecho", 120, 36);
        done.addActionListener(e -> System.exit(0));
        panel.add(done, gbc);

        return panel;
    }

    private JComboBox<String> createModernComboBox(String[] items) {
        JComboBox<String> combo = new JComboBox<>(items);
        combo.setFont(FONT_NORMAL);
        combo.setPreferredSize(new Dimension(260, 34));
        combo.setBackground(COLOR_FIELD_BG);
        combo.setForeground(COLOR_TEXT);
        combo.setOpaque(true);
        combo.setFocusable(true);

        combo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list,
                                                          Object value, int index, boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                label.setBorder(new EmptyBorder(6, 8, 6, 8));
                label.setFont(FONT_NORMAL);
                label.setOpaque(true);
                label.setHorizontalAlignment(SwingConstants.LEFT);
                if (isSelected) {
                    label.setBackground(COLOR_PRIMARY.darker());
                    label.setForeground(Color.WHITE);
                } else {
                    label.setBackground(COLOR_FIELD_BG);
                    label.setForeground(COLOR_TEXT);
                }
                return label;
            }
        });

        return combo;
    }

    private JButton createAccentButton(String text, int w, int h) {
        JButton b = new JButton(text);
        b.setFont(FONT_BOLD);
        b.setForeground(Color.WHITE);
        b.setBackground(COLOR_PRIMARY);
        b.setPreferredSize(new Dimension(w, h));
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createEmptyBorder(6, 14, 6, 14));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { b.setBackground(COLOR_PRIMARY.brighter()); }
            @Override public void mouseExited(MouseEvent e) { b.setBackground(COLOR_PRIMARY); }
        });
        return b;
    }

    private void styleTextField(JTextField tf) {
        tf.setFont(FONT_NORMAL);
        tf.setForeground(COLOR_TEXT);
        tf.setBackground(COLOR_FIELD_BG);
        tf.setCaretColor(COLOR_TEXT);
        tf.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(80, 80, 80)),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)));
    }

    private void performInstall() {
        ProgressCallback progressCallback = new ProgressCallback() {
            @Override
            public void onDownloadStart(String fileName, long totalBytes) {
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Descargando: " + fileName);
                    progressBar.setValue(0);
                    progressLabel.setText("0% - Iniciando descarga...");
                });
            }

            @Override
            public void onProgress(long bytesDownloaded, long totalBytes, double downloadSpeed, String fileName) {
                // Hacer las variables finales para usar en lambda
                final int percentage = Math.min(100, (totalBytes > 0) ? (int) ((bytesDownloaded * 100) / totalBytes) : 0);
                final String speedText = DownloadManager.formatSpeed(downloadSpeed);
                final String sizeText = DownloadManager.formatBytes(bytesDownloaded);
                final String totalText = (totalBytes > 0) ? DownloadManager.formatBytes(totalBytes) : "Desconocido";

                SwingUtilities.invokeLater(() -> {
                    progressBar.setValue(percentage);
                    progressLabel.setText(String.format("%d%% - %s (%s/%s)",
                            percentage, speedText, sizeText, totalText));
                    statusLabel.setText("Descargando: " + fileName);
                });
            }

            @Override
            public void onDownloadComplete(String fileName) {
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Descarga completada: " + fileName);
                    progressBar.setValue(100);
                    progressLabel.setText("100% - Descarga completada");
                });
            }
        };

        SwingWorker<Boolean, Void> worker = new SwingWorker<>() {
            private String errorMessage = null;

            @Override
            protected Boolean doInBackground() {
                try {
                    SwingUtilities.invokeLater(() -> {
                        statusLabel.setText("Preparando instalación...");
                        progressBar.setValue(0);
                        progressLabel.setText("0% - Preparando...");
                    });

                    installer.install(progressCallback);

                    SwingUtilities.invokeLater(() -> {
                        statusLabel.setText("Procesando archivos descargados...");
                        progressBar.setValue(95);
                        progressLabel.setText("95% - Finalizando...");
                    });

                    Thread.sleep(500); // Delay reducido

                    SwingUtilities.invokeLater(() -> {
                        statusLabel.setText("¡Instalación completada!");
                        progressBar.setValue(100);
                        progressLabel.setText("100% - ¡Completado!");
                    });

                    Thread.sleep(300); // Delay reducido
                    return true;

                } catch (IOException | DownloadException e) {
                    e.printStackTrace();
                    errorMessage = e.getMessage();
                    return false;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }

            @Override
            protected void done() {
                try {
                    if (get()) {
                        finishLabel.setText("¡La instalación de " + installer.selectedInstall + " fue completada exitosamente!");
                        cards.show(cardsContainer, FINISH_INSTALL_PANEL);
                    } else {
                        JOptionPane.showMessageDialog(BundleGuiApp.this,
                                "Error durante la instalación: " + errorMessage,
                                "Error", JOptionPane.ERROR_MESSAGE);

                        lblModpacks.setVisible(true);
                        combo.setVisible(true);
                        btnBuscar.setVisible(true);
                        filePath.setVisible(true);
                        btnInstall.setVisible(true);
                        progressPanel.setVisible(false);
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(BundleGuiApp.this,
                            "Error inesperado: " + ex.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    public void open() {
        SwingUtilities.invokeLater(() -> setVisible(true));
    }

    public void changeGameDir() {
        try {
            JFileChooser fileSelect = new JFileChooser();

            fileSelect.setFileSystemView(javax.swing.filechooser.FileSystemView.getFileSystemView());
            fileSelect.setCurrentDirectory(installer.gameDir.toFile());
            fileSelect.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            fileSelect.setDialogTitle("Selecciona la ruta de instalación del modpack");
            fileSelect.setAcceptAllFileFilterUsed(false);
            fileSelect.setFileHidingEnabled(true);

            if (fileSelect.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                java.io.File selectedFile = fileSelect.getSelectedFile();
                if (selectedFile != null && selectedFile.exists() && selectedFile.isDirectory()) {
                    installer.gameDir = selectedFile.toPath();
                    if (filePath.isVisible()) {
                        filePath.setText(installer.gameDir.toString());
                    }
                } else {
                    JOptionPane.showMessageDialog(this,
                            "El directorio seleccionado no es válido.",
                            "Error de Directorio", JOptionPane.WARNING_MESSAGE);
                }
            }
        } catch (Exception e) {
            System.err.println("Error en JFileChooser, usando fallback: " + e.getMessage());
            showManualPathDialog();
        }
    }

    private void showManualPathDialog() {
        String currentPath = installer.gameDir.toString();
        String newPath = JOptionPane.showInputDialog(this,
                "Ingresa la ruta del directorio de instalación:",
                currentPath);

        if (newPath != null && !newPath.trim().isEmpty()) {
            try {
                java.nio.file.Path path = java.nio.file.Paths.get(newPath.trim());
                if (java.nio.file.Files.exists(path) && java.nio.file.Files.isDirectory(path)) {
                    installer.gameDir = path;
                    if (filePath.isVisible()) {
                        filePath.setText(installer.gameDir.toString());
                    }
                } else {
                    JOptionPane.showMessageDialog(this,
                            "La ruta especificada no existe o no es un directorio válido.",
                            "Error de Ruta", JOptionPane.ERROR_MESSAGE);
                }
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this,
                        "La ruta especificada no es válida: " + e.getMessage(),
                        "Error de Ruta", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void centerWindow() {
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        setLocation((screenSize.width - getWidth()) / 2, (screenSize.height - getHeight()) / 2);
    }
}