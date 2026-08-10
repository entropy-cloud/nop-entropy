package io.nop.datav.service.chatbi;

import io.nop.api.core.annotations.data.DataBean;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * ChatBI 查询结果。由 {@code NopDatavChatBiBizModel.chatToQuery} 返回。
 *
 * <p>{@code answer} 为 LLM 最终文本答案；{@code columns}/{@code rows} 为从 tool-calling 过程中解析出的
 * 最后一次 {@code datav-query-dataset} 调用结果（若 LLM 未执行查询则为空）。</p>
 */
@DataBean
public class ChatBiResult {

    private String answer;
    private List<String> columns = Collections.emptyList();
    private List<Map<String, Object>> rows = Collections.emptyList();
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

    public int getIterations() {
        return iterations;
    }

    public void setIterations(int iterations) {
        this.iterations = iterations;
    }
}
