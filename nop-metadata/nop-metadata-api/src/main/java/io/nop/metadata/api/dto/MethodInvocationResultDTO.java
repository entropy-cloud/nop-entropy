
package io.nop.metadata.api.dto;

import io.nop.api.core.annotations.data.DataBean;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 实体方法执行结果 DTO（来源：{@code NopMetaEntityBizModel.invokeMethod}，设计 14 §3.4）。
 *
 * <p>{@code items} 保留 {@code List<Map<String,Object>>}——返回行结构由实现 SQL 决定，
 * {@code returnType} 是消费方文档性契约，执行器不做强类型装配（沿 queryData P2-24
 * 同一裁定的合理例外）。
 */
@DataBean
public class MethodInvocationResultDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String methodId;
    private String methodName;
    private List<String> columns = new ArrayList<>();
    private List<Map<String, Object>> items = new ArrayList<>();
    private int rowCount;

    public String getMethodId() {
        return methodId;
    }

    public void setMethodId(String methodId) {
        this.methodId = methodId;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public List<String> getColumns() {
        return columns;
    }

    public void setColumns(List<String> columns) {
        this.columns = columns;
    }

    public List<Map<String, Object>> getItems() {
        return items;
    }

    public void setItems(List<Map<String, Object>> items) {
        this.items = items;
    }

    public int getRowCount() {
        return rowCount;
    }

    public void setRowCount(int rowCount) {
        this.rowCount = rowCount;
    }
}
