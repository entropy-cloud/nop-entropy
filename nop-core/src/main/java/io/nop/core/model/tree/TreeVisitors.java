/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.model.tree;

import io.nop.commons.collections.IterableIterator;
import io.nop.core.model.tree.impl.DepthFirstIterator;
import io.nop.core.model.tree.impl.WidthFirstIterator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

public class TreeVisitors {
    static final ITreeChildrenAdapter<ITreeChildrenStructure> DEFAULT_CHILDREN_ADAPTER = node -> node.getChildren();

    public static <T extends ITreeChildrenStructure> ITreeChildrenAdapter<T> childrenStructureAdapter() {
        return (ITreeChildrenAdapter<T>) DEFAULT_CHILDREN_ADAPTER;
    }

    /**
     * 递归遍历树形结构，根节点也被访问。
     */
    public static <T> TreeVisitResult visitTree(ITreeChildrenAdapter<T> adapter, T node, ITreeVisitor<T> visitor) {
        TreeVisitResult vr = visitor.beginNode(node);
        switch (vr) {
            case CONTINUE:
                break;
            case END:
            case SKIP_SIBLINGS:
                return vr;
            case SKIP_CHILD:
                return TreeVisitResult.CONTINUE;
        }

        Iterable<? extends T> children = adapter.getChildren(node);
        if (children != null) {
            loop:
            for (T child : children) {
                vr = visitTree(adapter, child, visitor);
                switch (vr) {
                    case CONTINUE:
                        continue;
                    case END:
                        visitor.endNode(node);
                        return vr;
                    case SKIP_SIBLINGS:
                        break loop;
                    case SKIP_CHILD:

                }
            }
        }

        return visitor.endNode(node);
    }

    public static <S extends TreeVisitState<T>, T> TreeVisitResult visitTreeState(S state,
                                                                                  ITreeStateVisitor<S> visitor) {
        TreeVisitResult vr = visitor.beginNodeState(state);
        switch (vr) {
            case CONTINUE:
                break;
            case END:
            case SKIP_SIBLINGS:
                return vr;
            case SKIP_CHILD:
                return TreeVisitResult.CONTINUE;
        }

        Iterable<? extends T> children = state.getChildren();
        if (children != null) {
            T current = state.getCurrent();
            state.enterChildren(current);
            int index = 0;
            loop:
            for (T child : children) {
                state.setCurrent(child);
                state.setChildIndex(index);
                index++;
                vr = visitTreeState(state, visitor);
                switch (vr) {
                    case CONTINUE:
                        continue;
                    case END:
                        state.leaveChildren(current);
                        visitor.endNodeState(state);
                        return vr;
                    case SKIP_SIBLINGS:
                        break loop;
                    case SKIP_CHILD:

                }
            }
            state.leaveChildren(current);
        }

        return visitor.endNodeState(state);
    }

    public static <T extends ITreeChildrenStructure> T find(T root, boolean includeRoot, Predicate<? super T> filter) {
        return find(childrenStructureAdapter(), root, includeRoot, filter);
    }

    public static <T> T find(ITreeChildrenAdapter<T> adapter, T root, boolean includeRoot,
                             Predicate<? super T> filter) {
        if (includeRoot) {
            if (filter.test(root))
                return root;
        }

        return _findChild(adapter, root, filter);
    }

    static <T> T _findChild(ITreeChildrenAdapter<T> adapter, T node, Predicate<? super T> filter) {
        Collection<? extends T> children = adapter.getChildren(node);
        if (children == null || children.isEmpty())
            return null;
        for (T child : children) {
            if (filter.test(child))
                return child;

            T found = _findChild(adapter, child, filter);
            if (found != null)
                return found;
        }
        return null;
    }

    public static <T extends ITreeChildrenStructure> List<T> findAll(T root, boolean includeRoot,
                                                                     Predicate<? super T> filter) {
        return findAll(childrenStructureAdapter(), root, includeRoot, filter);
    }

    public static <T> List<T> findAll(ITreeChildrenAdapter<T> adapter, T root, boolean includeRoot,
                                      Predicate<? super T> filter) {
        List<T> ret = new ArrayList<>();
        visitTree(adapter, root, new ITreeVisitor<T>() {
            @Override
            public TreeVisitResult beginNode(T node) {
                if (includeRoot || node != root) {
                    if (filter.test(node)) {
                        ret.add(node);
                        return TreeVisitResult.SKIP_CHILD;
                    }
                }
                return TreeVisitResult.CONTINUE;
            }
        });
        return ret;
    }

    public static <T extends ITreeChildrenStructure> T findChild(T root, Predicate<? super T> filter) {
        return findChild(childrenStructureAdapter(), root, filter);
    }

    public static <T> T findChild(ITreeChildrenAdapter<T> adapter, T root, Predicate<? super T> filter) {
        Collection<? extends T> children = adapter.getChildren(root);
        if (children == null || children.isEmpty())
            return null;
        for (T child : children) {
            if (filter.test(child))
                return child;
        }
        return null;
    }

    public static <T extends ITreeChildrenStructure> List<T> findChildren(T root, Predicate<? super T> filter) {
        return findChildren(childrenStructureAdapter(), root, filter);
    }

    public static <T> List<T> findChildren(ITreeChildrenAdapter<T> adapter, T root, Predicate<? super T> filter) {
        Collection<? extends T> children = adapter.getChildren(root);
        if (children == null || children.isEmpty())
            return Collections.emptyList();

        List<T> ret = new ArrayList<>();
        for (T child : children) {
            if (filter.test(child)) {
                ret.add(child);
            }
        }
        return ret;
    }

    public static <T> IterableIterator<T> depthFirstIterator(ITreeChildrenAdapter<T> adapter, T root,
                                                             boolean includeRoot, Predicate<? super T> filter) {
        return new DepthFirstIterator<>(adapter, root, includeRoot, filter);
    }

    public static <T> IterableIterator<T> widthFirstIterator(ITreeChildrenAdapter<T> adapter, T root,
                                                             boolean includeRoot, Predicate<? super T> filter) {
        return new WidthFirstIterator<>(adapter, root, includeRoot, filter);
    }

    public static <T extends ITreeChildrenStructure> IterableIterator<T> depthFirstIterator(T root, boolean includeRoot,
                                                                                            Predicate<? super T> filter) {
        return depthFirstIterator(childrenStructureAdapter(), root, includeRoot, filter);
    }

    public static <T extends ITreeChildrenStructure> IterableIterator<T> depthFirstIterator(T root,
                                                                                            boolean includeRoot) {
        return depthFirstIterator(childrenStructureAdapter(), root, includeRoot, null);
    }

    public static <T extends ITreeChildrenStructure> IterableIterator<T> widthFirstIterator(T root, boolean includeRoot,
                                                                                            Predicate<? super T> filter) {
        return widthFirstIterator(childrenStructureAdapter(), root, includeRoot, filter);
    }

    public static <T extends ITreeChildrenStructure> IterableIterator<T> widthFirstIterator(T root,
                                                                                            boolean includeRoot) {
        return widthFirstIterator(childrenStructureAdapter(), root, includeRoot, null);
    }
}