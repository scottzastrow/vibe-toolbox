import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class SpinWheelChecks {
    private static int assertions;

    public static void main(String[] args) throws Exception {
        checkRosterValidation();
        checkAddingNames();
        checkTurnLifecycle();
        checkUniformSelection();
        checkAnglesAndEasing();
        SwingUtilities.invokeAndWait(SpinWheelChecks::checkDrawing);
        System.out.println("PASS: " + assertions + " automated assertions "
                + "(roster, turns, uniform selection, alignment, easing, offscreen drawing).");
    }

    private static void checkRosterValidation() {
        RosterState state = new RosterState(new Random(1));
        equal(state.activeNames(), RosterState.DEFAULT_NAMES, "Premade names");
        state.applyNames("  Scott  \n\n Emma \r\n \t \nLiam\n");
        equal(state.activeNames(), List.of("Scott", "Emma", "Liam"), "Trim and ignore blanks");
        state.beginSpin();
        state.finishSpin(true);
        List<String> before = state.activeNames();
        List<String> history = state.selectionHistory();
        expect(IllegalArgumentException.class, () -> state.applyNames("Alex\n alex "));
        equal(state.activeNames(), before, "Duplicate rejection is atomic");
        equal(state.selectionHistory(), history, "Duplicate rejection preserves history");
        expect(IllegalArgumentException.class, () -> state.applyNames("  \n \t\r\n"));
        equal(state.activeNames(), before, "Empty rejection is atomic");
        state.applyNames("Taylor A\nTaylor B");
        equal(state.selectionHistory(), List.of(), "Apply clears history");
        equal(state.activeNames(), List.of("Taylor A", "Taylor B"), "Apply replaces roster");
        expect(UnsupportedOperationException.class, () -> state.activeNames().add("Extra"));
    }

    private static void checkTurnLifecycle() {
        RosterState state = new RosterState(new Random(2));
        state.applyNames("One\nTwo\nThree");
        List<String> display = state.activeNames();
        int index = state.beginSpin();
        truth(state.isSpinning(), "Spin enters running state");
        expect(IllegalStateException.class, state::beginSpin);
        expect(IllegalStateException.class, state::resetTurns);
        expect(IllegalStateException.class, () -> state.applyNames("Replacement"));
        equal(state.activeNames(), display, "Active roster stable during animation");
        String winner = state.finishSpin(true);
        equal(winner, display.get(index), "Announced name matches chosen index");
        truth(!state.isSpinning(), "Finish returns to idle");
        truth(!state.activeNames().contains(winner), "Winner excluded from future spins");
        truth(display.contains(winner), "Displayed snapshot still contains winner");
        equal(state.selectionHistory(), List.of(winner), "History records winner");
        expect(IllegalStateException.class, () -> state.finishSpin(true));
        while (!state.activeNames().isEmpty()) {
            List<String> eligible = state.activeNames();
            int chosen = state.beginSpin();
            String next = state.finishSpin(true);
            equal(next, eligible.get(chosen), "Removal uses current eligible index");
        }
        equal(state.selectionHistory().stream().distinct().count(), 3L, "No repeat on exhaustion");
        expect(IllegalStateException.class, state::beginSpin);
        state.resetTurns();
        equal(state.activeNames(), RosterState.DEFAULT_NAMES, "Reset restores premade roster");
        equal(state.selectionHistory(), List.of(), "Reset clears selections");
        state.applyNames("Only student");
        for (int i = 0; i < 3; i++) {
            equal(state.beginSpin(), 0, "One student always selects zero");
            equal(state.finishSpin(false), "Only student", "Removal off allows repeat");
            equal(state.activeNames().size(), 1, "Removal off retains winner");
        }
        equal(state.selectionHistory().size(), 3, "Repeated selections recorded");
        state.beginSpin();
        state.finishSpin(true);
        truth(state.activeNames().isEmpty(), "Single student exhausts correctly");
        state.resetTurns();
        equal(state.activeNames(), RosterState.DEFAULT_NAMES, "Single student reset restores defaults");
    }

    private static void checkAddingNames() {
        RosterState state = new RosterState(new Random(3));
        state.applyNames("One\nTwo");
        state.beginSpin();
        String winner = state.finishSpin(true);
        List<String> before = state.activeNames();
        List<String> history = state.selectionHistory();
        expect(IllegalArgumentException.class, () -> state.addName(" \t\u2003 "));
        expect(IllegalArgumentException.class, () -> state.addName("One\nThree"));
        expect(IllegalArgumentException.class, () -> state.addName(" " + winner.toUpperCase() + " "));
        expect(IllegalArgumentException.class,
                () -> state.addName(state.activeNames().getFirst().toLowerCase()));
        equal(state.activeNames(), before, "Invalid additions preserve active names");
        equal(state.selectionHistory(), history, "Invalid additions preserve selections");
        state.addName(" \t Three \u2003 ");
        List<String> expected = new ArrayList<>(before);
        expected.add("Three");
        equal(state.activeNames(), expected, "Trimmed addition extends active wheel");
        equal(state.selectionHistory(), history, "Addition preserves selections");
        truth(!state.activeNames().contains(winner), "Addition does not restore selected students");
        state.beginSpin();
        expect(IllegalStateException.class, () -> state.addName("Four"));
        equal(state.activeNames(), expected, "Adding during a spin cannot mutate the roster");
        state.finishSpin(true);
        while (!state.activeNames().isEmpty()) {
            state.beginSpin();
            state.finishSpin(true);
        }
        history = state.selectionHistory();
        state.addName("Four");
        equal(state.activeNames(), List.of("Four"), "Addition revives an exhausted wheel");
        equal(state.selectionHistory(), history, "Exhausted wheel addition retains selections");
        state.resetTurns();
        equal(state.activeNames(), RosterState.DEFAULT_NAMES,
                "Reset removes temporary names and restores only premade students");
        equal(state.selectionHistory(), List.of(), "Reset clears selections after additions");
        equal(RosterState.DEFAULT_NAMES, List.of("Scott", "Emma", "Liam", "Olivia", "Noah",
                "Ava", "James", "Sophia", "Lucas", "Mia"), "Original defaults never change");
        expect(UnsupportedOperationException.class, () -> RosterState.DEFAULT_NAMES.add("Extra"));
        state.addName("Three");
        truth(state.activeNames().contains("Three"), "Reset permits a discarded temporary name again");
        state.resetTurns();
        equal(state.activeNames(), RosterState.DEFAULT_NAMES, "Repeated reset discards new additions");
        state.beginSpin();
        state.finishSpin(false);
        before = state.activeNames();
        history = state.selectionHistory();
        state.addName("Five");
        expected = new ArrayList<>(before);
        expected.add("Five");
        equal(state.activeNames(), expected, "Addition preserves removal-off eligibility");
        equal(state.selectionHistory(), history, "Addition preserves removal-off selections");
        state.resetTurns();
        equal(state.activeNames(), RosterState.DEFAULT_NAMES, "Reset with removal off restores defaults");
        equal(state.selectionHistory(), List.of(), "Reset with removal off clears selections");
    }

    private static void checkUniformSelection() {
        // Verify every possible Random.nextInt outcome maps to exactly one section.
        for (int count : new int[]{1, 2, 3, 10, 37}) {
            List<String> names = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                names.add("Student " + i);
            }
            for (int chosen = 0; chosen < count; chosen++) {
                final int outcome = chosen;
                final int expectedBound = count;
                Random controlled = new Random(0) {
                    @Override
                    public int nextInt(int bound) {
                        equal(bound, expectedBound, "Uniform generator receives active count");
                        return outcome;
                    }
                };
                RosterState state = new RosterState(controlled);
                state.applyNames(String.join("\n", names));
                equal(state.beginSpin(), chosen, "Index maps directly without weighting");
                equal(state.finishSpin(false), names.get(chosen), "Index maps to announced name");
            }
        }
        RosterState state = new RosterState(new Random(12345));
        int[] frequencies = new int[10];
        for (int i = 0; i < 100_000; i++) {
            frequencies[state.beginSpin()]++;
            state.finishSpin(false);
        }
        for (int frequency : frequencies) {
            truth(Math.abs(frequency - 10_000) < 500,
                    "Seeded frequency sanity check: " + frequency);
        }
    }

    private static void checkAnglesAndEasing() {
        for (int count = 1; count <= 150; count++) {
            for (int index = 0; index < count; index++) {
                double target = WheelMath.winningRotation(index, count);
                equal(WheelMath.indexAtPointer(target, count), index, "Target under top pointer");
                double center = WheelMath.normalize(target + (index + 0.5) * 360.0 / count);
                truth(Math.min(center, 360 - center) < 1e-8, "Slice center exactly at pointer");
                for (double start : new double[]{0, 17.25, 359.9, 725.5}) {
                    double destination = WheelMath.spinDestination(start, index, count);
                    truth(destination - start >= 1800 && destination - start < 2160,
                            "Five full turns plus alignment");
                    equal(WheelMath.indexAtPointer(destination, count), index,
                            "Animated destination agrees with pointer");
                    close(WheelMath.easedRotation(start, destination, 0), start, "Animation start");
                    close(WheelMath.easedRotation(start, destination, 1), destination, "Animation end");
                }
            }
        }
        double previous = 0;
        double previousStep = Double.POSITIVE_INFINITY;
        for (int i = 1; i <= 100; i++) {
            double current = WheelMath.easedRotation(0, 2000, i / 100.0);
            double step = current - previous;
            truth(step > 0 && step <= previousStep, "Animation moves forward and slows down");
            previous = current;
            previousStep = step;
        }
        close(WheelMath.easedRotation(0, 2000, 1.5), 2000, "Delayed timer clamps endpoint");
    }

    private static void checkDrawing() {
        WheelPanel panel = new WheelPanel();
        for (int[] size : new int[][]{{660, 660}, {900, 400}, {400, 700}}) {
            panel.setSize(size[0], size[1]);
            for (List<String> names : List.of(List.of("Only"), RosterState.DEFAULT_NAMES,
                    List.of("A very long student name that must fit", "Emma", "Liam"))) {
                panel.setNames(names);
                for (int index = 0; index < names.size(); index++) {
                    panel.setRotation(WheelMath.winningRotation(index, names.size()));
                    panel.highlight(index);
                    BufferedImage image = new BufferedImage(size[0], size[1], BufferedImage.TYPE_INT_RGB);
                    Graphics2D g = image.createGraphics();
                    panel.paint(g);
                    g.dispose();
                    int cx = size[0] / 2;
                    int cy = size[1] / 2;
                    double r = (Math.min(size[0], size[1]) - 64) / 2.0;
                    MouseEvent pointerSection = new MouseEvent(panel, MouseEvent.MOUSE_MOVED, 0, 0,
                            cx, (int) (cy - r * 0.75), 0, false);
                    equal(panel.getToolTipText(pointerSection), names.get(index),
                            "Painted top section tooltip agrees with selected name");
                    int outside = image.getRGB((int) (cx + r + 12), cy);
                    equal(new Color(outside), panel.getBackground(),
                            "Wheel stays circular in nonsquare panels");
                    truth(image.getRGB(cx, (int) (cy - r - 10))
                                    != panel.getBackground().getRGB(),
                            "Fixed pointer drawn above wheel");
                    // The upper-right rim sample is inside the chosen slice but away
                    // from the pointer, outlines, center dot, and radial text.
                    double offset = Math.min(20, 360.0 / names.size() * 0.3);
                    double sampleAngle = Math.toRadians(-90 + offset);
                    int sx = (int) Math.round(cx + r * 0.96 * Math.cos(sampleAngle));
                    int sy = (int) Math.round(cy + r * 0.96 * Math.sin(sampleAngle));
                    int[] colors = {0xBFE3FA, 0xFFC9A3, 0xC8E6B0, 0xE2CCFA, 0xFFE599,
                            0xA9E3DA, 0xFAC1D2, 0xC5CDF5, 0xE2EBA7, 0xF5D0B5};
                    equal(image.getRGB(sx, sy) & 0xFFFFFF, colors[index % colors.length],
                            "Actual Java2D slice color agrees with winner");
                }
            }
        }
    }

    private static void truth(boolean condition, String message) {
        assertions++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void equal(Object actual, Object expected, String message) {
        truth(actual.equals(expected), message + ": expected " + expected + ", got " + actual);
    }

    private static void close(double actual, double expected, String message) {
        truth(Math.abs(actual - expected) < 1e-8, message);
    }

    private static void expect(Class<? extends RuntimeException> type, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException exception) {
            truth(type.isInstance(exception), "Expected " + type + ", got " + exception);
            return;
        }
        throw new AssertionError("Expected " + type.getSimpleName());
    }
}
