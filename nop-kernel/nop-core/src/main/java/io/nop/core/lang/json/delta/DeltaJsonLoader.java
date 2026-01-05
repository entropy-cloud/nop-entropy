/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.lang.json.delta;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.objects.ValueWithLocation;
import io.nop.core.CoreConstants;
import io.nop.core.i18n.JsonI18nHelper;
import io.nop.core.lang.json.DeltaJsonOptions;
import io.nop.core.lang.json.JObject;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.component.ResourceComponentManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class DeltaJsonLoader {
    static final Logger LOG = LoggerFactory.getLogger(DeltaJsonLoader.class);

    private static final DeltaJsonLoader _INSTANCE = new DeltaJsonLoader();

    public static DeltaJsonLoader instance() {
        return _INSTANCE;
    }

    public Object resolveExtends(Object obj, DeltaJsonOptions options) {
        Object result = new JsonExtender(path -> loadFromPath(path, options), options).xtend(obj, true);
        if (result instanceof Map
                && ConvertHelper.toPrimitiveBoolean(((Map<?, ?>) result).get(CoreConstants.ATTR_X_DUMP))) {
            if (LOG.isInfoEnabled())
                LOG.info("nop.json.merge-result:{}", JsonTool.serialize(result, true));
        }
        if (options == null || options.isCleanDelta())
            JsonCleaner.instance().cleanDelta(result);
        return result;
    }

    public Map<String, Object> getGenExtends(SourceLocation loc, Map<String, Object> obj, boolean keepXNode) {
        JObject base = new JObject();
        base.setLocation(loc);
        Object ext = obj.get(CoreConstants.ATTR_X_EXTENDS);
        if (ext != null) {
            base.put(CoreConstants.ATTR_X_EXTENDS, ext);
        }
        Object genExtends = obj.get(CoreConstants.ATTR_X_GEN_EXTENDS);
        if (genExtends != null) {
            if (genExtends instanceof XNode) {
                if (!keepXNode) {
                    genExtends = ((XNode) genExtends).innerXml();
                    genExtends = ValueWithLocation.of(loc, genExtends);
                }
            } else {
                if (!(genExtends instanceof String))
                    throw new IllegalArgumentException("nop.web.page.gen-extends-not-string");

                genExtends = ValueWithLocation.of(loc, genExtends);
            }
            base.put(CoreConstants.ATTR_X_GEN_EXTENDS, genExtends);
        }
        return base;
    }

    public Map<String, Object> loadFromPath(String path, DeltaJsonOptions options) {
        return loadFromResource(VirtualFileSystem.instance().getResource(path), options);
    }

    public Map<String, Object> loadFromResource(IResource resource, DeltaJsonOptions options) {
        ResourceComponentManager.instance().traceDepends(resource.getPath());

        Map<String, Object> obj;
        if (options != null && options.getResourceLoader() != null) {
            obj = options.getResourceLoader().apply(resource);
        } else {
            obj = JsonTool.parseBeanFromResource(resource, JObject.class, true);

            if (options != null && options.getFeatureSwitchEvaluator() != null)
                obj = JsonFeatureSwitch.INSTANCE.processMap(obj, options.getFeatureSwitchEvaluator());
        }

        if (obj != null && options != null && options.isNormalizeI18nKey()) {
            JsonI18nHelper.i18nKeyToBindExpr(obj);
        }
        return obj;
    }
}