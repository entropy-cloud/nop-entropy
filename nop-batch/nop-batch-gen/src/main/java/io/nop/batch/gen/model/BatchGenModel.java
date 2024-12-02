/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.gen.model;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.util.INeedInit;
import io.nop.core.lang.json.delta.JsonMerger;
import io.nop.core.resource.component.AbstractComponentModel;
import io.nop.core.type.IGenericType;

import java.util.List;
import java.util.Map;

/**
 * 数据生成模型。用于按照指定的数据分布比例来批量生成一批测试数据。
 */
@DataBean
public class BatchGenModel extends AbstractComponentModel implements IBatchGenCaseModel, INeedInit {

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

    private boolean inited = false;

    @Override
    public void init() {
        if (inited)
            return;
        inited = true;
        for (BatchGenCaseModel subCase : getSubCases()) {
            mergeWithParent(subCase, this);
        }
    }

    void mergeWithParent(BatchGenCaseModel subCase, IBatchGenCaseModel parent) {
        if (subCase.isInheritParent()) {
            Map<String, Object> template = mergeMap(parent.getMergedTemplate(), subCase.getTemplate());
            subCase.setMergedTemplate(template);

            Map<String, Object> outputVars = mergeMap(parent.getMergedOutputVars(), subCase.getOutputVars());
            subCase.setMergedOutputVars(outputVars);
        } else {
            subCase.setMergedTemplate(subCase.getTemplate());
            subCase.setMergedOutputVars(subCase.getOutputVars());
        }

        if (subCase.getSubCases() != null) {
            subCase.getSubCases().forEach(sub -> {
                mergeWithParent(sub, subCase);
            });
        }
    }

    Map<String, Object> mergeMap(Map<String, Object> m1, Map<String, Object> m2) {
        if (m1 == null || m1.isEmpty())
            return m2;
        if (m2 == null || m1.isEmpty())
            return m1;

        return (Map<String, Object>) JsonMerger.instance().merge(m1, m2);
    }
}