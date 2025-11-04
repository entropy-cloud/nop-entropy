package io.nop.core.model.tree;

import io.nop.core.model.tree.impl.DepthFirstIterator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public class TreeDataHelper {
    public static final String PARENT = "parent";
    public static final String PARENTS = "parents";

    public static final String ROOT = "root";

    public static final String SELF = "self";

    public static final String SIBLINGS = "siblings";

    public static final String SIBLINGS_WITH_SELF = "siblings-with-self";

    public static final String PARENT_WITH_SIBLINGS = "parent-with-siblings";

    public static final String CHILDREN = "children";

    public static final String DESCENDANTS = "descendants";

    static final int MAX_PARENTS = 50;

    public static <T> void collectParents(ITreeParentAdapter<T> adapter,
                                          T entity, Consumer<T> consumer) {
        for (int i = 0; i < MAX_PARENTS; i++) {
            T parent = adapter.getParent(entity);
            if (parent == null)
                break;
            consumer.accept(parent);
            entity = parent;
        }
    }

    public static <T> T getRoot(ITreeParentAdapter<T> adapter,
                                T entity) {
        T node = entity;
        for (int i = 0; i < MAX_PARENTS; i++) {
            T parent = adapter.getParent(node);
            if (parent == null)
                return node;
            node = parent;
        }
        return node;
    }

    public static <T> void collectSiblings(ITreeAdapter<T> adapter, T entity, boolean includeSelf,
                                           Consumer<T> consumer) {
        T parent = adapter.getParent(entity);
        if (parent == null) {
            if (includeSelf) {
                consumer.accept(entity);
            }
        } else {
            Collection<? extends T> children = adapter.getChildren(parent);
            if (children != null) {
                if (includeSelf) {
                    children.forEach(consumer);
                } else {
                    for (T child : children) {
                        if (!entity.equals(child)) {
                            consumer.accept(child);
                        }
                    }
                }
            }
        }
    }


    private static <T> void collectParentWithSiblings(ITreeAdapter<T> adapter, T entity,
                                                      Consumer<T> consumer) {

        T parent = adapter.getParent(entity);

        if (parent != null) {
            parent = adapter.getParent(parent);
            if (parent != null)
                collectChildren(adapter, parent, consumer);
        }
    }

    public static <T> void collectChildren(ITreeChildrenAdapter<T> adapter, T entity, Consumer<T> consumer) {
        Collection<? extends T> children = adapter.getChildren(entity);
        if (children != null) {
            children.forEach(consumer);
        }
    }

    public static <T> void collectDescendants(ITreeAdapter<T> adapter, T entity, Consumer<T> consumer) {
        DepthFirstIterator<T> it = new DepthFirstIterator<>(adapter, entity, false, null);
        it.forEach(consumer);
    }

    public static <T> List<T> collectAsList(ITreeAdapter<T> adapter, T entity, List<String> keys) {
        List<T> ret = new ArrayList<>();
        collect(adapter, entity, keys, ret::add);
        return ret;
    }

    public static <T> Set<T> collectAsSet(ITreeAdapter<T> adapter, T entity, List<String> keys) {
        Set<T> ret = new LinkedHashSet<>();
        collect(adapter, entity, keys, ret::add);
        return ret;
    }

    public static <T> void collect(ITreeAdapter<T> adapter, T entity, List<String> keys, Consumer<T> consumer) {
        for (String key : keys) {
            if (PARENT.equals(key)) {
                T parent = adapter.getParent(entity);
                if (parent != null)
                    consumer.accept(parent);
            } else if (PARENTS.equals(key)) {
                collectParents(adapter, entity, consumer);
            } else if (ROOT.equals(key)) {
                T root = getRoot(adapter, entity);
                if (root != null) {
                    consumer.accept(root);
                }
            } else if (SELF.equals(key)) {
                consumer.accept(entity);
            } else if (SIBLINGS.equals(key)) {
                collectSiblings(adapter, entity, false, consumer);
            } else if (SIBLINGS_WITH_SELF.equals(key)) {
                collectSiblings(adapter, entity, true, consumer);
            } else if (PARENT_WITH_SIBLINGS.equals(key)) {
                collectParentWithSiblings(adapter, entity, consumer);
            } else if (CHILDREN.equals(key)) {
                collectChildren(adapter, entity, consumer);
            } else if (DESCENDANTS.equals(key)) {
                collectDescendants(adapter, entity, consumer);
            } else {
                throw new IllegalArgumentException("nop.err.tree.invalid-tree-structure-key:" + key);
            }
        }
    }
}
