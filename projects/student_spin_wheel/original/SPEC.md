# Student Spin Wheel

A dependency-free Java 21 Swing/Java2D desktop app. `Main` launches on the EDT.
The existing IntelliJ module and `src` source root remain unchanged.

- Starts with Scott, Emma, Liam, Olivia, Noah, Ava, James, Sophia, Lucas, Mia.
- The labeled New student name input and Add Name button add one student by click
  or Enter. Names are trimmed; blank names and case-insensitive duplicates (including
  already-selected students) are rejected with an inline message. Add last initials
  to disambiguate. Successful additions clear the input, immediately rebuild equal
  wheel sections, and update the remaining count without clearing history or the
  previous result, or restoring excluded students. Added names are temporary for the
  current session; the immutable original default roster is kept separate.
- Each active student has one equal section and one equally likely random index.
  A five-second Swing Timer animation completes at the chosen section's center.
- Winners remain announced and highlighted until another spin or roster change.
  Removal defaults on; the winner is removed from eligibility immediately, but the
  displayed wheel is refreshed only at the next spin.
- Turning removal off retains future winners; it does not restore previously removed
  students. History records every completed spin, including repeats.
- The prominent Reset button removes all temporary names and restores only the
  original premade students, rebuilds the wheel,
  clears the winner and history, and refreshes the count and Spin availability.
  It preserves editor text and the removal setting, and is disabled during spins.
- During animation all state-changing controls and the name input are disabled.
  Exhaustion disables Spin and displays reset instructions beside the final result.
- Resizing preserves a circular wheel. Labels scale to their sections; hovering a
  section also reveals its full name. Very large rosters or names reduce label size.

## Run and check

In IntelliJ, open this folder, use JDK 21, and run `src\Main.java`.
No dependencies or build-system conversion are required.

From PowerShell in the project folder:

```powershell
javac -d out src\*.java
java -cp out Main
javac -cp out -d out tests\*.java
java '-Djava.awt.headless=true' -cp out SpinWheelChecks
java -cp out DesktopChecks
```

The standalone automated checks cover state, probability mapping, alignment, easing,
and offscreen drawing. `DesktopChecks` additionally requires a graphical environment
and exercises real Swing timers and controls without human visual inspection.
Actual desktop appearance and interaction require a visual check: resize, spin,
add names by click/Enter, toggle removal, exhaust, and reset.
