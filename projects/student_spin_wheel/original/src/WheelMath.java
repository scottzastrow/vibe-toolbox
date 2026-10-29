public final class WheelMath {
    private WheelMath() {
    }

    public static double normalize(double degrees) {
        return ((degrees % 360.0) + 360.0) % 360.0;
    }

    // Rotation is clockwise; zero puts the first section's leading edge at the top.
    public static double winningRotation(int index, int count) {
        return normalize(-(index + 0.5) * 360.0 / count);
    }

    public static int indexAtPointer(double rotation, int count) {
        return Math.min(count - 1, (int) (normalize(-rotation) / (360.0 / count)));
    }

    public static double spinDestination(double rotation, int index, int count) {
        return rotation + 5 * 360.0
                + normalize(winningRotation(index, count) - normalize(rotation));
    }

    public static double easedRotation(double start, double destination, double progress) {
        double clamped = Math.max(0, Math.min(1, progress));
        return start + (destination - start) * (1 - Math.pow(1 - clamped, 4));
    }
}
