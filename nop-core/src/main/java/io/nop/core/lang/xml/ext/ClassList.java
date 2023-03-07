/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.lang.xml.ext;

import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.IXNodeExtension;
import io.nop.core.lang.xml.XNode;

import java.io.Serializable;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class ClassList implements Serializable, Iterable<String>, IXNodeExtension {
    private static final long serialVersionUID = -2144290977778763730L;
    private static final String ATTR_CLASS = "class";

    private Set<String> classes;

    public ClassList(Set<String> classes) {
        this.classes = classes == null ? new LinkedHashSet<>() : classes;
    }

    public ClassList() {
        this.classes = new LinkedHashSet<>();
    }

    public static ClassList getFromNode(XNode node) {
        return (ClassList) node.getExtension(ClassList.class.getName());
    }

    public static ClassList makeFromNode(XNode node) {
        ClassList classList = (ClassList) node.getExtension(ClassList.class.getName());
        if (classList == null) {
            classList = parse(node.attrText(ATTR_CLASS));
            node.setExtension(ClassList.class.getName(), classList);
        }
        return classList;
    }

    public static Set<String> parseClassName(String classNames) {
        List<String> classList = StringHelper.stripedSplit(classNames, ' ');
        if (classList == null || classList.isEmpty())
            return new LinkedHashSet<>();
        return new LinkedHashSet<>(classList);
    }

    public static ClassList parse(String className) {
        return new ClassList(parseClassName(className));
    }

    @Override
    public void syncToNode(XNode node) {
        if (classes.isEmpty()) {
            node.removeAttr(ATTR_CLASS);
        } else {
            String text = node.attrText(ATTR_CLASS);
            String str = this.toString();
            if (!Objects.equals(text, str)) {
                node.setAttr(ATTR_CLASS, str);
            }
        }
    }

    @Override
    public void syncFromNode(XNode node) {
        this.classes = parseClassName(node.attrText(ATTR_CLASS));
    }

    public String toString() {
        return StringHelper.join(classes, " ");
    }

    public int length() {
        return classes.size();
    }

    @Override
    public Iterator<String> iterator() {
        return classes.iterator();
    }

    protected void checkNotReadOnly() {

    }

    public void add(String className) {
        checkNotReadOnly();
        classes.add(className);
    }

    public void add(String className, String... otherClasses) {
        checkNotReadOnly();

        classes.add(className);
        for (String otherClass : otherClasses) {
            classes.add(otherClass);
        }
    }

    public boolean addAll(Collection<String> classNames) {
        checkNotReadOnly();
        if (classNames != null) {
            return classes.addAll(classNames);
        }
        return false;
    }

    public void remove(String className) {
        checkNotReadOnly();
        classes.remove(className);
    }

    public void remove(String className, String... otherClasses) {
        checkNotReadOnly();
        classes.remove(className);
        for (String otherClass : otherClasses) {
            classes.remove(otherClass);
        }
    }

    public boolean removeAll(Collection<String> classNames) {
        checkNotReadOnly();
        if (classNames != null) {
            return classes.removeAll(classNames);
        }
        return false;
    }

    public boolean contains(String className) {
        return classes.contains(className);
    }

    public boolean containsAll(Collection<String> classNames) {
        if (classNames == null)
            return false;
        return classes.containsAll(classNames);
    }

    /**
     * 如果类存在，则删除它并返回false，如果不存在，则添加它并返回true。
     *
     * @param className
     * @return
     */
    public boolean toggle(String className) {
        checkNotReadOnly();
        if (classes.remove(className)) {
            return false;
        }
        classes.add(className);
        return true;
    }

    public void replace(String oldClass, String newClass) {
        remove(oldClass);
        add(newClass);
    }
}