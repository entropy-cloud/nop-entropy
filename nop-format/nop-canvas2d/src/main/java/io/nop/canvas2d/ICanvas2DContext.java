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

    // ----- text（WI2 追加） -----

    /**
     * 解析并设置 CSS font 简写（D5/D6：非法串或缺字体显式抛 {@link NopCanvas2DException}；
     * 解析结果走缓存快路径）。line-height 未指定时派生为 width('国')。
     */
    void setFont(String cssFont);

    /** @param align "start" | "end" | "left" | "right" | "center"（D8：start≡left、end≡right） */
    void setTextAlign(String align);

    /** @param baseline "alphabetic" | "top" | "hanging" | "middle" | "ideographic" | "bottom" */
    void setTextBaseline(String baseline);

    /**
     * 度量文本宽度（不受 textAlign/textBaseline 影响；null 强转 "null"，空串宽 0）。
     */
    ITextMetrics measureText(String text);

    void fillText(String text, double x, double y);

    void strokeText(String text, double x, double y);

    // ----- gradients / patterns / shadows / images（WI3 追加） -----

    ICanvasGradient createLinearGradient(double x0, double y0, double x1, double y1);

    ICanvasGradient createRadialGradient(double x0, double y0, double r0, double x1, double y1, double r1);

    void setFillStyleGradient(ICanvasGradient gradient);

    void setStrokeStyleGradient(ICanvasGradient gradient);

    /** repetition: "repeat"（缺省）| "repeat-x" | "repeat-y" | "no-repeat"；源图为创建时快照（D10） */
    ICanvasPattern createPattern(java.awt.image.BufferedImage image, String repetition);

    void setFillStylePattern(ICanvasPattern pattern);

    void setStrokeStylePattern(ICanvasPattern pattern);

    void setShadowBlur(double blur);

    void setShadowColor(String cssColor);

    void setShadowOffsetX(double offset);

    void setShadowOffsetY(double offset);

    /** D11：drawImage 受阴影影响；D12：源矩形越界求交 + 目标同比例收缩 */
    void drawImage(java.awt.image.BufferedImage image, double dx, double dy);

    void drawImage(java.awt.image.BufferedImage image, double dx, double dy, double dw, double dh);

    void drawImage(java.awt.image.BufferedImage image, double sx, double sy, double sw, double sh,
                   double dx, double dy, double dw, double dh);

    /** D12：越界部分返回透明像素；w/h ≤ 0 抛错 */
    IImageData getImageData(int x, int y, int width, int height);

    /** D12：整块替换（wholesale 含 alpha 无混合），绕过变换/clip/alpha/阴影 */
    void putImageData(IImageData data, double dx, double dy);

    void putImageData(IImageData data, double dx, double dy, double dirtyX, double dirtyY,
                      double dirtyWidth, double dirtyHeight);
}
