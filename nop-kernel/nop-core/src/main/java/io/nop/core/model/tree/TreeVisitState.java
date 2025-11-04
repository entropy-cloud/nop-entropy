/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.model.tree;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.collections.IntArray;
import io.nop.commons.collections.MutableIntArray;
import io.nop.commons.util.CollectionHelper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static io.nop.api.core.util.Guard.notNull;
import static io.nop.core.CoreErrors.ARG_PARENTS;
import static io.nop.core.CoreErrors.ERR_TREE_ILLEGAL_VISIT_STATE;

public class TreeVisitState<T> {
    private final T root;
    private final ITreeChildrenAdapter<T> adapter;

    private final List<T> parents = new ArrayList<>();
    private final MutableIntArray childIndexes = new MutableIntArray(16);

    private T current;
    private Collection<? extends T> _children;

    public TreeVisitState(T root, ITreeChildrenAdapter<T> adapter) {
        this.root = notNull(root, "root is null");
        this.adapter = notNull(adapter, "adapter is null");
        this.current = root;
    }

    public ITreeChildrenAdapter<T> getAdapter() {
        return adapter;
    }

    public T getCurrent() {
        return current;
    }

    public void setCurrent(T current) {
        this.current = current;
        this._children = null;
    }

    public T getRoot() {
        return root;
    }

    public List<T> getParents() {
        return parents;
    }

    public Collection<? extends T> getChildren() {
        if (_children == null)
            _children = adapter.getChildren(current);
        return _children;
    }

    public T getParent() {
        return CollectionHelper.last(parents);
    }

    protected void enterChildren(T node) {
        parents.add(node);
        childIndexes.push(-1);
    }

    public void setChildIndex(int index) {
        childIndexes.replaceTop(index);
    }

    public IntArray getChildIndexes() {
        return childIndexes;
    }

    protected void leaveChildren(T node) {
        if (node != getParent())
            throw new NopException(ERR_TREE_ILLEGAL_VISIT_STATE).param(ARG_PARENTS, parents);
        parents.remove(parents.size() - 1);
        childIndexes.pop();
        current = node;
    }
}