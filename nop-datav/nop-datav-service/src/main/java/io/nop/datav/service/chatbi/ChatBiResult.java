package io.nop.datav.service.chatbi;

import io.nop.api.core.annotations.data.DataBean;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * ChatBI 结果（D6-1 查询 + D6-1b 看板生成共享，裁定 K）。
 *
 * <p>查询路径（{@code chatToQuery}）使用 {@code answer} + {@code columns}/{@code rows}（从 tool-calling
 * 过程中解析出的最后一次 {@code datav-query-dataset} 调用结果）；{@code createdEntityId} 为 null。</p>
 *
 * <p>生成路径（{@code chatToDashboard}）使用 {@code answer} + {@code createdEntityId}（从
 * {@code datav-generate-dashboard} 调用结果解析出的 dashboardId）；{@code columns}/{@code rows} 为空。</p>
 *
 * <p>{@code iterations} 为实际 tool-calling 轮次，两路径共用。</p>
 */
@DataBean
public class ChatBiResult {

    private String answer;
    private List<String> columns = Collections.emptyList();
    private List<Map<String, Object>> rows = Collections.emptyList();

    /**
     * 生成路径产物 ID（dashboardId）；查询路径不用（保持 null）。裁定 K。
     */
    private String createdEntityId;

    /**
     * 多轮会话模式下的会话标识回显（裁定 S3）；单轮模式保持 null。
     */
    private String sessionId;

    private int iterations;

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public List<String> getColumns() {
        return columns;
    }

    public void setColumns(List<String> columns) {
        this.columns = columns != null ? new ArrayList<>(columns) : Collections.emptyList();
    }

    public List<Map<String, Object>> getRows() {
        return rows;
    }

    public void setRows(List<Map<String, Object>> rows) {
        this.rows = rows != null ? new ArrayList<>(rows) : Collections.emptyList();
    }

    public String getCreatedEntityId() {
        return createdEntityId;
    }

    public void setCreatedEntityId(String createdEntityId) {
        this.createdEntityId = createdEntityId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public int getIterations() {
        return iterations;
    }

    public void setIterations(int iterations) {
        this.iterations = iterations;
    }
}
