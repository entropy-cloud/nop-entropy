/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ioc.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.nop.commons.util.CollectionHelper;
import io.nop.ioc.model._gen._BeanValue;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;

public class BeanValue extends _BeanValue implements IBeanPropValue {
    private String embeddedId;

    public BeanValue() {

    }

    public int getIocSortOrder() {
        return 0;
    }

    public String getId() {
        return null;
    }

    @JsonIgnore
    public String getEmbeddedId() {
        return embeddedId;
    }

    public void setEmbeddedId(String embeddedId) {
        this.embeddedId = embeddedId;
    }

    @Override
    public String getBeanValueType() {
        return "bean";
    }

    public void addDepend(String depend) {
        Set<String> depends = getDependsOn();
        if (depends == null || depends.isEmpty()) {
            depends = new LinkedHashSet<>();
            setDependsOn(depends);
        }
        depends.add(depend);
    }

    @Override
    public void forEachChild(Consumer<IBeanPropValue> consumer) {
        for (BeanConstructorArgModel argModel : this.getConstructorArgs()) {
            IBeanPropValue value = argModel.getBody();
            if (value != null)
                consumer.accept(value);
        }

        for (BeanPropertyModel propModel : this.getProperties()) {
            IBeanPropValue value = propModel.getBody();
            if (value != null)
                consumer.accept(value);
        }
    }

    @JsonIgnore
    public int getConstructorArgCount() {
        return CollectionHelper.getSize(getConstructorArgs());
    }
}
