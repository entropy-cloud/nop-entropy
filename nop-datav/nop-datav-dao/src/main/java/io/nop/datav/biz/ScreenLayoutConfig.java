package io.nop.datav.biz;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 大屏布局解析结果。{@code getScreenLayout} 的返回类型。
 *
 * <p>参见 {@code ai-dev/design/nop-datav/screen-design.md} §4 {@code getScreenLayout} API 契约。</p>
 *
 * <p>该类位于 nop-datav-dao 的 biz 包，仅使用基础 Java 类型（Map/List/Integer/Long/String），
 * 不引用服务层或前端类型，保持依赖方向正确（INopDatavScreenBiz 接口位于 dao 模块）。</p>
 */
public class ScreenLayoutConfig {

    private String screenId;
    private String screenName;
    private String displayName;
    private Canvas canvas;
    private Adaptation adaptation;
    private ScreenThemeConfig theme;
    private List<Widget> widgets;
    private long snapshotVersion;

    public String getScreenId() {
        return screenId;
    }

    public void setScreenId(String screenId) {
        this.screenId = screenId;
    }

    public String getScreenName() {
        return screenName;
    }

    public void setScreenName(String screenName) {
        this.screenName = screenName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public Canvas getCanvas() {
        return canvas;
    }

    public void setCanvas(Canvas canvas) {
        this.canvas = canvas;
    }

    public Adaptation getAdaptation() {
        return adaptation;
    }

    public void setAdaptation(Adaptation adaptation) {
        this.adaptation = adaptation;
    }

    /**
     * 解析后的结构化主题（D4-3，additive）。含命名色板（palette 缺省值已填充）+ 背景定义（type/value 缺省已应用）。
     *
     * <p>与 {@link Canvas#getBackgroundConfig()} 区分：本字段是解析产物，{@code Canvas.backgroundConfig}
     * 是原始配置透传（D4-1 契约不变，向后兼容）。</p>
     */
    public ScreenThemeConfig getTheme() {
        return theme;
    }

    public void setTheme(ScreenThemeConfig theme) {
        this.theme = theme;
    }

    public List<Widget> getWidgets() {
        return widgets == null ? Collections.emptyList() : widgets;
    }

    public void setWidgets(List<Widget> widgets) {
        this.widgets = widgets;
    }

    public long getSnapshotVersion() {
        return snapshotVersion;
    }

    public void setSnapshotVersion(long snapshotVersion) {
        this.snapshotVersion = snapshotVersion;
    }

    /** 画布定义：尺寸 + 适配模式 + 背景配置。 */
    public static class Canvas {
        private int width;
        private int height;
        private int adaptorMode;
        private Map<String, Object> backgroundConfig;

        public int getWidth() {
            return width;
        }

        public void setWidth(int width) {
            this.width = width;
        }

        public int getHeight() {
            return height;
        }

        public void setHeight(int height) {
            this.height = height;
        }

        public int getAdaptorMode() {
            return adaptorMode;
        }

        public void setAdaptorMode(int adaptorMode) {
            this.adaptorMode = adaptorMode;
        }

        public Map<String, Object> getBackgroundConfig() {
            return backgroundConfig;
        }

        public void setBackgroundConfig(Map<String, Object> backgroundConfig) {
            this.backgroundConfig = backgroundConfig;
        }
    }

    /** 适配基准：供前端计算缩放 transform。值为画布尺寸 + 适配模式。 */
    public static class Adaptation {
        private int baseWidth;
        private int baseHeight;
        private int adaptorMode;

        public int getBaseWidth() {
            return baseWidth;
        }

        public void setBaseWidth(int baseWidth) {
            this.baseWidth = baseWidth;
        }

        public int getBaseHeight() {
            return baseHeight;
        }

        public void setBaseHeight(int baseHeight) {
            this.baseHeight = baseHeight;
        }

        public int getAdaptorMode() {
            return adaptorMode;
        }

        public void setAdaptorMode(int adaptorMode) {
            this.adaptorMode = adaptorMode;
        }
    }

    /** widget 解析结果：定位 + 组件类型 + 数据绑定 + 组件配置。 */
    public static class Widget {
        private String widgetId;
        private String widgetName;
        private String displayName;
        private String componentType;
        private String datasetRefId;
        private int x;
        private int y;
        private int w;
        private int h;
        private int z;
        private Map<String, Object> widgetConfig;
        /**
         * widget 主题命名引用解析结果（D4-3）。当 {@code widgetConfig.theme} 存在时，
         * 其命名引用（如 {@code "primary"}）被解析期替换为屏幕级 palette 实际色值放入此字段。
         * {@code widgetConfig} 本身不被改写（styleOptions 也不被自动改写）。
         */
        private Map<String, Object> resolvedTheme;

        public String getWidgetId() {
            return widgetId;
        }

        public void setWidgetId(String widgetId) {
            this.widgetId = widgetId;
        }

        public String getWidgetName() {
            return widgetName;
        }

        public void setWidgetName(String widgetName) {
            this.widgetName = widgetName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public String getComponentType() {
            return componentType;
        }

        public void setComponentType(String componentType) {
            this.componentType = componentType;
        }

        public String getDatasetRefId() {
            return datasetRefId;
        }

        public void setDatasetRefId(String datasetRefId) {
            this.datasetRefId = datasetRefId;
        }

        public int getX() {
            return x;
        }

        public void setX(int x) {
            this.x = x;
        }

        public int getY() {
            return y;
        }

        public void setY(int y) {
            this.y = y;
        }

        public int getW() {
            return w;
        }

        public void setW(int w) {
            this.w = w;
        }

        public int getH() {
            return h;
        }

        public void setH(int h) {
            this.h = h;
        }

        public int getZ() {
            return z;
        }

        public void setZ(int z) {
            this.z = z;
        }

        public Map<String, Object> getWidgetConfig() {
            return widgetConfig;
        }

        public void setWidgetConfig(Map<String, Object> widgetConfig) {
            this.widgetConfig = widgetConfig;
        }

        public Map<String, Object> getResolvedTheme() {
            return resolvedTheme;
        }

        public void setResolvedTheme(Map<String, Object> resolvedTheme) {
            this.resolvedTheme = resolvedTheme;
        }
    }
}
