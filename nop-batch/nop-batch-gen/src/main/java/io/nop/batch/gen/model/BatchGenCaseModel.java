/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.gen.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.beans.TreeBean;
import io.nop.core.type.IGenericType;

import java.util.List;
import java.util.Map;

@DataBean
public class BatchGenCaseModel implements IBatchGenCaseModel {
    private String name;
    private String description;

    /**
     * 在生成数据中所占的权重。父case生成时会按照子case的权重分配具体生成多少条记录。 subCase.totalCount = weight / sum(weight) * parentCase.totalCount
     */
    private double weight;

    private TreeBean when;

    private IGenericType beanType;

    /**
     * 是否继承父case的template配置。如果为true，则父case的template和本case的template会按照delta merge的方式合并在一起
     */
    private boolean inheritParent;

    /**
     * 请求数据模板
     */
    private Map<String, Object> template;

    /**
     * 将template和父分支的template合并之后得到的模板对象
     */
    private transient Map<String, Object> mergedTemplate;

    private transient Map<String, Object> mergedOutputVars;

    /**
     * 输出变量集合。当每个生成的request被处理之后，输出变量会更新到上下文中。 在生成过程中，template中可能会用到这些上下文变量，从而实现关联数据的生成
     */
    private Map<String, Object> outputVars;

    /**
     * 如果指定顺序生成，则表示subCases对应一个生成序列，它们的weight属性将被忽略。
     */
    private boolean sequential;

    /**
     * 如果可以分解为更细致的几个分支
     */
    private List<BatchGenCaseModel> subCases;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public double getWeight() {
        return weight;
    }

    public void setWeight(double weight) {
        this.weight = weight;
    }

    public TreeBean getWhen() {
        return when;
    }

    public void setWhen(TreeBean when) {
        this.when = when;
    }

    public IGenericType getBeanType() {
        return beanType;
    }

    public void setBeanType(IGenericType beanType) {
        this.beanType = beanType;
    }

    public boolean isInheritParent() {
        return inheritParent;
    }

    public void setInheritParent(boolean inheritParent) {
        this.inheritParent = inheritParent;
    }

    public Map<String, Object> getTemplate() {
        return template;
    }

    public void setTemplate(Map<String, Object> template) {
        this.template = template;
    }

    public Map<String, Object> getOutputVars() {
        return outputVars;
    }

    public void setOutputVars(Map<String, Object> outputVars) {
        this.outputVars = outputVars;
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

    @JsonIgnore
    public Map<String, Object> getMergedTemplate() {
        return mergedTemplate;
    }

    public void setMergedTemplate(Map<String, Object> mergedTemplate) {
        this.mergedTemplate = mergedTemplate;
    }

    @JsonIgnore
    public Map<String, Object> getMergedOutputVars() {
        return mergedOutputVars;
    }

    public void setMergedOutputVars(Map<String, Object> mergedOutputVars) {
        this.mergedOutputVars = mergedOutputVars;
    }
}