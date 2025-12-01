/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xdsl;

import io.nop.api.core.config.AppConfig;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.core.resource.IResource;
import io.nop.core.resource.IResourceDslNodeLoader;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.xlang.api.XLang;
import io.nop.xlang.feature.XModelInclude;
import io.nop.xlang.xdef.IXDefinition;
import io.nop.xlang.xmeta.SchemaLoader;

import static io.nop.xlang.XLangErrors.ARG_NODE;
import static io.nop.xlang.XLangErrors.ARG_REQUIRED_SCHEMA;
import static io.nop.xlang.XLangErrors.ARG_SCHEMA_PATH;
import static io.nop.xlang.XLangErrors.ERR_XDSL_NOT_REQUIRED_SCHEMA;
import static io.nop.xlang.XLangErrors.ERR_XDSL_NO_SCHEMA;

public class DslNodeLoader implements IXDslNodeLoader {
    public static final DslNodeLoader INSTANCE = new DslNodeLoader();

    @Override
    public XDslExtendResult loadFromResource(IResource resource, String requiredSchema, XDslExtendPhase phase) {
        // 处理feature:on和feature:off开关
        XNode node = XModelInclude.instance().keepComment(true).loadActiveNodeFromResource(resource);
//        if (!node.hasAttr("x:schema") && !node.hasAttr("xmlns:x") && requiredSchema != null) {
//            node.setAttr("x:schema", requiredSchema);
//            node.setAttr("xmlns:x", XLangConstants.XDSL_SCHEMA_XDSL);
//        }

        return loadFromNode(node, requiredSchema, phase);
    }

    public XNode loadDslNodeFromResource(IResource resource, String requiredSchema,
                                         IResourceDslNodeLoader.ResolvePhase resolvePhase) {
        if (resolvePhase == null)
            resolvePhase = IResourceDslNodeLoader.ResolvePhase.completed;

        XNode node = XNodeParser.instance().keepComment(true).parseFromResource(resource);

        return processDslNode(node, requiredSchema, resolvePhase);
    }

    public XNode processDslNode(XNode node, String requiredSchema,
                                IResourceDslNodeLoader.ResolvePhase phase) {
        if (phase == null)
            phase = IResourceDslNodeLoader.ResolvePhase.completed;

        if (phase == IResourceDslNodeLoader.ResolvePhase.raw)
            return node;

        node = XModelInclude.instance().processNode(node);
        if (phase == IResourceDslNodeLoader.ResolvePhase.filtered)
            return node;

        if (phase == IResourceDslNodeLoader.ResolvePhase.merged) {
            return loadFromNode(node, requiredSchema, XDslExtendPhase.mergeBase).getNode();
        }

        return loadFromNode(node, requiredSchema, XDslExtendPhase.validate).getNodeForDump();
    }

    @Override
    public XDslExtendResult loadFromNode(XNode node, String requiredSchema, XDslExtendPhase phase) {
        XDslKeys keys = XDslKeys.of(node);
        String schemaPath = node.attrText(keys.SCHEMA);
        if (schemaPath == null)
            schemaPath = requiredSchema;

        if (StringHelper.isEmpty(schemaPath))
            throw new NopException(ERR_XDSL_NO_SCHEMA).param(ARG_NODE, node);
        IXDefinition def = SchemaLoader.loadXDefinition(schemaPath);

        if (requiredSchema != null) {
            if (!requiredSchema.equals(schemaPath) &&
                    !requiredSchema.equals(def.getXdefBase()) && !def.getAllRefSchemas().contains(requiredSchema)) {
                throw new NopException(ERR_XDSL_NOT_REQUIRED_SCHEMA).param(ARG_REQUIRED_SCHEMA, requiredSchema)
                        .param(ARG_SCHEMA_PATH, schemaPath);
            }
        }

        IEvalScope scope = XLang.newEvalScope();
        XDslExtendResult result = new XDslExtender(keys).xtend(def, def.getRootNode(), node, phase, scope);

        if (phase == XDslExtendPhase.validate) {
            if (!result.isValidated()) {
                SchemaLoader.validateNode(result.getNode(), def.getRootNode(), true);
                result.setValidated(true);
            }

            dumpMergedResult(node.resourcePath(), result);
        }
        return result;
    }

    private void dumpMergedResult(String path, XDslExtendResult result) {
        if (AppConfig.isDebugMode() && path != null) {
            String dumpPath = ResourceHelper.getDumpPath(path);
            XNode node = result.getNodeForDump();

            IResource resource = VirtualFileSystem.instance().getResource(dumpPath);
            ResourceHelper.writeText(resource, node.fullXml(true, true));
        }
    }
}
