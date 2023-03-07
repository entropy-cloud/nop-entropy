/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.biz.dev;

import io.nop.api.core.annotations.biz.BizLoader;
import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.biz.ContextSource;
import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.annotations.core.Internal;
import io.nop.api.core.annotations.core.Locale;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.beans.WebContentBean;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.config.DefaultConfigReference;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.biz.dev.beans.ConfigVarBean;
import io.nop.biz.dev.beans.FunctionArgBean;
import io.nop.biz.dev.beans.FunctionDefBean;
import io.nop.biz.dev.beans.GlobalVariableDefBean;
import io.nop.commons.util.StringHelper;
import io.nop.core.i18n.I18nMessageManager;
import io.nop.core.lang.eval.global.EvalGlobalRegistry;
import io.nop.core.lang.eval.global.IGlobalVariableDefinition;
import io.nop.core.reflect.IClassModel;
import io.nop.core.reflect.IFunctionArgument;
import io.nop.core.reflect.IFunctionModel;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.core.resource.deps.ResourceDependencySet;
import io.nop.core.type.IGenericType;
import io.nop.ioc.api.IBeanContainerImplementor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 开发期通过内省机制返回内部变量和函数定义等信息
 */
@Locale("zh-CN")
@BizModel("DevDoc")
public class DevDocBizModel {
    @BizQuery
    @Description("全局函数列表")
    public List<FunctionDefBean> globalFunctions() {
        Map<String, IFunctionModel> funcs = new TreeMap<>(EvalGlobalRegistry.instance().getRegisteredFunctions());

        String locale = ContextProvider.currentLocale();

        List<FunctionDefBean> ret = new ArrayList<>(funcs.size());
        funcs.forEach((name, fn) -> {
            if (fn.isAnnotationPresent(Internal.class))
                return;

            ret.add(toFunctionBean(locale, fn));
        });
        return ret;
    }

    FunctionDefBean toFunctionBean(String locale, IFunctionModel fn) {
        FunctionDefBean def = new FunctionDefBean();
        def.setName(fn.getName());
        def.setDescription(getDescription(locale, fn));
        def.setMacro(fn.isMacro());
        def.setArgs(toArgsBean(locale, fn));
        def.setReturnType(fn.getAsyncReturnType().toString());
        if (fn.getDeclaringClass() != null)
            def.setDeclaringClass(fn.getDeclaringClass().getCanonicalName());
        return def;
    }

    @BizQuery
    @Description("全局变量")
    public List<GlobalVariableDefBean> globalVars() {
        Map<String, IGlobalVariableDefinition> vars = new TreeMap<>(EvalGlobalRegistry.instance().getRegisteredVars());

        String locale = ContextProvider.currentLocale();

        List<GlobalVariableDefBean> ret = new ArrayList<>(vars.size());
        vars.forEach((name, var) -> {
            GlobalVariableDefBean def = new GlobalVariableDefBean();
            def.setName(name);
            def.setDescription(getDescription(locale, var));
            def.setType(var.getResolvedType().toString());

            if (var.getResolvedType().getRawClass() == Class.class) {
                def.setStaticClass(true);
            }
            ret.add(def);
        });
        return ret;
    }

    @Description("全局变量上的方法")
    @BizLoader(forType = GlobalVariableDefBean.class)
    public List<FunctionDefBean> methods(@ContextSource GlobalVariableDefBean varDef) {
        IGlobalVariableDefinition var = EvalGlobalRegistry.instance().getRegisteredVariable(varDef.getName());
        if (var == null)
            throw new IllegalArgumentException("nop.err.graphql.invalid-global-var::" + varDef.getName());

        String locale = ContextProvider.currentLocale();
        IGenericType type = var.getResolvedType();
        List<? extends IFunctionModel> methods;
        if (type.getRawClass() == Class.class) {
            IClassModel classModel = ReflectionManager.instance().getClassModelForType(type.getTypeParameters().get(0));
            methods = classModel.getStaticMethods();
        } else {
            IClassModel classModel = ReflectionManager.instance().getClassModel(type.getRawClass());
            methods = classModel.getMethods();
        }

        List<FunctionDefBean> ret = new ArrayList<>(methods.size());

        for (IFunctionModel fn : methods) {
            ret.add(toFunctionBean(locale, fn));
        }
        return ret;
    }

    String getDescription(String locale, IGlobalVariableDefinition var) {
        String desc = var.getDescription();
        if (desc != null)
            return I18nMessageManager.instance().resolveI18nVar(locale, desc);

        Description descAnn = var.getClass().getAnnotation(Description.class);
        if (descAnn != null) {
            return I18nMessageManager.instance().resolveI18nVar(locale, descAnn.value());
        }
        return null;
    }

    String getDescription(String locale, IFunctionModel fn) {
        String key = getFunctionDocKey(fn);
        String text = I18nMessageManager.instance().getMessage(locale, key, null);
        if (text != null)
            return text;

        Description desc = fn.getAnnotation(Description.class);
        if (desc != null) {
            return I18nMessageManager.instance().resolveI18nVar(locale, desc.value());
        }
        return null;
    }

    String getFunctionDocKey(IFunctionModel fn) {
        if (fn.getDeclaringClass() == null)
            return "doc.fn." + fn.getName();

        return "doc." + fn.getDeclaringClass().getName() + "." + fn.getName();
    }

    List<FunctionArgBean> toArgsBean(String locale, IFunctionModel fn) {
        List<FunctionArgBean> args = new ArrayList<>(fn.getArgCount());
        for (IFunctionArgument argModel : fn.getArgs()) {
            args.add(toArgBean(locale, fn, argModel));
        }
        return args;
    }

    FunctionArgBean toArgBean(String locale, IFunctionModel fn, IFunctionArgument arg) {
        FunctionArgBean ret = new FunctionArgBean();
        ret.setName(arg.getName());
        ret.setDescription(getDescription(locale, fn, arg));
        ret.setType(arg.getType().toString());
        return ret;
    }

    String getDescription(String locale, IFunctionModel fn, IFunctionArgument arg) {
        String key = getFunctionDocKey(fn) + "." + arg.getName();
        String text = I18nMessageManager.instance().getMessage(locale, key, null);
        if (text != null)
            return text;

        Description desc = fn.getAnnotation(Description.class);
        if (desc != null) {
            return I18nMessageManager.instance().resolveI18nVar(locale, desc.value());
        }

        return null;
    }

    @Description("Ioc容器中的bean定义")
    @BizQuery
    public WebContentBean beans() {
        IBeanContainerImplementor container = (IBeanContainerImplementor) BeanContainer.instance();
        return WebContentBean.xml(container.toConfigNode().xml());
    }

    @Description("所有配置变量的当前值")
    @BizQuery
    public List<ConfigVarBean> configVars() {
        Map<String, DefaultConfigReference<?>> vars = new TreeMap<>(
                AppConfig.getConfigProvider().getConfigReferences());
        List<ConfigVarBean> ret = new ArrayList<>(vars.size());
        vars.values().forEach(var -> {
            ConfigVarBean bean = new ConfigVarBean();
            bean.setName(var.getName());
            bean.setLocation(StringHelper.toString(var.getLocation(), null));
            bean.setType(var.getValueType().getCanonicalName());
            bean.setDefaultValue(StringHelper.toString(var.getDefaultValue(), null));
            bean.setValue(StringHelper.toString(var.get(), null));
            ret.add(bean);
        });

        return ret;
    }

    @Description("模型文件的依赖文件")
    @BizQuery
    public String dependsSet(@Name("path") String path) {
        ResourceDependencySet deps = ResourceComponentManager.instance().getModelDepends(path);
        if (deps == null)
            return null;
        return ResourceComponentManager.instance().dumpDependsSet(deps);
    }
}