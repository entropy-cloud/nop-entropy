/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xdsl.json;

import io.nop.core.lang.eval.EvalExprProvider;
import io.nop.core.lang.json.DeltaJsonOptions;
import io.nop.core.lang.json.JObject;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.json.bind.ValueResolverCompilerRegistry;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.json.CompactXNodeToJsonTransformer;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceConstants;
import io.nop.xlang.api.XLang;
import io.nop.xlang.feature.XModelInclude;
import io.nop.xlang.xdsl.XDslKeys;

import java.util.Map;

public class XJsonLoader {

    public static Map<String, Object> loadDeltaXJson(IResource resource, ValueResolverCompilerRegistry registry) {
        DeltaJsonOptions options = newOptions(registry);
        return JsonTool.instance().loadDeltaBean(resource, JObject.class, options);
    }

    public static DeltaJsonOptions newOptions(ValueResolverCompilerRegistry registry) {
        DeltaJsonOptions options = new DeltaJsonOptions();
        options.setEvalContext(XLang.newEvalScope());
        options.setExprParser(EvalExprProvider.getDefaultExprParser());
        options.setExtendsGenerator(EvalExprProvider.getDeltaExtendsGenerator());
        options.setResourceLoader(XJsonLoader::loadJsonResource);
        options.setRegistry(registry);
        options.setIgnoreUnknownValueResolver(true);
        return options;
    }

    public static Map<String, Object> loadJsonResource(IResource resource) {
        if (resource.getName().endsWith(ResourceConstants.FILE_POSTFIX_XML)) {
            return loadJsonXml(resource);
        }
        return JsonTool.parseBeanFromResource(resource, JObject.class, true);
    }

    private static Map<String, Object> loadJsonXml(IResource resource) {
        XNode node = XModelInclude.instance().keepComment(true).loadActiveNodeFromResource(resource);
        if (node == null)
            return null;

        XDslKeys keys = XDslKeys.of(node);

        XNode genExtends = node.removeChildByTag(keys.GEN_EXTENDS);
        XNode postExtends = node.removeChildByTag(keys.POST_EXTENDS);

        Map<String, Object> json = (Map<String, Object>) new CompactXNodeToJsonTransformer().transformToObject(node);
        if (genExtends != null) {
            json.put(keys.GEN_EXTENDS, genExtends);
        }
        if (postExtends != null) {
            json.put(keys.POST_EXTENDS, postExtends);
        }
        return json;
    }
}