# Implementation plan

1. Inspect and preserve the IntelliJ Java 21 module and source root.
2. Implement roster validation, eligible students, history, and turn lifecycle
   independently of Swing.
3. Use a single clockwise-angle convention for Java2D slices, the top pointer,
   and the animated destination.
4. Build a resizable Swing interface with a five-second nonblocking animation,
   deferred display removal, persistent results, and locked controls during spins.
5. Compile with the installed JDK and run dependency-free automated checks.
   Document visual checks separately rather than claiming desktop verification.
