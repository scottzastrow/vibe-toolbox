import javax.swing.JPanel;
import javax.swing.ToolTipManager;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.geom.Arc2D;
import java.awt.geom.AffineTransform;
import java.util.List;

public final class WheelPanel extends JPanel {
    private static final Color[] COLORS = {
            new Color(0xBFE3FA), new Color(0xFFC9A3), new Color(0xC8E6B0),
            new Color(0xE2CCFA), new Color(0xFFE599), new Color(0xA9E3DA),
            new Color(0xFAC1D2), new Color(0xC5CDF5), new Color(0xE2EBA7),
            new Color(0xF5D0B5)
    };
    private List<String> names = List.of();
    private double rotation;
    private int highlighted = -1;

    public WheelPanel() {
        setBackground(new Color(0xF5F7FC));
        setPreferredSize(new Dimension(660, 660));
        setMinimumSize(new Dimension(280, 280));
        ToolTipManager.sharedInstance().registerComponent(this);
    }

    public void setNames(List<String> names) {
        this.names = List.copyOf(names);
        highlighted = -1;
        repaint();
    }

    public void setRotation(double rotation) {
        this.rotation = rotation;
        repaint();
    }

    public double getRotation() {
        return rotation;
    }

    public void highlight(int index) {
        highlighted = index;
        repaint();
    }

    private double radius() {
        return Math.max(1, (Math.min(getWidth(), getHeight()) - 64) / 2.0);
    }

    @Override
    public String getToolTipText(MouseEvent event) {
        double dx = event.getX() - getWidth() / 2.0;
        double dy = event.getY() - getHeight() / 2.0;
        if (names.isEmpty() || Math.hypot(dx, dy) > radius()) {
            return null;
        }
        double clockwise = WheelMath.normalize(Math.toDegrees(Math.atan2(dy, dx)) + 90);
        int index = WheelMath.indexAtPointer(rotation - clockwise, names.size());
        return names.get(index);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            double r = radius();
            double cx = getWidth() / 2.0;
            double cy = getHeight() / 2.0;
            double sweep = names.isEmpty() ? 360 : 360.0 / names.size();
            for (int i = 0; i < names.size(); i++) {
                Arc2D slice = new Arc2D.Double(cx - r, cy - r, 2 * r, 2 * r,
                        90 - rotation - i * sweep, -sweep, Arc2D.PIE);
                g.setColor(COLORS[i % COLORS.length]);
                g.fill(slice);
                g.setColor(Color.WHITE);
                g.setStroke(new BasicStroke(2));
                g.draw(slice);
                if (i == highlighted) {
                    g.setColor(new Color(0x172D59));
                    g.setStroke(new BasicStroke(5));
                    g.draw(slice);
                }
            }
            for (int i = 0; i < names.size(); i++) {
                drawName(g, names.get(i), cx, cy, r,
                        rotation + (i + 0.5) * sweep - 90, sweep);
            }
            g.setColor(new Color(0x172D59));
            g.setStroke(new BasicStroke(3));
            g.drawOval((int) (cx - r), (int) (cy - r), (int) (2 * r), (int) (2 * r));
            g.fillOval((int) cx - 10, (int) cy - 10, 20, 20);
            int top = (int) (cy - r);
            Polygon pointer = new Polygon(
                    new int[]{(int) cx - 15, (int) cx + 15, (int) cx},
                    new int[]{top - 22, top - 22, top + 16}, 3);
            g.setColor(new Color(0x172D59));
            g.fill(pointer);
        } finally {
            g.dispose();
        }
    }

    private void drawName(Graphics2D g, String name, double cx, double cy,
                          double r, double angle, double sweep) {
        AffineTransform original = g.getTransform();
        double radians = Math.toRadians(angle);
        g.translate(cx + Math.cos(radians) * r * 0.59,
                cy + Math.sin(radians) * r * 0.59);
        double readableAngle = WheelMath.normalize(angle);
        g.rotate(radians + (readableAngle > 90 && readableAngle < 270 ? Math.PI : 0));
        double maxWidth = r * 0.67;
        double maxHeight = names.size() == 1 ? r * 0.4
                : 2 * r * 0.25 * Math.sin(Math.toRadians(sweep / 2));
        Font font = new Font(Font.SANS_SERIF, Font.BOLD, 22);
        FontMetrics metrics = g.getFontMetrics(font);
        double scale = Math.min(1, Math.min(maxWidth / Math.max(1, metrics.stringWidth(name)),
                maxHeight / metrics.getHeight()));
        g.setFont(font.deriveFont((float) Math.max(1, 22 * scale)));
        metrics = g.getFontMetrics();
        g.setColor(new Color(0x14213D));
        g.drawString(name, -metrics.stringWidth(name) / 2f,
                (metrics.getAscent() - metrics.getDescent()) / 2f);
        g.setTransform(original);
    }
}
