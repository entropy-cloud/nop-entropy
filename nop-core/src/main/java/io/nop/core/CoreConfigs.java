/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core;

import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.annotations.core.Locale;
import io.nop.api.core.config.IConfigReference;
import io.nop.api.core.util.SourceLocation;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Set;

import static io.nop.api.core.config.AppConfig.varRef;

@Locale("zh-CN")
public interface CoreConfigs {
    SourceLocation s_loc = SourceLocation.fromClass(CoreConfigs.class);

    String CFG_COMPONENT_RESOURCE_CACHE_NAMED_RELOADABLE = "nop.core.component.resource-cache.{name}.reloadable";
    String CFG_COMPONENT_RESOURCE_CACHE_NAMED_SIZE = "nop.core.component.resource-cache.{name}.size";
    String CFG_COMPONENT_RESOURCE_CACHE_NAMED_CACHE_NULL = "nop.core.component.resource-cache.{name}.cache-null";
    String CFG_COMPONENT_RESOURCE_CACHE_NAMED_SUPPORT_SERIALIZE = "nop.core.component.resource-cache.{name}.support-serialize";
    String CFG_COMPONENT_RESOURCE_CACHE_NAMED_REFRESH_MIN_INTERVAL = "nop.core.component.resource-cache.{name}.refresh-min-interval";
    String CFG_COMPONENT_RESOURCE_CACHE_NAMED_TIMEOUT = "nop.core.component.resource-cache.{name}.timeout";

    @Description("是否启用命令行支持")
    IConfigReference<Boolean> CFG_CORE_NOP_COMMAND_EXECUTOR_ENABLED = varRef(s_loc, "nop.core.nop-command-executor.enabled",
            Boolean.class, true);

    @Description("缺省启用的多语言类型，会自动装载相应的i18n.yaml文件")
    IConfigReference<String> CFG_CORE_I18N_ENABLED_LOCALES = varRef(s_loc, "nop.core.i18n.enabled-locales", String.class,
            "zh-CN,en");

    @Description("禁用的ICoreInitializer初始化类")
    IConfigReference<String> CFG_CORE_DISABLED_INITIALIZERS = varRef(s_loc, "nop.core.initialize.disabled-initializers",
            String.class, null);

    @Description("指定CoreInitializer的最高初始化级别，仅对于执行级别小于此值的CoreInitializer才会被执行")
    IConfigReference<Integer> CFG_CORE_MAX_INITIALIZE_LEVEL = varRef(s_loc, "nop.core.initialize.max-initialize-level",
            Integer.class, Integer.MAX_VALUE);

    @Description("是否启用表达式调试机制")
    IConfigReference<Boolean> CFG_EVAL_SCOPE_DEBUG_ENABLED = varRef(s_loc, "nop.core.eval.scope.debug-enabled", Boolean.class,
            false);

    @Description("返回的异常消息包含错误堆栈")
    IConfigReference<Boolean> CFG_EXCEPTION_RETURN_STACKTRACE = varRef(s_loc, "nop.core.exceptions.return-stacktrace",
            Boolean.class, false);

    @Description("隐藏内部错误码")
    IConfigReference<Boolean> CFG_EXCEPTION_HIDE_INTERNAL_ERROR = varRef(s_loc, "nop.core.exceptions.hide-internal-error",
            Boolean.class, false);

    @Description("是否启用编译缓存，当待解析的模型源文件不存在，但是编译缓存文件存在时，则直接使用该缓存对象")
    IConfigReference<Boolean> CFG_COMPONENT_USE_CP_CACHE = varRef(s_loc, "nop.core.component.use-cp-cache", Boolean.class,
            false);

    @Description("在每次从组件编译缓存中查找时，是否主动检查模型文件是否已经被修改，如果已修改，则将重新加载")
    IConfigReference<Boolean> CFG_COMPONENT_RESOURCE_CACHE_CHECK_CHANGED = varRef(s_loc,
            "nop.core.component.resource-cache.check-changed", Boolean.class, true);

    @Description("缺省情况下某种配置文件的缓存大小")
    IConfigReference<Integer> CFG_COMPONENT_RESOURCE_CACHE_PER_TYPE_SIZE = varRef(s_loc,
            "nop.core.component.resource-cache.per-type-size", Integer.class, 10000);

    @Description("如果组件模型获取结果为null, 是否记录缓存结果为null, 避免反复解析，总是得到null")
    IConfigReference<Boolean> CFG_COMPONENT_RESOURCE_CACHE_NULL = varRef(s_loc, "nop.core.component.resource-cache.cache-null",
            Boolean.class, false);

    @Description("组件编译缓存的保留时间，单位为毫秒")
    IConfigReference<Duration> CFG_COMPONENT_RESOURCE_CACHE_TIMEOUT = varRef(s_loc,
            "nop.core.components.resource-cache.timeout", Duration.class, null);

    IConfigReference<Boolean> CFG_COMPONENT_RESOURCE_SUPPORT_SERIALIZE = varRef(s_loc,
            "nop.core.components.resource-cache.support-serialize", Boolean.class, true);

    IConfigReference<Integer> CFG_COMPONENT_RESOURCE_REFRESH_MIN_INTERVAL = varRef(s_loc,
            "nop.core.components.resource-cache.refresh-min-interval", Integer.class, 100);

    @Description("在启用tenant定制的情况下，最多允许多少个tenant的数据缓存在内存中")
    IConfigReference<Integer> CFG_COMPONENT_RESOURCE_CACHE_TENANT_CACHE_CONTAINER_SIZE = varRef(s_loc,
            "nop.core.component.resource-cache.tenant-cache-container-size", Integer.class, 100);

    @Description("缓存IResource.lastModified的调用结果，避免频繁发出系统调用")
    IConfigReference<Integer> CFG_COMPONENT_RESOURCE_TIMESTAMP_CACHE_SIZE = varRef(s_loc,
            "nop.core.component.resource-timestamp-cache.size", Integer.class, 10000);

    @Description("缓存IResource.lastModified调用结果的超时时间")
    IConfigReference<Duration> CFG_COMPONENT_RESOURCE_TIMESTAMP_CACHE_TIMEOUT = varRef(s_loc,
            "nop.core.component.resource-timestamp-cache.timeout", Duration.class, Duration.of(100, ChronoUnit.MILLIS));

    @Description("虚拟文件系统的配置文件")
    IConfigReference<String> CFG_RESOURCE_VFS_CONFIG_FILE = varRef(s_loc, "nop.core.resource.vfs-config-file", String.class,
            "");

//    @Description("是否允许每个租户定义专属于自己的定制目录。如果启用，则每个租户都拥有自己的组件缓存")
//    IConfigReference<Boolean> CFG_RESOURCE_STORE_ENABLE_TENANT_DELTA = varRef(s_loc,
//            "nop.core.resource.store.enable-tenant-delta", Boolean.class, false);

    @Description("指定差量文件系统的Delta层,例如_platform,product,app。对应/_delta/product等虚拟路径")
    IConfigReference<String> CFG_VFS_DELTA_LAYER_IDS = varRef(s_loc, "nop.core.vfs.delta-layer-ids",
            String.class, null);

    @Description("指定差量文件系统的Delta层所在的目录或者jar文件,其中_vfs子目录为虚拟文件目录")
    IConfigReference<String> CFG_VFS_LIB_PATHS = varRef(s_loc, "nop.core.vfs.lib-paths",
            String.class, null);

    @Description("指定Delta差量文件系统的StoreBuilder对象，需要实现IDeltaResourceStoreBuilder接口")
    IConfigReference<String> CFG_VFS_DELTA_RESOURCE_STORE_BUILDER_CLASS = varRef(s_loc,
            "nop.core.vfs.delta-resource-store-builder-class", String.class, null);

    @Description("是否自动扫描classpath下/_store目录，在内存中构造虚拟文件系统")
    IConfigReference<Boolean> CFG_RESOURCE_STORE_ENABLE_CLASSPATH_SCAN = varRef(s_loc,
            "nop.core.resource.store.enable-classpath-scan", Boolean.class, true);

    @Description("资源文件内容缓存的大小，例如生成组件时可以选择将生成结果缓存在内存中")
    IConfigReference<Integer> CFG_RESOURCE_CONTENT_CACHE_SIZE = varRef(s_loc, "nop.core.resource.store.content-cache-size",
            Integer.class, 1000);

    @Description("dump名字空间的文件所存储的路径。如果没有明确指定，则为user.dir工作目录下的_dump/{nop.api.config.app-id}子目录")
    IConfigReference<String> CFG_RESOURCE_DUMP_ROOT_DIR = varRef(s_loc, "nop.core.resource.store.dump-root-dir", String.class,
            "");

    @Description("temp名字空间所对应的临时文件所在的目录。如果没有指定，则为java.io.tmpdir目录下的_temp/{nop.api.config.app-id}子目录")
    IConfigReference<String> CFG_RESOURCE_TEMP_ROOT_DIR = varRef(s_loc, "nop.core.resource.store.temp-root-dir", String.class,
            "");

    @Description("data名字空间所对应的文件所在的目录。如果没有指定，则为./task")
    IConfigReference<String> CFG_RESOURCE_DATA_ROOT_DIR = varRef(s_loc, "nop.core.resource.store.data-root-dir", String.class,
            "./data");

    @Description("为避免通过file名字空间可以访问本机所有文件，可以通过此参数来限制file名字空间中允许的的文件路径模式，*表示在本级目录中匹配任意单词，而**表示匹配多层目录，"
            + "例如/a/*.txt表示匹配a目录下的所有文本文件，而/a/**表示匹配a目录下的所有文件以及子目录下的所有文件")
    IConfigReference<String> CFG_RESOURCE_ALLOWED_FILE_PATH_PATTERN = varRef(s_loc,
            "nop.core.resource.store.file-namespace-path-pattern", String.class, "");

    @Description("文件依赖跟踪时最多允许嵌套多少层。缺省值为200")
    IConfigReference<Integer> CFG_RESOURCE_MAX_DEPS_STACK_SIZE = varRef(s_loc, "nop.core.resource.max-deps-stack-size",
            Integer.class, 200);

    @Description("全局theme配置，用于装载theme名字空间下的资源文件")
    IConfigReference<String> CFG_GLOBAL_THEME = varRef(s_loc, "nop.global.theme", String.class, "");

    @Description("XML节点最多允许的嵌套层数")
    IConfigReference<Integer> CFG_XML_MAX_NESTED_LEVEL = varRef(s_loc, "nop.core.xml.max-nested-level", Integer.class, 50);

    @Description("XML格式化时每行最多多少个字符")
    IConfigReference<Integer> CFG_XML_FORMAT_MAX_CHARS_PER_LINE = varRef(s_loc, "nop.core.xml.format.max-chars-per-line",
            Integer.class, 120);

    @Description("JSON节点最多允许的嵌套层数")
    IConfigReference<Integer> CFG_JSON_MAX_NESTED_LEVEL = varRef(s_loc, "nop.core.json.max-nested-level", Integer.class, 50);

    @Description("JSON序列化时是否只支持标记为DataBean的数据类型。缺省情况下限制只有没有标记为数据传输用的类型才能参与序列化，避免序列化所有bean导致安全漏洞")
    IConfigReference<Boolean> CFG_JSON_SERIALIZE_ONLY_DATA_BEAN = varRef(s_loc, "nop.core.json.serialize-only-data-bean",
            Boolean.class, true);

    @Description("JSON解析是否忽略未知的属性")
    IConfigReference<Boolean> CFG_JSON_PARSE_IGNORE_UNKNOWN_PROP = varRef(s_loc, "nop.core.json.parse-ignore-unknown-prop",
            Boolean.class, false);

    @Description("通过反射得到的类模型存放在WeakHashMap缓存中，缓存大小由此参数指定")
    IConfigReference<Integer> CFG_REFLECT_MODEL_CACHE_SIZE = varRef(s_loc, "nop.core.reflect.model-cache-size", Integer.class,
            10000);

    @Description("明确指定启用的模块名称列表，采用逗号分隔")
    IConfigReference<Set> CFG_MODULE_ENABLED_MODULE_NAMES = varRef(s_loc, "nop.core.module.enabled-module-names", Set.class, null);

    @Description("明确指定禁用的模块名称列表，采用逗号分隔")
    IConfigReference<Set> CFG_MODULE_DISABLED_MODULE_NAMES = varRef(s_loc, "nop.core.module.disabled-module-names", Set.class,
            null);

    @Description("xpath编译缓存的大小")
    IConfigReference<Integer> CFG_XPATH_CACHE_SIZE = varRef(s_loc, "nop.core.xml.xpath-cache-size", Integer.class, 1000);

    @Description("jpath编译缓存的大小")
    IConfigReference<Integer> CFG_JPATH_CACHE_SIZE = varRef(s_loc, "nop.core.json.jpath-cache-size", Integer.class, 1000);

    @Description("xpath表达式的最大长度")
    IConfigReference<Integer> CFG_XPATH_EXPR_MAX_LENGTH = varRef(s_loc, "nop.core.xml.xpath-expr-max-length", Integer.class,
            500);

    @Description("jpath表达式的最大长度")
    IConfigReference<Integer> CFG_JPATH_EXPR_MAX_LENGTH = varRef(s_loc, "nop.core.json.jpath-expr-max-length", Integer.class,
            500);

    @Description("是否使用nop-vfs-index文件。native-image中不能使用类路径扫描，必须通过nop-vfs-index来发现资源文件")
    IConfigReference<Boolean> CFG_USE_NOP_VFS_INDEX = varRef(s_loc, "nop.core.resource.use-nop-vfs-index", Boolean.class,
            true);

    @Description("是否使用当前工程目录下的resources资源")
    IConfigReference<Boolean> CFG_INCLUDE_CURRENT_PROJECT_RESOURCES = varRef(s_loc,
            "nop.core.resource.include-current-project-resources", Boolean.class, true);

    @Description("是否使用外部的_vfs目录覆盖打包在程序内部的资源文件")
    IConfigReference<String> CFG_RESOURCE_DIR_OVERRIDE_VFS = varRef(s_loc, "nop.core.resource.dir-override-vfs", String.class,
            "./_vfs");

    @Description("是否检查_vfs目录下的资源文件路径的唯一性。缺省只有_module文件允许重名，会自动选择第一个匹配文件")
    IConfigReference<Boolean> CFG_CHECK_DUPLICATE_VFS_RESOURCE = varRef(s_loc,
            "nop.core.resource.check-duplicate-vfs-resource", Boolean.class, true);

    @Description("当错误码没有定义外部映射的情况下，是否将其翻译为SysError以避免泄露内部结构信息，还是直接把该错误码信息作为API结果返回")
    IConfigReference<Boolean> CFG_ERROR_MESSAGE_PUBLIC_FOR_NO_MAPPING = varRef(s_loc,
            "nop.core.error-message-public-for-no-mapping", Boolean.class, true);

    @Description("字典表的label总是按照[value]-[label]格式显示")
    IConfigReference<Boolean> CFG_DICT_RETURN_NORMALIZED_LABEL = varRef(s_loc, "nop.core.dict.return-normalized-label",
            Boolean.class, true);

    @Description("缺省掩码保留位数")
    IConfigReference<Integer> CFG_DEFAULT_MASKING_KEEP_CHARS = varRef(s_loc, "nop.core.default-masking-keep-chars", Integer.class, 2);

    @Description("禁用租户定制的资源路径前缀列表")
    IConfigReference<Set> CFG_TENANT_RESOURCE_DISABLED_PATHS = varRef(s_loc, "nop.core.tenant-resource.disabled-paths", Set.class, null);

    @Description("启用租户定制的资源路径前缀列表")
    IConfigReference<Set> CFG_TENANT_RESOURCE_ENABLED_PATHS = varRef(s_loc, "nop.core.tenant-resource.enabled-paths", Set.class, null);

    @Description("资源文件启用租户支持")
    IConfigReference<Boolean> CFG_TENANT_RESOURCE_ENABLED = varRef(s_loc, "nop.core.tenant-resource.enabled", Boolean.class, false);

    @Description("开发工程的根目录，子模块全部存放在此目录下")
    IConfigReference<String> CFG_DEV_ROOT_PATH = varRef(s_loc, "nop.core.dev-root-path", String.class, null);
}