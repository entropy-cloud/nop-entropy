package io.nop.metadata.service.profiling;


import io.nop.api.core.time.CoreMetrics;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * 单表的剖析统计快照（{@link MetaTableProfiler} 产出，BizModel 据此构建 NopMetaProfilingResult 行）。
 *
 * <p>设计 06 §3.2 / 架构基线 §2.7.2：
 * <ul>
 *   <li>tableStats.rowCount：便携 {@code COUNT(*)}（精确）</li>
 *   <li>tableStats.sizeBytes / lastModified：方言特定，首版不实现 → null + unavailable 显式标记（不伪造）</li>
 *   <li>columnStats：每列统计列表（{@link ProfilingColumnStats}）</li>
 *   <li>errors：单列剖析失败（SQL 异常）的列名 + 消息，不中断整表</li>
 * </ul>
 */
public class ProfilingSnapshot {

    private String tableName;
    private long rowCount;
    /** 表级不可用统计（sizeBytes/lastModified 等方言特定，首版 null+unavailable，不伪造） */
    private final List<String> tableUnavailable = new LinkedList<>();
    /** 表级扩展字段（DB 产品名等） */
    private final Map<String, Object> tableExtras = new LinkedHashMap<>();
    private final List<ProfilingColumnStats> columnStats = new ArrayList<>();
    /** 单列剖析失败收集（列名 + 错误消息），不中断整表 */
    private final List<Map<String, Object>> errors = new ArrayList<>();

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public long getRowCount() {
        return rowCount;
    }

    public void setRowCount(long rowCount) {
        this.rowCount = rowCount;
    }

    public List<String> getTableUnavailable() {
        return tableUnavailable;
    }

    public Map<String, Object> getTableExtras() {
        return tableExtras;
    }

    public List<ProfilingColumnStats> getColumnStats() {
        return columnStats;
    }

    public List<Map<String, Object>> getErrors() {
        return errors;
    }

    /** 标记表级某统计不可用（显式记录，不静默跳过）。 */
    public void markTableUnavailable(String fieldName) {
        if (!tableUnavailable.contains(fieldName)) {
            tableUnavailable.add(fieldName);
        }
    }

    /** 记录单列剖析失败（收集进 errors，不中断整表）。 */
    public void recordColumnError(String columnName, String message) {
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("columnName", columnName);
        err.put("error", message);
        errors.add(err);
    }

    /** 序列化 tableStats JSON（rowCount 真实 + unavailable 显式列出方言特定不可用项，不伪造）。 */
    public Map<String, Object> toTableStatsMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("rowCount", rowCount);
        if (!tableUnavailable.isEmpty()) {
            m.put("unavailable", tableUnavailable);
        }
        m.putAll(tableExtras);
        return m;
    }

    /** 序列化 columnStats JSON 数组。 */
    public List<Map<String, Object>> toColumnStatsList() {
        List<Map<String, Object>> list = new ArrayList<>(columnStats.size());
        for (ProfilingColumnStats cs : columnStats) {
            list.add(cs.toMap());
        }
        return list;
    }

    /** 表级时间戳（snapshotTime 由 BizModel 赋值，这里仅占位）。 */
    public Timestamp snapshotTime() {
        return CoreMetrics.currentTimestamp();
    }
}
