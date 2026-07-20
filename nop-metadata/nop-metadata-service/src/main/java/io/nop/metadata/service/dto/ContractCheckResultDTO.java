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
 * 数据契约检查结果 DTO（来源：{@code NopMetaDataContractBizModel.checkContract}）。
 */
@DataBean
public class ContractCheckResultDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String contractId;
    private boolean passed;
    private List<ErrorDTO> errors = new ArrayList<>();

    public String getContractId() {
        return contractId;
    }

    public void setContractId(String contractId) {
        this.contractId = contractId;
    }

    public boolean isPassed() {
        return passed;
    }

    public void setPassed(boolean passed) {
        this.passed = passed;
    }

    public List<ErrorDTO> getErrors() {
        return errors;
    }

    public void setErrors(List<ErrorDTO> errors) {
        this.errors = errors;
    }
}
