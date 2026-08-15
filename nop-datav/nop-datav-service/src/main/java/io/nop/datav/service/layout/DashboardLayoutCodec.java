package io.nop.datav.service.layout;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.json.JsonTool;
import io.nop.datav.dao.entity.NopDatavPanel;
import io.nop.datav.service.component.PanelTypeMapping;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.nop.datav.service.NopDatavErrors.ARG_DASHBOARD_ID;
import static io.nop.datav.service.NopDatavErrors.ARG_LENGTH;
import static io.nop.datav.service.NopDatavErrors.ARG_MAX_LENGTH;
import static io.nop.datav.service.NopDatavErrors.ARG_PANEL_ID;
import static io.nop.datav.service.NopDatavErrors.ARG_REASON;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_INVALID_LAYOUT;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_INVALID_PANEL_CONFIG;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_LAYOUT_CONFIG_OVERFLOW;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_LAYOUT_DUPLICATE_PANEL_ID;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_PANEL_CONFIG_OVERFLOW;

/**
 * flux dashboard editor 布局 JSON（DashboardLayoutSchema）与 nop-datav 归一化模型的双向编解码器。
 *
 * <p>契约（裁定见 {@code ai-dev/design/nop-datav/runtime-design.md} §九）：面板身份映射、类型词表
 * （恒等映射 + 复用 {@link PanelTypeMapping}）、几何/网格参数存放（layoutConfig 结构钉死）、存量无几何
 * 默认布局合成、props ↔ panelConfig 区域映射（dataBinding 唯一保护区）、保存载荷形态（布局对象）、
 * 绑定一等描述（source=datasetRef:&lt;id&gt;，保存永不消费）、roundtrip 等价判据。</p>
 *
 * <p>纯函数类：无 dao/状态依赖，全部校验 fail-fast（无静默跳过，rule #24）。容量校验对齐 json-4000
 * 列上界，超限显式报错、绝不截断。</p>
 */
public final class DashboardLayoutCodec {

    // ---- 布局 JSON 键（flux DashboardLayoutSchema） ----
    public static final String KEY_TYPE = "type";
    public static final String KEY_PANELS = "panels";
    public static final String KEY_COLS = "cols";
    public static final String KEY_ROW_HEIGHT = "rowHeight";
    public static final String KEY_GAP = "gap";
    public static final String KEY_HEIGHT = "height";
    public static final String KEY_ID = "id";
    public static final String KEY_TITLE = "title";
    public static final String KEY_PROPS = "props";
    public static final String KEY_SOURCE = "source";
    public static final String KEY_X = "x";
    public static final String KEY_Y = "y";
    public static final String KEY_W = "w";
    public static final String KEY_H = "h";

    /** 布局顶层 type 标记值（导出携带；保存如出现则忽略）。 */
    public static final String LAYOUT_TYPE_DASHBOARD = "dashboard";

    /** 数据绑定一等描述前缀（§9.9）：source = "datasetRef:&lt;datasetRefId&gt;"。 */
    public static final String SOURCE_PREFIX_DATASET_REF = "datasetRef:";

    /** panelConfig 唯一保护区（§9.6）：不可经布局保存写入/改写/丢失。 */
    public static final String REGION_DATA_BINDING = "dataBinding";

    // ---- 网格缺省值（§9.5，flux 缺省值）与合成参数 ----
    public static final int DEFAULT_COLS = 12;
    public static final int DEFAULT_ROW_HEIGHT = 40;
    public static final int DEFAULT_GAP = 8;
    public static final int SYNTHESIZED_PANEL_HEIGHT = 4;

    // ---- 列上界（json-4000 域 + 名字列 precision） ----
    public static final int JSON_MAX_LENGTH = 4000;
    public static final int TITLE_MAX_LENGTH = 200;
    public static final int PANEL_NAME_MAX_LENGTH = 100;

    private static final Set<String> KNOWN_LAYOUT_KEYS = Set.of(
            KEY_COLS, KEY_ROW_HEIGHT, KEY_GAP, KEY_HEIGHT, KEY_PANELS);

    private DashboardLayoutCodec() {
    }

    // ==================== 导出方向（§9.2/9.3/9.4/9.5/9.6/9.9） ====================

    /**
     * 按契约产出 flux 编辑器可直接加载的布局 JSON（编辑态 live 数据）。
     *
     * <p>面板顺序 = 调用方传入的 sortOrder 序；无几何面板按 §9.5 默认布局合成（x=0, w=cols, h=4，
     * 堆叠游标从有几何面板的 max(y+h) 起）；props 去除 dataBinding 保护区；绑定面板携带
     * source=datasetRef:&lt;datasetRefId&gt;。</p>
     *
     * @param layoutConfigJson 看板 layoutConfig（json-4000；null/空视为无布局；解析失败或网格键类型非法显式报错）
     * @param panels           按 sortOrder 排序的面板行
     */
    public static Map<String, Object> buildExportLayout(String layoutConfigJson, List<NopDatavPanel> panels) {
        Map<String, Object> layoutConfig = parseLayoutConfig(layoutConfigJson);
        int cols = requireGridParam(layoutConfig, KEY_COLS, DEFAULT_COLS);
        int rowHeight = requireGridParam(layoutConfig, KEY_ROW_HEIGHT, DEFAULT_ROW_HEIGHT);
        int gap = requireGridParam(layoutConfig, KEY_GAP, DEFAULT_GAP);
        Integer height = optionalGridParam(layoutConfig, KEY_HEIGHT);

        // 合成堆叠游标：有几何面板的 max(y+h)（仅统计实际被消费的几何条目）
        int cursor = 0;
        for (NopDatavPanel panel : panels) {
            Map<String, Integer> g = consumeStoredGeometry(layoutConfig, panel.getPanelId());
            if (g != null) {
                cursor = Math.max(cursor, g.get(KEY_Y) + g.get(KEY_H));
            }
        }

        List<Map<String, Object>> panelJsons = new ArrayList<>(panels.size());
        for (NopDatavPanel panel : panels) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put(KEY_ID, panel.getPanelId());
            m.put(KEY_TYPE, PanelTypeMapping.toComponentType(panel.getPanelType()));
            m.put(KEY_TITLE, exportTitle(panel));
            Map<String, Integer> g = consumeStoredGeometry(layoutConfig, panel.getPanelId());
            if (g != null) {
                m.put(KEY_X, g.get(KEY_X));
                m.put(KEY_Y, g.get(KEY_Y));
                m.put(KEY_W, g.get(KEY_W));
                m.put(KEY_H, g.get(KEY_H));
            } else {
                // §9.5 默认布局合成：x=0, w=cols, h=4，按序堆叠（首次 flux 保存后固化为存储几何）
                m.put(KEY_X, 0);
                m.put(KEY_Y, cursor);
                m.put(KEY_W, cols);
                m.put(KEY_H, SYNTHESIZED_PANEL_HEIGHT);
                cursor += SYNTHESIZED_PANEL_HEIGHT;
            }
            Map<String, Object> props = propsWithoutProtectedRegion(panel);
            if (props != null) {
                m.put(KEY_PROPS, props);
            }
            if (panel.getDatasetRefId() != null) {
                m.put(KEY_SOURCE, SOURCE_PREFIX_DATASET_REF + panel.getDatasetRefId());
            }
            panelJsons.add(m);
        }

        Map<String, Object> layout = new LinkedHashMap<>();
        layout.put(KEY_TYPE, LAYOUT_TYPE_DASHBOARD);
        layout.put(KEY_COLS, cols);
        layout.put(KEY_ROW_HEIGHT, rowHeight);
        layout.put(KEY_GAP, gap);
        if (height != null) {
            layout.put(KEY_HEIGHT, height);
        }
        layout.put(KEY_PANELS, panelJsons);
        return layout;
    }

    /** §9.2：导出 title ← displayName 非空取 displayName，否则 panelName。 */
    private static String exportTitle(NopDatavPanel panel) {
        String displayName = panel.getDisplayName();
        return displayName != null && !displayName.isEmpty() ? displayName : panel.getPanelName();
    }

    /** §9.6：props = panelConfig 解析对象去除 dataBinding；空（null 或去除后为空）→ 返回 null（省略 props）。 */
    private static Map<String, Object> propsWithoutProtectedRegion(NopDatavPanel panel) {
        Map<String, Object> parsed = parsePanelConfigMap(panel.getPanelConfig(), panel.getPanelId());
        if (parsed == null || parsed.isEmpty()) {
            return null;
        }
        Map<String, Object> props = new LinkedHashMap<>(parsed);
        props.remove(REGION_DATA_BINDING);
        return props.isEmpty() ? null : props;
    }

    // ==================== 保存方向（§9.2/9.4/9.6/9.7） ====================

    /** 保存载荷解析结果：面板规格列表 + 可选网格参数（缺省表示「保留不动」）。 */
    public static final class SaveLayoutSpec {
        private final List<PanelSpec> panels;
        private final Integer cols;
        private final Integer rowHeight;
        private final Integer gap;
        private final Integer height;

        SaveLayoutSpec(List<PanelSpec> panels, Integer cols, Integer rowHeight, Integer gap, Integer height) {
            this.panels = panels;
            this.cols = cols;
            this.rowHeight = rowHeight;
            this.gap = gap;
            this.height = height;
        }

        public List<PanelSpec> getPanels() {
            return panels;
        }

        public Integer getCols() {
            return cols;
        }

        public Integer getRowHeight() {
            return rowHeight;
        }

        public Integer getGap() {
            return gap;
        }

        public Integer getHeight() {
            return height;
        }
    }

    /** 单个面板的载荷规格（已完成结构/几何/类型校验）。source 为只读回显字段，解析阶段即丢弃（§9.9）。 */
    public static final class PanelSpec {
        private final String id;
        private final String componentType;
        private final int panelTypeInt;
        private final String title;
        private final int x;
        private final int y;
        private final int w;
        private final int h;
        private final Map<String, Object> props;

        PanelSpec(String id, String componentType, int panelTypeInt, String title,
                  int x, int y, int w, int h, Map<String, Object> props) {
            this.id = id;
            this.componentType = componentType;
            this.panelTypeInt = panelTypeInt;
            this.title = title;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.props = props;
        }

        public String getId() {
            return id;
        }

        public String getComponentType() {
            return componentType;
        }

        public int getPanelTypeInt() {
            return panelTypeInt;
        }

        public String getTitle() {
            return title;
        }

        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }

        public int getW() {
            return w;
        }

        public int getH() {
            return h;
        }

        public Map<String, Object> getProps() {
            return props;
        }
    }

    /**
     * 解析并校验保存载荷（布局对象形态，§9.7）：panels 必填数组（可为空=清空）；网格参数可选
     * （含则校验并更新、缺省保留）；面板 id 非空、类型可映射、几何合法、title ≤ 200；props 可选对象。
     * 载荷内重复 id 显式报错；未知类型经 {@link PanelTypeMapping} fail-fast。
     */
    public static SaveLayoutSpec parseSavePayload(Map<String, Object> layout) {
        if (layout == null) {
            throw invalidLayout("layout payload is required");
        }
        Object panelsObj = layout.get(KEY_PANELS);
        if (!(panelsObj instanceof List)) {
            throw invalidLayout("'panels' must be an array");
        }
        List<?> rawPanels = (List<?>) panelsObj;

        Integer cols = optionalPayloadGridParam(layout, KEY_COLS, 1);
        Integer rowHeight = optionalPayloadGridParam(layout, KEY_ROW_HEIGHT, 1);
        Integer gap = optionalPayloadGridParam(layout, KEY_GAP, 0);
        Integer height = optionalPayloadGridParam(layout, KEY_HEIGHT, 0);

        List<PanelSpec> specs = new ArrayList<>(rawPanels.size());
        Set<String> seenIds = new LinkedHashSet<>();
        for (int i = 0; i < rawPanels.size(); i++) {
            Object raw = rawPanels.get(i);
            if (!(raw instanceof Map)) {
                throw invalidLayout("panels[" + i + "] must be an object");
            }
            Map<?, ?> m = (Map<?, ?>) raw;

            String id = requireString(m, KEY_ID, i);
            if (id.isEmpty()) {
                throw invalidLayout("panels[" + i + "].id must be non-empty");
            }
            if (!seenIds.add(id)) {
                throw new NopException(ERR_DATAV_LAYOUT_DUPLICATE_PANEL_ID).param(ARG_PANEL_ID, id);
            }

            String type = requireString(m, KEY_TYPE, i);
            if (type.isEmpty()) {
                throw invalidLayout("panels[" + i + "].type must be non-empty");
            }
            // 未知类型（含 flux html 与装饰/媒体 6 类）经 PanelTypeMapping fail-fast（§9.3）
            int panelTypeInt = PanelTypeMapping.toPanelTypeInt(type);

            String title = m.get(KEY_TITLE) == null ? null : requireString(m, KEY_TITLE, i);
            if (title != null && title.length() > TITLE_MAX_LENGTH) {
                throw invalidLayout("panels[" + i + "].title length " + title.length()
                        + " exceeds maximum " + TITLE_MAX_LENGTH);
            }

            int x = requireGeometryInt(m, KEY_X, i, 0);
            int y = requireGeometryInt(m, KEY_Y, i, 0);
            int w = requireGeometryInt(m, KEY_W, i, 1);
            int h = requireGeometryInt(m, KEY_H, i, 1);

            Map<String, Object> props = null;
            Object propsObj = m.get(KEY_PROPS);
            if (propsObj != null) {
                if (!(propsObj instanceof Map)) {
                    throw invalidLayout("panels[" + i + "].props must be an object");
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> propsMap = (Map<String, Object>) propsObj;
                props = propsMap;
            }

            specs.add(new PanelSpec(id, type, panelTypeInt, title, x, y, w, h, props));
        }
        return new SaveLayoutSpec(specs, cols, rowHeight, gap, height);
    }

    /**
     * 重建 layoutConfig JSON（§9.4）：已知键按载荷网格参数（缺省保留既有值/缺省值）与最终面板几何写回；
     * 未知遗留顶层键保留；序列化超 4000 显式报错（不截断）。
     *
     * @param finalPanelIds 与 spec.panels 同序的最终 panelId 列表（既有 id 或服务端新生成 id）
     */
    public static String buildLayoutConfigJson(String existingLayoutConfigJson, SaveLayoutSpec spec,
                                               List<String> finalPanelIds) {
        Map<String, Object> existing = parseLayoutConfig(existingLayoutConfigJson);
        Map<String, Object> result = new LinkedHashMap<>();
        // 未知遗留键保留（增量钉死而非清场）
        for (Map.Entry<String, Object> entry : existing.entrySet()) {
            if (!KNOWN_LAYOUT_KEYS.contains(entry.getKey())) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        result.put(KEY_COLS, spec.getCols() != null ? spec.getCols()
                : requireGridParam(existing, KEY_COLS, DEFAULT_COLS));
        result.put(KEY_ROW_HEIGHT, spec.getRowHeight() != null ? spec.getRowHeight()
                : requireGridParam(existing, KEY_ROW_HEIGHT, DEFAULT_ROW_HEIGHT));
        result.put(KEY_GAP, spec.getGap() != null ? spec.getGap()
                : requireGridParam(existing, KEY_GAP, DEFAULT_GAP));
        if (spec.getHeight() != null) {
            result.put(KEY_HEIGHT, spec.getHeight());
        } else {
            Integer existingHeight = optionalGridParam(existing, KEY_HEIGHT);
            if (existingHeight != null) {
                result.put(KEY_HEIGHT, existingHeight);
            }
        }

        Map<String, Object> panelsMap = new LinkedHashMap<>();
        List<PanelSpec> panels = spec.getPanels();
        for (int i = 0; i < panels.size(); i++) {
            PanelSpec panel = panels.get(i);
            Map<String, Integer> geom = new LinkedHashMap<>();
            geom.put(KEY_X, panel.getX());
            geom.put(KEY_Y, panel.getY());
            geom.put(KEY_W, panel.getW());
            geom.put(KEY_H, panel.getH());
            panelsMap.put(finalPanelIds.get(i), geom);
        }
        result.put(KEY_PANELS, panelsMap);

        String json = JsonTool.stringify(result);
        if (json.length() > JSON_MAX_LENGTH) {
            throw new NopException(ERR_DATAV_LAYOUT_CONFIG_OVERFLOW)
                    .param(ARG_LENGTH, json.length())
                    .param(ARG_MAX_LENGTH, JSON_MAX_LENGTH);
        }
        return json;
    }

    /**
     * props 写回 panelConfig（§9.6）：props 缺省 → panelConfig 原样不动（返回既有 JSON）；
     * props 存在 → panelConfig := props（去 dataBinding）∪ 既有 dataBinding（若有）；空结果存 null；
     * 序列化超 4000 显式报错（不截断）。
     */
    public static String mergePanelConfig(String existingPanelConfigJson, Map<String, Object> props,
                                          String panelId) {
        if (props == null) {
            return existingPanelConfigJson;
        }
        Map<String, Object> merged = new LinkedHashMap<>(props);
        merged.remove(REGION_DATA_BINDING);
        Map<String, Object> existing = parsePanelConfigMap(existingPanelConfigJson, panelId);
        if (existing != null) {
            Object binding = existing.get(REGION_DATA_BINDING);
            if (binding != null) {
                merged.put(REGION_DATA_BINDING, binding);
            }
        }
        String json = merged.isEmpty() ? null : JsonTool.stringify(merged);
        if (json != null && json.length() > JSON_MAX_LENGTH) {
            throw new NopException(ERR_DATAV_PANEL_CONFIG_OVERFLOW)
                    .param(ARG_PANEL_ID, panelId)
                    .param(ARG_LENGTH, json.length())
                    .param(ARG_MAX_LENGTH, JSON_MAX_LENGTH);
        }
        return json;
    }

    /** §9.2：新建面板 panelName 派生——title 截断 100；空 title 取 panel-{数组下标+1}。 */
    public static String derivePanelName(String title, int index) {
        if (title != null && !title.isEmpty()) {
            return title.length() <= PANEL_NAME_MAX_LENGTH ? title
                    : title.substring(0, PANEL_NAME_MAX_LENGTH);
        }
        return "panel-" + (index + 1);
    }

    // ==================== JSON 解析与网格/几何校验 ====================

    /** layoutConfig 解析：null/空 → 空 Map；解析失败显式报错（§9.5，非静默降级）。 */
    private static Map<String, Object> parseLayoutConfig(String layoutConfigJson) {
        if (layoutConfigJson == null || layoutConfigJson.isEmpty()) {
            return new LinkedHashMap<>();
        }
        Object parsed;
        try {
            parsed = JsonTool.parse(layoutConfigJson);
        } catch (RuntimeException e) {
            throw invalidLayout("layoutConfig JSON parse failed: " + e.getMessage());
        }
        if (parsed == null) {
            return new LinkedHashMap<>();
        }
        if (!(parsed instanceof Map)) {
            throw invalidLayout("layoutConfig must be a JSON object");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) parsed;
        return map;
    }

    /** panelConfig 解析：null/空 → null；解析失败抛 ERR_DATAV_INVALID_PANEL_CONFIG（既有先例语义）。 */
    private static Map<String, Object> parsePanelConfigMap(String panelConfigJson, String panelId) {
        if (panelConfigJson == null || panelConfigJson.isEmpty()) {
            return null;
        }
        Object parsed;
        try {
            parsed = JsonTool.parse(panelConfigJson);
        } catch (RuntimeException e) {
            throw new NopException(ERR_DATAV_INVALID_PANEL_CONFIG)
                    .param(ARG_PANEL_ID, panelId)
                    .param(ARG_REASON, e.getMessage());
        }
        if (parsed == null) {
            return null;
        }
        if (!(parsed instanceof Map)) {
            throw new NopException(ERR_DATAV_INVALID_PANEL_CONFIG).param(ARG_PANEL_ID, panelId);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) parsed;
        return map;
    }

    /** 读取已存网格参数（带缺省）；键存在但类型/取值非法显式报错。 */
    private static int requireGridParam(Map<String, Object> layoutConfig, String key, int defaultValue) {
        if (!layoutConfig.containsKey(key) || layoutConfig.get(key) == null) {
            return defaultValue;
        }
        Integer value = asGridInt(layoutConfig.get(key), key, 0);
        if (value < minGridValue(key)) {
            throw invalidLayout("layoutConfig." + key + " must be >= " + minGridValue(key) + ", got " + value);
        }
        return value;
    }

    /** 读取已存可选网格参数（height）；键存在但类型/取值非法显式报错。 */
    private static Integer optionalGridParam(Map<String, Object> layoutConfig, String key) {
        if (!layoutConfig.containsKey(key) || layoutConfig.get(key) == null) {
            return null;
        }
        Integer value = asGridInt(layoutConfig.get(key), key, 0);
        if (value < minGridValue(key)) {
            throw invalidLayout("layoutConfig." + key + " must be >= " + minGridValue(key) + ", got " + value);
        }
        return value;
    }

    /** 载荷可选网格参数校验：存在则必须为合法整数且 ≥ 最小值；缺省返回 null（保留语义）。 */
    private static Integer optionalPayloadGridParam(Map<String, Object> layout, String key, int minValue) {
        if (!layout.containsKey(key) || layout.get(key) == null) {
            return null;
        }
        Integer value = asGridInt(layout.get(key), key, minValue);
        if (value < minValue) {
            throw invalidLayout("'" + key + "' must be >= " + minValue + ", got " + value);
        }
        return value;
    }

    /** 网格参数最小合法值：cols/rowHeight ≥ 1，gap/height ≥ 0。 */
    private static int minGridValue(String key) {
        return KEY_COLS.equals(key) || KEY_ROW_HEIGHT.equals(key) ? 1 : 0;
    }

    /**
     * 读取并校验单个面板的已存几何（结构/取值非法显式报错）；layoutConfig.panels 非对象或面板无条目
     * 返回 null（无几何 → 合成路径）。仅校验实际被消费的条目，悬空遗留键跳过。
     */
    static Map<String, Integer> consumeStoredGeometry(Map<String, Object> layoutConfig, String panelId) {
        Object panelsObj = layoutConfig.get(KEY_PANELS);
        if (!(panelsObj instanceof Map)) {
            return null;
        }
        Object stored = ((Map<?, ?>) panelsObj).get(panelId);
        if (stored == null) {
            return null;
        }
        if (!(stored instanceof Map)) {
            throw invalidLayout("layoutConfig.panels[" + panelId + "] must be an object");
        }
        Map<?, ?> m = (Map<?, ?>) stored;
        Map<String, Integer> geom = new LinkedHashMap<>();
        geom.put(KEY_X, storedGeomInt(m, KEY_X, panelId, 0));
        geom.put(KEY_Y, storedGeomInt(m, KEY_Y, panelId, 0));
        geom.put(KEY_W, storedGeomInt(m, KEY_W, panelId, 1));
        geom.put(KEY_H, storedGeomInt(m, KEY_H, panelId, 1));
        return geom;
    }

    private static int storedGeomInt(Map<?, ?> m, String key, String panelId, int minValue) {
        Object v = m.get(key);
        if (!(v instanceof Number)) {
            throw invalidLayout("layoutConfig.panels[" + panelId + "]." + key + " must be an integer");
        }
        int value = integralInt((Number) v);
        if (value < minValue) {
            throw invalidLayout("layoutConfig.panels[" + panelId + "]." + key
                    + " must be >= " + minValue + ", got " + value);
        }
        return value;
    }

    // ==================== 载荷字段读取与校验 ====================

    private static String requireString(Map<?, ?> m, String key, int index) {
        Object v = m.get(key);
        if (v == null) {
            throw invalidLayout("panels[" + index + "]." + key + " is required");
        }
        if (!(v instanceof String)) {
            throw invalidLayout("panels[" + index + "]." + key + " must be a string");
        }
        return (String) v;
    }

    private static int requireGeometryInt(Map<?, ?> m, String key, int index, int minValue) {
        Object v = m.get(key);
        if (!(v instanceof Number)) {
            throw invalidLayout("panels[" + index + "]." + key + " must be an integer");
        }
        int value = integralInt((Number) v);
        if (value < minValue) {
            throw invalidLayout("panels[" + index + "]." + key + " must be >= " + minValue + ", got " + value);
        }
        return value;
    }

    /** 整数语义读取（拒绝小数：intValue != doubleValue 视为非法）。 */
    private static int integralInt(Number n) {
        if (n.doubleValue() != n.intValue()) {
            throw invalidLayout("non-integer grid/geometry value: " + n);
        }
        return n.intValue();
    }

    private static Integer asGridInt(Object v, String key, int minValue) {
        if (!(v instanceof Number)) {
            throw invalidLayout(key + " must be an integer, got: "
                    + (v == null ? "null" : v.getClass().getSimpleName()));
        }
        int value = integralInt((Number) v);
        if (value < minValue) {
            throw invalidLayout(key + " must be >= " + minValue + ", got " + value);
        }
        return value;
    }

    private static NopException invalidLayout(String reason) {
        return new NopException(ERR_DATAV_INVALID_LAYOUT).param(ARG_REASON, reason);
    }

    /** 供调用方构造「面板 id 属于其他看板」错误（§9.2，需 dashboardId 上下文）。 */
    public static NopException foreignPanelId(String panelId, String dashboardId) {
        return new NopException(io.nop.datav.service.NopDatavErrors.ERR_DATAV_LAYOUT_FOREIGN_PANEL_ID)
                .param(ARG_PANEL_ID, panelId)
                .param(ARG_DASHBOARD_ID, dashboardId);
    }
}
