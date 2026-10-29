import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Random;

public final class StudentSpinWheel extends JFrame {
    private static final String EXHAUSTED = "Everyone has had a turn! Reset to start again.";
    private static final int SPIN_MILLIS = 5000;
    private final RosterState state = new RosterState(new Random());
    private final WheelPanel wheel = new WheelPanel();
    private final JTextField newName = new JTextField();
    private final JButton add = new JButton("Add Name");
    private final JButton spin = new JButton("Spin");
    private final JButton reset = new JButton("Reset");
    private final JCheckBox remove = new JCheckBox("Remove selected students", true);
    private final JLabel remaining = new JLabel();
    private final JLabel status = new JLabel("Ready to spin.");
    private final JTextArea result = new JTextArea("Who goes next?");
    private final DefaultListModel<String> history = new DefaultListModel<>();
    private final Timer timer;
    private long startedAt;
    private double startRotation;
    private double destination;
    private int winnerIndex;

    public StudentSpinWheel() {
        super("Student Spin Wheel");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        timer = new Timer(16, event -> animate());
        timer.setCoalesce(true);
        JPanel content = new JPanel(new BorderLayout(16, 12));
        content.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        JLabel title = new JLabel("Student Spin Wheel");
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 28));
        content.add(title, BorderLayout.NORTH);

        JPanel wheelArea = new JPanel(new BorderLayout(8, 8));
        wheelArea.add(wheel, BorderLayout.CENTER);
        result.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 26));
        result.setEditable(false);
        result.setFocusable(false);
        result.setLineWrap(true);
        result.setWrapStyleWord(true);
        result.setRows(2);
        result.setForeground(new Color(0x172D59));
        result.setOpaque(false);
        result.getAccessibleContext().setAccessibleName("Spin result");
        wheelArea.add(new JScrollPane(result), BorderLayout.SOUTH);
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, wheelArea, controls());
        split.setResizeWeight(0.72);
        split.setBorder(null);
        content.add(split, BorderLayout.CENTER);
        setContentPane(content);

        add.addActionListener(event -> addName());
        newName.addActionListener(event -> addName());
        reset.addActionListener(event -> {
            state.resetTurns();
            refreshRoster();
        });
        spin.addActionListener(event -> startSpin());
        wheel.setNames(state.activeNames());
        updateControls();
        setMinimumSize(new Dimension(850, 650));
        setSize(1100, 800);
        setLocationRelativeTo(null);
    }

    private JPanel controls() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setMinimumSize(new Dimension(280, 400));
        panel.setPreferredSize(new Dimension(300, 650));
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(5, 6, 5, 6);
        JLabel rosterLabel = new JLabel("New student name");
        rosterLabel.setLabelFor(newName);
        panel.add(rosterLabel, c);
        newName.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 16));
        newName.setMargin(new Insets(8, 8, 8, 8));
        c.gridy++;
        panel.add(newName, c);
        c.gridy++;
        panel.add(add, c);
        c.gridy++;
        panel.add(remove, c);
        spin.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 24));
        spin.setBackground(new Color(0x172D59));
        spin.setForeground(Color.WHITE);
        spin.setOpaque(true);
        c.gridy++;
        panel.add(spin, c);
        reset.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
        reset.setToolTipText("Restore only the premade students, remove temporary names, and clear all turns");
        c.gridy++;
        panel.add(reset, c);
        c.gridy++;
        panel.add(remaining, c);
        c.gridy++;
        panel.add(status, c);
        c.gridy++;
        JList<String> selected = new JList<>(history);
        selected.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));
        JLabel selectedLabel = new JLabel("Already selected (in order)");
        selectedLabel.setLabelFor(selected);
        panel.add(selectedLabel, c);
        c.gridy++;
        c.weighty = 1;
        c.fill = GridBagConstraints.BOTH;
        panel.add(new JScrollPane(selected), c);
        return panel;
    }

    private void addName() {
        if (state.isSpinning()) {
            status.setText("Please wait for the wheel to stop.");
            return;
        }
        try {
            state.addName(newName.getText());
            wheel.setNames(state.activeNames());
            wheel.setRotation(0);
            newName.setText("");
            status.setText("Student added. Ready to spin.");
            updateControls();
        } catch (IllegalArgumentException exception) {
            status.setText(exception.getMessage());
        }
    }

    private void refreshRoster() {
        wheel.setNames(state.activeNames());
        wheel.setRotation(0);
        history.clear();
        result.setText("Who goes next?");
        result.setCaretPosition(0);
        status.setText("Ready to spin.");
        updateControls();
    }

    private void startSpin() {
        // The previous winning slice stays visible until this next spin begins.
        wheel.setNames(state.activeNames());
        winnerIndex = state.beginSpin();
        startRotation = WheelMath.normalize(wheel.getRotation());
        destination = WheelMath.spinDestination(startRotation, winnerIndex, state.activeNames().size());
        startedAt = System.nanoTime();
        result.setText("Spinning...");
        status.setText("Spinning - good luck!");
        updateControls();
        timer.start();
    }

    private void animate() {
        double progress = (System.nanoTime() - startedAt) / (SPIN_MILLIS * 1_000_000.0);
        wheel.setRotation(WheelMath.easedRotation(startRotation, destination, progress));
        if (progress >= 1) {
            timer.stop();
            wheel.setRotation(WheelMath.normalize(destination));
            wheel.highlight(winnerIndex);
            String winner = state.finishSpin(remove.isSelected());
            history.addElement(history.size() + 1 + ". " + winner);
            result.setText(winner + " goes next!");
            result.setCaretPosition(0);
            status.setText(state.activeNames().isEmpty() ? "All turns complete." : "Ready to spin.");
            updateControls();
        }
    }

    private void updateControls() {
        boolean idle = !state.isSpinning();
        int count = state.activeNames().size();
        spin.setEnabled(idle && count > 0);
        add.setEnabled(idle);
        newName.setEnabled(idle);
        reset.setEnabled(idle);
        remove.setEnabled(idle);
        remaining.setText("Remaining students: " + count);
        // Keep the final winner announcement visible alongside the exhaustion message.
        spin.setToolTipText(count == 0 ? EXHAUSTED : "Spin for the next student");
        if (idle && count == 0) {
            status.setText("<html>Everyone has had a turn!<br>Reset to start again.</html>");
        }
    }
}
