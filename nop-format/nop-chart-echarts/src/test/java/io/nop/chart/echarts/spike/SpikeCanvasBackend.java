package io.nop.chart.echarts.spike;

import javax.imageio.ImageIO;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.LinearGradientPaint;
import java.awt.Paint;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
/**
 * WI0 spike 的 Canvas2D → Java2D 翻译后端，仅供 PoC 测试使用，非生产代码。
 * 生产级 Canvas2D shim 由 WI1-WI3 的 nop-canvas2d 模块承载。
 * 覆盖 bar/line/pie 级静态渲染所需的 Canvas2D 子面；未覆盖的操作显式抛错（不静默跳过）。
 */
public class SpikeCanvasBackend {
    private static final Set<String> AVAILABLE_FAMILIES = new HashSet<>(Arrays.asList(
            GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));

    private static final Pattern FONT_PATTERN = Pattern.compile(
            "^(?:(normal|italic|oblique)\\s+)?(?:(normal|bold|lighter|bolder|\\d{3})\\s+)?(\\d+(?:\\.\\d+)?)px\\s+(.+)$",
            Pattern.CASE_INSENSITIVE);

    private static final Map<String, Color> NAMED_COLORS = buildNamedColors();

    private BufferedImage image;
    private Graphics2D g;

    private AffineTransform transform = new AffineTransform();
    private Shape clipShape;
    private Path2D.Double path;
    private Paint fillPaint = Color.BLACK;
    private Paint strokePaint = Color.BLACK;
    private float lineWidth = 1f;
    private int lineCap = BasicStroke.CAP_BUTT;
    private int lineJoin = BasicStroke.JOIN_MITER;
    private float miterLimit = 10f;
    private float[] lineDash;
    private float lineDashOffset;
    private float globalAlpha = 1f;
    private Font font = new Font(Font.SANS_SERIF, Font.PLAIN, 10);
    private int textAlign; // 0=start/left, 1=center, 2=right/end
    private int textBaseline; // 0=alphabetic, 1=top, 2=middle, 3=bottom
    private final Deque<CtxState> stateStack = new ArrayDeque<>();

    public SpikeCanvasBackend(int width, int height) {
        recreate(width, height);
    }

    private void recreate(int width, int height) {
        image = new BufferedImage(Math.max(1, width), Math.max(1, height), BufferedImage.TYPE_INT_RGB);
        Graphics2D newG = image.createGraphics();
        newG.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        newG.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        newG.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        newG.setColor(Color.WHITE);
        newG.fillRect(0, 0, image.getWidth(), image.getHeight());
        newG.dispose();
        g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        transform = new AffineTransform();
        clipShape = null;
        path = null;
    }

    public int getWidth() {
        return image.getWidth();
    }

    public int getHeight() {
        return image.getHeight();
    }

    public void resize(int width, int height) {
        if (width == image.getWidth() && height == image.getHeight())
            return;
        recreate(width, height);
    }

    private void applyState() {
        g.setTransform(transform);
        g.setClip(clipShape);
        g.setPaint(fillPaint);
        Composite composite = AlphaComposite.SrcOver.derive(globalAlpha);
        g.setComposite(composite);
    }

    private void applyStrokeState() {
        applyState();
        g.setPaint(strokePaint);
        g.setStroke(new BasicStroke(lineWidth, lineCap, lineJoin, miterLimit, lineDash, lineDashOffset));
    }

    // ----- canvas size / save-restore -----

    public void save() {
        stateStack.push(new CtxState((AffineTransform) transform.clone(), clipShape, fillPaint, strokePaint,
                lineWidth, lineCap, lineJoin, miterLimit, lineDash, lineDashOffset, globalAlpha, font,
                textAlign, textBaseline));
    }

    public void restore() {
        if (stateStack.isEmpty())
            throw new IllegalStateException("restore() without matching save()");
        CtxState s = stateStack.pop();
        this.transform = s.transform;
        this.clipShape = s.clip;
        this.fillPaint = s.fillPaint;
        this.strokePaint = s.strokePaint;
        this.lineWidth = s.lineWidth;
        this.lineCap = s.lineCap;
        this.lineJoin = s.lineJoin;
        this.miterLimit = s.miterLimit;
        this.lineDash = s.lineDash;
        this.lineDashOffset = s.lineDashOffset;
        this.globalAlpha = s.globalAlpha;
        this.font = s.font;
        this.textAlign = s.textAlign;
        this.textBaseline = s.textBaseline;
    }

    // ----- transforms -----

    public void translate(double x, double y) {
        transform.translate(x, y);
    }

    public void scale(double x, double y) {
        transform.scale(x, y);
    }

    public void rotate(double angle) {
        transform.rotate(angle);
    }

    public void transform(double a, double b, double c, double d, double e, double f) {
        transform.concatenate(new AffineTransform(a, b, c, d, e, f));
    }

    public void setTransform(double a, double b, double c, double d, double e, double f) {
        transform = new AffineTransform(a, b, c, d, e, f);
    }

    // ----- paths -----

    public void beginPath() {
        path = new Path2D.Double(Path2D.WIND_NON_ZERO);
    }

    private Path2D.Double requirePath() {
        if (path == null)
            throw new IllegalStateException("beginPath() must be called before path operations");
        return path;
    }

    public void moveTo(double x, double y) {
        requirePath().moveTo(x, y);
    }

    public void lineTo(double x, double y) {
        requirePath().lineTo(x, y);
    }

    public void bezierCurveTo(double c1x, double c1y, double c2x, double c2y, double x, double y) {
        requirePath().curveTo(c1x, c1y, c2x, c2y, x, y);
    }

    public void quadraticCurveTo(double cx, double cy, double x, double y) {
        Path2D.Double p = requirePath();
        Point2D current = p.getCurrentPoint();
        double x0 = current == null ? 0 : current.getX();
        double y0 = current == null ? 0 : current.getY();
        double c1x = x0 + 2.0 / 3.0 * (cx - x0);
        double c1y = y0 + 2.0 / 3.0 * (cy - y0);
        double c2x = x + 2.0 / 3.0 * (cx - x);
        double c2y = y + 2.0 / 3.0 * (cy - y);
        p.curveTo(c1x, c1y, c2x, c2y, x, y);
    }

    public void arc(double x, double y, double radius, double startAngle, double endAngle, boolean anticlockwise) {
        double sweep = endAngle - startAngle;
        if (anticlockwise) {
            while (sweep >= 0)
                sweep -= Math.PI * 2;
        } else {
            while (sweep <= 0)
                sweep += Math.PI * 2;
        }
        // Canvas 角度在 y 向下的屏幕坐标系中顺时针增长；Java2D Arc2D 角度按数学方向取度数，
        // 取负号把屏幕顺时针映射为 frame 内逆时针。
        Arc2D segment = new Arc2D.Double(x - radius, y - radius, radius * 2, radius * 2,
                -Math.toDegrees(startAngle), -Math.toDegrees(sweep), Arc2D.OPEN);
        requirePath().append(segment, true);
    }

    public void rect(double x, double y, double width, double height) {
        Path2D.Double p = requirePath();
        p.moveTo(x, y);
        p.lineTo(x + width, y);
        p.lineTo(x + width, y + height);
        p.lineTo(x, y + height);
        p.closePath();
    }

    public void closePath() {
        requirePath().closePath();
    }

    // ----- painting -----

    public void fill() {
        applyState();
        g.fill(requirePath());
    }

    public void stroke() {
        applyStrokeState();
        g.draw(requirePath());
    }

    public void clip() {
        Area base = clipShape == null
                ? new Area(new Rectangle2D.Double(0, 0, image.getWidth(), image.getHeight()))
                : new Area(clipShape);
        base.intersect(new Area(requirePath()));
        clipShape = base;
    }

    public void fillRect(double x, double y, double width, double height) {
        applyState();
        g.fill(new Rectangle2D.Double(x, y, width, height));
    }

    public void strokeRect(double x, double y, double width, double height) {
        applyStrokeState();
        g.draw(new Rectangle2D.Double(x, y, width, height));
    }

    public void clearRect(double x, double y, double width, double height) {
        applyState();
        Paint saved = fillPaint;
        g.setPaint(Color.WHITE);
        g.fill(new Rectangle2D.Double(x, y, width, height));
        g.setPaint(saved);
    }

    // ----- style properties -----

    public void setFillStyle(String css) {
        this.fillPaint = parseColor(css);
    }

    public void setFillStyleGradient(Paint gradient) {
        this.fillPaint = gradient;
    }

    public void setStrokeStyle(String css) {
        this.strokePaint = parseColor(css);
    }

    public void setStrokeStyleGradient(Paint gradient) {
        this.strokePaint = gradient;
    }

    public void setLineWidth(double width) {
        this.lineWidth = (float) width;
    }

    public void setLineCap(String cap) {
        switch (cap) {
            case "butt":
                this.lineCap = BasicStroke.CAP_BUTT;
                break;
            case "round":
                this.lineCap = BasicStroke.CAP_ROUND;
                break;
            case "square":
                this.lineCap = BasicStroke.CAP_SQUARE;
                break;
            default:
                throw new IllegalArgumentException("Unknown lineCap: " + cap);
        }
    }

    public void setLineJoin(String join) {
        switch (join) {
            case "miter":
                this.lineJoin = BasicStroke.JOIN_MITER;
                break;
            case "round":
                this.lineJoin = BasicStroke.JOIN_ROUND;
                break;
            case "bevel":
                this.lineJoin = BasicStroke.JOIN_BEVEL;
                break;
            default:
                throw new IllegalArgumentException("Unknown lineJoin: " + join);
        }
    }

    public void setMiterLimit(double limit) {
        this.miterLimit = (float) limit;
    }

    public void setGlobalAlpha(double alpha) {
        if (alpha < 0 || alpha > 1)
            throw new IllegalArgumentException("globalAlpha out of range [0,1]: " + alpha);
        this.globalAlpha = (float) alpha;
    }

    public void setLineDash(List<Number> dash) {
        if (dash == null || dash.isEmpty()) {
            this.lineDash = null;
        } else {
            float[] segments = new float[dash.size()];
            for (int i = 0; i < dash.size(); i++) {
                segments[i] = dash.get(i).floatValue();
            }
            this.lineDash = segments;
        }
    }

    public void setLineDashOffset(double offset) {
        this.lineDashOffset = (float) offset;
    }

    // ----- text -----

    public void setFont(String cssFont) {
        this.font = parseFont(cssFont);
    }

    public void setTextAlign(String align) {
        switch (align) {
            case "start":
            case "left":
                this.textAlign = 0;
                break;
            case "center":
            case "middle":
                this.textAlign = 1;
                break;
            case "right":
            case "end":
                this.textAlign = 2;
                break;
            default:
                throw new IllegalArgumentException("Unknown textAlign: " + align);
        }
    }

    public void setTextBaseline(String baseline) {
        switch (baseline) {
            case "alphabetic":
                this.textBaseline = 0;
                break;
            case "top":
            case "hanging":
                this.textBaseline = 1;
                break;
            case "middle":
                this.textBaseline = 2;
                break;
            case "bottom":
            case "ideographic":
                this.textBaseline = 3;
                break;
            default:
                throw new IllegalArgumentException("Unknown textBaseline: " + baseline);
        }
    }

    public void fillText(String text, double x, double y) {
        applyState();
        g.setFont(font);
        FontMetrics metrics = g.getFontMetrics();
        double[] origin = anchorText(text, x, y, metrics);
        g.drawString(text, (float) origin[0], (float) origin[1]);
    }

    public void strokeText(String text, double x, double y) {
        applyStrokeState();
        g.setFont(font);
        FontMetrics metrics = g.getFontMetrics();
        double[] origin = anchorText(text, x, y, metrics);
        TextLayout layout = new TextLayout(text, font, g.getFontRenderContext());
        Shape outline = layout.getOutline(null);
        AffineTransform saved = (AffineTransform) transform.clone();
        g.translate(origin[0], origin[1]);
        g.draw(outline);
        g.setTransform(saved);
    }

    private double[] anchorText(String text, double x, double y, FontMetrics metrics) {
        double dx = x;
        if (textAlign == 1)
            dx = x - metrics.stringWidth(text) / 2.0;
        else if (textAlign == 2)
            dx = x - metrics.stringWidth(text);
        double dy = y;
        if (textBaseline == 1)
            dy = y + metrics.getAscent();
        else if (textBaseline == 2)
            dy = y + (metrics.getAscent() - metrics.getDescent()) / 2.0;
        else if (textBaseline == 3)
            dy = y - metrics.getDescent();
        return new double[]{dx, dy};
    }

    public SpikeTextMetrics measureText(String text) {
        BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        Graphics2D probeG = probe.createGraphics();
        try {
            probeG.setFont(font);
            FontMetrics metrics = probeG.getFontMetrics();
            return new SpikeTextMetrics(metrics.stringWidth(text));
        } finally {
            probeG.dispose();
        }
    }

    // ----- gradients -----

    public SpikeGradient createLinearGradient(double x0, double y0, double x1, double y1) {
        return SpikeGradient.linear(x0, y0, x1, y1);
    }

    public SpikeGradient createRadialGradient(double x0, double y0, double r0, double x1, double y1, double r1) {
        return SpikeGradient.radial(x0, y0, r0, x1, y1, r1);
    }

    // ----- pixel output -----

    public int nonWhitePixelCount() {
        return image.getWidth() * image.getHeight() - countPixelsNear(255, 255, 255, 0);
    }

    public int countPixelsNear(int r, int gr, int b, int tolerance) {
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int rgb = image.getRGB(x, y);
                int pr = (rgb >> 16) & 0xFF;
                int pg = (rgb >> 8) & 0xFF;
                int pb = rgb & 0xFF;
                if (Math.abs(pr - r) <= tolerance && Math.abs(pg - gr) <= tolerance && Math.abs(pb - b) <= tolerance)
                    count++;
            }
        }
        return count;
    }

    public int countDarkPixels(int maxLuminance) {
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int rgb = image.getRGB(x, y);
                int pr = (rgb >> 16) & 0xFF;
                int pg = (rgb >> 8) & 0xFF;
                int pb = rgb & 0xFF;
                int lum = (pr * 299 + pg * 587 + pb * 114) / 1000;
                if (lum <= maxLuminance)
                    count++;
            }
        }
        return count;
    }

    public void savePng(File file) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists())
            parent.mkdirs();
        ImageIO.write(image, "png", file);
    }

    // ----- CSS color parsing -----

    public static Color parseColor(String css) {
        String s = css == null ? "" : css.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty())
            throw new IllegalArgumentException("Empty CSS color");
        if (s.equals("transparent"))
            return new Color(0, 0, 0, 0);
        Color named = NAMED_COLORS.get(s);
        if (named != null)
            return named;
        if (s.startsWith("#")) {
            String hex = s.substring(1);
            try {
                switch (hex.length()) {
                    case 3: {
                        int r = Integer.parseInt(hex.substring(0, 1), 16) * 17;
                        int gr = Integer.parseInt(hex.substring(1, 2), 16) * 17;
                        int b = Integer.parseInt(hex.substring(2, 3), 16) * 17;
                        return new Color(r, gr, b);
                    }
                    case 4: {
                        int r = Integer.parseInt(hex.substring(0, 1), 16) * 17;
                        int gr = Integer.parseInt(hex.substring(1, 2), 16) * 17;
                        int b = Integer.parseInt(hex.substring(2, 3), 16) * 17;
                        int a = Integer.parseInt(hex.substring(3, 4), 16) * 17;
                        return new Color(r, gr, b, a);
                    }
                    case 6:
                        return new Color(Integer.parseInt(hex, 16));
                    case 8: {
                        int rgb = (int) Long.parseLong(hex.substring(0, 6), 16);
                        int r = (rgb >> 16) & 0xFF;
                        int gr = (rgb >> 8) & 0xFF;
                        int b = rgb & 0xFF;
                        int a = Integer.parseInt(hex.substring(6, 8), 16);
                        return new Color(r, gr, b, a);
                    }
                    default:
                        throw new IllegalArgumentException("Unknown CSS color: " + css);
                }
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Unknown CSS color: " + css, e);
            }
        }
        if (s.startsWith("rgb(") || s.startsWith("rgba(")) {
            String inner = s.substring(s.indexOf('(') + 1, s.length() - 1);
            String[] parts = inner.split("[,\\s/]+");
            try {
                int r = component(parts[0]);
                int gr = component(parts[1]);
                int b = component(parts[2]);
                int a = parts.length > 3 ? alphaComponent(parts[3]) : 255;
                return new Color(r, gr, b, a);
            } catch (RuntimeException e) {
                throw new IllegalArgumentException("Unknown CSS color: " + css, e);
            }
        }
        if (s.startsWith("hsl(") || s.startsWith("hsla(")) {
            String inner = s.substring(s.indexOf('(') + 1, s.length() - 1);
            String[] parts = inner.split("[,\\s/]+");
            try {
                float h = Float.parseFloat(parts[0].endsWith("deg") ? parts[0].replace("deg", "") : parts[0]) / 360f;
                float sat = Float.parseFloat(parts[1].trim()) / 100f;
                float lum = Float.parseFloat(parts[2].trim()) / 100f;
                float a = parts.length > 3 ? alphaComponent(parts[3]) / 255f : 1f;
                int rgb = Color.HSBtoRGB(h, sat, lum);
                int r = (rgb >> 16) & 0xFF;
                int gr = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                return new Color(r, gr, b, Math.round(a * 255f));
            } catch (RuntimeException e) {
                throw new IllegalArgumentException("Unknown CSS color: " + css, e);
            }
        }
        throw new IllegalArgumentException("Unknown CSS color: " + css);
    }

    private static int component(String part) {
        part = part.trim();
        if (part.endsWith("%"))
            return Math.round(Float.parseFloat(part.substring(0, part.length() - 1)) * 255f / 100f);
        return Integer.parseInt(part);
    }

    private static int alphaComponent(String part) {
        part = part.trim();
        if (part.endsWith("%"))
            return Math.round(Float.parseFloat(part.substring(0, part.length() - 1)) * 255f / 100f);
        return (int) Math.round(Double.parseDouble(part) * 255);
    }

    private Font parseFont(String cssFont) {
        Matcher matcher = FONT_PATTERN.matcher(cssFont == null ? "" : cssFont.trim());
        if (!matcher.matches())
            throw new IllegalArgumentException("Unsupported CSS font shorthand: " + cssFont);
        String styleToken = matcher.group(1);
        String weightToken = matcher.group(2);
        int size = (int) Math.round(Double.parseDouble(matcher.group(3)));
        String familyList = matcher.group(4);
        int style = Font.PLAIN;
        if ("italic".equalsIgnoreCase(styleToken) || "oblique".equalsIgnoreCase(styleToken))
            style |= Font.ITALIC;
        if (weightToken != null && ("bold".equalsIgnoreCase(weightToken) || "bolder".equalsIgnoreCase(weightToken)
                || parseIntSafe(weightToken) >= 600))
            style |= Font.BOLD;
        for (String rawFamily : familyList.split(",")) {
            String family = rawFamily.trim().replace("'", "").replace("\"", "");
            if (family.isEmpty())
                continue;
            if ("sans-serif".equalsIgnoreCase(family) || "-apple-system".equalsIgnoreCase(family))
                return new Font(Font.SANS_SERIF, style, size);
            if (AVAILABLE_FAMILIES.contains(family))
                return new Font(family, style, size);
        }
        throw new IllegalArgumentException("No available font family in CSS font: " + cssFont);
    }

    private static int parseIntSafe(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static Map<String, Color> buildNamedColors() {
        Map<String, Color> map = new HashMap<>();
        put(map, "black", 0x000000);
        put(map, "white", 0xFFFFFF);
        put(map, "red", 0xFF0000);
        put(map, "green", 0x008000);
        put(map, "lime", 0x00FF00);
        put(map, "blue", 0x0000FF);
        put(map, "yellow", 0xFFFF00);
        put(map, "orange", 0xFFA500);
        put(map, "purple", 0x800080);
        put(map, "gray", 0x808080);
        put(map, "grey", 0x808080);
        put(map, "silver", 0xC0C0C0);
        put(map, "maroon", 0x800000);
        put(map, "navy", 0x000080);
        put(map, "teal", 0x008080);
        put(map, "aqua", 0x00FFFF);
        put(map, "cyan", 0x00FFFF);
        put(map, "magenta", 0xFF00FF);
        put(map, "fuchsia", 0xFF00FF);
        put(map, "olive", 0x808000);
        return map;
    }

    private static void put(Map<String, Color> map, String name, int rgb) {
        map.put(name, new Color(rgb));
    }

    private record CtxState(AffineTransform transform, Shape clip, Paint fillPaint, Paint strokePaint,
                            float lineWidth, int lineCap, int lineJoin, float miterLimit, float[] lineDash,
                            float lineDashOffset, float globalAlpha, Font font, int textAlign, int textBaseline) {
    }
}
