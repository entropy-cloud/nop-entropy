/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.batch.gen.model;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.beans.TreeBean;
import io.nop.core.resource.component.AbstractComponentModel;
import io.nop.core.type.IGenericType;

import java.util.List;
import java.util.Map;

/**
 * 数据生成模型。用于按照指定的数据分布比例来批量生成一批测试数据。
 */
@DataBean
public class BatchGenModel extends AbstractComponentModel implements IBatchGenCaseModel {

    private String name;

    private String description;

    private IGenericType beanType;

    /**
     * 测试数据模板。根据这些模板数据来生成新的测试数据。
     */
    private Map<String, Object> template;

    private boolean sequential;

    private List<BatchGenCaseModel> subCases;

    private TreeBean when;

    private Map<String, Object> outputVars;

    @Override
    public Map<String, Object> getOutputVars() {
        return outputVars;
    }

    public void setOutputVars(Map<String, Object> outputVars) {
        this.outputVars = outputVars;
    }

    @Override
    public TreeBean getWhen() {
        return when;
    }

    public void setWhen(TreeBean when) {
        this.when = when;
    }

    @Override
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public IGenericType getBeanType() {
        return beanType;
    }

    public void setBeanType(IGenericType beanType) {
        this.beanType = beanType;
    }

    @Override
    public Map<String, Object> getTemplate() {
        return template;
    }

    public void setTemplate(Map<String, Object> template) {
        this.template = template;
    }

    public boolean isSequential() {
        return sequential;
    }

    public void setSequential(boolean sequential) {
        this.sequential = sequential;
    }

    public List<BatchGenCaseModel> getSubCases() {
        return subCases;
    }

    public void setSubCases(List<BatchGenCaseModel> subCases) {
        this.subCases = subCases;
    }
}