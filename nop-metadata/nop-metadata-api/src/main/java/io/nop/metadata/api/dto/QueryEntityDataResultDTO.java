
package io.nop.metadata.api.dto;

import io.nop.api.core.annotations.data.DataBean;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 单表数据查询结果 DTO（来源：{@code NopMetaEntityBizModel.queryData}）。
 *
 * <p>{@code items} 保留 {@code List<Map<String,Object>>}——行 schema 跟随表结构动态变化，
 * 强行引入 DTO 反而损失灵活性（plan Non-Goals 中 50+ @SuppressWarnings 完整 DTO 化延后的同一裁定）。
 */
@DataBean
public class QueryEntityDataResultDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String entityKind;
    private List<Map<String, Object>> items = new ArrayList<>();

    /**
     * 分页总数（plan 2026-10-26-2047-1 Phase 4）：仅目录合成入口 {@code findEntityPage} 填充；
     * {@code queryData}/{@code findEntityList}/{@code getEntity} 保持 null（既有消费面零变化）。
     */
    private Long total;

    public String getEntityKind() {
        return entityKind;
    }

    public void setEntityKind(String entityKind) {
        this.entityKind = entityKind;
    }

    public List<Map<String, Object>> getItems() {
        return items;
    }

    public void setItems(List<Map<String, Object>> items) {
        this.items = items;
    }

    public Long getTotal() {
        return total;
    }

    public void setTotal(Long total) {
        this.total = total;
    }
}
