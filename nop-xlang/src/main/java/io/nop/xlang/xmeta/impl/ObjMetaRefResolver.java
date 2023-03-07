/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.xmeta.impl;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.ResourceHelper;
import io.nop.xlang.xdef.impl.XDefHelper;
import io.nop.xlang.xmeta.IObjMeta;
import io.nop.xlang.xmeta.IObjPropMeta;
import io.nop.xlang.xmeta.IObjSchema;
import io.nop.xlang.xmeta.ISchema;
import io.nop.xlang.xmeta.ISchemaNode;
import io.nop.xlang.xmeta.SchemaLoader;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.nop.xlang.XLangErrors.ARG_REF_NAME;
import static io.nop.xlang.XLangErrors.ERR_XDEF_REF_NOT_ALLOW_CIRCULAR_REFERENCE;
import static io.nop.xlang.XLangErrors.ERR_XMETA_UNKNOWN_REF;

public class ObjMetaRefResolver {
    private ResolveState resolveState;

    /**
     * 在resolve过程中通过上下文缓存识别循环引用
     */
    static class ResolveState {
        static ThreadLocal<ResolveState> s_state = new ThreadLocal<>();

        int refCount;
        Map<String, ISchema> refCache = new HashMap<>();
        Set<String> resolving = new HashSet<>();

        static ResolveState get() {
            ResolveState state = s_state.get();
            if (state == null) {
                state = new ResolveState();
                s_state.set(state);
            }
            state.inc();
            return state;
        }

        public void inc() {
            refCount++;
        }

        public boolean dec() {
            refCount--;
            if (refCount == 0) {
                s_state.remove();
                return true;
            }
            return false;
        }
    }

    public void resolve(IObjMeta objMeta) {
        Guard.notEmpty(objMeta.resourcePath(), "objMeta.resourcePath");

        resolveState = ResolveState.get();

        try {
            String stdPath = ResourceHelper.getStdPath(objMeta.resourcePath());
            if (!resolveState.resolving.add(stdPath))
                throw new NopException(ERR_XDEF_REF_NOT_ALLOW_CIRCULAR_REFERENCE).param(ARG_REF_NAME, stdPath);

            for (ISchema localDef : objMeta.getDefines()) {
                String refPath = XDefHelper.buildFullRefPath(stdPath, localDef.getName());
                resolveState.refCache.put(refPath, localDef);
            }

            resolveRef(objMeta.getRootSchema());

            for (ISchema localDef : objMeta.getDefines()) {
                resolveSchema(localDef);
            }

            resolveObjSchema(objMeta);
        } finally {
            resolveState.dec();
        }
    }

    private void resolveRef(ISchema schema) {
        schema.setRefResolved(true);
        ISchema refSchema = getRefSchema(schema);
        schema.setRefSchema(refSchema);
        ObjMetaMergeHelper.mergeRefSchema(schema);
    }

    private void resolveSchema(ISchema schema) {
        if (schema == null)
            return;

        if (schema.isRefResolved())
            return;

        resolveRef(schema);

        switch (schema.getSchemaKind()) {
            case OBJ: {
                resolveObjSchema(schema);
                break;
            }
            case LIST: {
                resolveSchema(schema.getItemSchema());
                break;
            }
            case UNION: {
                resolveUnionSchema(schema);
                break;
            }
            case MAP: {
                resolveSchema(schema.getMapValueSchema());
                break;
            }
            case SIMPLE: {
            }
        }
    }

    private void resolveUnionSchema(ISchema schema) {
        List<ISchema> oneOf = schema.getOneOf();
        if (oneOf != null) {
            for (ISchema sub : oneOf) {
                resolveSchema(sub);
            }
        }
    }

    private void resolveObjSchema(IObjSchema schema) {
        for (IObjPropMeta prop : schema.getProps()) {
            resolveSchema(prop.getSchema());
        }
    }

    private ISchema getRefSchema(ISchemaNode schema) {
        String ref = schema.getRef();
        if (StringHelper.isEmpty(ref))
            return null;

        ISchema refNode = resolveState.refCache.get(ref);
        if (refNode != null) {
            resolveSchema(refNode);
            return refNode;
        }

        String localRef = schema.getLocalRef();
        if (localRef != null) {
            throw new NopException(ERR_XMETA_UNKNOWN_REF).param(ARG_REF_NAME, ref).source(schema);
        } else {
            IObjMeta refDef;
            try {
                refDef = SchemaLoader.loadXMeta(ref);
            } catch (NopException e) {
                e.addXplStack(schema);
                throw e;
            }

            if (refDef == null)
                throw new NopException(ERR_XMETA_UNKNOWN_REF).param(ARG_REF_NAME, ref).source(refNode);
            refNode = refDef.getRootSchema();
            Guard.checkState(refNode.isRefResolved(), "refNode must be resolved");
        }
        resolveState.refCache.put(ref, refNode);

        return refNode;
    }
}