package io.nop.canvas2d;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Java2D 渲染后端：Canvas2D 语义到 Graphics2D 的翻译（WI1 状态机/路径 + WI2 文本 + WI3 渐变/图案/阴影/图像）。
 *
 * <p>实现裁定（plan 2359/2360/2361）：
 * D1 ARGB 透明底、clearRect 绕过 alpha/合成（Clear 合成）；D2 非法输入快速失败、空路径隐式 moveTo、
 * AWT 异常包装不泄漏（退化渐变/无 stop 渐变为 D2 的规范对齐例外：绘制为空不抛错）；D3 fill/stroke/clip
 * 不消费路径；D4 hints 固定；D8 文本锚点；D9 渐变用户空间语义；D10 图案四态 repetition；
 * D11 阴影在设备空间（σ=blur/2、offset 不受 CTM 影响、shapes/text/images 三类作用面）；
 * D12 drawImage 源矩形求交 + 目标同比例收缩、putImageData 整块替换绕过全部状态、copyFrom 像素直写。
 * clip 固定于设备空间（canvas 规范，WI3 修复：clip() 时经当前 CTM 变换存储）。
 */
public class Java2DCanvas implements ICanvas2D, ICanvas2DContext {
    private final BufferedImage image;
    private final Graphics2D g;

    private AffineTransform transform = new AffineTransform();
    private Shape clipShape;
    private Object fillStyle = Color.BLACK;
    private Object strokeStyle = Color.BLACK;
    private float lineWidth = 1f;
    private int lineCap = BasicStroke.CAP_BUTT;
    private int lineJoin = BasicStroke.JOIN_MITER;
    private float miterLimit = 10f;
    private float[] lineDash;
    private float lineDashOffset;
    private float globalAlpha = 1f;

    // 文本状态（WI2）：初始为 canvas 规范默认，未 setFont 即 fillText 合法（D8）
    private TextMeasurer textMeasurer;
    private TextMeasurer.ResolvedFont resolvedFont;
    private String textAlign = "start";
    private String textBaseline = "alphabetic";

    // 阴影状态（WI3 D11）：offset/blur 在设备空间，不受 CTM 影响
    private float shadowBlur;
    private Color shadowColor = new Color(0, 0, 0, 0);
    private float shadowOffsetX;
    private float shadowOffsetY;

    private Path2D.Double path;
    private boolean pathBegun;
    private int pathSegments;

    private final Deque<CtxState> stack = new ArrayDeque<>();

    public Java2DCanvas(int width, int height) {
        this(width, height, null);
    }

    public Java2DCanvas(int width, int height, TextMeasurer textMeasurer) {
        if (width <= 0 || height <= 0)
            throw new NopCanvas2DException("Canvas size must be positive, got: " + width + "x" + height);
        image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        g = image.createGraphics();
        // D4c：hints 是本模块像素语义的一部分，不得按调用点切换
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        this.textMeasurer = textMeasurer != null ? textMeasurer : new TextMeasurer();
        this.resolvedFont = this.textMeasurer.resolveFont("10px sans-serif");
    }

    @Override
    public int getWidth() {
        return image.getWidth();
    }

    @Override
    public int getHeight() {
        return image.getHeight();
    }

    @Override
    public ICanvas2DContext getContext2D() {
        return this;
    }

    @Override
    public BufferedImage getImage() {
        BufferedImage copy = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D copyG = copy.createGraphics();
        try {
            copyG.drawImage(image, 0, 0, null);
        } finally {
            copyG.dispose();
        }
        return copy;
    }

    // ----- state stack -----

    @Override
    public void save() {
        stack.push(new CtxState((AffineTransform) transform.clone(), clipShape, fillStyle, strokeStyle,
                lineWidth, lineCap, lineJoin, miterLimit, lineDash, lineDashOffset, globalAlpha,
                resolvedFont, textAlign, textBaseline, shadowBlur, shadowColor, shadowOffsetX, shadowOffsetY));
    }

    @Override
    public void restore() {
        if (stack.isEmpty())
            throw new NopCanvas2DException("restore() without matching save()");
        CtxState s = stack.pop();
        this.transform = s.transform;
        this.clipShape = s.clip;
        this.fillStyle = s.fillStyle;
        this.strokeStyle = s.strokeStyle;
        this.lineWidth = s.lineWidth;
        this.lineCap = s.lineCap;
        this.lineJoin = s.lineJoin;
        this.miterLimit = s.miterLimit;
        this.lineDash = s.lineDash;
        this.lineDashOffset = s.lineDashOffset;
        this.globalAlpha = s.globalAlpha;
        this.resolvedFont = s.resolvedFont;
        this.textAlign = s.textAlign;
        this.textBaseline = s.textBaseline;
        this.shadowBlur = s.shadowBlur;
        this.shadowColor = s.shadowColor;
        this.shadowOffsetX = s.shadowOffsetX;
        this.shadowOffsetY = s.shadowOffsetY;
    }

    // ----- transforms -----

    @Override
    public void translate(double x, double y) {
        requireFinite("translate", x, y);
        transform.translate(x, y);
    }

    @Override
    public void scale(double x, double y) {
        requireFinite("scale", x, y);
        if (x == 0 || y == 0)
            throw new NopCanvas2DException("scale() with zero factor degenerates coordinates, got: " + x + ", " + y);
        transform.scale(x, y);
    }

    @Override
    public void rotate(double angle) {
        requireFinite("rotate", angle);
        transform.rotate(angle);
    }

    @Override
    public void transform(double a, double b, double c, double d, double e, double f) {
        requireFinite("transform", a, b, c, d, e, f);
        transform.concatenate(new AffineTransform(a, b, c, d, e, f));
    }

    @Override
    public void setTransform(double a, double b, double c, double d, double e, double f) {
        requireFinite("setTransform", a, b, c, d, e, f);
        transform = new AffineTransform(a, b, c, d, e, f);
    }

    // ----- path construction -----

    @Override
    public void beginPath() {
        path = new Path2D.Double(Path2D.WIND_NON_ZERO);
        pathBegun = true;
        pathSegments = 0;
    }

    @Override
    public void closePath() {
        if (pathSegments == 0)
            return;
        try {
            path.closePath();
        } catch (RuntimeException e) {
            throw new NopCanvas2DException("closePath() failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void moveTo(double x, double y) {
        requireFinite("moveTo", x, y);
        ensurePath();
        path.moveTo(x, y);
        pathSegments++;
    }

    @Override
    public void lineTo(double x, double y) {
        requireFinite("lineTo", x, y);
        ensurePath();
        if (pathSegments == 0) {
            path.moveTo(x, y);
            pathSegments++;
            return;
        }
        try {
            path.lineTo(x, y);
        } catch (RuntimeException e) {
            throw new NopCanvas2DException("lineTo() failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void bezierCurveTo(double cp1x, double cp1y, double cp2x, double cp2y, double x, double y) {
        requireFinite("bezierCurveTo", cp1x, cp1y, cp2x, cp2y, x, y);
        ensurePath();
        if (pathSegments == 0)
            path.moveTo(cp1x, cp1y);
        try {
            path.curveTo(cp1x, cp1y, cp2x, cp2y, x, y);
        } catch (RuntimeException e) {
            throw new NopCanvas2DException("bezierCurveTo() failed: " + e.getMessage(), e);
        }
        pathSegments++;
    }

    @Override
    public void quadraticCurveTo(double cpx, double cpy, double x, double y) {
        requireFinite("quadraticCurveTo", cpx, cpy, x, y);
        ensurePath();
        Point2D current = pathSegments == 0 ? null : path.getCurrentPoint();
        double x0;
        double y0;
        if (current == null) {
            // D2：空路径隐式 moveTo 第一控制点（Canvas 语义）
            path.moveTo(cpx, cpy);
            x0 = cpx;
            y0 = cpy;
        } else {
            x0 = current.getX();
            y0 = current.getY();
        }
        double c1x = x0 + 2.0 / 3.0 * (cpx - x0);
        double c1y = y0 + 2.0 / 3.0 * (cpy - y0);
        double c2x = x + 2.0 / 3.0 * (cpx - x);
        double c2y = y + 2.0 / 3.0 * (cpy - y);
        try {
            path.curveTo(c1x, c1y, c2x, c2y, x, y);
        } catch (RuntimeException e) {
            throw new NopCanvas2DException("quadraticCurveTo() failed: " + e.getMessage(), e);
        }
        pathSegments++;
    }

    @Override
    public void arc(double x, double y, double radius, double startAngle, double endAngle, boolean anticlockwise) {
        requireFinite("arc", x, y, radius, startAngle, endAngle);
        if (radius < 0)
            throw new NopCanvas2DException("arc() radius must be non-negative, got: " + radius);
        ensurePath();
        appendEllipticalArc(x, y, radius, radius, 0, startAngle, endAngle, anticlockwise);
    }

    @Override
    public void arcTo(double x1, double y1, double x2, double y2, double radius) {
        requireFinite("arcTo", x1, y1, x2, y2, radius);
        if (radius < 0)
            throw new NopCanvas2DException("arcTo() radius must be non-negative, got: " + radius);
        ensurePath();
        if (pathSegments == 0)
            moveTo(x1, y1);
        Point2D current = path.getCurrentPoint();
        double x0 = current.getX();
        double y0 = current.getY();
        // 端点重合或半径为 0：直线到拐点（Canvas 语义）
        if (radius == 0 || (x0 == x1 && y0 == y1) || (x1 == x2 && y1 == y2)) {
            lineTo(x1, y1);
            return;
        }
        double v1x = x0 - x1;
        double v1y = y0 - y1;
        double v2x = x2 - x1;
        double v2y = y2 - y1;
        double v1Len = Math.hypot(v1x, v1y);
        double v2Len = Math.hypot(v2x, v2y);
        if (v1Len == 0 || v2Len == 0) {
            lineTo(x1, y1);
            return;
        }
        v1x /= v1Len;
        v1y /= v1Len;
        v2x /= v2Len;
        v2y /= v2Len;
        double cross = v1x * v2y - v1y * v2x;
        double dot = v1x * v2x + v1y * v2y;
        if (cross == 0 && dot > 0) {
            // 共线同向：直线到拐点
            lineTo(x1, y1);
            return;
        }
        double theta = Math.acos(Math.max(-1, Math.min(1, dot)));
        double tangentLen = radius / Math.tan(theta / 2);
        double t1x = x1 + v1x * tangentLen;
        double t1y = y1 + v1y * tangentLen;
        double t2x = x1 + v2x * tangentLen;
        double t2y = y1 + v2y * tangentLen;
        lineTo(t1x, t1y);
        // 圆心在拐角内侧角平分线上，到两切点距离均为 radius
        double bisectorX = v1x + v2x;
        double bisectorY = v1y + v2y;
        double bisectorLen = Math.hypot(bisectorX, bisectorY);
        double centerDist = radius / Math.sin(theta / 2);
        double cx = x1 + bisectorX / bisectorLen * centerDist;
        double cy = y1 + bisectorY / bisectorLen * centerDist;
        double a0 = Math.atan2(t1y - cy, t1x - cx);
        double a1 = Math.atan2(t2y - cy, t2x - cx);
        // cross<0 为 y 向下坐标系中的顺时针转弯
        appendEllipticalArc(cx, cy, radius, radius, 0, a0, a1, cross > 0);
    }

    @Override
    public void ellipse(double x, double y, double radiusX, double radiusY, double rotation,
                        double startAngle, double endAngle, boolean anticlockwise) {
        requireFinite("ellipse", x, y, radiusX, radiusY, rotation, startAngle, endAngle);
        if (radiusX < 0 || radiusY < 0)
            throw new NopCanvas2DException("ellipse() radii must be non-negative, got: " + radiusX + ", " + radiusY);
        ensurePath();
        appendEllipticalArc(x, y, radiusX, radiusY, rotation, startAngle, endAngle, anticlockwise);
    }

    @Override
    public void rect(double x, double y, double width, double height) {
        requireFinite("rect", x, y, width, height);
        ensurePath();
        path.moveTo(x, y);
        path.lineTo(x + width, y);
        path.lineTo(x + width, y + height);
        path.lineTo(x, y + height);
        path.closePath();
        pathSegments++;
    }

    private void appendEllipticalArc(double cx, double cy, double rx, double ry, double rotation,
                                     double startAngle, double endAngle, boolean anticlockwise) {
        double sweep = endAngle - startAngle;
        if (anticlockwise) {
            while (sweep > 0)
                sweep -= 2 * Math.PI;
        } else {
            while (sweep < 0)
                sweep += 2 * Math.PI;
        }
        // 连接线：有当前点则 lineTo 弧起点，否则 moveTo（Canvas 语义）
        double cosRot = Math.cos(rotation);
        double sinRot = Math.sin(rotation);
        double startX = cx + rx * Math.cos(startAngle) * cosRot - ry * Math.sin(startAngle) * sinRot;
        double startY = cy + rx * Math.cos(startAngle) * sinRot + ry * Math.sin(startAngle) * cosRot;
        if (pathSegments > 0)
            path.lineTo(startX, startY);
        else
            path.moveTo(startX, startY);
        pathSegments++;
        if (sweep == 0)
            return;
        // Canvas 角度在 y 向下坐标系中顺时针增长；Java2D Arc2D 角度按数学方向取度数，
        // 取负号把屏幕顺时针映射为 frame 内逆时针（WI0 spike 实证正确）
        Arc2D segment = new Arc2D.Double(cx - rx, cy - ry, rx * 2, ry * 2,
                -Math.toDegrees(startAngle), -Math.toDegrees(sweep), Arc2D.OPEN);
        if (rotation == 0) {
            path.append(segment, false);
        } else {
            Path2D.Double local = new Path2D.Double(segment);
            AffineTransform rot = new AffineTransform();
            rot.translate(cx, cy);
            rot.rotate(rotation);
            rot.translate(-cx, -cy);
            local.transform(rot);
            path.append(local, false);
        }
    }

    // ----- painting -----

    @Override
    public void fill() {
        requirePathForPainting("fill()");
        if (pathSegments == 0)
            return;
        Paint paint = resolvePaint(fillStyle, "fillStyle");
        if (paint == null)
            return;
        Shape silhouette = path;
        paintWithShadow(paint, silhouette, false, lg -> lg.fill(path));
    }

    @Override
    public void stroke() {
        requirePathForPainting("stroke()");
        if (pathSegments == 0)
            return;
        Paint paint = resolvePaint(strokeStyle, "strokeStyle");
        if (paint == null)
            return;
        paintWithShadow(paint, path, true, lg -> {
            lg.setStroke(currentStroke());
            lg.draw(path);
        });
    }

    @Override
    public void clip() {
        requirePathForPainting("clip()");
        // canvas 规范：clip 固定于设备空间——clip() 时把路径经当前 CTM 变换存储（WI3 修复）
        Area base = clipShape == null
                ? new Area(new Rectangle2D.Double(0, 0, image.getWidth(), image.getHeight()))
                : new Area(clipShape);
        base.intersect(new Area(transform.createTransformedShape(path)));
        clipShape = base;
    }

    @Override
    public void fillRect(double x, double y, double width, double height) {
        requireFinite("fillRect", x, y, width, height);
        Paint paint = resolvePaint(fillStyle, "fillStyle");
        if (paint == null)
            return;
        Rectangle2D rect = new Rectangle2D.Double(x, y, width, height);
        paintWithShadow(paint, rect, false, lg -> lg.fill(rect));
    }

    @Override
    public void strokeRect(double x, double y, double width, double height) {
        requireFinite("strokeRect", x, y, width, height);
        Paint paint = resolvePaint(strokeStyle, "strokeStyle");
        if (paint == null)
            return;
        Rectangle2D rect = new Rectangle2D.Double(x, y, width, height);
        paintWithShadow(paint, rect, true, lg -> {
            lg.setStroke(currentStroke());
            lg.draw(rect);
        });
    }

    @Override
    public void clearRect(double x, double y, double width, double height) {
        requireFinite("clearRect", x, y, width, height);
        // D1：Clear 合成恢复全透明，绕过 globalAlpha 与 fillStyle（clip/transform 仍生效）
        Composite saved = g.getComposite();
        applyGraphicsState();
        g.setComposite(AlphaComposite.Clear);
        g.fill(new Rectangle2D.Double(x, y, width, height));
        g.setComposite(saved);
    }

    // ----- style -----

    @Override
    public void setFillStyleCss(String cssColor) {
        this.fillStyle = CssColors.parse(cssColor);
    }

    @Override
    public void setStrokeStyleCss(String cssColor) {
        this.strokeStyle = CssColors.parse(cssColor);
    }

    @Override
    public void setLineWidth(double width) {
        requireFinite("setLineWidth", width);
        if (width <= 0)
            throw new NopCanvas2DException("lineWidth must be positive, got: " + width);
        this.lineWidth = (float) width;
    }

    @Override
    public void setLineCap(String cap) {
        switch (cap == null ? "" : cap) {
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
                throw new NopCanvas2DException("Unknown lineCap: " + cap);
        }
    }

    @Override
    public void setLineJoin(String join) {
        switch (join == null ? "" : join) {
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
                throw new NopCanvas2DException("Unknown lineJoin: " + join);
        }
    }

    @Override
    public void setMiterLimit(double limit) {
        requireFinite("setMiterLimit", limit);
        if (limit <= 0)
            throw new NopCanvas2DException("miterLimit must be positive, got: " + limit);
        this.miterLimit = (float) limit;
    }

    @Override
    public void setLineDash(double... dash) {
        if (dash == null || dash.length == 0) {
            this.lineDash = null;
            return;
        }
        requireFinite("setLineDash", dash);
        for (double d : dash) {
            if (d < 0)
                throw new NopCanvas2DException("lineDash segments must be non-negative, got: "
                        + java.util.Arrays.toString(dash));
        }
        // 奇数长度按 Canvas 语义重复末段偶数化
        int len = dash.length % 2 == 0 ? dash.length : dash.length * 2;
        float[] segments = new float[len];
        for (int i = 0; i < len; i++)
            segments[i] = (float) dash[i % dash.length];
        this.lineDash = segments;
    }

    @Override
    public void setLineDashOffset(double offset) {
        requireFinite("setLineDashOffset", offset);
        this.lineDashOffset = (float) offset;
    }

    @Override
    public void setGlobalAlpha(double alpha) {
        requireFinite("setGlobalAlpha", alpha);
        if (alpha < 0 || alpha > 1)
            throw new NopCanvas2DException("globalAlpha out of range [0,1], got: " + alpha);
        this.globalAlpha = (float) alpha;
    }

    // ----- text（WI2） -----

    @Override
    public void setFont(String cssFont) {
        this.resolvedFont = textMeasurer.resolveFont(cssFont);
    }

    @Override
    public void setTextAlign(String align) {
        switch (align == null ? "" : align) {
            case "start":
            case "end":
            case "left":
            case "right":
            case "center":
                this.textAlign = align;
                break;
            default:
                throw new NopCanvas2DException("Unknown textAlign: " + align);
        }
    }

    @Override
    public void setTextBaseline(String baseline) {
        switch (baseline == null ? "" : baseline) {
            case "alphabetic":
            case "top":
            case "hanging":
            case "middle":
            case "ideographic":
            case "bottom":
                this.textBaseline = baseline;
                break;
            default:
                throw new NopCanvas2DException("Unknown textBaseline: " + baseline);
        }
    }

    @Override
    public ITextMetrics measureText(String text) {
        return textMeasurer.measure(text, resolvedFont.cssFont());
    }

    @Override
    public void fillText(String text, double x, double y) {
        requireFinite("fillText", x, y);
        String normalized = text == null ? "null" : text;
        if (normalized.isEmpty())
            return;
        Paint paint = resolvePaint(fillStyle, "fillStyle");
        if (paint == null)
            return;
        Shape silhouette = textSilhouette(normalized, x, y);
        paintWithShadow(paint, silhouette, false, lg -> drawTextAt(lg, normalized, x, y));
    }

    @Override
    public void strokeText(String text, double x, double y) {
        requireFinite("strokeText", x, y);
        String normalized = text == null ? "null" : text;
        if (normalized.isEmpty())
            return;
        Paint paint = resolvePaint(strokeStyle, "strokeStyle");
        if (paint == null)
            return;
        Shape silhouette = textSilhouette(normalized, x, y);
        paintWithShadow(paint, silhouette, true, lg -> {
            lg.setStroke(currentStroke());
            lg.draw(silhouette);
        });
    }

    private void drawTextAt(Graphics2D target, String text, double x, double y) {
        target.setFont(resolvedFont.font());
        FontMetrics fm = target.getFontMetrics();
        double[] origin = anchorText(text, x, y, fm);
        target.drawString(text, (float) origin[0], (float) origin[1]);
    }

    private Shape textSilhouette(String text, double x, double y) {
        g.setFont(resolvedFont.font());
        FontMetrics fm = g.getFontMetrics();
        double[] origin = anchorText(text, x, y, fm);
        TextLayout layout = new TextLayout(text, resolvedFont.font(), g.getFontRenderContext());
        Shape outline = layout.getOutline(null);
        AffineTransform at = AffineTransform.getTranslateInstance(origin[0], origin[1]);
        return at.createTransformedShape(outline);
    }

    /**
     * D8 锚点换算：textAlign 水平（start/left→x、center→x-w/2、right/end→x-w），
     * textBaseline 垂直（top/hanging→+ascent、middle→(ascent-descent)/2、alphabetic→0、
     * bottom/ideographic→-descent）；返回 drawString 基线点。
     */
    private double[] anchorText(String text, double x, double y, FontMetrics fm) {
        double dx = x;
        switch (textAlign) {
            case "center":
                dx = x - fm.stringWidth(text) / 2.0;
                break;
            case "right":
            case "end":
                dx = x - fm.stringWidth(text);
                break;
            default:
                break;
        }
        double dy = y;
        switch (textBaseline) {
            case "top":
            case "hanging":
                dy = y + fm.getAscent();
                break;
            case "middle":
                dy = y + (fm.getAscent() - fm.getDescent()) / 2.0;
                break;
            case "bottom":
            case "ideographic":
                dy = y - fm.getDescent();
                break;
            default:
                break;
        }
        return new double[]{dx, dy};
    }

    // ----- gradients / patterns（WI3） -----

    @Override
    public ICanvasGradient createLinearGradient(double x0, double y0, double x1, double y1) {
        requireFinite("createLinearGradient", x0, y0, x1, y1);
        return new CanvasGradient(false, x0, y0, 0, x1, y1, 0);
    }

    @Override
    public ICanvasGradient createRadialGradient(double x0, double y0, double r0, double x1, double y1, double r1) {
        requireFinite("createRadialGradient", x0, y0, r0, x1, y1, r1);
        if (r0 < 0 || r1 < 0)
            throw new NopCanvas2DException("createRadialGradient radii must be non-negative, got: " + r0 + ", " + r1);
        return new CanvasGradient(true, x0, y0, r0, x1, y1, r1);
    }

    @Override
    public void setFillStyleGradient(ICanvasGradient gradient) {
        requireGradient(gradient);
        this.fillStyle = gradient;
    }

    @Override
    public void setStrokeStyleGradient(ICanvasGradient gradient) {
        requireGradient(gradient);
        this.strokeStyle = gradient;
    }

    @Override
    public ICanvasPattern createPattern(BufferedImage image, String repetition) {
        if (image == null)
            throw new NopCanvas2DException("createPattern image must not be null");
        if (image.getWidth() <= 0 || image.getHeight() <= 0)
            throw new NopCanvas2DException("createPattern image must not be empty");
        String rep = repetition == null || repetition.isEmpty() ? "repeat" : repetition;
        switch (rep) {
            case "repeat":
            case "repeat-x":
            case "repeat-y":
            case "no-repeat":
                return new CanvasPattern(image, rep);
            default:
                throw new NopCanvas2DException("Unknown pattern repetition: " + repetition);
        }
    }

    @Override
    public void setFillStylePattern(ICanvasPattern pattern) {
        requirePattern(pattern);
        this.fillStyle = pattern;
    }

    @Override
    public void setStrokeStylePattern(ICanvasPattern pattern) {
        requirePattern(pattern);
        this.strokeStyle = pattern;
    }

    // ----- shadows（WI3） -----

    @Override
    public void setShadowBlur(double blur) {
        requireFinite("setShadowBlur", blur);
        if (blur < 0)
            throw new NopCanvas2DException("shadowBlur must be non-negative, got: " + blur);
        this.shadowBlur = (float) blur;
    }

    @Override
    public void setShadowColor(String cssColor) {
        this.shadowColor = CssColors.parse(cssColor);
    }

    @Override
    public void setShadowOffsetX(double offset) {
        requireFinite("setShadowOffsetX", offset);
        this.shadowOffsetX = (float) offset;
    }

    @Override
    public void setShadowOffsetY(double offset) {
        requireFinite("setShadowOffsetY", offset);
        this.shadowOffsetY = (float) offset;
    }

    // ----- images（WI3） -----

    @Override
    public void drawImage(BufferedImage img, double dx, double dy) {
        if (img == null)
            throw new NopCanvas2DException("drawImage image must not be null");
        requireFinite("drawImage", dx, dy);
        drawImageCropped(img, 0, 0, img.getWidth(), img.getHeight(), dx, dy,
                img.getWidth(), img.getHeight());
    }

    @Override
    public void drawImage(BufferedImage img, double dx, double dy, double dw, double dh) {
        if (img == null)
            throw new NopCanvas2DException("drawImage image must not be null");
        requireFinite("drawImage", dx, dy, dw, dh);
        drawImageCropped(img, 0, 0, img.getWidth(), img.getHeight(), dx, dy, dw, dh);
    }

    @Override
    public void drawImage(BufferedImage img, double sx, double sy, double sw, double sh,
                          double dx, double dy, double dw, double dh) {
        if (img == null)
            throw new NopCanvas2DException("drawImage image must not be null");
        requireFinite("drawImage", sx, sy, sw, sh, dx, dy, dw, dh);
        if (sw < 0 || sh < 0)
            throw new NopCanvas2DException("drawImage source size must be non-negative, got: " + sw + "x" + sh);
        if (sw == 0 || sh == 0)
            return;
        drawImageCropped(img, sx, sy, sw, sh, dx, dy, dw, dh);
    }

    /**
     * D12：源矩形与图像求交，目标矩形同比例收缩（不补边）。
     */
    private void drawImageCropped(BufferedImage img, double sx, double sy, double sw, double sh,
                                  double dx, double dy, double dw, double dh) {
        double sxI = Math.max(sx, 0);
        double syI = Math.max(sy, 0);
        double exI = Math.min(sx + sw, img.getWidth());
        double eyI = Math.min(sy + sh, img.getHeight());
        if (exI <= sxI || eyI <= syI)
            return;
        double visibleW = exI - sxI;
        double visibleH = eyI - syI;
        double scaleW = dw / sw;
        double scaleH = dh / sh;
        double dxI = dx + (sxI - sx) * scaleW;
        double dyI = dy + (syI - sy) * scaleH;
        double dwI = visibleW * scaleW;
        double dhI = visibleH * scaleH;
        Runnable painter = () -> g.drawImage(img,
                (int) Math.round(dxI), (int) Math.round(dyI),
                (int) Math.round(dxI + dwI), (int) Math.round(dyI + dhI),
                (int) Math.round(sxI), (int) Math.round(syI),
                (int) Math.round(exI), (int) Math.round(eyI), null);
        if (shadowActive()) {
            BufferedImage shadowLayer = renderShadowSilhouette(device -> {
                device.drawImage(img,
                        (int) Math.round(dxI), (int) Math.round(dyI),
                        (int) Math.round(dxI + dwI), (int) Math.round(dyI + dhI),
                        (int) Math.round(sxI), (int) Math.round(syI),
                        (int) Math.round(exI), (int) Math.round(eyI), null);
            }, true);
            compositeShadow(shadowLayer);
            applyGraphicsState();
            painter.run();
        } else {
            applyGraphicsState();
            painter.run();
        }
    }

    @Override
    public IImageData getImageData(int x, int y, int width, int height) {
        if (width <= 0 || height <= 0)
            throw new NopCanvas2DException("getImageData size must be positive, got: " + width + "x" + height);
        int[] data = new int[width * height];
        for (int row = 0; row < height; row++) {
            int imageY = y + row;
            if (imageY < 0 || imageY >= image.getHeight())
                continue;
            for (int col = 0; col < width; col++) {
                int imageX = x + col;
                if (imageX < 0 || imageX >= image.getWidth())
                    continue;
                data[row * width + col] = image.getRGB(imageX, imageY);
            }
        }
        return new ImageData(width, height, data);
    }

    @Override
    public void putImageData(IImageData data, double dx, double dy) {
        putImageData(data, dx, dy, 0, 0, data == null ? 0 : data.getWidth(), data == null ? 0 : data.getHeight());
    }

    @Override
    public void putImageData(IImageData data, double dx, double dy, double dirtyX, double dirtyY,
                             double dirtyWidth, double dirtyHeight) {
        if (data == null)
            throw new NopCanvas2DException("putImageData data must not be null");
        requireFinite("putImageData", dx, dy, dirtyX, dirtyY, dirtyWidth, dirtyHeight);
        if (dirtyX < 0 || dirtyY < 0 || dirtyWidth < 0 || dirtyHeight < 0)
            throw new NopCanvas2DException("putImageData dirty rect must be non-negative, got: "
                    + dirtyX + "," + dirtyY + "," + dirtyWidth + "x" + dirtyHeight);
        if (dirtyWidth == 0 || dirtyHeight == 0)
            return;
        int[] src = data.getData();
        int startCol = (int) Math.max(0, dirtyX);
        int startRow = (int) Math.max(0, dirtyY);
        int endCol = (int) Math.min(data.getWidth(), dirtyX + dirtyWidth);
        int endRow = (int) Math.min(data.getHeight(), dirtyY + dirtyHeight);
        // D12：整块替换，绕过 transform/clip/alpha/阴影——image.setRGB 直接写像素
        for (int row = startRow; row < endRow; row++) {
            int imageY = (int) Math.round(dy) + row;
            if (imageY < 0 || imageY >= image.getHeight())
                continue;
            for (int col = startCol; col < endCol; col++) {
                int imageX = (int) Math.round(dx) + col;
                if (imageX < 0 || imageX >= image.getWidth())
                    continue;
                image.setRGB(imageX, imageY, src[row * data.getWidth() + col]);
            }
        }
    }

    @Override
    public void copyFrom(ICanvas2D source) {
        if (source == null)
            throw new NopCanvas2DException("copyFrom source must not be null");
        if (source.getWidth() != image.getWidth() || source.getHeight() != image.getHeight())
            throw new NopCanvas2DException("copyFrom requires same-size canvases, got "
                    + source.getWidth() + "x" + source.getHeight() + " vs "
                    + image.getWidth() + "x" + image.getHeight());
        // 像素级直写（裁定：非 canvas 标准 API，语义收敛为最直读形态）
        Composite saved = g.getComposite();
        AffineTransform savedTransform = (AffineTransform) transform.clone();
        Shape savedClip = clipShape;
        g.setComposite(AlphaComposite.Src);
        g.setTransform(new AffineTransform());
        g.setClip(null);
        g.drawImage(source.getImage(), 0, 0, null);
        g.setComposite(saved);
        g.setTransform(new AffineTransform());
        g.setClip(savedClip);
        g.setTransform(savedTransform);
    }

    // ----- internals -----

    private void ensurePath() {
        if (path == null)
            beginPath();
    }

    private void requirePathForPainting(String op) {
        if (!pathBegun)
            throw new NopCanvas2DException(op + " called before beginPath()");
    }

    private BasicStroke currentStroke() {
        return new BasicStroke(lineWidth, lineCap, lineJoin, miterLimit, lineDash, lineDashOffset);
    }

    /**
     * 样式对象 → Java2D Paint；无 stop/退化渐变返回 null（消费方绘制为空，D9）。
     */
    private Paint resolvePaint(Object style, String kind) {
        if (style instanceof Paint p)
            return p;
        if (style instanceof CanvasGradient gradient)
            return gradient.toPaint();
        if (style instanceof CanvasPattern pattern)
            return new PatternPaint(pattern.getImage(), pattern.getRepetition());
        throw new NopCanvas2DException("Unsupported " + kind + " style object: " + style);
    }

    private void requireGradient(ICanvasGradient gradient) {
        if (!(gradient instanceof CanvasGradient))
            throw new NopCanvas2DException("gradient must be created by createLinearGradient/createRadialGradient");
    }

    private void requirePattern(ICanvasPattern pattern) {
        if (!(pattern instanceof CanvasPattern))
            throw new NopCanvas2DException("pattern must be created by createPattern");
    }

    private void applyGraphicsState() {
        // setClip 会按当前 CTM 烘焙 user clip——必须先 identity 设 clip（设备空间形状），再设绘制变换
        g.setTransform(new AffineTransform());
        g.setClip(clipShape);
        g.setTransform(transform);
        g.setComposite(AlphaComposite.SrcOver.derive(globalAlpha));
    }

    private void applyStrokeState(Paint paint) {
        applyGraphicsState();
        g.setPaint(paint);
        g.setStroke(currentStroke());
    }

    private boolean shadowActive() {
        return shadowColor.getAlpha() > 0
                && (shadowBlur > 0 || shadowOffsetX != 0 || shadowOffsetY != 0);
    }

    /**
     * D11 阴影合成：轮廓按 CTM 绘制进离屏层（clip 已烘入），blur 在设备空间卷积（σ=blur/2），
     * offset 在设备空间平移合成（受 globalAlpha 影响），最后绘制本体。
     */
    private void paintWithShadow(Paint paint, Shape silhouette, boolean isStroke,
                                 java.util.function.Consumer<Graphics2D> mainPainter) {
        if (!shadowActive()) {
            if (isStroke) {
                applyStrokeState(paint);
                mainPainter.accept(g);
            } else {
                applyGraphicsState();
                g.setPaint(paint);
                mainPainter.accept(g);
            }
            return;
        }
        BufferedImage shadowLayer = renderShadowSilhouette(device -> {
            device.setPaint(shadowColor);
            if (isStroke) {
                device.setStroke(currentStroke());
                device.draw(silhouette);
            } else {
                device.fill(silhouette);
            }
        }, false);
        compositeShadow(shadowLayer);
        if (isStroke) {
            applyStrokeState(paint);
        } else {
            applyGraphicsState();
            g.setPaint(paint);
        }
        mainPainter.accept(g);
    }

    /**
     * 离屏轮廓：colorize=true 时（drawImage 路径）绘制后 SrcIn 着色为 shadowColor。
     */
    private BufferedImage renderShadowSilhouette(java.util.function.Consumer<Graphics2D> painter,
                                                 boolean colorize) {
        BufferedImage layer = new BufferedImage(image.getWidth(), image.getHeight(),
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D lg = layer.createGraphics();
        try {
            lg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            lg.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            lg.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            lg.setTransform(new AffineTransform());
            lg.setClip(clipShape);
            lg.setTransform(transform);
            painter.accept(lg);
            if (colorize) {
                lg.setTransform(new AffineTransform());
                lg.setClip(null);
                lg.setComposite(AlphaComposite.SrcIn);
                lg.setColor(shadowColor);
                lg.fill(new Rectangle2D.Double(0, 0, layer.getWidth(), layer.getHeight()));
            }
        } finally {
            lg.dispose();
        }
        if (shadowBlur > 0) {
            layer = gaussianBlur(layer, shadowBlur / 2f);
        }
        return layer;
    }

    private void compositeShadow(BufferedImage shadowLayer) {
        // D11：阴影受 clip 约束——合成时保持 clip（identity 下设 clip），模糊尾部不得越出裁剪区
        Composite saved = g.getComposite();
        AffineTransform savedTransform = (AffineTransform) transform.clone();
        Shape savedClip = clipShape;
        g.setComposite(AlphaComposite.SrcOver.derive(globalAlpha));
        g.setTransform(new AffineTransform());
        g.setClip(savedClip);
        g.drawImage(shadowLayer, Math.round(shadowOffsetX), Math.round(shadowOffsetY), null);
        g.setComposite(saved);
        g.setTransform(savedTransform);
    }

    /**
     * 高斯近似卷积：σ = shadowBlur/2（D11）；核半径 = ceil(2σ)。
     */
    private static BufferedImage gaussianBlur(BufferedImage source, float sigma) {
        if (sigma <= 0)
            return source;
        int radius = Math.max(1, (int) Math.ceil(sigma * 2));
        int size = radius * 2 + 1;
        float[] matrix = new float[size];
        float sum = 0;
        for (int i = 0; i < size; i++) {
            float d = i - radius;
            float v = (float) Math.exp(-(d * d) / (2 * sigma * sigma));
            matrix[i] = v;
            sum += v;
        }
        for (int i = 0; i < size; i++)
            matrix[i] /= sum;
        ConvolveOp horizontal = new ConvolveOp(new Kernel(size, 1, matrix), ConvolveOp.EDGE_NO_OP, null);
        ConvolveOp vertical = new ConvolveOp(new Kernel(1, size, matrix), ConvolveOp.EDGE_NO_OP, null);
        BufferedImage tmp = new BufferedImage(source.getWidth(), source.getHeight(),
                BufferedImage.TYPE_INT_ARGB);
        BufferedImage out = new BufferedImage(source.getWidth(), source.getHeight(),
                BufferedImage.TYPE_INT_ARGB);
        horizontal.filter(source, tmp);
        vertical.filter(tmp, out);
        return out;
    }

    private void requireFinite(String op, double... values) {
        for (double v : values) {
            if (!Double.isFinite(v))
                throw new NopCanvas2DException(op + "() requires finite arguments, got non-finite value");
        }
    }

    private record CtxState(AffineTransform transform, Shape clip, Object fillStyle, Object strokeStyle,
                            float lineWidth, int lineCap, int lineJoin, float miterLimit, float[] lineDash,
                            float lineDashOffset, float globalAlpha, TextMeasurer.ResolvedFont resolvedFont,
                            String textAlign, String textBaseline, float shadowBlur, Color shadowColor,
                            float shadowOffsetX, float shadowOffsetY) {
    }
}
