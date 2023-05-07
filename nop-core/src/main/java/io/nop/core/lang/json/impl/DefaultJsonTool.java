/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.lang.json.impl;

import io.nop.api.core.config.AppConfig;
import io.nop.api.core.json.JsonParseOptions;
import io.nop.api.core.util.Guard;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.StringHelper;
import io.nop.core.CoreConstants;
import io.nop.core.lang.eval.DisabledEvalScope;
import io.nop.core.lang.json.DeltaJsonOptions;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.core.lang.json.IJsonSerializerFactory;
import io.nop.core.lang.json.IJsonTool;
import io.nop.core.lang.json.JObject;
import io.nop.core.lang.json.JsonSaveOptions;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.json.bind.JsonBindExprEvaluator;
import io.nop.core.lang.json.delta.DeltaJsonLoader;
import io.nop.core.lang.json.handler.CollectTextJsonHandler;
import io.nop.core.lang.json.parse.JsonParser;
import io.nop.core.lang.json.serialize.JsonSerializeHelper;
import io.nop.core.lang.json.serialize.JsonSerializer;
import io.nop.core.lang.json.serialize.JsonSerializerFactory;
import io.nop.core.lang.json.serialize.JsonWhitelistChecker;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.reflect.bean.BeanCopyOptions;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.core.reflect.bean.IBeanModelManager;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.type.IGenericType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Writer;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.function.Function;

import static io.nop.core.CoreConfigs.CFG_JSON_SERIALIZE_ONLY_DATA_BEAN;

public class DefaultJsonTool implements IJsonTool {
    static final Logger LOG = LoggerFactory.getLogger(DefaultJsonTool.class);

    private static final JsonParseOptions DEFAULT_OPTIONS = new JsonParseOptions();

    @Override
    public Object parseFromResource(IResource resource, JsonParseOptions options) {
        options = normalizeOptions(options);
        Object result = JsonParser.instance(options).parseFromResource(resource, false);
        if (result == null)
            return null;
        return makeResult(result, options);
    }

    @Override
    public Object parseFromText(SourceLocation loc, String text, JsonParseOptions options) {
        if (StringHelper.isEmpty(text))
            return null;
        options = normalizeOptions(options);
        Object result = JsonParser.instance(options).parseFromText(loc, text);
        if (result == null)
            return null;
        return makeResult(result, options);
    }

    JsonParseOptions normalizeOptions(JsonParseOptions options) {
        if (options == null)
            return DEFAULT_OPTIONS;
        return options;
    }

    boolean isTypeMatch(Type type, Object obj) {
        if (type instanceof Class) {
            return ((Class<?>) type).isInstance(obj);
        }
        if (type instanceof IGenericType) {
            return ((IGenericType) type).isInstance(obj);
        }
        return false;
    }

    Object makeResult(Object result, JsonParseOptions options) {
        if (options != null && options.getTargetType() != null && !isTypeMatch(options.getTargetType(), result)) {
            BeanCopyOptions copyOptions = new BeanCopyOptions();
            copyOptions.setIgnoreUnknownProp(options.isIgnoreUnknownProp());
            return BeanTool.instance().buildBean(result,
                    ReflectionManager.instance().buildGenericType(options.getTargetType()), copyOptions);
        }
        return result;
    }

    @Override
    public String stringify(Object o, Function<String, String> transformer, String indent) {
        IBeanModelManager beanModelManager = ReflectionManager.instance();
        IJsonSerializerFactory factory = JsonSerializerFactory.DEFAULT_FACTORY;
        JsonSerializer serializer = new JsonSerializer(beanModelManager, CFG_JSON_SERIALIZE_ONLY_DATA_BEAN.get(),
                factory, JsonWhitelistChecker.instance(), DisabledEvalScope.INSTANCE);

        return JsonSerializeHelper.serializeToJsonString(serializer, null, o, indent);
    }

    @Override
    public void serialize(Object obj, String indent, Writer out) {
        CollectTextJsonHandler handler = new CollectTextJsonHandler(out);
        handler.indent(indent);
        JsonSerializeHelper.serialize(null, obj, handler);
    }

    @Override
    public void serializeTo(Object obj, IJsonHandler handler) {
        JsonSerializeHelper.serialize(null, obj, handler);
    }

    @Override
    public <T> T loadDeltaBean(IResource resource, Type targetType, DeltaJsonOptions options) {
        Map<String, Object> map = DeltaJsonLoader.instance().loadFromResource(resource, options);
        if (map == null)
            return null;

        map = (Map<String, Object>) DeltaJsonLoader.instance().resolveExtends(map, options);
        dumpResult(resource, map);

        return evalAndCastType(map, targetType, options);
    }

    protected void dumpResult(IResource resource, Map<String, Object> map) {
        if (AppConfig.isDebugMode()) {
            String dumpPath = ResourceHelper.getDumpPath(resource.getPath());

            IResource dumpResource = VirtualFileSystem.instance().getResource(dumpPath);
            JsonSaveOptions saveOptions = new JsonSaveOptions();
            saveOptions.setPretty(true);
            saveOptions.setKeepComment(true);
            saveToResource(dumpResource, map, saveOptions);
        }
    }

    @Override
    public <T> T buildDeltaBean(Map<String, Object> obj, Type targetType, DeltaJsonOptions options) {
        Guard.notNull(obj, "obj");

        Map<String, Object> map = (Map<String, Object>) DeltaJsonLoader.instance().resolveExtends(obj, options);

        return evalAndCastType(map, targetType, options);
    }

    protected <T> T evalAndCastType(Map<String, Object> map, Type targetType, DeltaJsonOptions options) {
        if (options != null && options.getRegistry() != null) {
            map = (Map<String, Object>) JsonBindExprEvaluator.evalBindExpr(map, options.isIgnoreUnknownValueResolver(),
                    options.getExprParser(), options.getRegistry(), options.getEvalContext());
        }

        if (targetType == Map.class || targetType == null)
            return (T) map;
        if (targetType == JObject.class) {
            if (map instanceof JObject)
                return (T) map;
            JObject ret = new JObject();
            ret.putAll(map);
            return (T) ret;
        }
        return BeanTool.buildBean(map, targetType);
    }

    @Override
    public boolean saveToResource(IResource resource, Object json, JsonSaveOptions options) {
        if (options == null)
            options = new JsonSaveOptions();

        String fileName = resource.getName();
        String text;
        if (fileName.endsWith(CoreConstants.FILE_POSTFIX_YAML) || fileName.endsWith(CoreConstants.FILE_POSTFIX_YML)) {
            text = JsonTool.serializeToYaml(json);
        } else {
            text = JsonTool.serialize(json, options.isPretty());
        }

        if (options.isCheckSameContent()) {
            if (resource.exists()) {
                String oldText = ResourceHelper.readText(resource);
                if (oldText == null)
                    oldText = "";
                if (oldText.equals(text)) {
                    LOG.info("nop.json.skip-save-since-file-contents-are-not-change:path={},len={}", resource.getPath(),
                            text.length());
                    return false;
                }
            }
        }
        ResourceHelper.writeText(resource, text);
        return true;
    }
}