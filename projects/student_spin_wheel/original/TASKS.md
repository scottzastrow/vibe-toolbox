# Implementation checklist

- [x] Inspect and retain existing build setup.
- [x] Add premade roster, atomic validation, reset, history, and removal state.
- [x] Draw a circular colorful wheel, readable names, fixed pointer, and highlight.
- [x] Implement uniform selection and consistent target-angle calculations.
- [x] Add responsive five-second timer animation and persistent winner result.
- [x] Add controls, animation locks, remaining count, and exhaustion handling.
- [x] Add dependency-free automated checks and run instructions.
- [x] Compile application and both check runners with Java 21 `javac`.
- [x] Pass 204,327 headless assertions for validation, selection, reset, exhaustion,
  pointer alignment, animation easing, and offscreen drawing.
- [x] Rename and emphasize Reset, preserving its existing reset behavior.
- [x] Pass 31 automated Swing assertions using real five-second timers for control
  locks, persistent winning sections, exhaustion, reset, and unsaved editor text.
- [ ] Manually inspect the live desktop UI (not performed in this environment).
