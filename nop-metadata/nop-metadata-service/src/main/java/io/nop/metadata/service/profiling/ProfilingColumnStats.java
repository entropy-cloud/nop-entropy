package io.nop.metadata.service.profiling;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * 单列的剖析统计结果（{@link MetaTableProfiler} 产出）。承载：
 * <ul>
 *   <li>所有类型通用：totalCount / distinctCount / nullCount / emptyCount / min / max</li>
 *   <li>数值列：numericStats（min/max/mean/stddev/median/percentiles/distribution）</li>
 *   <li>字符串列：stringStats（minLength/maxLength/avgLength/topValues）</li>
 *   <li>unavailable：本列不可用统计字段名（显式列出，不静默跳过、不伪造）</li>
 * </ul>
 *
 * <p>设计 06 §3.2 / 架构基线 §2.7.2 D2：类型不适用的统计省略对应字段（不伪造）；
 * 不可用统计显式记入 unavailable。
 */
public class ProfilingColumnStats {

    private String columnName;
    private String dataType;
    private Long totalCount;
    private Long distinctCount;
    private Long nullCount;
    private Long emptyCount;
    private String minValue;
    private String maxValue;
    /** 数值列统计（null 表示非数值列）；填充与否由列类型适配决定（不伪造） */
    private Map<String, Object> numericStats;
    /** 字符串列统计（null 表示非字符串列）；填充与否由列类型适配决定（不伪造） */
    private Map<String, Object> stringStats;
    /** 本列不可用统计字段名（显式列出，不静默跳过） */
    private final List<String> unavailable = new LinkedList<>();

    public String getColumnName() {
        return columnName;
    }

    public void setColumnName(String columnName) {
        this.columnName = columnName;
    }

    public String getDataType() {
        return dataType;
    }

    public void setDataType(String dataType) {
        this.dataType = dataType;
    }

    public Long getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(Long totalCount) {
        this.totalCount = totalCount;
    }

    public Long getDistinctCount() {
        return distinctCount;
    }

    public void setDistinctCount(Long distinctCount) {
        this.distinctCount = distinctCount;
    }

    public Long getNullCount() {
        return nullCount;
    }

    public void setNullCount(Long nullCount) {
        this.nullCount = nullCount;
    }

    public Long getEmptyCount() {
        return emptyCount;
    }

    public void setEmptyCount(Long emptyCount) {
        this.emptyCount = emptyCount;
    }

    public String getMinValue() {
        return minValue;
    }

    public void setMinValue(String minValue) {
        this.minValue = minValue;
    }

    public String getMaxValue() {
        return maxValue;
    }

    public void setMaxValue(String maxValue) {
        this.maxValue = maxValue;
    }

    public Map<String, Object> getNumericStats() {
        return numericStats;
    }

    public void setNumericStats(Map<String, Object> numericStats) {
        this.numericStats = numericStats;
    }

    public Map<String, Object> getStringStats() {
        return stringStats;
    }

    public void setStringStats(Map<String, Object> stringStats) {
        this.stringStats = stringStats;
    }

    public List<String> getUnavailable() {
        return unavailable;
    }

    /** 标记本列某统计不可用（显式记录，不静默跳过）。 */
    public void markUnavailable(String fieldName) {
        if (!unavailable.contains(fieldName)) {
            unavailable.add(fieldName);
        }
    }

    /** 序列化为 columnStats JSON 数组中的一个元素 Map（省略 null 字段，不伪造）。 */
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("columnName", columnName);
        if (dataType != null) {
            m.put("dataType", dataType);
        }
        if (totalCount != null) {
            m.put("totalCount", totalCount);
        }
        if (distinctCount != null) {
            m.put("distinctCount", distinctCount);
        }
        if (nullCount != null) {
            m.put("nullCount", nullCount);
        }
        if (emptyCount != null) {
            m.put("emptyCount", emptyCount);
        }
        if (minValue != null) {
            m.put("min", minValue);
        }
        if (maxValue != null) {
            m.put("max", maxValue);
        }
        if (numericStats != null) {
            m.put("numericStats", numericStats);
        }
        if (stringStats != null) {
            m.put("stringStats", stringStats);
        }
        if (!unavailable.isEmpty()) {
            m.put("unavailable", unavailable);
        }
        return m;
    }
}
