/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.lang.json;

import io.nop.api.core.annotations.core.GlobalInstance;
import io.nop.api.core.json.JsonParseOptions;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.type.StdDataType;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.EvalExprProvider;
import io.nop.core.lang.json.bind.ValueResolverCompilerRegistry;
import io.nop.core.lang.json.handler.BuildJObjectJsonHandler;
import io.nop.core.lang.json.handler.BuildObjectJsonHandler;
import io.nop.core.lang.json.impl.DefaultJsonTool;
import io.nop.core.lang.json.yaml.CollectYamlJsonHandler;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceConstants;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.type.utils.JavaTypeHelper;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.StringWriter;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.function.Function;

import static io.nop.core.CoreConfigs.CFG_JSON_PARSE_IGNORE_UNKNOWN_PROP;

@GlobalInstance
public class JsonTool {
    private static IJsonTool _instance = new DefaultJsonTool();

    public static void registerInstance(IJsonTool tool) {
        _instance = tool;
    }

    public static IJsonTool instance() {
        return _instance;
    }

    public static Object parse(String str) {
        if (StringHelper.isBlank(str))
            return null;
        JsonParseOptions options = new JsonParseOptions();
        options.setStrictMode(true);
        return instance().parseFromText(null, str, options);
    }

    public static Object parseNonStrict(String str) {
        JsonParseOptions options = new JsonParseOptions();
        options.setStrictMode(false);
        return instance().parseFromText(null, str, options);
    }

    public static Map<String, Object> parseMap(String str) {
        return (Map<String, Object>) parseNonStrict(str);
    }

    public static Object parseNonStrict(SourceLocation loc, String str) {
        JsonParseOptions options = new JsonParseOptions();
        options.setStrictMode(false);
        return instance().parseFromText(loc, str, options);
    }

    public static String stringify(Object o, Function<String, String> transformer, String indent) {
        return instance().stringify(o, transformer, indent);
    }

    public static String serialize(Object o, boolean pretty) {
        return stringify(o, null, pretty ? "  " : null);
    }

    public static String stringify(Object o) {
        return stringify(o, null, null);
    }

    public static Object beanToJsonObject(Object o, boolean keepLoc) {
        if (o == null)
            return null;

        BuildObjectJsonHandler handler = keepLoc ? new BuildJObjectJsonHandler() : new BuildObjectJsonHandler();
        instance().serializeTo(o, handler);
        return handler.getResult();
    }

    public static Object beanToJsonObject(Object o) {
        return beanToJsonObject(o, false);
    }

    public static Object jsonObjectToBean(Object o, Type targetType) {
        return BeanTool.buildBean(o, targetType);
    }

    public static <T> T parseBeanFromText(String text, Type targetType) {
        JsonParseOptions options = new JsonParseOptions();
        options.setTargetType(targetType);
        options.setStrictMode(false);
        options.setIgnoreUnknownProp(CFG_JSON_PARSE_IGNORE_UNKNOWN_PROP.get());
        if (JavaTypeHelper.getRawClass(options.getTargetType()) == JObject.class) {
            options.setKeepLocation(true);
        }
        return (T) instance().parseFromText(null, text, options);
    }

    public static <T> T parseBeanFromYaml(String text, Type targetType) {
        JsonParseOptions options = new JsonParseOptions();
        options.setYaml(true);
        options.setTargetType(targetType);
        options.setIgnoreUnknownProp(CFG_JSON_PARSE_IGNORE_UNKNOWN_PROP.get());
        if (JavaTypeHelper.getRawClass(options.getTargetType()) == JObject.class) {
            options.setKeepLocation(true);
        }
        return (T) instance().parseFromText(null, text, options);
    }

    public static <T> T parseBeanFromResource(IResource resource, Type targetType) {
        return parseBeanFromResource(resource, targetType, false);
    }

    public static Object parseBeanFromResource(IResource resource) {
        return parseBeanFromResource(resource, Object.class);
    }

    public static Object parseBeanFromVirtualPath(String path) {
        IResource resource = VirtualFileSystem.instance().getResource(path);
        return parseBeanFromResource(resource);
    }

    public static <T> T parseBeanFromResource(IResource resource, Type targetType, boolean traceDepends) {
        JsonParseOptions options = new JsonParseOptions();
        String fileName = resource.getName();
        if (fileName.endsWith(ResourceConstants.FILE_POSTFIX_YAML)
                || fileName.endsWith(ResourceConstants.FILE_POSTFIX_YML)) {
            options.setYaml(true);
        }
        if (resource.getName().endsWith(ResourceConstants.FILE_POSTFIX_JSON5)) {
            options.setStrictMode(false);
        }
        options.setTargetType(targetType);
        options.setTraceDepends(traceDepends);
        options.setIgnoreUnknownProp(CFG_JSON_PARSE_IGNORE_UNKNOWN_PROP.get());
        if (JavaTypeHelper.getRawClass(options.getTargetType()) == JObject.class) {
            options.setKeepLocation(true);
        }
        return (T) instance().parseFromResource(resource, options);
    }

    public static boolean isJsonOrYamlFileExt(String fileExt) {
        return ResourceConstants.JSON_OR_YAML_FILE_EXTS.contains(fileExt);
    }

    public static boolean isJsonOrYaml(String filePath) {
        String fileExt = StringHelper.fileExt(filePath);
        return ResourceConstants.JSON_OR_YAML_FILE_EXTS.contains(fileExt);
    }

    public static boolean isJsonFileExt(String fileExt) {
        return ResourceConstants.JSON_OR_YAML_FILE_EXTS.contains(fileExt);
    }

    public static boolean isYamlFileExt(String fileExt) {
        return ResourceConstants.YAML_FILE_EXTS.contains(fileExt);
    }

    public static <T> T loadBean(String path, Class<?> beanClass) {
        return parseBeanFromResource(VirtualFileSystem.instance().getResource(path), beanClass);
    }

    public static Object loadJson(String path) {
        return loadBean(path, Object.class);
    }

    public static <T> T tryLoadBean(String path, Class<?> beanClass, boolean traceDepends) {
        IResource resource = VirtualFileSystem.instance().getResource(path);
        if (!resource.exists() || resource.length() == 0)
            return null;
        return parseBeanFromResource(resource, beanClass, traceDepends);
    }

    public <T> T loadDeltaBean(String path, Type targetType) {
        return loadDeltaBeanFromResource(VirtualFileSystem.instance().getResource(path), targetType);
    }

    public static <T> T loadDeltaBeanFromResource(IResource resource, Type targetType) {
        DeltaJsonOptions options = new DeltaJsonOptions();
        options.setIgnoreUnknownValueResolver(false);
        options.setEvalContext(EvalExprProvider.newEvalScope());
        options.setRegistry(ValueResolverCompilerRegistry.DEFAULT);
        options.setExtendsGenerator(EvalExprProvider.getDeltaExtendsGenerator());
        options.setFeatureSwitchEvaluator(EvalExprProvider.getFeaturePredicateEvaluator());
        return loadDeltaBeanFromResource(resource, targetType, options);
    }

    public static <T> T loadDeltaBeanFromResource(IResource resource, Type targetType, DeltaJsonOptions options) {
        return instance().loadDeltaBean(resource, targetType, options);
    }

    public static Object parseSimpleJsonValue(String value) {
        return parseSimpleJsonValue(value, StdDataType.ANY);
    }

    public static Object parseSimpleJsonValue(String str, StdDataType dataType) {
        if (dataType == StdDataType.STRING) {
            if (str.startsWith("'") && str.endsWith("'"))
                return StringHelper.unquote(str);
            if (str.startsWith("\"") && str.endsWith("\""))
                return StringHelper.unquote(str);
            return str;
        }

        if (dataType.isSimpleType())
            return dataType.convert(str);

        if (str.equals("true") || str.equals("false"))
            return Boolean.valueOf(str);

        if (str.equals("0"))
            return 0;

        if (str.startsWith("0.") && StringHelper.isNumber(str))
            return StringHelper.parseNumber(str);

        if (str.startsWith("0"))
            return str;

        if (StringHelper.isNumber(str)) {
            return StringHelper.parseNumber(str);
        }
        if (str.startsWith("[") && str.endsWith("]")) {
            return parseNonStrict(str);
        }
        if (str.startsWith("{") && str.endsWith("}"))
            return parseNonStrict(str);

        if (str.startsWith("'") && str.endsWith("'"))
            return StringHelper.unquote(str);
        if (str.startsWith("\"") && str.endsWith("\""))
            return StringHelper.unquote(str);
        return str;
    }

    public static String serializeToYaml(Object value) {
        if (value == null)
            return null;

        // 对于 JObject/JArray 使用保留 comment 的序列化方式
        if (value instanceof JObject || value instanceof JArray) {
            return serializeToYamlWithComments(value);
        }

        DumperOptions options = new DumperOptions();
        options.setPrettyFlow(true);
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        BuildObjectJsonHandler handler = new BuildObjectJsonHandler();
        instance().serializeTo(value, handler);
        return new Yaml(options).dump(handler.getResult());
    }

    /**
     * 序列化 JObject/JArray 到 YAML，保留 comment
     * 使用 CollectYamlJsonHandler 来处理嵌套结构中的注释
     */
    private static String serializeToYamlWithComments(Object value) {
        StringWriter writer = new StringWriter();
        CollectYamlJsonHandler handler = new CollectYamlJsonHandler(writer);
        handler.beginDoc(StringHelper.ENCODING_UTF8);
        serializeToYamlRecursive(value, handler);
        handler.endDoc();
        return writer.toString();
    }

    /**
     * 递归序列化对象到 YAML，处理 JObject/JArray 的 comment
     */
    private static void serializeToYamlRecursive(Object value, CollectYamlJsonHandler handler) {
        if (value instanceof JObject) {
            JObject obj = (JObject) value;
            if (obj.getComment() != null) {
                handler.comment(obj.getComment());
            }
            handler.beginObject(obj.getLocation());
            for (Map.Entry<String, Object> entry : obj.entrySet()) {
                handler.key(entry.getKey());
                serializeToYamlRecursive(entry.getValue(), handler);
            }
            handler.endObject();
        } else if (value instanceof JArray) {
            JArray arr = (JArray) value;
            if (arr.getComment() != null) {
                handler.comment(arr.getComment());
            }
            handler.beginArray(arr.getLocation());
            for (Object item : arr) {
                serializeToYamlRecursive(item, handler);
            }
            handler.endArray();
        } else if (value instanceof IJsonSerializable) {
            ((IJsonSerializable) value).serializeToJson(handler);
        } else if (value instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) value;
            handler.beginObject(null);
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                handler.key(String.valueOf(entry.getKey()));
                serializeToYamlRecursive(entry.getValue(), handler);
            }
            handler.endObject();
        } else if (value instanceof java.util.Collection) {
            java.util.Collection<?> coll = (java.util.Collection<?>) value;
            handler.beginArray(null);
            for (Object item : coll) {
                serializeToYamlRecursive(item, handler);
            }
            handler.endArray();
        } else {
            handler.value(null, value);
        }
    }

    public static Object parseYaml(SourceLocation loc, String text) {
        JsonParseOptions options = new JsonParseOptions();
        options.setYaml(true);
        return instance().parseFromText(loc, text, options);
    }
}