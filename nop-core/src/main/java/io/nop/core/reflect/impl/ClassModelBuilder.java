/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.reflect.impl;

import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.core.NoReflection;
import io.nop.api.core.annotations.core.StaticFactoryMethod;
import io.nop.api.core.exceptions.NopEvalException;
import io.nop.commons.util.CollectionHelper;
import io.nop.core.lang.eval.IEvalFunction;
import io.nop.core.reflect.IClassModel;
import io.nop.core.reflect.IFieldModel;
import io.nop.core.reflect.IFunctionArgument;
import io.nop.core.reflect.IFunctionModel;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.reflect.accessor.FieldPropertyAccessor;
import io.nop.core.reflect.aop.IAopProxy;
import io.nop.core.type.IGenericType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.concurrent.Immutable;
import java.lang.annotation.Annotation;
import java.lang.annotation.Inherited;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static io.nop.commons.util.CollectionHelper.immutableList;
import static io.nop.commons.util.CollectionHelper.immutableMap;
import static io.nop.core.CoreConstants.METHOD_CONSTRUCTOR;
import static io.nop.core.CoreErrors.ARG_CLASS_NAME;
import static io.nop.core.CoreErrors.ARG_METHOD_NAME;
import static io.nop.core.CoreErrors.ERR_REFLECT_MULTIPLE_FACTORY_METHOD;

public class ClassModelBuilder extends MethodModelBuilder {
    static final Logger LOG = LoggerFactory.getLogger(ClassModelBuilder.class);

    public static final Set<String> SYS_CLASS_PREFIXES = new HashSet<>(Arrays.asList("java.", "javax.", "jakarta."));

    private final Class<?> clazz;

    private final IGenericType classType;

    private final MethodModelCollection constructors = new MethodModelCollection();
    private final List<IFunctionModel> declaredMethods = new ArrayList<>();
    private final Map<String, FieldModel> declaredFields = new HashMap<>();
    private final List<IFunctionModel> declaredStaticMethods = new ArrayList<>();
    private final Map<String, FieldModel> declaredStaticFields = new HashMap<>();

    private final Map<String, IFieldModel> staticFields = new HashMap<>();
    private final Map<String, IFieldModel> fields = new HashMap<>();
    private final Map<String, MethodModelCollection> staticMethods = new TreeMap<>();
    private final Map<String, MethodModelCollection> methods = new TreeMap<>();

    private final Map<String, List<Method>> noReflectionMethods = new HashMap<>();

    private IFunctionModel factoryMethod;

    private final MethodInvokers invokers;

    static class TypeMap {
        boolean parameterized;
        IGenericType rawType;
        IGenericType contextType;
    }

    public ClassModelBuilder(ReflectionManager reflectionManager, Class<?> clazz) {
        super(reflectionManager);
        this.clazz = clazz;
        this.classType = reflectionManager.buildRawType(clazz);
        this.invokers = reflectionManager.getInvokers(clazz);
        discover();
    }

    private boolean isSysClass() {
        String className = clazz.getName();
        for (String prefix : SYS_CLASS_PREFIXES) {
            if (className.startsWith(prefix))
                return true;
        }
        return false;
    }

    private void discover() {
        discoverConstructors();
        // 如果是自动生成的代理类，则忽略本类上所有的方法
        if (!IAopProxy.class.isAssignableFrom(clazz) && clazz != Class.class) {
            discoverDeclaredMethods();
            discoverDefaultMethods();
            discoverDeclaredFields();
        }
    }

    public IClassModel build() {
        initMethods();
        initFields();
        initSuper();
        initInterfaceMethods();

        initHelperMethods();

        initFactoryMethod();

        return buildClassModel();
    }

    private void initFactoryMethod() {
        IFunctionModel factoryMethod = null;
        for (IFunctionModel mtd : this.declaredStaticMethods) {
            if (mtd.getArgCount() == 1) {
                if (mtd.isAnnotationPresent(StaticFactoryMethod.class)) {
                    if (factoryMethod != null)
                        throw new NopEvalException(ERR_REFLECT_MULTIPLE_FACTORY_METHOD)
                                .param(ARG_METHOD_NAME, mtd.getName()).param(ARG_CLASS_NAME, this.clazz);
                    factoryMethod = mtd;
                }
            }
        }

        // if (factoryMethod == null) {
        // for (MethodModelCollection mtds : this.staticMethods.values()) {
        // for (IFunctionModel mtd : mtds.getMethods()) {
        // if (mtd.getArgCount() == 1) {
        // if (mtd.isAnnotationPresent(StaticFactoryMethod.class)) {
        // factoryMethod = mtd;
        // break;
        // }
        // }
        // }
        // }
        // }

        if (factoryMethod == null) {
            if (clazz.isEnum()) {
                factoryMethod = getValueOfStaticMethod();
            }
        }

        this.factoryMethod = factoryMethod;
    }

    private IFunctionModel getValueOfStaticMethod() {
        for (IFunctionModel model : declaredStaticMethods) {
            if (model.getArgCount() == 1 && model.getName().equals("valueOf")
                    && model.getArgRawTypes()[0] == String.class) {
                return model;
            }
        }
        return null;
    }

    private IClassModel buildClassModel() {
        ClassModel classModel = new ClassModel(classType);
        Class<?> realClass = clazz;
        if (IAopProxy.class != clazz && IAopProxy.class.isAssignableFrom(clazz)) {
            realClass = clazz.getSuperclass();
        }

        addAnnotations(classModel, realClass);

        classModel.setClassName(clazz.getCanonicalName());
        classModel.setConstructors(constructors);

        classModel.setDeclaredMethods(immutableList(declaredMethods));
        classModel.setDeclaredFields(immutableMap(declaredFields));
        classModel.setDeclaredStaticFields(immutableMap(declaredStaticFields));
        classModel.setDeclaredStaticMethods(immutableList(declaredStaticMethods));

        classModel.setFields(immutableMap(fields));
        classModel.setStaticFields(immutableMap(staticFields));
        classModel.setMethods(immutableList(collectMethods(methods)));
        classModel.setMethodCollections(immutableMap(methods));
        classModel.setStaticMethods(immutableList(collectMethods(staticMethods)));
        classModel.setStaticMethodCollections(immutableMap(staticMethods));

        classModel.setImmutable(realClass.isAnnotationPresent(Immutable.class));
        classModel.setFactoryMethod(factoryMethod);
        classModel.freeze(true);

        return classModel;
    }

    private List<IFunctionModel> collectMethods(Map<String, MethodModelCollection> methods) {
        List<IFunctionModel> mtds = new ArrayList<>();
        for (MethodModelCollection mc : methods.values()) {
            mtds.addAll(mc.getMethods());
        }
        return mtds;
    }

    private void discoverConstructors() {
        // 跳过私有类的构造函数
        if (Modifier.isPrivate(clazz.getModifiers()))
            return;

        Constructor<?>[] ctors = clazz.getDeclaredConstructors();
        for (int i = 0, n = ctors.length; i < n; i++) {
            Constructor<?> ctor = ctors[i];

            // 只考虑public的构造器
            if (!Modifier.isPublic(ctor.getModifiers())) {
                if (ctor.getParameterCount() != 0) {
                    continue;
                }
                if (ctor.getModifiers() != 0)
                    continue;
            }

            if (ctor.getParameterCount() > 0) {
                if (ctor.isSynthetic())
                    continue;
            }

            FunctionModel method = buildExecutable(ctor);
            addConstructor(method);
        }
    }

    public void addConstructor(FunctionModel method) {
        constructors.addMethod(method);
    }

    //
    // private boolean isTemplateStringMethod(Method method) {
    // if (!Modifier.isStatic(method.getModifiers()))
    // return false;
    //
    // if (!Modifier.isPublic(method.getModifiers()))
    // return false;
    //
    // if (!method.isAnnotationPresent(TemplateStringMethod.class))
    // return false;
    //
    // boolean valid = true;
    //
    // if (method.getParameterCount() != 2) {
    // valid = false;
    // } else if (!ASTNode.class.isAssignableFrom(method.getReturnType())) {
    // valid = false;
    // } else if (!IEvalScope.class.isAssignableFrom(method.getParameters()[0].getType())) {
    // valid = false;
    // } else if (!String.class.isAssignableFrom(method.getParameters()[1].getType())) {
    // valid = false;
    // }
    //
    // if (!valid)
    // throw new NopException(ERR_REFLECT_INVALID_TEMPLATE_STRING_METHOD)
    // .param(ARG_METHOD_NAME, method.getName())
    // .param(ARG_CLASS_NAME, method.getDeclaringClass().getName());
    //
    // return true;
    // }

    private void discoverDeclaredMethods() {
        boolean publicClass = Modifier.isPublic(clazz.getModifiers());
        boolean sysClass = isSysClass();

        Method[] mtds = clazz.getDeclaredMethods();
        for (int i = 0, n = mtds.length; i < n; i++) {
            Method mtd = mtds[i];

            if (Modifier.isPrivate(mtd.getModifiers()))
                continue;

            if (mtd.isSynthetic())
                continue;

            // default method在discoverDefaultMethods()函数中处理
            if (mtd.isDefault())
                continue;

            // 禁止通过反射访问getClass(), notify(), finalize()等Object上内置的方法
            if (ForbiddenObjectMethods.contains(mtd.getName(), mtd.getParameterTypes()))
                continue;

            if (mtd.isAnnotationPresent(NoReflection.class)) {
                noReflectionMethods.computeIfAbsent(mtd.getName(), k -> new ArrayList<>()).add(mtd);
                continue;
            }

            // jdk9之后module中非公开类上的方法不能通过反射被调用
            if (sysClass) {
                if (!Modifier.isPublic(mtd.getModifiers()))
                    continue;

                if (!publicClass) {
                    IFunctionModel fn = findInInterface(mtd);
                    if (fn != null) {
                        addMethod(fn);
                    }
                    continue;
                }
            }

            IFunctionModel method = buildExecutable(mtd);
            addMethod(method);
        }
    }

    private void discoverDefaultMethods() {
        Method[] mtds = clazz.getMethods();
        for (int i = 0, n = mtds.length; i < n; i++) {
            Method mtd = mtds[i];

            if (mtd.isSynthetic())
                continue;

            // 仅处理default method
            if (!mtd.isDefault())
                continue;

            if (mtd.isAnnotationPresent(NoReflection.class))
                continue;

            if (ForbiddenObjectMethods.contains(mtd.getName(), mtd.getParameterTypes()))
                continue;

            if (isNoReflection(mtd))
                continue;

            FunctionModel method = buildExecutable(mtd);
            method = fixTypeParameter(classType, method, mtd);
            getMethodCollection(method.getName()).addMethod(method);
        }
    }

    boolean isNoReflection(Method method) {
        List<Method> methods = noReflectionMethods.get(method.getName());
        if (methods == null)
            return false;

        for (Method mtd : methods) {
            if (mtd.getName().equals(method.getName())
                    && Arrays.equals(mtd.getParameterTypes(), method.getParameterTypes())) {
                return true;
            }
        }
        return false;
    }

    boolean isNoReflection(IFunctionModel fn) {
        List<Method> methods = noReflectionMethods.get(fn.getName());
        if (methods == null)
            return false;

        for (Method mtd : methods) {
            if (mtd.getName().equals(fn.getImplName()) && fn.isExactlyMatch(mtd.getParameterTypes())) {
                return true;
            }
        }
        return false;
    }

    IFunctionModel findInInterface(Method method) {
        Class<?>[] interfaces = clazz.getInterfaces();
        for (Class<?> inf : interfaces) {
            IClassModel infModel = reflectionManager.getClassModel(inf);
            IFunctionModel fn = infModel.getMethodByExactType(method.getName(), method.getParameterTypes());
            if (fn != null) {
                IGenericType returnType = ReflectionManager.instance().buildGenericType(method.getReturnType());
                if (!returnType.equals(fn.getReturnType())) {
                    // fn = fn.forReturnType(returnType);
                    LOG.warn("nop.interface-method-type:returnType={}", returnType);
                }
                return fn;
            }
        }
        return null;
    }

    public void addMethod(IFunctionModel method) {
        if (method.isStatic()) {
            declaredStaticMethods.add(method);
        } else {
            declaredMethods.add(method);
        }
    }

    private void discoverDeclaredFields() {
        // boolean publicClass = Modifier.isPublic(clazz.getModifiers());
        boolean sysClass = isSysClass();

        Field[] flds = clazz.getDeclaredFields();
        for (int i = 0, n = flds.length; i < n; i++) {
            Field fld = flds[i];

            if (Modifier.isPrivate(fld.getModifiers()))
                continue;

            if (sysClass) {
                if (!Modifier.isPublic(fld.getModifiers()))
                    continue;
            }

            FieldModel field = buildField(fld);
            addField(field);
        }
    }

    public void addField(FieldModel field) {
        if (field.isStatic()) {
            declaredStaticFields.put(field.getName(), field);
        } else {
            declaredFields.put(field.getName(), field);
        }
    }

    private FieldModel buildField(Field field) {
        FieldModel prop = new FieldModel();
        prop.setModifiers(field.getModifiers());
        addAnnotations(prop, field);
        prop.setName(getFieldName(field));
        prop.setType(buildGenericType(field.getGenericType()));
        buildFieldAccessor(prop, field);
        return prop;
    }

    private void buildFieldAccessor(FieldModel prop, Field field) {
        FieldPropertyAccessor accessor = new FieldPropertyAccessor(field);
        prop.setGetter(accessor);
        if (!Modifier.isFinal(field.getModifiers()))
            prop.setSetter(accessor);
    }

    private String getFieldName(Field field) {
        String name = field.getName();
        Name reflectionName = field.getAnnotation(Name.class);
        if (reflectionName != null)
            name = reflectionName.value();
        return name;
    }

    protected IEvalFunction buildConstructorInvoker(Constructor<?> constructor) {
        if (invokers != null) {
            IEvalFunction invoker = invokers.getInvoker(false, METHOD_CONSTRUCTOR, constructor.getParameterTypes());
            if (invoker != null)
                return invoker;
        }
        return super.buildConstructorInvoker(constructor);
    }

    protected IEvalFunction buildMethodInvoker(Method method) {
        if (invokers != null) {
            IEvalFunction invoker = invokers.getInvoker(false, method.getName(), method.getParameterTypes());
            if (invoker != null)
                return invoker;
        }
        return super.buildMethodInvoker(method);
    }

    private void initSuper() {
        Class<?> superClass = clazz.getSuperclass();
        // 首先拷贝基类的所有属性和方法
        if (superClass != null) {
            IClassModel superModel = reflectionManager.getClassModel(superClass);
            CollectionHelper.putAllIfAbsent(staticFields, superModel.getStaticFields());
            CollectionHelper.putAllIfAbsent(fields, superModel.getFields());

            for (IFunctionModel method : superModel.getStaticMethods()) {
                MethodModelCollection mc = getStaticMethodCollection(method.getName());
                if (mc.getExactMatchMethod(method.getArgRawTypes()) == null) {
                    method = fixTypeParameter(classType, (FunctionModel) method, true);
                    mc.addMethod(method);
                }
            }

            for (IFunctionModel method : superModel.getMethods()) {
                if (isNoReflection(method))
                    continue;

                MethodModelCollection mc = getMethodCollection(method.getName());
                IFunctionModel fn = mc.getExactMatchMethod(method.getArgRawTypes());
                if (fn == null) {
                    method = fixTypeParameter(classType, (FunctionModel) method, true);
                    mc.addMethod(method);
                } else {
                    inheritAnnotations((FunctionModel) fn, method);
                }
            }
        }
    }

    private void initInterfaceMethods() {
        // 如果不是抽象类和接口，则所有接口上的方法必然已经被实现，不需要再查找
        if (!Modifier.isAbstract(clazz.getModifiers()))
            return;

        Class<?>[] infs = clazz.getInterfaces();
        for (Class<?> inf : infs) {
            IClassModel infModel = reflectionManager.getClassModel(inf);
            for (IFunctionModel method : infModel.getMethods()) {
                if (isNoReflection(method))
                    continue;

                MethodModelCollection mc = getMethodCollection(method.getName());
                IFunctionModel fn = mc.getExactMatchMethod(method.getArgRawTypes());
                if (fn == null) {
                    method = fixTypeParameter(classType, (FunctionModel) method, true);
                    mc.addMethod(method);
                } else {
                    inheritAnnotations((FunctionModel) fn, method);
                }
            }

            for (IFieldModel field : infModel.getStaticFields().values()) {
                staticFields.putIfAbsent(field.getName(), field);
            }
        }
    }

    private void inheritAnnotations(FunctionModel fn, IFunctionModel superMethod) {
        for (Annotation ann : superMethod.getAnnotations()) {
            Annotation thisAnn = fn.getAnnotation(ann.annotationType());
            if (thisAnn != null)
                continue;

            if (ann.annotationType().isAnnotationPresent(Inherited.class)) {
                fn.addAnnotation(ann);
            }
        }

        for (int i = 0, n = superMethod.getArgCount(); i < n; i++) {
            IFunctionArgument arg = superMethod.getArgs().get(i);
            FunctionArgument thisArg = fn.getArgs().get(i);
            Name name = arg.getAnnotation(Name.class);
            if (name != null) {
                if (thisArg.getAnnotation(Name.class) == null) {
                    thisArg.addAnnotation(name);
                    thisArg.setName(name.value());
                }
            }

            Description desc = arg.getAnnotation(Description.class);
            if (desc != null) {
                if (thisArg.getAnnotation(Description.class) == null) {
                    thisArg.addAnnotation(desc);
                }
            }
        }
    }

    private void initMethods() {
        for (IFunctionModel method : this.declaredStaticMethods) {
            getStaticMethodCollection(method.getName()).addMethod(method);
        }

        for (IFunctionModel method : this.declaredMethods) {
            getMethodCollection(method.getName()).mergeMethod(method);
        }
    }

    private MethodModelCollection getStaticMethodCollection(String name) {
        return staticMethods.computeIfAbsent(name, k -> new MethodModelCollection());
    }

    private MethodModelCollection getMethodCollection(String name) {
        return methods.computeIfAbsent(name, k -> new MethodModelCollection());
    }

    private void initFields() {
        fields.putAll(declaredFields);
        staticFields.putAll(declaredStaticFields);
    }

    private void initHelperMethods() {
        ClassExtension extension = reflectionManager.getClassExtension(this.clazz);
        if (extension != null) {
            addExtension(extension);
        }

        // super ClassModel中已经包含针对基类的扩展函数
        Class<?> superClass = this.clazz.getSuperclass();
        while (superClass != null) {
            extension = reflectionManager.getClassExtension(superClass);
            if (extension != null) {
                addExtension(extension);
            }
            superClass = superClass.getSuperclass();
        }

        Class<?>[] interfaces = clazz.getInterfaces();
        for (Class<?> inf : interfaces) {
            extension = reflectionManager.getClassExtension(inf);
            if (extension != null) {
                addExtension(extension);
            }
        }
    }

    private void addExtension(ClassExtension extension) {
        List<IFunctionModel> helperMethods = extension.getHelperMethods();
        extension.recordExtendedClass(this.clazz);
        for (IFunctionModel method : helperMethods) {
            MethodModelCollection mc = getMethodCollection(method.getName());
            if (mc.getExactMatchMethod(method.getArgRawTypes()) == null) {
                mc.addMethod(method);
            }
        }
    }
}