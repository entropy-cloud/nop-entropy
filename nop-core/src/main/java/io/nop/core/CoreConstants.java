package io.nop.core;

import io.nop.core.resource.ResourceConstants;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public interface CoreConstants extends ResourceConstants {
    String FILTER_OP_SQL = "sql";

    String RESOURCE_CONTENT_CACHE_NAME = "resource-content-cache";

    String CORE_REGISTRY_PATH = "/nop/core/registry";

    String FILE_TYPE_REGISTER_MODEL = "register-model.xml";

    String MAIN_REGISTRY_PATH = "/nop/main/registry/app.registry.xml";

    String ANNOTATION_REGISTRY_PATH = "/nop/aop";
    String FILE_POSTFIX_ANNOTATIONS = ".annotations";

    String I18N_PATH_PREFIX = "/i18n/";
    String MODEL_TYPE_I18N = "i18n";
    String FILE_TYPE_I18N_YAML = "i18n.yaml";
    String FILE_TYPE_I18N_JSON = "i18n.json";

    String GEN_VAR_PREFIX = "_gen_";

    String FIELD_LENGTH = "length";

    String METHOD_CONSTRUCTOR = "this";

    String GLOBAL_VAR_EVAL_HELPER = "$";

    String GLOBAL_VAR_CONFIG = "$config";

    String GLOBAL_VAR_CONTEXT = "$context";

    String GLOBAL_VAR_SCOPE = "$scope";
    String GLOBAL_VAR_OUT = "$out";

    String GLOBAL_VAR_JSON = "$JSON";

    String GLOBAL_VAR_MATH = "$Math";

    String GLOBAL_VAR_DATE = "$Date";

    String GLOBAL_VAR_STRING = "$String";

    String GLOBAL_VAR_UNDERSCORE = "_";

    String GLOBAL_VAR_GEN_JS = "$genJs";

    String GLOBAL_VAR_GEN_JAVA = "$genJava";

    String PROP_TYPE = "type";
    String PROP_BODY = "body";
    String TAG_NAME_J_LIST = "j:list";
    String TAG_NAME_J_SIMPLE = "j:simple";

    String PREFIX_INTERNAL_DEPENDS = "~";

    String XML_PROP_TYPE = "$type";
    String XML_PROP_BODY = "$body";

    String ATTR_EXPR_PREFIX = "@:";

    String ATTR_JSON_PREFIX = "@j:";

    String ESCAPED_ATTR_EXPR_PREFIX = "@@:";
    String ESCAPED_ATTR_JSON_PREFIX = "@@j:";

    String BINDER_VAR_PREFIX = "@var:";
    String BINDER_STR_PREFIX = "@s:";

    /**
     * 系统缺省的错误码
     */
    String ERROR_SYS_ERR = "SYS_ERR";

    String BASE_64_PREFIX = "@base64:";

    String I18N_VAR_PREFIX = "@i18n:";
    String I18N_VAR_START = "{@i18n:";
    String I18N_VAR_END = "}";
    String I18N_NS_PREFIX = "i18n:";

    String I18N_COMMON_KEY = "common.";

    Set<String> HTML_INLINE_TAG_NAMES = new HashSet<>(Arrays.asList("img", "span", "button"));
    Set<String> HTML_SHORT_TAG_NAMES = new HashSet<>(
            Arrays.asList("hr", "br", "input", "img", "meta", "col", "link", "base", "embed"));

    String XNODE_PARENT_PROP = "$parent";
    String XNODE_LOC_PROP = "$loc";

    String TAG_NAME_C_SCRIPT = "c:script";

    String DUMMY_TAG_NAME = "_";
    String TEXT_TAG_NAME = "#";

    String FILTER_TAG_NAME = "filter";
    String ATTR_ID = "id";
    String ATTR_NAME = "name";
    String ATTR_TAGS = "tags";
    String ATTR_CLASS = "class";
    String ATTR_STYLE = "style";
    String ATTR_V_ID = "v:id";
    // String ATTR_J_TYPE = "j:type";
    String ATTR_J_LIST = "j:list";
    String ATTR_J_KEY = "j:key";
    String NS_XMLNS_PREFIX = "xmlns:";

    String ATTR_VALUE = "value";

    // json属性 $x被替换为xml属性_:x
    String NS_JSON_SYS_PREFIX = "_:";

    String NS_EXT_PREFIX = "ext:";
    String NS_INFO_PREFIX = "info:";

    String ATTR_XML_MULTIPLE = "xml:multiple";

    String ATTR_XMLNS = "xmlns";

    char SLOT_NAME_PREFIX = '#';
    char SYS_NAME_PREFIX = '$';

    String LOOP_ROOT_VAR = "$LOOP_ROOT$";

    String XRUN_FILE_SUFFIX = ".xrun";
    String XGEN_FILE_SUFFIX = ".xgen";
    String XGEN_FILE_PREFIX = "_";
    String XGEN_FILE_DIR = "/_gen/";

    String VAR_TPL_RESOURCE = "tplResource";
    String VAR_TARGET_RESOURCE = "targetResource";
    String XGEN_MARK_IGNORE = "|XGEN_IGNORE|";
    String XGEN_MARK_FORCE_OVERRIDE = "__XGEN_FORCE_OVERRIDE__";
    String XGEN_MARK_TPL_FORCE_OVERRIDE = "__XGEN_TPL_FORCE_OVERRIDE__";

    String POSTFIX_NOT_DELETE = ".not-delete";

    String RESOURCE_TIMESTAMP_CACHE_NAME = "resource-timestamp-cache";

    int MAX_FUNCTION_ARG_COUNT = 100;

    String NAMESPACE_X = "x";

    String NAMESPACE_X_PREFIX = "x:";
    String NAMESPACE_XDSL_PREFIX = "xdsl:";

    String ATTR_X_ABSTRACT = "x:abstract";
    String ATTR_X_EXTENDS = "x:extends";
    String ATTR_X_ID = "x:id";
    String ATTR_X_UNIQUE_ATTR = "x:unique-attr";
    String ATTR_X_GEN_EXTENDS = "x:gen-extends";
    String ATTR_X_OVERRIDE = "x:override";
    String ATTR_X_VALIDATED = "x:validated";
    String ATTR_X_DUMP = "x:dump";
    String ATTR_X_VIRTUAL = "x:virtual";
    String ATTR_X_INHERIT = "x:inherit";

    String ATTR_FEATURE_ON = "feature:on";
    String ATTR_FEATURE_OFF = "feature:off";

    String OVERRIDE_REPLACE = "replace";
    String OVERRIDE_REMOVE = "remove";

    String MD_ATTR_ERROR_CODE = "errorCode";
    String MD_ATTR_EXCEPTION = "exception";
    String MD_ATTR_RETURN = "return";
    String MD_ATTR_OUTPUT_MODE = "outputMode";
    String MD_ATTR_OUTPUT = "output";

    String VALUE_TYPE_JSON = "json";

    String PROP_LOCATION = "location";

    String VAR_REQUEST = "request";
    String VAR_RESPONSE = "response";
    String VAR_SVC_CTX = "svcCtx";

    // 在此优先级之前（包含此优先级）执行的initializer为precompile之前
    int INITIALIZER_PRIORITY_PRECOMPILE = 3000;

    // 在此优先级之前（包含此优先级）执行的initializer为代码生成机制所需的
    int INITIALIZER_PRIORITY_ANALYZE = 5000;

    int INITIALIZER_PRIORITY_INTERNAL = INITIALIZER_PRIORITY_PRECOMPILE - 200;
    // 注册替代反射机制所需的对象
    int INITIALIZER_PRIORITY_REGISTER_INVOKER = INITIALIZER_PRIORITY_INTERNAL + 10;
    // 注册反射系统所需的扩展对象
    int INITIALIZER_PRIORITY_REGISTER_REFLECTION = INITIALIZER_PRIORITY_INTERNAL + 20;
    // 注册xpl/meta/xdef等XLang基础支持
    int INITIALIZER_PRIORITY_REGISTER_XLANG = INITIALIZER_PRIORITY_INTERNAL + 30;
    // 读取配置文件，从远程配置中心拉取配置
    int INITIALIZER_PRIORITY_START_CONFIG = INITIALIZER_PRIORITY_INTERNAL + 40;

    // 如果START_CONFIG没有初始化VFS，则这里延迟初始化VFS。
    int INITIALIZER_PRIORITY_REGISTER_VFS = INITIALIZER_PRIORITY_INTERNAL + 50;

    int INITIALIZER_PRIORITY_REGISTER_COMPONENT = INITIALIZER_PRIORITY_INTERNAL + 100;

    // 初始化Ioc容器
    int INITIALIZER_PRIORITY_IOC = INITIALIZER_PRIORITY_ANALYZE - 100;

    int INITIALIZER_PRIORITY_POST_PROCESS = INITIALIZER_PRIORITY_ANALYZE + 100;

    /**
     * 是否启用配置中心服务
     */
    String CFG_CONFIG_SERVICE_ENABLED = "nop.config.config-service.enabled";
}