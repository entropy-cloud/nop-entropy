/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

package io.nop.metadata.service.dto;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.metadata.dao.dto.ErrorDTO;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * catalog 收集结果 DTO（来源：{@code NopMetaDataSourceBizModel.collectCatalog} /
 * {@code collectCatalogForTable}）。
 */
@DataBean
public class CollectCatalogResultDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private int tableCount;
    private List<CollectCatalogTableDTO> tables = new ArrayList<>();
    private List<ErrorDTO> errors = new ArrayList<>();

    public int getTableCount() {
        return tableCount;
    }

    public void setTableCount(int tableCount) {
        this.tableCount = tableCount;
    }

    public List<CollectCatalogTableDTO> getTables() {
        return tables;
    }

    public void setTables(List<CollectCatalogTableDTO> tables) {
        this.tables = tables;
    }

    public List<ErrorDTO> getErrors() {
        return errors;
    }

    public void setErrors(List<ErrorDTO> errors) {
        this.errors = errors;
    }
}
