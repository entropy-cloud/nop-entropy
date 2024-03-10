/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.codegen.graalvm;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.commons.collections.KeyedList;

import java.util.Comparator;
import java.util.List;

/**
 * 用于生成reflect-config.json文件
 */
@DataBean
public class ReflectConfig {
    private KeyedList<ReflectClass> classList = KeyedList.emptyList();

    public List<ReflectClass> getClassList() {
        return classList;
    }

    public void setClassList(List<ReflectClass> classList) {
        this.classList = KeyedList.fromList(classList, ReflectClass::getName);
    }

    public void addClass(ReflectClass clazz) {
        if (classList.isEmpty()) {
            classList = new KeyedList<>(ReflectClass::getName);
        }
        classList.add(clazz);
    }

    public boolean containsClass(String className) {
        return classList.containsKey(className);
    }

    public void mergeClass(ReflectClass reflectClass) {
        ReflectClass old = classList.getByKey(reflectClass.getName());
        if (old == null) {
            addClass(reflectClass);
        } else {
            old.merge(reflectClass);
        }
    }

    public void sort() {
        classList.sort(Comparator.comparing(ReflectClass::getName));
        for (ReflectClass reflectClass : classList) {
            reflectClass.sort();
        }
    }

    public void merge(ReflectConfig config) {
        for (ReflectClass reflectClass : config.getClassList()) {
            mergeClass(reflectClass);
        }
    }

    public void remove(ReflectConfig config) {
        for (ReflectClass reflectClass : config.getClassList()) {
            ReflectClass current = classList.getByKey(reflectClass.getName());
            if (current != null) {
                current.remove(reflectClass);
                if (current.isEmpty()) {
                    classList.remove(current);
                }
            }
        }
    }
}