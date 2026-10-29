# Run and evidence

Dependency-free Java 21 Swing/Java2D classroom tool. It spins a student roster,
tracks previous results, and can remove selected winners from future draws.

## Run

With the local server running, click **Open app** on the Student Spin Wheel
card. It compiles the reviewed bundled version and opens its desktop window on
the computer running the server. A full JDK 21 and graphical desktop are
required. Repeated clicks do not open duplicate instances. Stopping the server
closes the launched app; use the commands below to run it independently.

### Offline or manual fallback

From the Vibe Toolbox root in PowerShell, compile outside the preserved source
folder and launch the desktop app:

```powershell
$build = Join-Path $env:TEMP 'student-spin-wheel-out'
New-Item -ItemType Directory -Force $build | Out-Null
javac --release 21 -d $build projects\student_spin_wheel\original\src\*.java
java -cp $build Main
```

See the preserved [specification](original/SPEC.md) for behavior and checks,
and [plan](original/PLAN.md) for the recorded implementation outline.

## Test results

Compiled with Java 21 and passed `SpinWheelChecks` (204,350 assertions) and
`DesktopChecks` (102 Swing checks). No human visual inspection was performed.

The local-server browser check clicked **Open app**, verified the launched
process was a child of the server with an actual window titled Student Spin
Wheel, and confirmed a second click reused the running instance.

## Known limitations

The app requires a graphical desktop. The preserved specification calls for a
visual check of resizing, spinning, adding names, removal, exhaustion, and reset.
