/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.api.debugger;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.nop.api.core.annotations.data.DataBean;

import java.io.Serializable;
import java.lang.reflect.Array;

@DataBean
public class DebugVariable implements Serializable {
    private static final long serialVersionUID = 1L;

    private String kind;
    private String scope;
    private String name;

    // 对于非String类型的Map，也需要通过index来获取值
    private int index = -1;
    private String type;
    private String value;
    private String ownerClass;

    private int hash;

    private LineLocation assignLoc;

    @JsonIgnore
    public DebugValueKey getValueKey() {
        DebugValueKey key = new DebugValueKey();
        key.setName(name);
        key.setIndex(index);
        key.setOwnerClass(ownerClass);
        return key;
    }

    public int getHash() {
        return hash;
    }

    public void setHash(int hash) {
        this.hash = hash;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public LineLocation getAssignLoc() {
        return assignLoc;
    }

    public void setAssignLoc(LineLocation assignLoc) {
        this.assignLoc = assignLoc;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String getOwnerClass() {
        return ownerClass;
    }

    public void setOwnerClass(String ownerClass) {
        this.ownerClass = ownerClass;
    }

    public static DebugVariable newVariable(String name, Object value, String scope) {
        DebugVariable var = new DebugVariable();
        var.setName(name);

        try {
            String val = value == null ? "" : value.toString();
            if (val.length() > 100) {
                val = val.substring(0, 100);
            }
            var.setValue(val);
        } catch (Throwable e) {
            var.setValue(e.getMessage());
        }

        String type = null;
        if (value != null) {
            Class<?> clazz = value.getClass();
            if (clazz.isArray()) {
                type = clazz.getComponentType().getName() + "[" + Array.getLength(value) + "]";
            } else {
                type = clazz.getName();
            }
        }
        var.setScope(scope);
        var.setType(type);
        return var;
    }

    public static DebugVariable newVariable(String name, Object value) {
        return DebugVariable.newVariable(name, value, null);
    }
}