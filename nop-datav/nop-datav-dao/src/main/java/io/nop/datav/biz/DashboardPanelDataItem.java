package io.nop.datav.biz;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 批量面板查询单面板条目。{@code getDashboardData} 响应 {@link DashboardDataResult} 的组成元素。
 *
 * <p>参见 {@code ai-dev/design/nop-datav/runtime-design.md} §4.3 响应形态与面板纳入集。</p>
 *
 * <p>两级失败语义：面板级失败（datasetRef 失效、数据集不存在、dsType 非 sql、SQL 执行失败、未知组件类型）
 * 置 {@code success=false} 并携带 {@code errorCode}/{@code errorMessage}，其余面板正常返回；看板级失败
 * （看板不存在、panelIds 越权引用、面板数超上限、筛选类型不匹配）不产出条目，由整体响应显式抛错。</p>
 *
 * <p>成功路径由 {@link #success(PanelDataResult)} 从既有 {@link PanelDataResult} 转换（字段语义一致）；
 * 无数据集面板（text/iframe）为 {@code success=true, hasDataset=false} 条目，与逐面板语义一致。</p>
 */
public class DashboardPanelDataItem {

    private final String panelId;
    private final String componentType;
    private final boolean success;
    private final boolean hasDataset;
    private final List<String> columns;
    private final List<Map<String, Object>> rows;
    private final String errorCode;
    private final String errorMessage;

    /**
     * 从面板查询成功结果构造条目。
     */
    public static DashboardPanelDataItem success(PanelDataResult result) {
        return new DashboardPanelDataItem(result.getPanelId(), result.getComponentType(), true,
                result.isHasDataset(), result.getColumns(), result.getRows(), null, null);
    }

    /**
     * 构造面板级失败条目。
     *
     * @param panelId       面板 ID
     * @param errorCode     NopException 错误码字符串（如 {@code nop.err.datav.dataset-ref-not-found}）
     * @param errorMessage  异常消息
     */
    public static DashboardPanelDataItem failure(String panelId, String errorCode, String errorMessage) {
        return new DashboardPanelDataItem(panelId, null, false, false,
                Collections.emptyList(), Collections.emptyList(), errorCode, errorMessage);
    }

    private DashboardPanelDataItem(String panelId, String componentType, boolean success,
                                   boolean hasDataset, List<String> columns, List<Map<String, Object>> rows,
                                   String errorCode, String errorMessage) {
        this.panelId = panelId;
        this.componentType = componentType;
        this.success = success;
        this.hasDataset = hasDataset;
        this.columns = columns == null ? Collections.emptyList() : columns;
        this.rows = rows == null ? Collections.emptyList() : rows;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    /**
     * 面板 ID。
     */
    public String getPanelId() {
        return panelId;
    }

    /**
     * 组件类型标识（如 "chart"/"text"）。失败条目为 null（无法可靠确定时）。
     */
    public String getComponentType() {
        return componentType;
    }

    /**
     * 面板查询是否成功。false 时携带 {@link #getErrorCode()} / {@link #getErrorMessage()}。
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * 是否有数据集绑定。text/iframe 等无数据集组件返回 false（成功条目）。
     */
    public boolean isHasDataset() {
        return hasDataset;
    }

    /**
     * 查询结果字段名列表。成功且有数据集时有意义，否则为空列表。
     */
    public List<String> getColumns() {
        return columns;
    }

    /**
     * 查询结果行列表（每行为 Map：列名 → 值）。成功且有数据集时有意义，否则为空列表。
     */
    public List<Map<String, Object>> getRows() {
        return rows;
    }

    /**
     * 面板级失败错误码（如 {@code nop.err.datav.dataset-ref-not-found}）。成功条目为 null。
     */
    public String getErrorCode() {
        return errorCode;
    }

    /**
     * 面板级失败错误消息。成功条目为 null。
     */
    public String getErrorMessage() {
        return errorMessage;
    }
}
