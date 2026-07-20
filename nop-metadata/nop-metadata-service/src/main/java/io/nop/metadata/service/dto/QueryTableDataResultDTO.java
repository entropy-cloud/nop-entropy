/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

package io.nop.metadata.service.dto;

import io.nop.api.core.annotations.data.DataBean;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 单表数据查询结果 DTO（来源：{@code NopMetaTableBizModel.queryTableData}）。
 *
 * <p>{@code items} 保留 {@code List<Map<String,Object>>}——行 schema 跟随表结构动态变化，
 * 强行引入 DTO 反而损失灵活性（plan Non-Goals 中 50+ @SuppressWarnings 完整 DTO 化延后的同一裁定）。
 */
@DataBean
public class QueryTableDataResultDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String tableType;
    private List<Map<String, Object>> items = new ArrayList<>();

    public String getTableType() {
        return tableType;
    }

    public void setTableType(String tableType) {
        this.tableType = tableType;
    }

    public List<Map<String, Object>> getItems() {
        return items;
    }

    public void setItems(List<Map<String, Object>> items) {
        this.items = items;
    }
}
