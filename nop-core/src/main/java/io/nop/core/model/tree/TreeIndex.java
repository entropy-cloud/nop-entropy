/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.model.tree;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static io.nop.core.CoreErrors.ARG_NODE;
import static io.nop.core.CoreErrors.ARG_NODE_ID;
import static io.nop.core.CoreErrors.ERR_TREE_DUPLICATE_NODE_ID;

public class TreeIndex<T> implements ITreeChildrenAdapter<T> {
    private List<T> roots;

    private Map<T, List<T>> childrenMap;

    private Map<Object, T> nodeMap;

    public static <T> TreeIndex<T> buildFromParentId(Collection<T> list,
                                                     Function<T, ?> idGetter,
                                                     Function<T, ?> parentIdGetter) {
        Map<Object, T> map = new HashMap<>();
        List<T> roots = new ArrayList<>();
        TreeIndex<T> index = new TreeIndex<>();
        index.setRoots(roots);
        index.setNodeMap(map);

        for (T item : list) {
            Object id = idGetter.apply(item);
            if (map.put(id, item) != null)
                throw new NopException(ERR_TREE_DUPLICATE_NODE_ID)
                        .param(ARG_NODE, item)
                        .param(ARG_NODE_ID, id);
        }

        Map<T, List<T>> childrenMap = new HashMap<>();
        for (T item : list) {
            Object parentId = parentIdGetter.apply(item);
            if (StringHelper.isEmptyObject(parentId)) {
                roots.add(item);
            } else {
                T parent = map.get(parentId);
                if (parent == null) {
                    roots.add(item);
                } else {
                    List<T> children = childrenMap.computeIfAbsent(parent, k -> new ArrayList<>());
                    children.add(item);
                }
            }
        }
        index.setChildrenMap(childrenMap);
        return index;
    }

    public List<T> getRoots() {
        return roots;
    }

    public void setRoots(List<T> roots) {
        this.roots = roots;
    }

    public Map<T, List<T>> getChildrenMap() {
        return childrenMap;
    }

    public void setChildrenMap(Map<T, List<T>> childrenMap) {
        this.childrenMap = childrenMap;
    }

    @Override
    public List<T> getChildren(T node) {
        return childrenMap.get(node);
    }

    public Map<Object, T> getNodeMap() {
        return nodeMap;
    }

    public void setNodeMap(Map<Object, T> nodeMap) {
        this.nodeMap = nodeMap;
    }
}
