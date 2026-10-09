package io.nop.canvas2d;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Java2D 渲染后端：Canvas2D 语义到 Graphics2D 的翻译（WI1 范围：状态机 + 路径 + 绘制）。
 *
 * <p>实现裁定（plan 2359）：D1 ARGB 透明底、clearRect 绕过 alpha/合成（Clear 合成）；
 * D2 非法输入快速失败抛 {@link NopCanvas2DException}、空路径隐式 moveTo、AWT 异常包装不泄漏；
 * D3 fill/stroke/clip 不消费路径；D4 渲染 hints 固定为 ANTIALIAS_ON + STROKE_PURE（像素语义的一部分）。
 */
public class Java2DCanvas implements ICanvas2D, ICanvas2DContext {
    private final BufferedImage image;
    private final Graphics2D g;

    private AffineTransform transform = new AffineTransform();
    private Shape clipShape;
    private Paint fillPaint = Color.BLACK;
    private Paint strokePaint = Color.BLACK;
    private float lineWidth = 1f;
    private int lineCap = BasicStroke.CAP_BUTT;
    private int lineJoin = BasicStroke.JOIN_MITER;
    private float miterLimit = 10f;
    private float[] lineDash;
    private float lineDashOffset;
    private float globalAlpha = 1f;

    private Path2D.Double path;
    private boolean pathBegun;
    private int pathSegments;

    private final Deque<CtxState> stack = new ArrayDeque<>();

    public Java2DCanvas(int width, int height) {
        if (width <= 0 || height <= 0)
            throw new NopCanvas2DException("Canvas size must be positive, got: " + width + "x" + height);
        image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        g = image.createGraphics();
        // D4c：hints 是本模块像素语义的一部分，不得按调用点切换
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
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
        stack.push(new CtxState((AffineTransform) transform.clone(), clipShape, fillPaint, strokePaint,
                lineWidth, lineCap, lineJoin, miterLimit, lineDash, lineDashOffset, globalAlpha));
    }

    @Override
    public void restore() {
        if (stack.isEmpty())
            throw new NopCanvas2DException("restore() without matching save()");
        CtxState s = stack.pop();
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
        // Canvas 角度在 y 向下坐标系中顺时针增长；Java2D Arc2D 按数学方向取度数，取负号映射（spike 实证正确）
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
        applyState();
        g.fill(path);
    }

    @Override
    public void stroke() {
        requirePathForPainting("stroke()");
        if (pathSegments == 0)
            return;
        applyStrokeState();
        g.draw(path);
    }

    @Override
    public void clip() {
        requirePathForPainting("clip()");
        Area base = clipShape == null
                ? new Area(new Rectangle2D.Double(0, 0, image.getWidth(), image.getHeight()))
                : new Area(clipShape);
        base.intersect(new Area(path));
        clipShape = base;
    }

    @Override
    public void fillRect(double x, double y, double width, double height) {
        requireFinite("fillRect", x, y, width, height);
        applyState();
        g.fill(new Rectangle2D.Double(x, y, width, height));
    }

    @Override
    public void strokeRect(double x, double y, double width, double height) {
        requireFinite("strokeRect", x, y, width, height);
        applyStrokeState();
        g.draw(new Rectangle2D.Double(x, y, width, height));
    }

    @Override
    public void clearRect(double x, double y, double width, double height) {
        requireFinite("clearRect", x, y, width, height);
        // D1：Clear 合成恢复全透明，绕过 globalAlpha 与 fillStyle（clip/transform 仍生效）
        Composite saved = g.getComposite();
        applyState();
        g.setComposite(AlphaComposite.Clear);
        g.fill(new Rectangle2D.Double(x, y, width, height));
        g.setComposite(saved);
    }

    // ----- style -----

    @Override
    public void setFillStyleCss(String cssColor) {
        this.fillPaint = CssColors.parse(cssColor);
    }

    @Override
    public void setStrokeStyleCss(String cssColor) {
        this.strokePaint = CssColors.parse(cssColor);
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
                throw new NopCanvas2DException("lineDash segments must be non-negative, got: " + java.util.Arrays.toString(dash));
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

    // ----- internals -----

    private void ensurePath() {
        if (path == null)
            beginPath();
    }

    private void requirePathForPainting(String op) {
        if (!pathBegun)
            throw new NopCanvas2DException(op + " called before beginPath()");
    }

    private void applyState() {
        g.setTransform(transform);
        g.setClip(clipShape);
        g.setPaint(fillPaint);
        g.setComposite(AlphaComposite.SrcOver.derive(globalAlpha));
    }

    private void applyStrokeState() {
        applyState();
        g.setPaint(strokePaint);
        g.setStroke(new BasicStroke(lineWidth, lineCap, lineJoin, miterLimit, lineDash, lineDashOffset));
    }

    private void requireFinite(String op, double... values) {
        for (double v : values) {
            if (!Double.isFinite(v))
                throw new NopCanvas2DException(op + "() requires finite arguments, got non-finite value");
        }
    }

    private record CtxState(AffineTransform transform, Shape clip, Paint fillPaint, Paint strokePaint,
                            float lineWidth, int lineCap, int lineJoin, float miterLimit, float[] lineDash,
                            float lineDashOffset, float globalAlpha) {
    }
}
