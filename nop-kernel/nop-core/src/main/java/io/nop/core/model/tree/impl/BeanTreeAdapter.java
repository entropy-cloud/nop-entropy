package io.nop.core.model.tree.impl;

import io.nop.core.model.tree.ITreeAdapter;
import io.nop.core.reflect.bean.BeanTool;

import java.util.Collection;

public class BeanTreeAdapter<T> implements ITreeAdapter<T> {

    public static BeanTreeAdapter<Object> DEFAULT = new BeanTreeAdapter<>("parent", "children");
    private final String parentProp;
    private final String childrenProp;

    public BeanTreeAdapter(String parentProp, String childrenProp) {
        this.parentProp = parentProp;
        this.childrenProp = childrenProp;
    }

    @Override
    public Collection<? extends T> getChildren(T node) {
        return (Collection<? extends T>) BeanTool.getProperty(node, childrenProp);
    }

    @Override
    public T getParent(T node) {
        return (T) BeanTool.getProperty(node, parentProp);
    }
}
