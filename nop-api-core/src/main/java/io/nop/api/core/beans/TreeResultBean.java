package io.nop.api.core.beans;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.annotations.graphql.GraphQLObject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@DataBean
@GraphQLObject
public class TreeResultBean {
    private String value;
    private String label;

    private Map<String, Object> attrs;

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

    @JsonAnyGetter
    public Map<String, Object> getAttrs() {
        return attrs;
    }

    public Object getAttr(String name) {
        if (attrs == null)
            return null;
        return attrs.get(name);
    }

    @JsonAnySetter
    public void setAttr(String name, Object value) {
        if (value == null) {
            if (attrs != null)
                attrs.remove(name);
        } else {
            if (attrs == null)
                attrs = new LinkedHashMap<>();
            attrs.put(name, value);
        }
    }
}