/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.feature;

import io.nop.api.core.config.AppConfig;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.commons.util.StringHelper;
import io.nop.commons.util.objects.ValueWithLocation;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.core.resource.IResource;
import io.nop.xlang.xdsl.XDslConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;

/**
 * 对任意xml文件都支持的组合和裁剪操作，不需要XDefinition元模型支持。
 *
 * @author canonical_entropy@163.com
 */
public class XModelInclude {
    // static final XModelInclude _instance = new XModelInclude();
    static final Logger LOG = LoggerFactory.getLogger(XModelInclude.class);

    public static XModelInclude instance() {
        return new XModelInclude();
    }

    private boolean keepComment;

    public XModelInclude keepComment(boolean keepComment) {
        this.keepComment = keepComment;
        return this;
    }

    public XNode loadActiveNode(String path) {
        XNode node = XNodeParser.instance().parseFromVirtualPath(path);
        // ResourceDepends.traceResource(resource);
        if (!checkFeatureSwitch(node, new FeatureConditionEvaluator())) {
            node.clearBody();
            node.clearAttrs();
        }
        return node;
    }

    public XNode loadActiveNodeFromResource(IResource resource) {
        XNode node = XNodeParser.instance().keepComment(keepComment).parseFromResource(resource);
        // ResourceDepends.traceResource(resource);
        if (!checkFeatureSwitch(node, new FeatureConditionEvaluator())) {
            node.clearBody();
            node.clearAttrs();
        }
        return node;
    }

    public boolean checkFeatureSwitch(XNode node, FeatureConditionEvaluator evaluator) {
        if (!isEnabled(node, evaluator)) {
            LOG.info("nop.xlang.xdsl.remove-node-when-feature-disabled:node={}", node);
            return false;
        }

        if (node.hasChild()) {
            Iterator<XNode> it = node.getChildren().iterator();
            while (it.hasNext()) {
                if (!checkFeatureSwitch(it.next(), evaluator))
                    it.remove();
            }
        }

        return true;
    }

    private boolean isEnabled(XNode node, FeatureConditionEvaluator evaluator) {
        ValueWithLocation onAttr = node.removeAttr(XDslConstants.ATTR_FEATURE_ON);
        ValueWithLocation offAttr = node.removeAttr(XDslConstants.ATTR_FEATURE_OFF);

        if (!StringHelper.isBlank(onAttr.asString())) {
            String on = onAttr.asString();
            if (StringHelper.isValidConfigVar(on)) {
                if (ConvertHelper.toFalsy(AppConfig.var(on)))
                    return false;
            } else {
                if (!evaluator.evaluate(onAttr.getLocation(), onAttr.asString()))
                    return false;
            }
        }

        if (!StringHelper.isBlank(offAttr.asString())) {
            String off = offAttr.asString();

            if (StringHelper.isValidConfigVar(off)) {
                if (ConvertHelper.toTruthy(AppConfig.var(off)))
                    return false;
            }

            if (evaluator.evaluate(onAttr.getLocation(), onAttr.asString()))
                return false;
        }

        return true;
    }
}