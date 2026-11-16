/*
 *  Copyright (C) 2010 Ryszard Wiśniewski <brut.alll@gmail.com>
 *  Copyright (C) 2010 Connor Tumbleson <connor.tumbleson@gmail.com>
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package brut.apktool.gui;

import brut.androlib.ApkBuilder;
import brut.androlib.ApkDecoder;
import brut.androlib.Config;
import brut.androlib.exceptions.AndrolibException;
import brut.androlib.res.AaptManager;
import brut.androlib.res.Framework;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

/**
 * Graphical front-end for Apktool (decode, build and framework management).
 */
public class ApktoolGui extends JFrame {
    private static final Preferences PREFS = Preferences.userNodeForPackage(ApktoolGui.class);

    private final String version = loadVersion();

    // Decode tab
    private final JTextField decApkField = new JTextField();
    private final JTextField decOutField = new JTextField();
    private final JRadioButton decSrcMain = new JRadioButton("Solo classi principali", true);
    private final JRadioButton decSrcAll = new JRadioButton("Tutti i dex (-a)");
    private final JRadioButton decSrcNone = new JRadioButton("Nessuno (-s)");
    private final JRadioButton decResFull = new JRadioButton("Complete", true);
    private final JRadioButton decResManifest = new JRadioButton("Solo AndroidManifest.xml");
    private final JRadioButton decResNone = new JRadioButton("Nessuna (-r)");
    private final JCheckBox decForce = new JCheckBox("Sovrascrivi la cartella di destinazione (-f)");
    private final JCheckBox decNoDebugInfo = new JCheckBox("Senza info di debug (.line, .local, ...)");
    private final JCheckBox decNoAssets = new JCheckBox("Non estrarre gli assets");
    private final JCheckBox decKeepBroken = new JCheckBox("Mantieni risorse non valide");
    private final JCheckBox decIgnoreRaw = new JCheckBox("Ignora valori raw negli XML");
    private final JCheckBox decMatchOriginal = new JCheckBox("Fedele all'originale (impedisce la ricompilazione)");
    private final JComboBox<String> decResolveMode = new JComboBox<>(new String[] {"default", "greedy", "lazy"});
    private final JButton decRunButton = new JButton("Decompila");
    private final JButton decOpenButton = new JButton("Apri cartella");

    // Build tab
    private final JTextField bldDirField = new JTextField();
    private final JTextField bldOutField = new JTextField();
    private final JTextField bldAaptField = new JTextField();
    private final JCheckBox bldForce = new JCheckBox("Ricompila tutto, ignorando le modifiche (-f)");
    private final JCheckBox bldDebuggable = new JCheckBox("Rendi l'app debuggable");
    private final JCheckBox bldNetSecConf = new JCheckBox("Aggiungi network security config generica");
    private final JCheckBox bldCopyOriginal = new JCheckBox("Copia AndroidManifest.xml e META-INF originali");
    private final JCheckBox bldNoCrunch = new JCheckBox("Non comprimere le immagini (no crunch)");
    private final JCheckBox bldNoApk = new JCheckBox("Non creare l'APK (solo build intermedia)");
    private final JButton bldRunButton = new JButton("Ricompila");
    private final JButton bldOpenButton = new JButton("Apri cartella");

    // Framework tab
    private final JTextField fwApkField = new JTextField();
    private final JCheckBox fwAll = new JCheckBox("Tutti i framework, indipendentemente dal tag");
    private final JButton fwInstallButton = new JButton("Installa framework");
    private final JButton fwListButton = new JButton("Elenca");
    private final JButton fwCleanButton = new JButton("Elimina...");
    private final DefaultListModel<String> fwListModel = new DefaultListModel<>();

    // Settings tab
    private final JSpinner jobsSpinner = new JSpinner(new SpinnerNumberModel(
        Math.min(Runtime.getRuntime().availableProcessors(), 8), 1, 64, 1));
    private final JTextField frameDirField = new JTextField();
    private final JTextField frameTagField = new JTextField();
    private final JCheckBox verboseCheck = new JCheckBox("Log dettagliato (verbose)");

    // Log area
    private final JTextPane logPane = new JTextPane();
    private final JProgressBar progressBar = new JProgressBar();
    private final JLabel statusLabel = new JLabel("Pronto.");
    private final List<JComponent> busyDisabled = new ArrayList<>();
    private final JTabbedPane tabs = new JTabbedPane();

    private Path lastDecodedDir;
    private Path lastBuildDir;

    public ApktoolGui() {
        super("Apktool GUI");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        tabs.addTab("Decompila", buildDecodeTab());
        tabs.addTab("Ricompila", buildBuildTab());
        tabs.addTab("Framework", buildFrameworkTab());
        tabs.addTab("Impostazioni", buildSettingsTab());

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tabs, buildLogPanel());
        split.setResizeWeight(0.6);
        split.setBorder(new EmptyBorder(6, 6, 6, 6));

        JPanel statusBar = new JPanel(new BorderLayout(8, 0));
        statusBar.setBorder(new EmptyBorder(2, 8, 4, 8));
        statusBar.add(statusLabel, BorderLayout.CENTER);
        progressBar.setPreferredSize(new Dimension(180, 14));
        progressBar.setVisible(false);
        statusBar.add(progressBar, BorderLayout.EAST);

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(split, BorderLayout.CENTER);
        getContentPane().add(statusBar, BorderLayout.SOUTH);

        busyDisabled.add(decRunButton);
        busyDisabled.add(bldRunButton);
        busyDisabled.add(fwInstallButton);
        busyDisabled.add(fwListButton);
        busyDisabled.add(fwCleanButton);

        installDropSupport();
        setupLogging();
        loadSettings();
        updateDecodeOptionState();
        updateBuildOptionState();

        setSize(900, 760);
        setMinimumSize(new Dimension(700, 560));
        setLocationRelativeTo(null);

        appendLog(Level.INFO, "Apktool " + version + " - trascina un file .apk o una cartella decompilata nella finestra.");
    }

    // ------------------------------------------------------------------ UI construction

    private JComponent buildDecodeTab() {
        Form form = new Form();
        form.row("File APK:", decApkField, browseButton(decApkField, true, "apk"));
        form.row("Cartella di output:", decOutField, browseButton(decOutField, false, null));
        decApkField.getDocument().addDocumentListener(new SimpleDocListener(this::suggestDecodeOutput));

        ButtonGroup srcGroup = new ButtonGroup();
        srcGroup.add(decSrcMain);
        srcGroup.add(decSrcAll);
        srcGroup.add(decSrcNone);
        form.full(titled("Sorgenti (smali)", flow(decSrcMain, decSrcAll, decSrcNone)));

        ButtonGroup resGroup = new ButtonGroup();
        resGroup.add(decResFull);
        resGroup.add(decResManifest);
        resGroup.add(decResNone);
        form.full(titled("Risorse", flow(decResFull, decResManifest, decResNone,
            new JLabel("   Risoluzione:"), decResolveMode)));

        JPanel opts = new JPanel(new GridLayout(0, 2, 8, 2));
        opts.add(decForce);
        opts.add(decNoDebugInfo);
        opts.add(decNoAssets);
        opts.add(decKeepBroken);
        opts.add(decIgnoreRaw);
        opts.add(decMatchOriginal);
        form.full(titled("Opzioni", opts));

        for (AbstractButton b : new AbstractButton[] {decSrcMain, decSrcAll, decSrcNone, decResFull, decResManifest, decResNone}) {
            b.addActionListener(e -> updateDecodeOptionState());
        }

        decRunButton.addActionListener(this::onDecode);
        decOpenButton.setEnabled(false);
        decOpenButton.addActionListener(e -> openInExplorer(lastDecodedDir));
        form.full(actions(decOpenButton, decRunButton));
        return form.finish();
    }

    private JComponent buildBuildTab() {
        Form form = new Form();
        form.row("Cartella progetto:", bldDirField, browseButton(bldDirField, false, null));
        form.row("APK di output:", bldOutField, browseButton(bldOutField, true, "apk"));
        form.hint("Lascia vuoto per usare <cartella>/dist/<nome>.apk");
        form.row("aapt2 personalizzato:", bldAaptField, browseButton(bldAaptField, true, null));
        form.hint("Opzionale: lascia vuoto per usare l'aapt2 incluso.");

        JPanel opts = new JPanel(new GridLayout(0, 2, 8, 2));
        opts.add(bldForce);
        opts.add(bldNoCrunch);
        opts.add(bldDebuggable);
        opts.add(bldNetSecConf);
        opts.add(bldCopyOriginal);
        opts.add(bldNoApk);
        form.full(titled("Opzioni", opts));
        bldCopyOriginal.addActionListener(e -> updateBuildOptionState());
        bldNoApk.addActionListener(e -> updateBuildOptionState());

        JLabel note = new JLabel("<html><i>Nota: l'APK generato non è firmato. Prima di installarlo usa "
            + "<b>zipalign</b> e <b>apksigner</b> dell'Android SDK.</i></html>");
        form.full(note);

        bldRunButton.addActionListener(this::onBuild);
        bldOpenButton.setEnabled(false);
        bldOpenButton.addActionListener(e -> openInExplorer(lastBuildDir));
        form.full(actions(bldOpenButton, bldRunButton));
        return form.finish();
    }

    private JComponent buildFrameworkTab() {
        Form form = new Form();
        form.row("APK framework:", fwApkField, browseButton(fwApkField, true, "apk"));
        form.hint("Es. framework-res.apk estratto dal dispositivo (Samsung, Xiaomi, ...). Il tag si imposta in Impostazioni.");
        fwInstallButton.addActionListener(e -> onInstallFramework());
        form.full(actions(fwInstallButton));

        JList<String> list = new JList<>(fwListModel);
        JScrollPane scroll = new JScrollPane(list);
        scroll.setPreferredSize(new Dimension(200, 140));
        JPanel installed = new JPanel(new BorderLayout(0, 6));
        installed.add(scroll, BorderLayout.CENTER);
        fwListButton.addActionListener(e -> onListFrameworks());
        fwCleanButton.addActionListener(e -> onCleanFrameworks());
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(fwAll, BorderLayout.WEST);
        bottom.add(flow(fwListButton, fwCleanButton), BorderLayout.EAST);
        installed.add(bottom, BorderLayout.SOUTH);
        form.fill(titled("Framework installati", installed));
        return form.finish();
    }

    private JComponent buildSettingsTab() {
        Form form = new Form();
        form.row("Thread paralleli:", jobsSpinner, null);
        form.row("Cartella framework:", frameDirField, browseButton(frameDirField, false, null));
        form.hint("Lascia vuoto per la cartella predefinita di Apktool.");
        form.row("Tag framework:", frameTagField, null);
        form.full(verboseCheck);

        JButton save = new JButton("Salva impostazioni");
        save.addActionListener(e -> {
            saveSettings();
            statusLabel.setText("Impostazioni salvate.");
        });
        form.full(actions(save));
        return form.finish();
    }

    private JComponent buildLogPanel() {
        logPane.setEditable(false);
        logPane.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane scroll = new JScrollPane(logPane);

        JButton clear = new JButton("Pulisci log");
        clear.addActionListener(e -> logPane.setText(""));

        JPanel panel = new JPanel(new BorderLayout(0, 4));
        JPanel header = new JPanel(new BorderLayout());
        header.add(new JLabel("Output"), BorderLayout.WEST);
        header.add(clear, BorderLayout.EAST);
        panel.add(header, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    // ------------------------------------------------------------------ Option state

    private void updateDecodeOptionState() {
        boolean src = !decSrcNone.isSelected();
        boolean fullRes = decResFull.isSelected();
        decNoDebugInfo.setEnabled(src);
        decResolveMode.setEnabled(fullRes);
        decKeepBroken.setEnabled(fullRes);
        decIgnoreRaw.setEnabled(!decResNone.isSelected());
    }

    private void updateBuildOptionState() {
        boolean copyOriginal = bldCopyOriginal.isSelected();
        bldDebuggable.setEnabled(!copyOriginal);
        bldNetSecConf.setEnabled(!copyOriginal);
        bldOutField.setEnabled(!bldNoApk.isSelected());
    }

    private void suggestDecodeOutput() {
        String apk = decApkField.getText().trim();
        if (apk.isEmpty()) {
            return;
        }
        Path apkFile = Paths.get(apk);
        Path name = apkFile.getFileName();
        if (name == null) {
            return;
        }
        String fileName = name.toString();
        String outName = fileName.toLowerCase().endsWith(".apk")
            ? fileName.substring(0, fileName.length() - 4).trim()
            : fileName + ".out";
        decOutField.setText(apkFile.resolveSibling(outName).toString());
    }

    // ------------------------------------------------------------------ Actions

    private void onDecode(ActionEvent e) {
        String apk = decApkField.getText().trim();
        String out = decOutField.getText().trim();
        if (apk.isEmpty() || !Files.isRegularFile(Paths.get(apk))) {
            error("Seleziona un file APK esistente.");
            return;
        }
        if (out.isEmpty()) {
            error("Indica la cartella di output.");
            return;
        }
        Path apkFile = Paths.get(apk);
        Path outDir = Paths.get(out);

        Config config = newConfig();
        if (decForce.isSelected()) {
            config.setForced(true);
        } else if (Files.exists(outDir)) {
            int choice = JOptionPane.showConfirmDialog(this,
                "La cartella\n" + outDir + "\nesiste già. Vuoi sovrascriverla?",
                "Cartella esistente", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (choice != JOptionPane.YES_OPTION) {
                return;
            }
            config.setForced(true);
        }

        if (decSrcAll.isSelected()) {
            config.setDecodeSources(Config.DecodeSources.FULL);
        } else if (decSrcNone.isSelected()) {
            config.setDecodeSources(Config.DecodeSources.NONE);
        }
        if (decNoDebugInfo.isEnabled() && decNoDebugInfo.isSelected()) {
            config.setBaksmaliDebugMode(false);
        }
        if (decResNone.isSelected()) {
            config.setDecodeResources(Config.DecodeResources.NONE);
        } else if (decResManifest.isSelected()) {
            config.setDecodeResources(Config.DecodeResources.ONLY_MANIFEST);
        } else {
            String mode = (String) decResolveMode.getSelectedItem();
            if ("greedy".equals(mode)) {
                config.setDecodeResolve(Config.DecodeResolve.GREEDY);
            } else if ("lazy".equals(mode)) {
                config.setDecodeResolve(Config.DecodeResolve.LAZY);
            }
            if (decKeepBroken.isSelected()) {
                config.setKeepBrokenResources(true);
            }
        }
        if (decIgnoreRaw.isEnabled() && decIgnoreRaw.isSelected()) {
            config.setIgnoreRawValues(true);
        }
        if (decMatchOriginal.isSelected()) {
            config.setAnalysisMode(true);
        }
        if (decNoAssets.isSelected()) {
            config.setDecodeAssets(Config.DecodeAssets.NONE);
        }

        runTask("Decompilazione di " + apkFile.getFileName(), () -> {
            new ApkDecoder(apkFile, config).decode(outDir);
            return outDir;
        }, result -> {
            lastDecodedDir = outDir;
            decOpenButton.setEnabled(true);
            bldDirField.setText(outDir.toString());
            appendLog(Level.INFO, "Decompilazione completata: " + outDir);
        });
    }

    private void onBuild(ActionEvent e) {
        String dir = bldDirField.getText().trim();
        if (dir.isEmpty() || !Files.isRegularFile(Paths.get(dir).resolve("apktool.yml"))) {
            error("Seleziona una cartella decompilata da Apktool (deve contenere apktool.yml).");
            return;
        }
        Path apkDir = Paths.get(dir);

        Config config = newConfig();
        config.setForced(bldForce.isSelected());
        config.setNoApk(bldNoApk.isSelected());
        config.setNoCrunch(bldNoCrunch.isSelected());
        config.setCopyOriginal(bldCopyOriginal.isSelected());
        if (!bldCopyOriginal.isSelected()) {
            config.setDebuggable(bldDebuggable.isSelected());
            config.setNetSecConf(bldNetSecConf.isSelected());
        }
        String aapt = bldAaptField.getText().trim();

        Path outFile = null;
        String out = bldOutField.getText().trim();
        if (!bldNoApk.isSelected() && !out.isEmpty()) {
            outFile = Paths.get(out);
        }
        final Path finalOut = outFile;
        final Path resultDir = outFile != null && outFile.getParent() != null
            ? outFile.getParent() : apkDir.resolve("dist");

        runTask("Ricompilazione di " + apkDir.getFileName(), () -> {
            if (!aapt.isEmpty()) {
                if (AaptManager.getBinaryVersion(Paths.get(aapt)) == 1) {
                    throw new AndrolibException("Legacy aapt is no longer supported.");
                }
                config.setAaptBinary(aapt);
            }
            new ApkBuilder(apkDir, config).build(finalOut);
            return resultDir;
        }, result -> {
            lastBuildDir = resultDir;
            bldOpenButton.setEnabled(true);
            appendLog(Level.INFO, "Ricompilazione completata. Output in: " + resultDir);
        });
    }

    private void onInstallFramework() {
        String apk = fwApkField.getText().trim();
        if (apk.isEmpty() || !Files.isRegularFile(Paths.get(apk))) {
            error("Seleziona un APK di framework esistente.");
            return;
        }
        Path apkFile = Paths.get(apk);
        Config config = newConfig();
        runTask("Installazione framework " + apkFile.getFileName(), () -> {
            new Framework(config).install(apkFile);
            return null;
        }, result -> onListFrameworks());
    }

    private void onListFrameworks() {
        Config config = newConfig();
        if (fwAll.isSelected()) {
            config.setForced(true);
            config.setFrameworkTag(null);
        }
        runTask("Elenco framework", () -> {
            List<String> names = new ArrayList<>();
            Framework framework = new Framework(config);
            for (Path file : framework.listDirectory()) {
                names.add(String.valueOf(file.getFileName()));
            }
            appendLog(Level.INFO, "Cartella framework: " + framework.getDirectory());
            return names;
        }, result -> {
            fwListModel.clear();
            @SuppressWarnings("unchecked")
            List<String> names = (List<String>) result;
            for (String name : names) {
                fwListModel.addElement(name);
            }
            if (names.isEmpty()) {
                fwListModel.addElement("(nessun framework installato)");
            }
        });
    }

    private void onCleanFrameworks() {
        int choice = JOptionPane.showConfirmDialog(this,
            fwAll.isSelected()
                ? "Eliminare TUTTI i framework installati?"
                : "Eliminare i framework installati (con il tag corrente)?",
            "Conferma", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) {
            return;
        }
        Config config = newConfig();
        if (fwAll.isSelected()) {
            config.setForced(true);
            config.setFrameworkTag(null);
        }
        runTask("Pulizia framework", () -> {
            new Framework(config).cleanDirectory();
            return null;
        }, result -> onListFrameworks());
    }

    private Config newConfig() {
        Config config = new Config(version);
        config.setJobs((Integer) jobsSpinner.getValue());
        String frameDir = frameDirField.getText().trim();
        if (!frameDir.isEmpty()) {
            config.setFrameworkDirectory(frameDir);
        }
        String tag = frameTagField.getText().trim();
        if (!tag.isEmpty()) {
            config.setFrameworkTag(tag);
        }
        config.setVerbose(verboseCheck.isSelected());
        Logger.getLogger("").setLevel(verboseCheck.isSelected() ? Level.ALL : Level.INFO);
        return config;
    }

    // ------------------------------------------------------------------ Background work

    private interface Task {
        Object run() throws Exception;
    }

    private interface Done {
        void accept(Object result);
    }

    private void runTask(String title, Task task, Done done) {
        setBusy(true, title + "...");
        appendLog(Level.INFO, "=== " + title + " ===");
        long start = System.currentTimeMillis();

        new SwingWorker<Object, Void>() {
            @Override
            protected Object doInBackground() throws Exception {
                return task.run();
            }

            @Override
            protected void done() {
                setBusy(false, null);
                try {
                    Object result = get();
                    double secs = (System.currentTimeMillis() - start) / 1000.0;
                    statusLabel.setText(String.format("%s: completato in %.1f s", title, secs));
                    done.accept(result);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (java.util.concurrent.ExecutionException ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    String message = cause.getMessage() != null ? cause.getMessage() : cause.toString();
                    appendLog(Level.SEVERE, message);
                    Throwable inner = cause.getCause();
                    while (inner != null) {
                        appendLog(Level.SEVERE, "  causato da: " + inner);
                        inner = inner.getCause();
                    }
                    statusLabel.setText(title + ": errore");
                    error(title + " non riuscita:\n" + message);
                }
            }
        }.execute();
    }

    private void setBusy(boolean busy, String status) {
        for (JComponent c : busyDisabled) {
            c.setEnabled(!busy);
        }
        progressBar.setVisible(busy);
        progressBar.setIndeterminate(busy);
        setCursor(busy ? Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR) : Cursor.getDefaultCursor());
        if (status != null) {
            statusLabel.setText(status);
        }
    }

    // ------------------------------------------------------------------ Logging

    private void setupLogging() {
        LogManager.getLogManager().reset();
        Logger root = Logger.getLogger("");
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (isLoggable(record)) {
                    appendLog(record.getLevel(), getFormatter().formatMessage(record));
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        handler.setFormatter(new Formatter() {
            @Override
            public String format(LogRecord record) {
                return formatMessage(record);
            }
        });
        handler.setLevel(Level.ALL);
        root.addHandler(handler);
        root.setLevel(Level.INFO);
    }

    private void appendLog(Level level, String message) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> appendLog(level, message));
            return;
        }
        String prefix;
        Color color;
        int value = level.intValue();
        if (value >= Level.SEVERE.intValue()) {
            prefix = "E: ";
            color = new Color(0xC6, 0x28, 0x28);
        } else if (value >= Level.WARNING.intValue()) {
            prefix = "W: ";
            color = new Color(0xB2, 0x6A, 0x00);
        } else if (value >= Level.INFO.intValue()) {
            prefix = "I: ";
            color = UIManager.getColor("TextPane.foreground");
        } else {
            prefix = "D: ";
            color = Color.GRAY;
        }
        SimpleAttributeSet attrs = new SimpleAttributeSet();
        if (color != null) {
            StyleConstants.setForeground(attrs, color);
        }
        StyledDocument doc = logPane.getStyledDocument();
        try {
            doc.insertString(doc.getLength(), prefix + message + "\n", attrs);
        } catch (BadLocationException ignored) {
        }
        logPane.setCaretPosition(doc.getLength());
    }

    // ------------------------------------------------------------------ Helpers

    private void error(String message) {
        JOptionPane.showMessageDialog(this, message, "Apktool", JOptionPane.ERROR_MESSAGE);
    }

    private void openInExplorer(Path dir) {
        if (dir == null) {
            return;
        }
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(dir.toFile());
            } else {
                error("Apertura cartelle non supportata su questo sistema:\n" + dir);
            }
        } catch (Exception ex) {
            error("Impossibile aprire " + dir + ":\n" + ex.getMessage());
        }
    }

    private JButton browseButton(JTextField target, boolean files, String extension) {
        JButton button = new JButton("Sfoglia...");
        button.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            String current = target.getText().trim();
            File start = current.isEmpty() ? new File(PREFS.get("lastDir", System.getProperty("user.home")))
                                           : new File(current);
            chooser.setCurrentDirectory(start.isDirectory() ? start : start.getParentFile());
            if (!current.isEmpty()) {
                chooser.setSelectedFile(start);
            }
            if (files) {
                chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
                if (extension != null) {
                    chooser.setFileFilter(new FileNameExtensionFilter("File ." + extension, extension));
                }
            } else {
                chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            }
            boolean save = target == bldOutField;
            int result = save ? chooser.showSaveDialog(this) : chooser.showOpenDialog(this);
            if (result == JFileChooser.APPROVE_OPTION) {
                File selected = chooser.getSelectedFile();
                if (save && extension != null && !selected.getName().toLowerCase().endsWith("." + extension)) {
                    selected = new File(selected.getPath() + "." + extension);
                }
                target.setText(selected.getPath());
                File parent = selected.isDirectory() ? selected : selected.getParentFile();
                if (parent != null) {
                    PREFS.put("lastDir", parent.getPath());
                }
            }
        });
        return button;
    }

    private void installDropSupport() {
        setTransferHandler(new TransferHandler() {
            @Override
            public boolean canImport(TransferSupport support) {
                return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
            }

            @Override
            public boolean importData(TransferSupport support) {
                try {
                    @SuppressWarnings("unchecked")
                    List<File> files = (List<File>) support.getTransferable()
                        .getTransferData(DataFlavor.javaFileListFlavor);
                    if (files.isEmpty()) {
                        return false;
                    }
                    File file = files.get(0);
                    if (file.isDirectory()) {
                        bldDirField.setText(file.getPath());
                        tabs.setSelectedIndex(1);
                    } else {
                        decApkField.setText(file.getPath());
                        tabs.setSelectedIndex(0);
                    }
                    return true;
                } catch (Exception ex) {
                    return false;
                }
            }
        });
    }

    private void loadSettings() {
        jobsSpinner.setValue(PREFS.getInt("jobs", (Integer) jobsSpinner.getValue()));
        frameDirField.setText(PREFS.get("frameDir", ""));
        frameTagField.setText(PREFS.get("frameTag", ""));
        verboseCheck.setSelected(PREFS.getBoolean("verbose", false));
    }

    private void saveSettings() {
        PREFS.putInt("jobs", (Integer) jobsSpinner.getValue());
        PREFS.put("frameDir", frameDirField.getText().trim());
        PREFS.put("frameTag", frameTagField.getText().trim());
        PREFS.putBoolean("verbose", verboseCheck.isSelected());
    }

    private static String loadVersion() {
        Properties props = new Properties();
        try (InputStream in = ApktoolGui.class.getResourceAsStream("/apktool.properties")) {
            if (in != null) {
                props.load(in);
            }
        } catch (Exception ignored) {
        }
        return props.getProperty("application.version", "(unknown)");
    }

    private static JPanel flow(Component... components) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        for (Component c : components) {
            panel.add(c);
        }
        return panel;
    }

    private static JPanel actions(Component... components) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        for (Component c : components) {
            panel.add(c);
        }
        return panel;
    }

    private static JComponent titled(String title, JComponent content) {
        content.setBorder(BorderFactory.createCompoundBorder(
            new TitledBorder(title), new EmptyBorder(2, 6, 4, 6)));
        return content;
    }

    /** Small GridBagLayout helper for label/field/button forms. */
    private static final class Form {
        private final JPanel panel = new JPanel(new GridBagLayout());
        private int row = 0;
        private boolean filled = false;

        Form() {
            panel.setBorder(new EmptyBorder(10, 10, 10, 10));
        }

        void row(String label, JComponent field, JComponent button) {
            GridBagConstraints c = base();
            c.gridx = 0;
            c.weightx = 0;
            c.fill = GridBagConstraints.NONE;
            c.anchor = GridBagConstraints.LINE_END;
            panel.add(new JLabel(label), c);

            c = base();
            c.gridx = 1;
            c.weightx = 1;
            c.gridwidth = button == null ? 2 : 1;
            if (field instanceof JSpinner) {
                c.fill = GridBagConstraints.NONE;
                c.anchor = GridBagConstraints.LINE_START;
            }
            panel.add(field, c);

            if (button != null) {
                c = base();
                c.gridx = 2;
                c.weightx = 0;
                panel.add(button, c);
            }
            row++;
        }

        void hint(String text) {
            JLabel label = new JLabel(text);
            label.setFont(label.getFont().deriveFont(Font.ITALIC, label.getFont().getSize2D() - 1f));
            label.setForeground(Color.GRAY);
            GridBagConstraints c = base();
            c.gridx = 1;
            c.gridwidth = 2;
            c.insets = new Insets(0, 4, 4, 4);
            panel.add(label, c);
            row++;
        }

        void full(JComponent component) {
            GridBagConstraints c = base();
            c.gridx = 0;
            c.gridwidth = 3;
            c.weightx = 1;
            panel.add(component, c);
            row++;
        }

        void fill(JComponent component) {
            GridBagConstraints c = base();
            c.gridx = 0;
            c.gridwidth = 3;
            c.weightx = 1;
            c.weighty = 1;
            c.fill = GridBagConstraints.BOTH;
            panel.add(component, c);
            row++;
            filled = true;
        }

        JComponent finish() {
            if (!filled) {
                GridBagConstraints c = base();
                c.weighty = 1;
                panel.add(Box.createGlue(), c);
            }
            JScrollPane scroll = new JScrollPane(panel);
            scroll.setBorder(null);
            scroll.getVerticalScrollBar().setUnitIncrement(16);
            return scroll;
        }

        private GridBagConstraints base() {
            GridBagConstraints c = new GridBagConstraints();
            c.gridy = row;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.insets = new Insets(3, 4, 3, 4);
            return c;
        }
    }

    /** DocumentListener that runs a callback on every change. */
    private static final class SimpleDocListener implements javax.swing.event.DocumentListener {
        private final Runnable callback;

        SimpleDocListener(Runnable callback) {
            this.callback = callback;
        }

        @Override
        public void insertUpdate(javax.swing.event.DocumentEvent e) {
            callback.run();
        }

        @Override
        public void removeUpdate(javax.swing.event.DocumentEvent e) {
            callback.run();
        }

        @Override
        public void changedUpdate(javax.swing.event.DocumentEvent e) {
            callback.run();
        }
    }

    public static void main(String[] args) {
        System.setProperty("awt.useSystemAAFontSettings", "on");
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
            }
            new ApktoolGui().setVisible(true);
        });
    }
}
