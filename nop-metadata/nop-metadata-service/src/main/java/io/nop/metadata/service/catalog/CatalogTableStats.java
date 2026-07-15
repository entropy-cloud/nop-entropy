package io.nop.metadata.service.catalog;

import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * 单张表的运行时统计收集结果（{@link MetaCatalogCollector} 产出，BizModel 据此构建 NopMetaCatalog 行）。
 *
 * <p>不可用统计（方言特定、首版未实现）记为对应字段 null，并在 {@link #unavailable} 显式列出字段名，
 * 由 BizModel 序列化进 {@code NopMetaCatalog.details} 的 {@code unavailable} 数组（不静默跳过、不伪造 0）。
 */
public class CatalogTableStats {

    private String tableName;
    private long rowCount;
    private Long sizeBytes;
    private Integer indexCount;
    private Integer partitionCount;
    private Timestamp lastModified;
    /** 不可用的统计字段名（如 sizeBytes/partitionCount/lastModified），显式列出，不静默跳过。 */
    private final List<String> unavailable = new LinkedList<>();
    /** 扩展/方言特定字段（如 DB 产品名），写入 details JSON。 */
    private final Map<String, Object> extras = new LinkedHashMap<>();

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

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(Long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public Integer getIndexCount() {
        return indexCount;
    }

    public void setIndexCount(Integer indexCount) {
        this.indexCount = indexCount;
    }

    public Integer getPartitionCount() {
        return partitionCount;
    }

    public void setPartitionCount(Integer partitionCount) {
        this.partitionCount = partitionCount;
    }

    public Timestamp getLastModified() {
        return lastModified;
    }

    public void setLastModified(Timestamp lastModified) {
        this.lastModified = lastModified;
    }

    public List<String> getUnavailable() {
        return unavailable;
    }

    public Map<String, Object> getExtras() {
        return extras;
    }

    /** 标记某统计字段不可用（加入 unavailable 列表，显式记录，不静默跳过）。 */
    public void markUnavailable(String fieldName) {
        if (!unavailable.contains(fieldName)) {
            unavailable.add(fieldName);
        }
    }
}
