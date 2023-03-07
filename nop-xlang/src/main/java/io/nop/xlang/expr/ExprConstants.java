/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.expr;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public interface ExprConstants {
    String DEFAULT_NS = "default";

    String KEY_FUNCTION = "function";
    String KEY_VAR = "var";
    String KEY_CONSt = "const";
    String KEY_LET = "let";
    String KEY_NULL = "null";
    String KEY_TRUE = "true";
    String KEY_FALSE = "false";
    String KEY_RETURN = "return";
    String KEY_BREAK = "break";
    String KEY_CONTINUE = "continue";
    String KEY_DO = "do";
    String KEY_IMPORT = "import";
    String KEY_OF = "of";
    String KEY_WHILE = "while";

    String KEY_FOR = "for";
    String KEY_IF = "if";
    String KEY_USING = "using";

    String KEY_ELSE = "else";

    String KEY_NEW = "new";
    String KEY_DELETE = "delete";
    String KEY_AWAIT = "await";
    String KEY_TRY = "try";
    String KEY_CATCH = "catch";
    String KEY_FINALLY = "finally";
    String KEY_THROW = "throw";

    // String KEY_AND = "and";
    // String KEY_OR = "or";

    Set<String> KEYWORDS = new HashSet<>(Arrays.asList(KEY_FUNCTION, KEY_WHILE, KEY_VAR, KEY_NULL, KEY_TRUE, KEY_FALSE,
            KEY_RETURN, KEY_BREAK, KEY_CONTINUE, KEY_DO, KEY_IMPORT, KEY_ELSE, KEY_FOR, KEY_IF, KEY_USING, //
            KEY_LET, KEY_NEW, KEY_DELETE, KEY_AWAIT, KEY_TRY, KEY_CATCH, KEY_FINALLY, KEY_THROW));

    /**
     * 以$为前缀的变量为系统内置变量，不允许运行时对这些变量赋值
     */
    char PREFIX_SYS_VAR = '$';

    /**
     * 通过XClass的registerHelper机制注册的扩展函数的名称前缀
     */
    char PREFIX_EXT_FUNC = '$';

    String GLOBAL_FUNC_PREFIX = "g_";

    /**
     * 任意对象调用$都会导致打印调试语句。例如 b = a.f().$("test")实际等价于 b = DebugHelper.v(location(), "test",a.f());
     */
    String SYS_FUNC_DEBUG = "$";

    /**
     * 全局上下文环境，对应ContextRegistry.currentContext()返回的IContext类型的对象
     */
    String SYS_VAR_CONTEXT = "$context";

    /**
     * EL表达式执行时的上下文环境变量集合, 对应IEvalScope所管理的variables
     */
    String SYS_VAR_SCOPE = "$scope";

    /**
     * 对应捕获的异常对象
     */
    String SYS_VAR_EXCEPTION = "exception";

    /**
     * 编译期指向当前源码节点
     */
    String SCOPE_VAR_XPL_NODE = "_xpl_node";

    /**
     * 编译期指向当前模型的根节点
     */
    String SCOPE_VAR_DSL_ROOT = "_dsl_root";

    /**
     * 在x:post-parse段中对应模型解析得到的java对象
     */
    String SYS_VAR_DSL_MODEL = "_dsl_model";

    String SYS_VAR_DSL_PARSER = "_dsl_parser";

    /**
     * xlib标签编译时指向IXplTagDesc
     */
    String SYS_VAR_TAG = "_tag";

    /**
     * AOP的advice上下文中通过this指针指向目标对象
     */
    String SYS_VAR_THIS = "this";

    /**
     * AOP环境中指向Invocation对象
     */
    String SYS_VAR_INVOCATION = "_invocation";

    /**
     * 对应Config全局配置对象
     */
    String SYS_OBJ_CONFIG = "$config";

    /**
     * 对应于EvalGlobal类，主要是提供调试支持
     */
    String SYS_OBJ_GLOBAL = "$";
}