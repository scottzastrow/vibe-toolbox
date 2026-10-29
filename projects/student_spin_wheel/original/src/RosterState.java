import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

public final class RosterState {
    public static final List<String> DEFAULT_NAMES = List.of(
            "Scott", "Emma", "Liam", "Olivia", "Noah",
            "Ava", "James", "Sophia", "Lucas", "Mia");

    private final Random random;
    private List<String> sessionRoster = DEFAULT_NAMES;
    private final List<String> active = new ArrayList<>(DEFAULT_NAMES);
    private final List<String> history = new ArrayList<>();
    private int selectedIndex = -1;

    public RosterState(Random random) {
        this.random = random;
    }

    public void addName(String text) {
        requireIdle();
        String name = text.strip();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Please enter a new student name.");
        }
        if (name.lines().count() != 1) {
            throw new IllegalArgumentException("Please enter only one student name at a time.");
        }
        if (sessionRoster.stream().anyMatch(existing ->
                existing.toLowerCase(Locale.ROOT).equals(name.toLowerCase(Locale.ROOT)))) {
            throw new IllegalArgumentException(
                    "Duplicate name: " + name + ". Add a last initial to distinguish students.");
        }
        List<String> names = new ArrayList<>(sessionRoster);
        names.add(name);
        sessionRoster = List.copyOf(names);
        active.add(name);
    }

    public void applyNames(String text) {
        requireIdle();
        List<String> names = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String line : text.split("\\R")) {
            String name = line.strip();
            if (name.isEmpty()) {
                continue;
            }
            if (!seen.add(name.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException(
                        "Each student needs a unique name. Duplicate: " + name
                                + "\nFor students with the same name, add a last initial.");
            }
            names.add(name);
        }
        if (names.isEmpty()) {
            throw new IllegalArgumentException("Please enter at least one student, one name per line.");
        }
        sessionRoster = List.copyOf(names);
        active.clear();
        active.addAll(sessionRoster);
        history.clear();
    }

    public void resetTurns() {
        requireIdle();
        sessionRoster = DEFAULT_NAMES;
        active.clear();
        active.addAll(DEFAULT_NAMES);
        history.clear();
    }

    public int beginSpin() {
        requireIdle();
        if (active.isEmpty()) {
            throw new IllegalStateException("Everyone has had a turn! Reset to start again.");
        }
        selectedIndex = random.nextInt(active.size());
        return selectedIndex;
    }

    public String finishSpin(boolean removeSelected) {
        if (!isSpinning()) {
            throw new IllegalStateException("No spin is in progress.");
        }
        String winner = active.get(selectedIndex);
        history.add(winner);
        if (removeSelected) {
            active.remove(selectedIndex);
        }
        selectedIndex = -1;
        return winner;
    }

    public List<String> activeNames() {
        return List.copyOf(active);
    }

    public List<String> selectionHistory() {
        return List.copyOf(history);
    }

    public boolean isSpinning() {
        return selectedIndex >= 0;
    }

    private void requireIdle() {
        if (isSpinning()) {
            throw new IllegalStateException("Please wait for the wheel to stop.");
        }
    }
}
