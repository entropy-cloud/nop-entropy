/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.reflect.impl;

import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.lang.Deterministic;
import io.nop.api.core.annotations.lang.EvalMethod;
import io.nop.api.core.annotations.lang.Macro;
import io.nop.api.core.convert.ITypeConverter;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.ReflectionHelper;
import io.nop.core.lang.ast.ASTNode;
import io.nop.core.lang.eval.IEvalFunction;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.reflect.IFunctionArgument;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.type.IGenericType;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.nop.commons.util.CollectionHelper.buildImmutableList;
import static io.nop.core.CoreErrors.ARG_CLASS_NAME;
import static io.nop.core.CoreErrors.ARG_METHOD_NAME;
import static io.nop.core.CoreErrors.ERR_REFLECT_INVALID_MACRO_METHOD;

public class MethodModelBuilder {
    protected final ReflectionManager reflectionManager;
    private final Map<Class<?>, ClassModelBuilder.TypeMap> extendsTypeMap = new HashMap<>();

    public MethodModelBuilder(ReflectionManager reflectionManager) {
        this.reflectionManager = reflectionManager;
    }

    public static FunctionModel from(Class<?> clazz, Executable method) {
        return new MethodModelBuilder().buildMethodModel(clazz, method);
    }

    public MethodModelBuilder() {
        this(ReflectionManager.instance());
    }

    public FunctionModel buildMethodModel(Class<?> clazz, Executable method) {
        FunctionModel model = buildExecutable(method);
        IGenericType type = reflectionManager.buildRawType(clazz);
        return fixTypeParameter(type, model, false);
    }

    protected FunctionModel buildExecutable(Executable method) {
        FunctionModel mtd = new FunctionModel();
        mtd.setModifiers(method.getModifiers());
        addAnnotations(mtd, method);
        mtd.setVarArgs(method.isVarArgs());
        mtd.setName(getName(method));
        mtd.setDeclaringClass(method.getDeclaringClass());

        Class<?>[] exceptionTypes = method.getExceptionTypes();
        if (exceptionTypes.length > 0) {
            mtd.setExceptionTypes(buildImmutableList(exceptionTypes));
        }

        List<FunctionArgument> args = buildArgs(method);
        mtd.setArgs(args);

        if (method instanceof Constructor) {
            mtd.setInvoker(buildConstructorInvoker((Constructor<?>) method));
        } else {
            Method m = (Method) method;
            mtd.setReturnType(buildGenericType(m.getGenericReturnType()));
            if (m.isAnnotationPresent(Nonnull.class)) {
                mtd.setReturnNullable(false);
            } else {
                mtd.setReturnNullable(true);
            }
            mtd.setInvoker(buildMethodInvoker((Method) method));
            mtd.setDeterministic(method.isAnnotationPresent(Deterministic.class));
            if (isMacroFunction(m)) {
                mtd.setMacro(true);
            }
            mtd.setDeprecated(method.isAnnotationPresent(Deprecated.class));

            if (method.isAnnotationPresent(EvalMethod.class)) {
                buildEvalMethod(mtd);
            }
        }
        return mtd;
    }

    private List<FunctionArgument> buildArgs(Executable method) {
        Type[] paramTypes = method.getGenericParameterTypes();
        Parameter[] parameters = method.getParameters();
        List<FunctionArgument> args = new ArrayList<>(parameters.length);
        for (int i = 0, n = parameters.length; i < n; i++) {
            Parameter param = parameters[i];
            String paramName = getParamName(param);
            FunctionArgument arg = new FunctionArgument();
            addAnnotations(arg, param);
            arg.setName(paramName);
            IGenericType type = buildGenericType(paramTypes[i]);
            arg.setType(type);
            arg.setConverter(buildConverter(type));
            if (param.isAnnotationPresent(Nullable.class)) {
                arg.setNullable(true);
            } else if (param.isAnnotationPresent(Nonnull.class)) {
                arg.setNullable(false);
            }
            args.add(arg);
        }
        return args;
    }

    // 宏函数必须是静态函数，标记了@Macro注解，且具有两个参数，分别为IEvalScope和ASTNode类型，返回类型必须是ASTNode
    private boolean isMacroFunction(Method method) {
        if (!Modifier.isStatic(method.getModifiers()))
            return false;

        if (!Modifier.isPublic(method.getModifiers()))
            return false;

        if (!method.isAnnotationPresent(Macro.class))
            return false;

        boolean valid = true;
        if (method.getParameterCount() != 2) {
            valid = false;
        } else if (!ASTNode.class.isAssignableFrom(method.getReturnType())) {
            valid = false;
        } else if (!IEvalScope.class.isAssignableFrom(method.getParameters()[0].getType())) {
            valid = false;
        } else if (!ASTNode.class.isAssignableFrom(method.getParameters()[1].getType())) {
            valid = false;
        }
        if (!valid)
            throw new NopException(ERR_REFLECT_INVALID_MACRO_METHOD).param(ARG_METHOD_NAME, method.getName())
                    .param(ARG_CLASS_NAME, method.getDeclaringClass().getName());
        return true;
    }

    protected String getName(Executable member) {
        String name = member.getName();
        Name reflectionName = member.getAnnotation(Name.class);
        if (reflectionName != null)
            name = reflectionName.value();
        return name;
    }

    private ITypeConverter buildConverter(IGenericType type) {
        return reflectionManager.getConverterForJavaType(type.getRawClass());
    }

    static String getParamName(Parameter arg) {
        return ReflectionHelper.getParamName(arg);
    }

    static void addAnnotations(AnnotatedElement element, java.lang.reflect.AnnotatedElement ao) {
        Annotation[] anns = ao.getAnnotations();
        element.addAnnotations(anns);
    }

    protected IGenericType buildGenericType(Type type) {
        return this.reflectionManager.buildGenericType(type);
    }

    protected IEvalFunction buildConstructorInvoker(Constructor<?> constructor) {
        return new ConstructorInvoker(constructor);
    }

    protected IEvalFunction buildMethodInvoker(Method method) {
        return new MethodInvoker(method);
    }

    private void buildEvalMethod(FunctionModel mtd) {
        if (mtd.getArgCount() <= 0)
            return;

        if (!IEvalScope.class.isAssignableFrom(mtd.getArgs().get(0).getRawClass()))
            return;

        List<FunctionArgument> args = mtd.getArgs();
        mtd.setArgs(args.subList(1, args.size()));
        mtd.setInvoker(new EvalMethodInvoker(mtd.getInvoker()));
    }


    /**
     * 根据当前类的类型确定泛型函数中类型变量对应的具体类型
     */
    protected FunctionModel fixTypeParameter(IGenericType classType, FunctionModel model, Executable method) {
        Class<?> declClass = method.getDeclaringClass();
        if (declClass != classType.getRawClass()) {
            TypeVariable[] paramTypes = declClass.getTypeParameters();
            if (paramTypes.length > 0) {
                return fixTypeParameter(classType, model, false);
            }
        }

        return model;
    }

    protected FunctionModel fixTypeParameter(IGenericType classType, FunctionModel model, boolean needCopy) {
        Class<?> declClass = model.getDeclaringClass();
        if (declClass == classType.getRawClass() || declClass == null)
            return model;

        ClassModelBuilder.TypeMap typeMap = makeTypeMap(classType, declClass);
        if (!typeMap.parameterized)
            return model;

        FunctionModel copyModel = needCopy ? null : model;

        IGenericType returnType = model.getReturnType();
        if (returnType.containsTypeVariable()) {
            returnType = returnType.refine(typeMap.rawType, typeMap.contextType);
            if (copyModel == null)
                copyModel = cloneFunction(model);
            copyModel.setReturnType(returnType);
        }

        for (int i = 0, n = model.getArgCount(); i < n; i++) {
            IFunctionArgument arg = model.getArgs().get(i);
            IGenericType argType = arg.getType();
            if (argType.containsTypeVariable()) {
                if (copyModel == null) {
                    copyModel = cloneFunction(model);
                }

                FunctionArgument copyArg = (FunctionArgument) arg.cloneInstance();
                argType = argType.refine(typeMap.rawType, typeMap.contextType);
                copyArg.setType(argType);
                copyModel.getArgs().set(i, copyArg);
            }
        }

        return copyModel == null ? model : copyModel;
    }

    FunctionModel cloneFunction(FunctionModel model) {
        FunctionModel ret = model.cloneInstance();
        if (ret.getArgCount() > 0) {
            ret.setArgs(new ArrayList<>(ret.getArgs()));
        }
        return ret;
    }

    ClassModelBuilder.TypeMap makeTypeMap(IGenericType classType, Class<?> declClass) {
        ClassModelBuilder.TypeMap map = extendsTypeMap.get(declClass);
        if (map == null) {
            map = new ClassModelBuilder.TypeMap();
            map.parameterized = declClass.getTypeParameters().length > 0;
            map.rawType = reflectionManager.buildRawType(declClass);
            map.contextType = classType.getGenericType(declClass);
            extendsTypeMap.put(declClass, map);
        }
        return map;
    }
}
