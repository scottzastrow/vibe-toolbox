import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class DesktopChecks {
    private static StudentSpinWheel app;
    private static JButton spin;
    private static JButton add;
    private static JButton reset;
    private static JCheckBox remove;
    private static JTextField newName;
    private static JTextArea result;
    private static JList<?> history;
    private static WheelPanel wheel;
    private static int assertions;

    public static void main(String[] args) throws Exception {
        try {
            SwingUtilities.invokeAndWait(() -> {
                app = new StudentSpinWheel();
                List<Component> components = descendants(app);
                spin = button(components, "Spin");
                add = button(components, "Add Name");
                reset = button(components, "Reset");
                remove = components.stream().filter(JCheckBox.class::isInstance)
                        .map(JCheckBox.class::cast).findFirst().orElseThrow();
                newName = components.stream().filter(JTextField.class::isInstance)
                        .map(JTextField.class::cast)
                        .findFirst().orElseThrow();
                result = components.stream().filter(JTextArea.class::isInstance)
                        .map(JTextArea.class::cast).filter(area -> !area.isEditable())
                        .findFirst().orElseThrow();
                history = components.stream().filter(JList.class::isInstance)
                        .map(JList.class::cast).findFirst().orElseThrow();
                wheel = components.stream().filter(WheelPanel.class::isInstance)
                        .map(WheelPanel.class::cast).findFirst().orElseThrow();
                truth(remove.isSelected(), "Removal enabled by default");
                truth(components.stream().filter(JLabel.class::isInstance)
                        .map(JLabel.class::cast).anyMatch(label ->
                                label.getText().equals("New student name")
                                        && label.getLabelFor() == newName),
                        "Name input has an associated visible label");
                newName.setText("  Taylor  ");
                add.doClick();
                truth(newName.getText().isEmpty(), "Button addition clears input");
                checkCount(11);
                newName.setText("Jordan");
                newName.postActionEvent();
                truth(newName.getText().isEmpty(), "Enter addition clears input");
                checkCount(12);
                checkSections(List.of("Scott", "Emma", "Liam", "Olivia", "Noah",
                        "Ava", "James", "Sophia", "Lucas", "Mia", "Taylor", "Jordan"));
                newName.setText(" \t ");
                add.doClick();
                truth(hasLabel("Please enter a new student name."), "Blank rejection is visible");
                truth(!newName.getText().isEmpty(), "Rejected input is preserved");
                newName.setText(" tAyLoR ");
                newName.postActionEvent();
                truth(hasLabel("Duplicate name: tAyLoR."), "Duplicate rejection is visible");
                checkCount(12);
                newName.setText("Unsubmitted name");
            });
            runSpin();
            SwingUtilities.invokeAndWait(() -> {
                String announcement = result.getText();
                String winner = announcement.substring(0, announcement.length() - " goes next!".length());
                truth(spin.isEnabled(), "Remaining students can spin");
                truth(history.getModel().getSize() == 1, "History displayed");
                Object selection = history.getModel().getElementAt(0);
                checkCount(11);
                truth(newName.getText().equals("Unsubmitted name"), "Spin preserves pending input");
                newName.setText(" " + winner.toUpperCase(java.util.Locale.ROOT) + " ");
                add.doClick();
                truth(hasLabel("Duplicate name:"), "Selected student cannot be added again");
                checkCount(11);
                newName.setText("Casey");
                add.doClick();
                checkCount(12);
                truth(history.getModel().getSize() == 1
                                && selection.equals(history.getModel().getElementAt(0)),
                        "Adding preserves displayed selections");
                truth(result.getText().equals(announcement), "Adding preserves winner announcement");
                List<String> eligible = new ArrayList<>(RosterState.DEFAULT_NAMES);
                eligible.add("Taylor");
                eligible.add("Jordan");
                eligible.remove(winner);
                eligible.add("Casey");
                checkSections(eligible);
                newName.setText("Unsubmitted name");
                reset.doClick();
                truth(newName.getText().equals("Unsubmitted name"), "Reset preserves pending input");
                truth(history.getModel().getSize() == 0, "Reset clears displayed history");
                truth(spin.isEnabled(), "Reset restores roster eligibility");
                truth(result.getText().equals("Who goes next?"), "Reset clears result");
                truth(remove.isSelected(), "Reset preserves enabled removal setting");
                checkCount(RosterState.DEFAULT_NAMES.size());
                checkSections(RosterState.DEFAULT_NAMES);
                newName.setText("Taylor");
                add.doClick();
                truth(newName.getText().isEmpty(), "Discarded temporary name can be added again");
                checkCount(11);
                reset.doClick();
                checkCount(RosterState.DEFAULT_NAMES.size());
                checkSections(RosterState.DEFAULT_NAMES);
                newName.setText("Unsubmitted name");
                remove.doClick();
            });
            runSpin();
            SwingUtilities.invokeAndWait(() -> {
                truth(spin.isEnabled(), "Removal off retains eligibility");
                truth(history.getModel().getSize() == 1, "Removal off records selection");
                checkCount(RosterState.DEFAULT_NAMES.size());
                reset.doClick();
                truth(!remove.isSelected(), "Reset preserves disabled removal setting");
                truth(newName.getText().equals("Unsubmitted name"),
                        "Reset with removal off preserves pending input");
                truth(history.getModel().getSize() == 0, "Reset with removal off clears history");
                truth(result.getText().equals("Who goes next?"), "Reset with removal off clears result");
                checkCount(RosterState.DEFAULT_NAMES.size());
                checkSections(RosterState.DEFAULT_NAMES);
            });
            System.out.println("PASS: " + assertions + " automated Swing assertions "
                    + "(adding by button/Enter, validation, equal sections, history, control locks, reset). "
                    + "No human visual inspection performed.");
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (app != null) {
                    app.dispose();
                }
            });
        }
    }

    private static void runSpin() throws Exception {
        CountDownLatch finished = new CountDownLatch(1);
        Timer[] watcher = new Timer[1];
        long start = System.nanoTime();
        SwingUtilities.invokeAndWait(() -> {
            spin.doClick();
            truth(!spin.isEnabled() && !add.isEnabled() && !reset.isEnabled()
                    && !remove.isEnabled() && !newName.isEnabled(), "All mutation controls locked");
            newName.postActionEvent();
            truth(hasLabel("Please wait for the wheel to stop."), "Enter cannot add during spin");
            truth(result.getText().equals("Spinning..."), "Previous result cleared at spin start");
            watcher[0] = new Timer(25, event -> {
                if (reset.isEnabled()) {
                    ((Timer) event.getSource()).stop();
                    finished.countDown();
                }
            });
            watcher[0].start();
        });
        boolean completed = finished.await(8, TimeUnit.SECONDS);
        SwingUtilities.invokeAndWait(() -> watcher[0].stop());
        truth(completed, "Swing timer completed without blocking EDT");
        double seconds = (System.nanoTime() - start) / 1_000_000_000.0;
        truth(seconds >= 4 && seconds <= 6, "Animation lasts approximately 4-6 seconds: " + seconds);
        SwingUtilities.invokeAndWait(() -> truth(add.isEnabled() && reset.isEnabled()
                && remove.isEnabled() && newName.isEnabled(), "Controls unlocked after completion"));
    }

    private static boolean hasLabel(String text) {
        return descendants(app).stream().filter(JLabel.class::isInstance)
                .map(JLabel.class::cast).anyMatch(label -> label.getText().contains(text));
    }

    private static void checkCount(int count) {
        truth(hasLabel("Remaining students: " + count), "Remaining count is " + count);
    }

    private static void checkSections(List<String> names) {
        wheel.setSize(500, 500);
        for (int i = 0; i < names.size(); i++) {
            double angle = Math.toRadians((i + 0.5) * 360.0 / names.size() - 90);
            MouseEvent section = new MouseEvent(wheel, MouseEvent.MOUSE_MOVED, 0, 0,
                    (int) Math.round(250 + 150 * Math.cos(angle)),
                    (int) Math.round(250 + 150 * Math.sin(angle)), 0, false);
            truth(names.get(i).equals(wheel.getToolTipText(section)),
                    "Equal section contains " + names.get(i));
        }
    }

    private static List<Component> descendants(Container parent) {
        List<Component> result = new ArrayList<>();
        for (Component component : parent.getComponents()) {
            result.add(component);
            if (component instanceof Container container) {
                result.addAll(descendants(container));
            }
        }
        return result;
    }

    private static JButton button(List<Component> components, String text) {
        return components.stream().filter(JButton.class::isInstance).map(JButton.class::cast)
                .filter(button -> button.getText().equals(text)).findFirst().orElseThrow();
    }

    private static void truth(boolean condition, String message) {
        assertions++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
