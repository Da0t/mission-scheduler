package dev.datnguyen.missionscheduler.app;

import dev.datnguyen.missionscheduler.io.MissionParser;
import dev.datnguyen.missionscheduler.model.Mission;
import dev.datnguyen.missionscheduler.planning.MissionPlan;
import dev.datnguyen.missionscheduler.planning.MissionPlanner;
import dev.datnguyen.missionscheduler.planning.ScheduledTask;
import dev.datnguyen.missionscheduler.simulation.MissionSimulator;
import dev.datnguyen.missionscheduler.simulation.SimulationEvent;
import dev.datnguyen.missionscheduler.simulation.SimulationReport;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.JTableHeader;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

public final class MissionSchedulerApp {
    private static final Color NAVY = new Color(12, 24, 48);
    private static final Color BLUE = new Color(38, 104, 216);
    private static final Color SURFACE = new Color(244, 247, 251);
    private static final Color MUTED = new Color(95, 107, 128);

    private final JFrame frame = new JFrame("Mission Scheduler");
    private final MissionTableModel tableModel = new MissionTableModel();
    private final JTable taskTable = new JTable(tableModel);
    private final JTextArea detailsArea = new JTextArea();
    private final JTextArea eventLog = new JTextArea();
    private final JLabel taskCountValue = metricValue();
    private final JLabel durationValue = metricValue();
    private final JLabel criticalValue = metricValue();
    private final JLabel sourceLabel = new JLabel("No mission loaded");
    private final JLabel statusLabel = new JLabel("Ready");
    private final JButton openButton = primaryButton("Open Mission");
    private final JButton demoButton = secondaryButton("Load Demo");
    private final JButton simulateButton = primaryButton("Run Simulation");
    private final JButton stopButton = secondaryButton("Stop");
    private final JComboBox<SpeedChoice> speedSelector = new JComboBox<>(SpeedChoice.values());

    private Mission currentMission;
    private MissionPlan currentPlan;
    private SwingWorker<SimulationReport, SimulationEvent> simulationWorker;

    private MissionSchedulerApp(Path initialMission) {
        configureFrame();
        configureActions();
        if (initialMission == null) {
            loadDemoMission();
        } else {
            loadMissionFile(initialMission);
        }
    }

    public static void launch(Path initialMission) {
        if (GraphicsEnvironment.isHeadless()) {
            throw new IllegalStateException("desktop UI is unavailable in a headless environment");
        }
        System.setProperty("apple.awt.application.name", "Mission Scheduler");
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ReflectiveOperationException | javax.swing.UnsupportedLookAndFeelException ignored) {
            // Swing's cross-platform look and feel remains available.
        }
        SwingUtilities.invokeLater(() -> {
            MissionSchedulerApp application = new MissionSchedulerApp(initialMission);
            application.frame.setVisible(true);
        });
    }

    private void configureFrame() {
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setIconImage(createAppIcon());
        frame.setMinimumSize(new Dimension(980, 640));
        frame.setSize(1_220, 760);
        frame.setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(SURFACE);
        root.add(createHeader(), BorderLayout.NORTH);
        root.add(createWorkspace(), BorderLayout.CENTER);
        root.add(createStatusBar(), BorderLayout.SOUTH);
        frame.setContentPane(root);
    }

    private JPanel createHeader() {
        JPanel header = new JPanel(new BorderLayout(20, 0));
        header.setBackground(NAVY);
        header.setBorder(BorderFactory.createEmptyBorder(18, 24, 18, 24));

        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("MISSION SCHEDULER");
        title.setForeground(Color.WHITE);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 24f));
        JLabel subtitle = new JLabel("Dependency planning and critical-path simulation");
        subtitle.setForeground(new Color(180, 195, 220));
        subtitle.setFont(subtitle.getFont().deriveFont(13f));
        titles.add(title);
        titles.add(Box.createVerticalStrut(4));
        titles.add(subtitle);
        header.add(titles, BorderLayout.WEST);

        JPanel actions = new JPanel();
        actions.setOpaque(false);
        actions.add(openButton);
        actions.add(demoButton);
        actions.add(Box.createHorizontalStrut(10));
        JLabel speedLabel = new JLabel("Speed");
        speedLabel.setForeground(Color.WHITE);
        actions.add(speedLabel);
        speedSelector.setSelectedItem(SpeedChoice.NORMAL);
        actions.add(speedSelector);
        actions.add(simulateButton);
        stopButton.setEnabled(false);
        actions.add(stopButton);
        header.add(actions, BorderLayout.EAST);
        return header;
    }

    private JPanel createWorkspace() {
        JPanel workspace = new JPanel(new BorderLayout(0, 14));
        workspace.setOpaque(false);
        workspace.setBorder(BorderFactory.createEmptyBorder(16, 20, 12, 20));

        JPanel summary = new JPanel(new BorderLayout(14, 0));
        summary.setOpaque(false);
        sourceLabel.setForeground(MUTED);
        sourceLabel.setFont(sourceLabel.getFont().deriveFont(Font.BOLD, 13f));
        summary.add(sourceLabel, BorderLayout.NORTH);

        JPanel metrics = new JPanel(new GridLayout(1, 3, 12, 0));
        metrics.setOpaque(false);
        metrics.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        metrics.add(metricCard("TASKS", taskCountValue));
        metrics.add(metricCard("EARLIEST COMPLETION", durationValue));
        metrics.add(metricCard("CRITICAL TASKS", criticalValue));
        summary.add(metrics, BorderLayout.CENTER);
        workspace.add(summary, BorderLayout.NORTH);

        configureTable();
        JScrollPane tableScroll = new JScrollPane(taskTable);
        tableScroll.setBorder(BorderFactory.createLineBorder(new Color(218, 224, 234)));

        JSplitPane inspector = new JSplitPane(
                JSplitPane.VERTICAL_SPLIT,
                titledPanel("TASK DETAILS", createDetailsArea()),
                titledPanel("SIMULATION EVENTS", createEventLog()));
        inspector.setResizeWeight(0.38);
        inspector.setDividerSize(7);
        inspector.setBorder(null);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, tableScroll, inspector);
        split.setResizeWeight(0.70);
        split.setDividerSize(8);
        split.setBorder(null);
        workspace.add(split, BorderLayout.CENTER);
        return workspace;
    }

    private JPanel createStatusBar() {
        JPanel status = new JPanel(new BorderLayout());
        status.setBackground(Color.WHITE);
        status.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(220, 225, 234)),
                BorderFactory.createEmptyBorder(8, 20, 8, 20)));
        statusLabel.setForeground(MUTED);
        status.add(statusLabel, BorderLayout.WEST);
        JLabel hint = new JLabel("Critical tasks have zero scheduling slack");
        hint.setForeground(MUTED);
        status.add(hint, BorderLayout.EAST);
        return status;
    }

    private void configureTable() {
        taskTable.setRowHeight(31);
        taskTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        taskTable.setShowVerticalLines(false);
        taskTable.setGridColor(new Color(231, 235, 242));
        taskTable.setFillsViewportHeight(true);
        taskTable.setDefaultRenderer(Object.class, new MissionTableRenderer());
        taskTable.getColumnModel().getColumn(0).setPreferredWidth(170);
        taskTable.getColumnModel().getColumn(6).setPreferredWidth(90);

        JTableHeader header = taskTable.getTableHeader();
        header.setBackground(new Color(230, 235, 244));
        header.setForeground(NAVY);
        header.setFont(header.getFont().deriveFont(Font.BOLD, 12f));
        header.setPreferredSize(new Dimension(header.getWidth(), 34));

        taskTable.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                showSelectedTask();
            }
        });
    }

    private JScrollPane createDetailsArea() {
        detailsArea.setEditable(false);
        detailsArea.setLineWrap(true);
        detailsArea.setWrapStyleWord(true);
        detailsArea.setBackground(Color.WHITE);
        detailsArea.setForeground(new Color(35, 44, 60));
        detailsArea.setFont(detailsArea.getFont().deriveFont(13f));
        detailsArea.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        JScrollPane scroll = new JScrollPane(detailsArea);
        scroll.setBorder(null);
        return scroll;
    }

    private JScrollPane createEventLog() {
        eventLog.setEditable(false);
        eventLog.setBackground(new Color(17, 25, 39));
        eventLog.setForeground(new Color(213, 222, 238));
        eventLog.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        eventLog.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        JScrollPane scroll = new JScrollPane(eventLog);
        scroll.setBorder(null);
        return scroll;
    }

    private void configureActions() {
        openButton.addActionListener(event -> chooseMissionFile());
        demoButton.addActionListener(event -> loadDemoMission());
        simulateButton.addActionListener(event -> startSimulation());
        stopButton.addActionListener(event -> stopSimulation());
    }

    private void chooseMissionFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Open Mission Plan");
        chooser.setFileFilter(new FileNameExtensionFilter("Mission files (*.mission)", "mission"));
        if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            loadMissionFile(chooser.getSelectedFile().toPath());
        }
    }

    private void loadMissionFile(Path path) {
        try {
            setMission(new MissionParser().parse(path), path.toString());
        } catch (IOException | IllegalArgumentException exception) {
            showError("Could not load mission", exception);
        }
    }

    private void loadDemoMission() {
        try (InputStream input = MissionSchedulerApp.class.getResourceAsStream("/demo.mission")) {
            if (input == null) {
                throw new IOException("bundled demo.mission was not found");
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(input, StandardCharsets.UTF_8))) {
                setMission(new MissionParser().parseLines(reader.lines().toList(), "demo.mission"),
                        "Bundled demo mission");
            }
        } catch (IOException | IllegalArgumentException exception) {
            showError("Could not load demo", exception);
        }
    }

    private void setMission(Mission mission, String source) {
        stopSimulation();
        currentMission = mission;
        currentPlan = new MissionPlanner().plan(mission);
        tableModel.setPlan(currentPlan);
        sourceLabel.setText(source);
        taskCountValue.setText(Integer.toString(mission.size()));
        durationValue.setText(formatDuration(currentPlan.totalDurationMs()));
        criticalValue.setText(Integer.toString(currentPlan.criticalTasks().size()));
        eventLog.setText("");
        statusLabel.setText("Mission validated and planned successfully");
        simulateButton.setEnabled(true);
        if (tableModel.getRowCount() > 0) {
            taskTable.setRowSelectionInterval(0, 0);
        }
    }

    private void showSelectedTask() {
        int selectedRow = taskTable.getSelectedRow();
        if (selectedRow < 0 || currentPlan == null) {
            detailsArea.setText("");
            return;
        }
        int modelRow = taskTable.convertRowIndexToModel(selectedRow);
        ScheduledTask scheduled = tableModel.taskAt(modelRow);
        String dependencies = scheduled.task().dependencies().isEmpty()
                ? "None"
                : String.join(", ", scheduled.task().dependencies());
        detailsArea.setText("""
                %s

                %s

                Dependencies: %s
                Earliest start: %s
                Latest start: %s
                Scheduling slack: %s
                Critical path: %s
                """.formatted(
                scheduled.task().id(),
                scheduled.task().description(),
                dependencies,
                formatDuration(scheduled.earliestStartMs()),
                formatDuration(scheduled.latestStartMs()),
                formatDuration(scheduled.slackMs()),
                scheduled.critical() ? "Yes" : "No"));
        detailsArea.setCaretPosition(0);
    }

    private void startSimulation() {
        if (currentMission == null || (simulationWorker != null && !simulationWorker.isDone())) {
            return;
        }

        SpeedChoice speed = (SpeedChoice) speedSelector.getSelectedItem();
        if (speed == null) {
            speed = SpeedChoice.NORMAL;
        }
        double multiplier = speed.multiplier();
        tableModel.resetStates();
        eventLog.setText("");
        setSimulationControls(true);
        statusLabel.setText(String.format(Locale.ROOT, "Simulation running at %.1fx", multiplier));

        simulationWorker = new SwingWorker<>() {
            @Override
            protected SimulationReport doInBackground() throws Exception {
                return new MissionSimulator().simulate(
                        currentMission,
                        multiplier,
                        event -> publish(event));
            }

            @Override
            protected void process(List<SimulationEvent> events) {
                if (isCancelled()) {
                    return;
                }
                for (SimulationEvent event : events) {
                    tableModel.applyEvent(event);
                    eventLog.append(String.format(
                            Locale.ROOT,
                            "T+%8.2f ms  %-9s  %s%n",
                            event.elapsedNanos() / 1_000_000.0,
                            event.kind(),
                            event.taskId()));
                }
                eventLog.setCaretPosition(eventLog.getDocument().getLength());
            }

            @Override
            protected void done() {
                setSimulationControls(false);
                if (isCancelled()) {
                    statusLabel.setText("Simulation stopped");
                    return;
                }
                try {
                    SimulationReport report = get();
                    statusLabel.setText("Simulation complete in " + formatDuration(report.actualDuration()));
                } catch (CancellationException exception) {
                    statusLabel.setText("Simulation stopped");
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    statusLabel.setText("Simulation interrupted");
                } catch (ExecutionException exception) {
                    showError("Simulation failed", exception.getCause());
                }
            }
        };
        simulationWorker.execute();
    }

    private void stopSimulation() {
        if (simulationWorker != null && !simulationWorker.isDone()) {
            simulationWorker.cancel(true);
        }
    }

    private void setSimulationControls(boolean running) {
        openButton.setEnabled(!running);
        demoButton.setEnabled(!running);
        simulateButton.setEnabled(!running && currentMission != null);
        speedSelector.setEnabled(!running);
        stopButton.setEnabled(running);
    }

    private void showError(String title, Throwable throwable) {
        statusLabel.setText(title);
        JOptionPane.showMessageDialog(
                frame,
                throwable.getMessage(),
                title,
                JOptionPane.ERROR_MESSAGE);
    }

    private static JPanel metricCard(String labelText, JLabel value) {
        JPanel card = new JPanel();
        card.setBackground(Color.WHITE);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(222, 227, 236)),
                BorderFactory.createEmptyBorder(12, 16, 12, 16)));
        JLabel label = new JLabel(labelText);
        label.setForeground(MUTED);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 11f));
        card.add(label);
        card.add(Box.createVerticalStrut(5));
        card.add(value);
        return card;
    }

    private static JLabel metricValue() {
        JLabel label = new JLabel("—");
        label.setForeground(NAVY);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 22f));
        return label;
    }

    private static JPanel titledPanel(String title, JScrollPane content) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Color.WHITE);
        JLabel label = new JLabel(title);
        label.setOpaque(true);
        label.setBackground(new Color(230, 235, 244));
        label.setForeground(NAVY);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 11f));
        label.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        panel.add(label, BorderLayout.NORTH);
        panel.add(content, BorderLayout.CENTER);
        panel.setBorder(BorderFactory.createLineBorder(new Color(218, 224, 234)));
        return panel;
    }

    private static JButton primaryButton(String text) {
        JButton button = new JButton(text);
        button.setBackground(BLUE);
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        return button;
    }

    private static JButton secondaryButton(String text) {
        JButton button = new JButton(text);
        button.setFocusPainted(false);
        return button;
    }

    private static String formatDuration(long milliseconds) {
        if (milliseconds < 1_000) {
            return milliseconds + " ms";
        }
        return String.format(Locale.ROOT, "%.2f s", milliseconds / 1_000.0);
    }

    private static String formatDuration(java.time.Duration duration) {
        double milliseconds = duration.toNanos() / 1_000_000.0;
        if (milliseconds < 1_000) {
            return String.format(Locale.ROOT, "%.1f ms", milliseconds);
        }
        return String.format(Locale.ROOT, "%.2f s", milliseconds / 1_000.0);
    }

    private static Image createAppIcon() {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(NAVY);
        graphics.fillRoundRect(0, 0, 64, 64, 16, 16);
        graphics.setColor(Color.WHITE);
        graphics.fillOval(27, 10, 10, 31);
        graphics.fillPolygon(new int[]{27, 20, 17}, new int[]{24, 37, 39}, 3);
        graphics.fillPolygon(new int[]{37, 47, 37}, new int[]{24, 39, 37}, 3);
        graphics.setColor(new Color(255, 174, 66));
        graphics.fillPolygon(new int[]{29, 35, 32}, new int[]{42, 42, 56}, 3);
        graphics.dispose();
        return image;
    }

    private enum SpeedChoice {
        HALF("0.5x", 0.5),
        NORMAL("1x", 1),
        DOUBLE("2x", 2),
        FIVE("5x", 5),
        TEN("10x", 10);

        private final String label;
        private final double multiplier;

        SpeedChoice(String label, double multiplier) {
            this.label = label;
            this.multiplier = multiplier;
        }

        double multiplier() {
            return multiplier;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
