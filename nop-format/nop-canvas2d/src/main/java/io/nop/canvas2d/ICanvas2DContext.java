package io.nop.canvas2d;

/**
 * Canvas2D 上下文接口，覆盖 zrender 实际使用的调用面（WI1：状态机 + 路径 + 绘制；不追完整 W3C 规范）。
 * WI2 将追加文本与度量（font 解析、textAlign/textBaseline、fillText/strokeText/measureText），
 * WI3 将追加渐变/图案/阴影/图像与 getImageData/putImageData——接口追加式生长，不封印。
 *
 * <p>语义约定（plan 2359 Adjudications）：
 * <ul>
 *   <li>D1：画布为 ARGB 透明底；clearRect 恢复全透明，不受 globalAlpha/合成模式影响（受 clip 与 transform 影响）</li>
 *   <li>D2：对非法输入（NaN/越界/非法枚举/负半径等）快速失败抛 {@link NopCanvas2DException}，
 *       与 Canvas 规范的静默忽略刻意分歧；路径操作在空路径上按 Canvas 语义隐式 moveTo</li>
 *   <li>D3：fill()/stroke()/clip() 不消费路径，路径仅由 beginPath() 重置</li>
 * </ul>
 */
public interface ICanvas2DContext {
    // ----- state stack -----

    void save();

    void restore();

    // ----- transforms -----

    void translate(double x, double y);

    void scale(double x, double y);

    void rotate(double angle);

    void transform(double a, double b, double c, double d, double e, double f);

    void setTransform(double a, double b, double c, double d, double e, double f);

    // ----- path construction -----

    void beginPath();

    void closePath();

    void moveTo(double x, double y);

    void lineTo(double x, double y);

    void bezierCurveTo(double cp1x, double cp1y, double cp2x, double cp2y, double x, double y);

    void quadraticCurveTo(double cpx, double cpy, double x, double y);

    void arc(double x, double y, double radius, double startAngle, double endAngle, boolean anticlockwise);

    void arcTo(double x1, double y1, double x2, double y2, double radius);

    void ellipse(double x, double y, double radiusX, double radiusY, double rotation,
                 double startAngle, double endAngle, boolean anticlockwise);

    void rect(double x, double y, double width, double height);

    // ----- painting -----

    void fill();

    void stroke();

    void clip();

    void fillRect(double x, double y, double width, double height);

    void strokeRect(double x, double y, double width, double height);

    void clearRect(double x, double y, double width, double height);

    // ----- style -----

    void setFillStyleCss(String cssColor);

    void setStrokeStyleCss(String cssColor);

    void setLineWidth(double width);

    /** @param cap "butt" | "round" | "square" */
    void setLineCap(String cap);

    /** @param join "miter" | "round" | "bevel" */
    void setLineJoin(String join);

    void setMiterLimit(double limit);

    /** 奇数长度按 Canvas 语义重复末段偶数化；含负值段或非有限值抛 {@link NopCanvas2DException} */
    void setLineDash(double... dash);

    void setLineDashOffset(double offset);

    void setGlobalAlpha(double alpha);
}
