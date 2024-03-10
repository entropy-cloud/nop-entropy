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
import io.nop.core.lang.json.handler.BuildJObjectJsonHandler;
import io.nop.core.lang.json.handler.BuildObjectJsonHandler;
import io.nop.core.lang.json.impl.DefaultJsonTool;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceConstants;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.type.utils.JavaTypeHelper;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.lang.reflect.Type;
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
        JsonParseOptions options = new JsonParseOptions();
        options.setStrictMode(true);
        return instance().parseFromText(null, str, options);
    }

    public static Object parseNonStrict(String str) {
        JsonParseOptions options = new JsonParseOptions();
        options.setStrictMode(false);
        return instance().parseFromText(null, str, options);
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

    public static Object serializeToJson(Object o, boolean keepLoc) {
        if (o == null)
            return null;

        BuildObjectJsonHandler handler = keepLoc ? new BuildJObjectJsonHandler() : new BuildObjectJsonHandler();
        instance().serializeTo(o, handler);
        return handler.getResult();
    }

    public static Object serializeToJson(Object o) {
        return serializeToJson(o, false);
    }

    public static Object parseBeanFromText(String text, Type targetType) {
        JsonParseOptions options = new JsonParseOptions();
        options.setTargetType(targetType);
        options.setStrictMode(false);
        options.setIgnoreUnknownProp(CFG_JSON_PARSE_IGNORE_UNKNOWN_PROP.get());
        if (JavaTypeHelper.getRawClass(options.getTargetType()) == JObject.class) {
            options.setKeepLocation(true);
        }
        return instance().parseFromText(null, text, options);
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

    public static boolean isJsonOrYaml(String filePath) {
        String fileExt = StringHelper.fileExt(filePath);
        return ResourceConstants.JSON_FILE_EXTS.contains(fileExt);
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

    public static <T> T loadDeltaBean(IResource resource, Type targetType, DeltaJsonOptions options) {
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
            return Boolean.getBoolean(str);

        if (str.equals("0"))
            return 0;

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
        DumperOptions options = new DumperOptions();
        options.setPrettyFlow(true);
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        BuildObjectJsonHandler handler = new BuildObjectJsonHandler();
        instance().serializeTo(value, handler);
        return new Yaml(options).dump(handler.getResult());
    }

    public static Object parseYaml(SourceLocation loc, String text) {
        JsonParseOptions options = new JsonParseOptions();
        options.setYaml(true);
        return instance().parseFromText(loc, text, options);
    }
}