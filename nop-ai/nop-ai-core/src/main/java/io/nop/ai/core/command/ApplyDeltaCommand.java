/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ai.core.command;

import io.nop.ai.core.file.FileContent;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.IResource;
import io.nop.core.resource.component.ComponentModelConfig;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.xlang.delta.DeltaMerger;
import io.nop.xlang.xdef.IXDefinition;
import io.nop.xlang.xdsl.XDslKeys;
import io.nop.xlang.xdsl.XDslValidator;
import io.nop.xlang.xmeta.SchemaLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ApplyDeltaCommand implements IToolCommand {
    static final Logger LOG = LoggerFactory.getLogger(ApplyDeltaCommand.class);

    public static final String NAME = "apply-delta";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public ToolCommandResult execute(XNode node, CallToolsContext context) {
        String id = node.attrText("id");
        String path = node.attrText("path");
        String deltaPath = node.attrText("deltaPath");
        boolean dryRun = node.attrBoolean("dryRun", false);

        if (StringHelper.isEmpty(path)) {
            return ToolCommandResult.error(id, "File path is empty");
        }

        try {
            IResource targetResource = context.getFileOperator().getResource(path);
            if (!targetResource.exists()) {
                return ToolCommandResult.error(id, "Target file does not exist: " + path);
            }

            XNode baseNode = XNode.parseFromResource(targetResource);
            String xdefPath = resolveXdefPath(path, baseNode);
            if (StringHelper.isEmpty(xdefPath)) {
                return ToolCommandResult.error(id, "No xdef configured for file: " + path);
            }

            IXDefinition xdef = SchemaLoader.loadXDefinition(xdefPath);
            XNode deltaNode = loadDeltaNode(node, deltaPath, context);

            XDslKeys keys = XDslKeys.of(baseNode);
            new DeltaMerger(keys).merge(baseNode, deltaNode, xdef.getRootNode(), false);
            new XDslValidator(keys).validate(baseNode, xdef.getRootNode(), true);

            String mergedContent = baseNode.xml();
            if (dryRun) {
                return ToolCommandResult.success(id, mergedContent);
            }

            context.getFileOperator().writeFileContent(new FileContent(path, mergedContent));
            return ToolCommandResult.success(id, "Delta applied successfully. File updated: " + path);
        } catch (Exception e) {
            LOG.debug("nop.ai.apply-delta-fail:path={},deltaPath={}", path, deltaPath, e);
            return ToolCommandResult.error(id, e);
        }
    }

    protected XNode loadDeltaNode(XNode commandNode, String deltaPath, CallToolsContext context) {
        XDslKeys keys = XDslKeys.DEFAULT;
        XNode deltaContentNode = commandNode.childByTag("deltaContent");
        if (deltaContentNode != null) {
            if (deltaContentNode.getChildCount() != 1) {
                throw new IllegalArgumentException("deltaContent must contain exactly one XML root node");
            }
            XNode deltaNode = deltaContentNode.child(0).cloneInstance();
            deltaNode.removeAttr(keys.EXTENDS);
            return deltaNode;
        }

        if (StringHelper.isEmpty(deltaPath)) {
            throw new IllegalArgumentException("Either deltaContent or deltaPath must be provided");
        }

        IResource deltaResource = context.getFileOperator().getResource(deltaPath);
        if (!deltaResource.exists()) {
            throw new IllegalArgumentException("Delta file does not exist: " + deltaPath);
        }
        XNode deltaNode = XNode.parseFromResource(deltaResource);
        deltaNode.removeAttr(keys.EXTENDS);
        return deltaNode;
    }

    protected String resolveXdefPath(String path, XNode baseNode) {
        XDslKeys keys = XDslKeys.of(baseNode);
        String schemaPath = baseNode.attrText(keys.SCHEMA);
        if (!StringHelper.isEmpty(schemaPath)) {
            return schemaPath;
        }

        ComponentModelConfig config = ResourceComponentManager.instance()
                .getModelConfigByFileType(StringHelper.fileType(path));
        return config == null ? null : config.getXdefPath();
    }
}
