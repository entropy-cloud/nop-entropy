package io.nop.xlang.utils;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.CollectionHelper;
import io.nop.core.resource.ResourceHelper;
import io.nop.xlang.xdef.impl.XDefHelper;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static io.nop.xlang.XLangErrors.ARG_REF_NAME;
import static io.nop.xlang.XLangErrors.ERR_XDEF_REF_NOT_ALLOW_CIRCULAR_REFERENCE;

public class RefResolver<T> {
    /**
     * 在resolve过程中通过上下文缓存识别循环引用
     */
    public static class ResolveState<T> {
        int refCount;
        Map<String, T> refCache = new HashMap<>();
        Set<String> resolving = new HashSet<>();

        public static <T> ResolveState<T> get(ThreadLocal<ResolveState<T>> tlState) {
            ResolveState<T> state = tlState.get();
            if (state == null) {
                state = new ResolveState<>();
                tlState.set(state);
            }
            state.inc();
            return state;
        }

        public void inc() {
            refCount++;
        }

        public boolean dec(ThreadLocal<ResolveState<T>> tlState) {
            refCount--;
            if (refCount == 0) {
                tlState.remove();
                return true;
            }
            return false;
        }
    }

    public interface IResolveNodeModel<T> {
        String getRef(T node);

        T getRefNode(T node);

        boolean isRefResolved(T node);

        void setRefNode(T node, T refNode);

        void mergeNode(T node, T refNode);

        String getResourcePath();

        Map<String, T> getNamedNodes();

        void forEachNode(Consumer<T> consumer);

        T loadRefNode(T node, String refPath);
    }

    private Set<String> refSchemas = new LinkedHashSet<>();

    private ResolveState<T> resolveState;

    private IResolveNodeModel<T> model;

    private final Set<T> mergedNodes = CollectionHelper.newIdentityHashSet();

    public void resolve(IResolveNodeModel<T> model, ThreadLocal<ResolveState<T>> tlState) {
        this.model = model;
        resolveState = ResolveState.get(tlState);

        try {
            String stdPath = ResourceHelper.getStdPath(model.getResourcePath());
            if (!resolveState.resolving.add(stdPath))
                throw new NopException(ERR_XDEF_REF_NOT_ALLOW_CIRCULAR_REFERENCE).param(ARG_REF_NAME, stdPath);

            model.getNamedNodes().forEach((name, node) -> {
                String refPath = XDefHelper.buildFullRefPath(stdPath, name);
                resolveState.refCache.put(refPath, node);
                resolveState.refCache.put(name, node);
            });

            model.forEachNode(node -> {
                if (model.isRefResolved(node))
                    return;

                T refNode = resolveRefNode(node);
                if (refNode != null) {
                    model.setRefNode(node, refNode);
                }
            });

            model.forEachNode(this::mergeNode);
        } finally {
            resolveState.dec(tlState);
        }
    }

    private T resolveRefNode(T node) {
        String ref = model.getRef(node);
        if (ref == null)
            return null;

        T refNode = resolveState.refCache.get(ref);
        if (refNode != null)
            return refNode;

        refNode = model.loadRefNode(node, ref);
        resolveState.refCache.put(ref, refNode);
        return refNode;
    }

    private void mergeNode(T node){
        mergeNode(node, false);
    }
    private void mergeNode(T node, boolean force) {
        if (!force && mergedNodes.contains(node))
            return;

        T refNode = model.getRefNode(node);
        if (refNode != null) {
            if (mergedNodes.add(refNode)) {
                mergeNode(refNode, true);
            }
            model.mergeNode(node, refNode);
        }
    }
}
