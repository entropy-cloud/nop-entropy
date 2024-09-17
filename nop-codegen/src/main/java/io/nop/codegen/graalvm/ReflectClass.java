/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.codegen.graalvm;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.commons.collections.KeyedList;

import java.util.Comparator;
import java.util.List;

@DataBean
public class ReflectClass {
    private String name;
    private boolean allDeclaredConstructors = true;
    private boolean allPublicConstructors = true;
    // 允许lookup。methods配置允许通过反射执行
    private boolean allDeclaredMethods = true;
    private boolean allPublicMethods = true;
    private boolean allPublicFields = true;

    // 允许通过Unsafe.allocateInstance来创建
    private boolean unsafeAllocated = false;

    private KeyedList<ReflectField> fields = KeyedList.emptyList();

    private KeyedList<ReflectMethod> methods = KeyedList.emptyList();

    public void sort() {
        fields.sort(Comparator.comparing(ReflectField::getName));
        methods.sort(ReflectMethod::compareTo);
    }

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public boolean isUnsafeAllocated() {
        return unsafeAllocated;
    }

    public void setUnsafeAllocated(boolean unsafeAllocated) {
        this.unsafeAllocated = unsafeAllocated;
    }

    /**
     * 删除ReflectClass中已经配置的部分
     *
     * @param reflectClass
     */
    public void remove(ReflectClass reflectClass) {
        if (reflectClass.allDeclaredConstructors)
            allDeclaredConstructors = false;

        if (reflectClass.allPublicConstructors)
            allPublicConstructors = false;

        if (reflectClass.allDeclaredMethods)
            allPublicMethods = false;

        if (reflectClass.allPublicMethods) {
            allPublicMethods = false;
        }

        reflectClass.fields.forEach(field -> {
            this.fields.removeByKey(field.getName());
        });

        reflectClass.methods.forEach(method -> {
            this.methods.removeByKey(method.getName());
        });
    }

    @JsonIgnore
    public boolean isEmpty() {
        if (allDeclaredConstructors)
            return false;
        if (allPublicConstructors)
            return false;
        if (allDeclaredMethods)
            return false;
        if (allPublicMethods)
            return false;

        if (!fields.isEmpty())
            return false;

        return methods.isEmpty();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isAllDeclaredConstructors() {
        return allDeclaredConstructors;
    }

    public void setAllDeclaredConstructors(boolean allDeclaredConstructors) {
        this.allDeclaredConstructors = allDeclaredConstructors;
    }

    public boolean isAllPublicConstructors() {
        return allPublicConstructors;
    }

    public void setAllPublicConstructors(boolean allPublicConstructors) {
        this.allPublicConstructors = allPublicConstructors;
    }

    public boolean isAllDeclaredMethods() {
        return allDeclaredMethods;
    }

    public void setAllDeclaredMethods(boolean allDeclaredMethods) {
        this.allDeclaredMethods = allDeclaredMethods;
    }

    public boolean isAllPublicMethods() {
        return allPublicMethods;
    }

    public void setAllPublicMethods(boolean allPublicMethods) {
        this.allPublicMethods = allPublicMethods;
    }

    public List<ReflectField> getFields() {
        return fields;
    }

    public void setFields(List<ReflectField> fields) {
        this.fields = KeyedList.fromList(fields, ReflectField::getName);
    }

    public List<ReflectMethod> getMethods() {
        return methods;
    }

    public void setMethods(List<ReflectMethod> methods) {
        this.methods = KeyedList.fromList(methods, ReflectMethod::getSignature);
    }

    public void addField(ReflectField field) {
        if (fields.isEmpty())
            fields = new KeyedList<>(ReflectField::getName);
        fields.add(field);
    }

    public void addMethod(ReflectMethod method) {
        if (methods.isEmpty())
            methods = new KeyedList<>(ReflectMethod::getSignature);
        methods.add(method);
    }

    public boolean isAllPublicFields() {
        return allPublicFields;
    }

    public void setAllPublicFields(boolean allPublicFields) {
        this.allPublicFields = allPublicFields;
    }

    public void merge(ReflectClass clazz) {
        this.allDeclaredConstructors = this.allDeclaredConstructors || clazz.allDeclaredConstructors;
        this.allPublicConstructors = this.allPublicConstructors || clazz.allPublicConstructors;
        this.allDeclaredMethods = this.allDeclaredMethods || clazz.allDeclaredMethods;
        this.allPublicMethods = this.allPublicMethods || clazz.allPublicMethods;
        this.allPublicFields = this.allPublicFields || clazz.allPublicFields;

        if (this.allPublicFields) {
            this.fields = KeyedList.emptyList();
        } else {
            if (!clazz.fields.isEmpty()) {
                if (fields.isEmpty())
                    fields = new KeyedList<>(ReflectField::getName);

                for (ReflectField field : clazz.fields) {
                    ReflectField fld = fields.getByKey(field.getName());
                    if (fld == null) {
                        fields.add(field);
                    } else {
                        fld.merge(field);
                    }
                }
            }
        }


        if (!clazz.methods.isEmpty()) {
            if (methods.isEmpty())
                methods = new KeyedList<>(ReflectMethod::getSignature);

            for (ReflectMethod method : clazz.methods) {
                ReflectMethod mtd = methods.getByKey(method.getName());
                if (mtd == null) {
                    methods.add(method);
                } else {
                    mtd.merge(method);
                }
            }
        }
    }
}