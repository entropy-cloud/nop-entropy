package io.nop.datav.service.component;

import io.nop.api.core.exceptions.NopException;

import java.util.HashMap;
import java.util.Map;

import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_UNKNOWN_COMPONENT_TYPE;

/**
 * panelType dict 的 int 值与组件类型标识（String）之间的映射。
 *
 * <p>参见 {@code ai-dev/design/nop-datav/runtime-design.md} §1.3 panelType dict 与组件类型的映射。</p>
 *
 * <p>dict 值集（int 类型，与 {@code model/nop-datav.orm.xml} 中 {@code datav/panel-type} dict 同步）：
 * <ul>
 *   <li>0 = CHART → chart</li>
 *   <li>10 = TABLE → table</li>
 *   <li>20 = METRIC → stat-tile（语义等价，复用）</li>
 *   <li>30 = TEXT → text</li>
 *   <li>40 = CONTAINER → container</li>
 *   <li>50 = PIVOT_TABLE → pivot-table</li>
 *   <li>60 = MAP → map</li>
 *   <li>70 = IFRAME → iframe</li>
 * </ul>
 * </p>
 */
public final class PanelTypeMapping {

    public static final int TYPE_CHART = 0;
    public static final int TYPE_TABLE = 10;
    public static final int TYPE_METRIC = 20;
    public static final int TYPE_TEXT = 30;
    public static final int TYPE_CONTAINER = 40;
    public static final int TYPE_PIVOT_TABLE = 50;
    public static final int TYPE_MAP = 60;
    public static final int TYPE_IFRAME = 70;

    private static final Map<Integer, String> INT_TO_TYPE = new HashMap<>();
    private static final Map<String, Integer> TYPE_TO_INT = new HashMap<>();

    static {
        put(TYPE_CHART, PanelComponentTypes.CHART);
        put(TYPE_TABLE, PanelComponentTypes.TABLE);
        put(TYPE_METRIC, PanelComponentTypes.STAT_TILE);
        put(TYPE_TEXT, PanelComponentTypes.TEXT);
        put(TYPE_CONTAINER, PanelComponentTypes.CONTAINER);
        put(TYPE_PIVOT_TABLE, PanelComponentTypes.PIVOT_TABLE);
        put(TYPE_MAP, PanelComponentTypes.MAP);
        put(TYPE_IFRAME, PanelComponentTypes.IFRAME);
    }

    private static void put(int intValue, String typeCode) {
        INT_TO_TYPE.put(intValue, typeCode);
        TYPE_TO_INT.put(typeCode, intValue);
    }

    private PanelTypeMapping() {
    }

    /**
     * 将 panelType（int）转换为组件类型标识。
     *
     * @param panelType ORM 字段值，允许 null（null 视为未知，抛异常）
     * @return 组件类型标识（如 "chart"）
     * @throws NopException panelType 未在映射表中时抛 {@code ERR_DATAV_UNKNOWN_COMPONENT_TYPE}
     */
    public static String toComponentType(Integer panelType) {
        if (panelType == null) {
            throw new NopException(ERR_DATAV_UNKNOWN_COMPONENT_TYPE)
                    .param("componentType", "null");
        }
        String typeCode = INT_TO_TYPE.get(panelType);
        if (typeCode == null) {
            throw new NopException(ERR_DATAV_UNKNOWN_COMPONENT_TYPE)
                    .param("componentType", String.valueOf(panelType));
        }
        return typeCode;
    }

    /**
     * 将组件类型标识转换为 panelType（int）。供测试或反向查询使用。
     */
    public static int toPanelTypeInt(String componentType) {
        Integer value = TYPE_TO_INT.get(componentType);
        if (value == null) {
            throw new NopException(ERR_DATAV_UNKNOWN_COMPONENT_TYPE)
                    .param("componentType", componentType);
        }
        return value;
    }
}
