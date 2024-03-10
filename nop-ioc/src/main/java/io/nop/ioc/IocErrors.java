/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ioc;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface IocErrors {
    String ARG_SCOPE = "scope";

    String ARG_BEAN_NAME = "beanName";
    String ARG_BEAN_TYPE = "beanType";
    String ARG_BEAN_CLASS = "beanClass";

    String ARG_BEFORE = "before";
    String ARG_AFTER = "after";

    String ARG_METHOD_NAME = "methodName";

    String ARG_BEAN_REF = "beanRef";

    String ARG_FACTORY_BEAN = "factoryBean";

    String ARG_BEAN_SCOPE = "beanScope";

    String ARG_ARG_COUNT = "argCount";

    String ARG_EXPR = "expr";

    String ARG_VALUE = "value";

    String ARG_DEPEND = "depend";

    String ARG_PARAM_COUNT = "paramCount";

    String ARG_BEANS = "beans";

    String ARG_PROP_NAME = "propName";

    String ARG_BEAN = "bean";
    String ARG_OTHER_BEAN = "otherBean";

    String ARG_INTERCEPTOR_BEAN = "interceptorBean";
    String ARG_INTERCEPTOR_NAME = "interceptorName";

    String ARG_ALIAS = "alias";
    String ARG_LOC_A = "locA";
    String ARG_LOC_B = "locB";

    String ARG_TRACE = "trace";

    String ARG_PARENT = "parent";

    String ARG_LOOP_REF = "loopRef";

    String ARG_CONTAINER_ID = "containerId";

    String ARG_CLASS_NAME = "className";

    String ARG_FIELD_NAME = "fieldName";

    String ARG_INDEX = "index";

    String ARG_STATIC_FIELD = "staticField";

    String ARG_CONFIG_VARS = "configVars";

    ErrorCode ERR_IOC_SCOPE_ALREADY_EXISTS = define("nop.err.ioc.scope-already-exists", "scope[{scope}]已经存在",
            ARG_SCOPE);

    ErrorCode ERR_IOC_SCOPE_NOT_OPENED = define("nop.err.ioc.scope-not-opened", "scope[{scope}]没有打开，不能访问其中的bean",
            ARG_SCOPE);

    ErrorCode ERR_IOC_CONTAINER_NOT_INITIALIZED = define("nop.err.ioc.container-not-initialized", "IoC容器尚未初始化");

    ErrorCode ERR_IOC_ALIAS_CONFLICT = define("nop.err.ioc.alias-conflict",
            "bean的别名[{name}]已经被使用，无法重复定义:locA={locA},locB={locB}", ARG_BEAN_NAME, ARG_LOC_A, ARG_LOC_B);

    ErrorCode ERR_IOC_DUPLICATE_BEAN_DEFINITION = define("nop.err.ioc.duplicate-bean-definition",
            "bean的名称[{beanName}]与已有的bean的名称或者别名重名，不允许重复定义:locA={locA},locB={locB}", ARG_BEAN_NAME, ARG_LOC_A,
            ARG_LOC_B);

    ErrorCode ERR_IOC_UNRESOLVED_ALIAS = define("nop.err.ioc.unresolved-alias", "alias[{alias}]对应的bean[{beanName}]未定义",
            ARG_ALIAS, ARG_BEAN_NAME);

    ErrorCode ERR_IOC_UNKNOWN_PARENT_REF = define("nop.err.ioc.unknown-parent-ref",
            "bean[{beanName}]的parent属性值[{parent}]没有指向已定义的bean", ARG_BEAN_NAME, ARG_PARENT);

    ErrorCode ERR_IOC_UNKNOWN_BEAN_REF = define("nop.err.ioc.unknown-bean-ref", "引用名[{beanRef}]没有指向已定义的bean",
            ARG_BEAN_NAME, ARG_BEAN_REF);

    ErrorCode ERR_IOC_UNKNOWN_CONCRETE_BEAN_REF = define("nop.err.ioc.unknown-concrete-bean-ref",
            "引用名[{beanRef}]没有指向已定义的具体的bean", ARG_BEAN_NAME, ARG_BEAN_REF);

    ErrorCode ERR_IOC_UNKNOWN_DEPEND_REF = define("nop.err.ioc.unknown-depend-ref",
            "[{beanName}]的依赖[{depend}]没有指向已定义的bean", ARG_BEAN_NAME, ARG_DEPEND);

    ErrorCode ERR_IOC_PARENT_REF_CONTAINS_LOOP = define("nop.err.ioc.parent-ref-contains-loop",
            "bean[{beanName}]的parent属性[{parent}]引用包含循环引用", ARG_BEAN_NAME, ARG_PARENT);

    ErrorCode ERR_IOC_CONTAINER_ALREADY_STARTED = define("nop.err.ioc.container-already-started",
            "依赖注入容器[{containerId}]已经启动，不允许再次调用start方法", ARG_CONTAINER_ID);

    ErrorCode ERR_IOC_EMPTY_CLASS_NAME = define("nop.err.ioc.empty-class-name", "bean[{beanName}]的class属性必须不为空",
            ARG_BEAN_NAME);

    ErrorCode ERR_IOC_CLASS_NOT_FOUND = define("nop.err.ioc.class-not-found", "类[{className}]未找到", ARG_CLASS_NAME);

    ErrorCode ERR_IOC_INVALID_CONSTRUCTOR_ARG_INDEX = define("nop.err.ioc.invalid-constructor-arg-index",
            "构造函数的参数顺序不正确");

    ErrorCode ERR_IOC_NOT_ALLOW_BOTH_FACTORY_METHOD_AND_BEAN_METHOD = define(
            "nop.err.ioc.not-allow-both-factory-method-and-bean-method",
            "bean[{beanName}]的定义不允许同时指定bean-method和factory-method属性", ARG_BEAN_NAME);

    ErrorCode ERR_IOC_NOT_ALLOW_BOTH_DELAY_METHOD_AND_BEAN_METHOD = define(
            "nop.err.ioc.not-allow-both-delay-method-and-bean-method",
            "bean[{beanName}]的定义不允许同时指定bean-method和delay-method属性", ARG_BEAN_NAME);

    ErrorCode ERR_IOC_NOT_FIND_BEAN_WITH_TYPE = define("nop.err.ioc.not-find-bean-with-type",
            "bean[{beanName}]的属性[{propName}]需要类型为[{beanType}]的bean，未找到已注册的bean", ARG_BEAN_NAME, ARG_PROP_NAME,
            ARG_BEAN_TYPE);

    ErrorCode ERR_IOC_MULTIPLE_PRIMARY_BEAN = define("nop.err.ioc.multiple-primary-bean",
            "bean[{beanName}]的属性[{propName}]需要类型为[{beanType}]的bean，多个bean都标注了primary，出现冲突", ARG_BEAN_NAME,
            ARG_PROP_NAME, ARG_BEAN_TYPE);

    ErrorCode ERR_IOC_MULTIPLE_BEAN_WITH_TYPE = define("nop.err.ioc.multiple-bean-with-type",
            "bean[{beanName}]的属性[{propName}]需要类型为[{beanType}]的bean，存在多个bean满足要求", ARG_BEAN_TYPE);

    ErrorCode ERR_IOC_UNKNOWN_BEAN_PROP = define("nop.err.ioc.unknown-bean-prop",
            "id为[{beanName}]的bean没有定义属性[{propName}]", ARG_BEAN_NAME, ARG_PROP_NAME);

    ErrorCode ERR_IOC_UNKNOWN_BEAN_METHOD = define("nop.err.ioc.unknown-bean-method",
            "类[{className}]上没有定义方法[{methodName}]", ARG_BEAN_NAME, ARG_CLASS_NAME, ARG_METHOD_NAME);

    ErrorCode ERR_IOC_NOT_PRODUCER_BEAN = define("nop.err.ioc.not-producer-bean", "不是工厂对象，不支持&语法", ARG_BEAN_NAME);

    ErrorCode ERR_IOC_MISSING_CONSTRUCTOR = define("nop.err.ioc.missing-constructor",
            "id为[{beanName}]的bean缺少参数个数为{paramCount}的构造函数", ARG_BEAN_NAME, ARG_PARAM_COUNT);

    ErrorCode ERR_IOC_INVALID_STATIC_FIELD = define("nop.err.ioc.invalid-static-field", "静态字段[{staticField}]的格式不正确",
            ARG_STATIC_FIELD);

    ErrorCode ERR_IOC_CLASS_NO_FIELD = define("nop.err.ioc.class-no-field",
            "静态字段[{staticField}]配置不正确，类[className]上不存在字段[{fieldName}]", ARG_CLASS_NAME, ARG_STATIC_FIELD,
            ARG_FIELD_NAME);

    ErrorCode ERR_IOC_INVALID_BEAN_CONFIG_VALUE = define("nop.err.ioc..invalid-bean-config-value",
            "bean[{beanName}]的配置值[{value}]格式不正确", ARG_BEAN_NAME, ARG_VALUE);

    ErrorCode ERR_IOC_BEAN_DEPEND_ON_HIGH_ORDER_BEAN = define("nop.err.ioc.bean-depend-on-high-order-bean",
            "bean[{beanName}]依赖的bean[{depend}]的优先级比自身的优先级高", ARG_BEAN_NAME, ARG_DEPEND);

    ErrorCode ERR_IOC_EMPTY_CONFIG_VAR = define("nop.err.ioc.empty-config-var", "配置[{configVars}]的值为空",
            ARG_CONFIG_VARS);

    ErrorCode ERR_IOC_INVALID_BIND_EXPR = define("nop.err.ioc.invalid-bind-expr", "未定义的表达式[{expr}]类型", ARG_BEAN_NAME,
            ARG_EXPR);

    ErrorCode ERR_IOC_BEAN_SCOPE_ALREADY_CLOSED = define("nop.err.ioc.bean-scope-already-closed",
            "scope[{beanScope}]已经被关闭", ARG_BEAN_SCOPE);

    ErrorCode ERR_IOC_REF_FACTORY_BEAN_NO_BEAN_TYPE = define("nop.err.ioc.ref-factory-bean-no-bean-type",
            "引用的factoryBean[{beanRef}]没有确定的beanType", ARG_BEAN_NAME, ARG_BEAN_REF, ARG_FACTORY_BEAN);

    ErrorCode ERR_IOC_FACTORY_BEAN_MUST_BE_USED_WITH_FACTORY_METHOD = define(
            "nop.err.ioc.factory-bean-must-be-used-with-factory-method", "factory-bean属性必须和factory-method属性一起使用",
            ARG_BEAN_NAME);

    ErrorCode ERR_IOC_PRODUCER_BEAN_NOT_INITED = define("nop.err.ioc.producer-bean-not-inited",
            "bean[{beanName}]尚未完成初始化，无法访问", ARG_BEAN_NAME);

    ErrorCode ERR_IOC_PROXY_BEAN_TYPE_NOT_INTERFACE = define("nop.err.ioc.proxy-bean-type-not-interface",
            "bean[{beanName}]标记了ioc:proxy=true，则必须设置ioc:type为接口类型");

    ErrorCode ERR_IOC_PROXY_BEAN_CLASS_NOT_INVOCATION_HANDLER = define(
            "nop.err.ioc.proxy-bean-class-not-invocation-handler",
            "标记了ioc:proxy=true的bean必须实现InvocationHandler接口，而bean[{beanName}]的类型是[{beanClass}],不满足要求", ARG_BEAN_NAME,
            ARG_BEAN_CLASS);

    ErrorCode ERR_IOC_NOT_ALLOW_BEAN_ID_TYPE = define("nop.err.ioc.not-allow-bean-id-type",
            "bean[{beanName}]为嵌入声明，不允许设置ioc:type=@bean:id", ARG_BEAN_NAME);

    ErrorCode ERR_IOC_AOP_BEAN_NO_BEAN_CLASS = define("nop.err.ioc.aop-bean-no-bean-class",
            "bean[{beanName}]设置了ioc:aop=true或者ioc:interceptor，则它必须具有class属性", ARG_BEAN_NAME);

    ErrorCode ERR_IOC_LOAD_CLASS_FAIL_FOR_BEAN = define("nop.err.ioc.load-class-fail-for-bean",
            "bean[{beanName}]初始化需要使用的类[{className}]装载失败");

    ErrorCode ERR_IOC_BEAN_AOP_PROXY_NOT_GENERATED = define("nop.err.ioc.bean-aop-proxy-not-generated",
            "bean[{beanName}]指定了interceptor，但没有生成对应的Aop代理类");

    ErrorCode ERR_IOC_POINTCUT_CLASS_NOT_ANNOTATION = define("nop.err.ioc.class-not-annotation",
            "bean[{beanName}]的pointcut配置中用到的类[{className}]不是注解类型");

    ErrorCode ERR_IOC_INTERCEPTOR_BEAN_NO_POINTCUT = define("nop.err.ioc.interceptor-bean-no-pointcut",
            "bean[{interceptorName}]没有配置pointcut，不能作为interceptor使用");

    ErrorCode ERR_IOC_LOAD_AOP_CLASS_FAIL = define("nop.err.ioc.load-aop-class-fail", "加载代理类[{className}]失败",
            ARG_CLASS_NAME);

    ErrorCode ERR_IOC_AOP_CLASS_NO_CONSTRUCTOR = define("nop.err.ioc.aop-class-no-constructor",
            "bean[{beanName}]对应的AOP代理类没有参数个数为{argCount}的构造函数", ARG_BEAN_NAME, ARG_ARG_COUNT);

    ErrorCode ERR_IOC_CONTAINER_NOT_STARTED = define("bean容器[{containerId}]没有启动，无法访问其中定义的bean", ARG_CONTAINER_ID);

    ErrorCode ERR_IOC_UNKNOWN_IOC_BEFORE = define("bean[{beanName}]的ioc:before指定的依赖[{before}]没有被定义", ARG_BEAN_NAME,
            ARG_BEFORE, ARG_BEAN);

    ErrorCode ERR_IOC_UNKNOWN_IOC_AFTER = define("bean[{beanName}]的ioc:after指定的依赖[{after}]没有被定义", ARG_BEAN_NAME,
            ARG_AFTER, ARG_BEAN);
}