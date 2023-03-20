package io.nop.api.core.beans;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.annotations.graphql.GraphQLObject;

import java.util.List;

@DataBean
@GraphQLObject
public class TreeResultBean {
    private String value;
    private String label;

    private List<TreeResultBean> children;

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public List<TreeResultBean> getChildren() {
        return children;
    }

    public void setChildren(List<TreeResultBean> children) {
        this.children = children;
    }
}
