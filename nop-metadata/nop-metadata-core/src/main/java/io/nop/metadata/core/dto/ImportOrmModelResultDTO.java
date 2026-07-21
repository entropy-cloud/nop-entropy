/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

package io.nop.metadata.core.dto;

import io.nop.api.core.annotations.data.DataBean;

import java.io.Serializable;

/**
 * 单条 ORM 模型导入结果 DTO（来源：{@code NopMetaModuleBizModel.importOrmModels}）。
 */
@DataBean
public class ImportOrmModelResultDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String metaModuleId;
    private String moduleName;
    private boolean success;
    private String error;

    public String getMetaModuleId() {
        return metaModuleId;
    }

    public void setMetaModuleId(String metaModuleId) {
        this.metaModuleId = metaModuleId;
    }

    public String getModuleName() {
        return moduleName;
    }

    public void setModuleName(String moduleName) {
        this.moduleName = moduleName;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}
