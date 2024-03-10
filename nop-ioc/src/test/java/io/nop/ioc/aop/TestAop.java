/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ioc.aop;

import io.nop.api.core.annotations.txn.Transactional;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.ClassHelper;
import io.nop.commons.util.FileHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.DisabledEvalScope;
import io.nop.core.reflect.IClassModel;
import io.nop.core.reflect.IFunctionModel;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.reflect.aop.AopCodeGenerator;
import io.nop.core.reflect.aop.IAopProxy;
import io.nop.core.reflect.aop.IMethodInterceptor;
import io.nop.core.reflect.aop.IMethodInvocation;
import io.nop.core.reflect.bean.IBeanModel;
import io.nop.core.reflect.impl.FunctionModel;
import io.nop.core.reflect.impl.MethodModelBuilder;
import io.nop.core.unittest.BaseTestCase;
import io.nop.javac.janino.JaninoClassLoader;
import io.nop.javac.jdk.JavaCompileResult;
import io.nop.javac.jdk.JavaSourceCode;
import io.nop.javac.jdk.JdkJavaCompiler;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestAop extends BaseTestCase {
    @Test
    public void testGenerateCode() {
        AopCodeGenerator gen = new AopCodeGenerator();
        String code = gen.build(MyClass.class, new Class[]{Transactional.class});
        System.out.println(code);
    }

    @Test
    public void testReflection() throws Exception {
        FunctionModel myMethod = MethodModelBuilder.from(MyClass.class, MyClass.class.getMethod("myMethod", String.class, int.class,
                byte[].class, List[].class));
        assertEquals(void.class, myMethod.getReturnClass());

        IClassModel classModel = ReflectionManager.instance().getClassModel(MyClass.class);
        IFunctionModel fn = classModel.getMethod("myMethod", 4);
        assertEquals(List.class, fn.getArgRawTypes()[3].getComponentType());
        assertEquals(void.class, fn.getReturnClass());
    }

    @Test
    public void testDynamicGen() throws Exception {
        File moduleDir = getModuleDir();
        String className = AopCodeGenerator.getAopClassName(MyClass.class);
        File sourceFile = JaninoClassLoader.getSourceFile(moduleDir, className, true);

        AopCodeGenerator gen = new AopCodeGenerator();
        String code = gen.build(MyClass.class, new Class[]{Transactional.class});
        System.out.println(code);
        assertEquals(StringHelper.normalizeCRLF(attachmentText("MyClass__aop.java"), false), code);
        FileHelper.writeText(sourceFile, code, null);

        JdkJavaCompiler compiler = new JdkJavaCompiler();
        List<String> classPaths = JdkJavaCompiler.getDefaultClassPaths();
        JavaCompileResult result = compiler.compile(Arrays.asList(new JavaSourceCode(className, code)), classPaths);
        System.out.println(result);
        assertTrue(result.isSuccess());
        result.saveGenerated(new File(moduleDir, "target/test-classes"));

//        JaninoClassLoader classLoader = JaninoClassLoader.createForProject(TestAop.class.getClassLoader(), moduleDir,
//                true);

        try {
            Class<?> clazz = getClass().getClassLoader().loadClass(className);
            Object bean = ReflectionManager.instance().getClassModel(clazz).getConstructor(1).call1(null, "a",
                    DisabledEvalScope.INSTANCE);

            IAopProxy proxy = (IAopProxy) bean;
            proxy.$$aop_interceptors(new IMethodInterceptor[]{new IMethodInterceptor() {
                @Override
                public Object invoke(IMethodInvocation inv) throws Exception {
                    Transactional a = inv.getMethod().getAnnotation(Transactional.class);
                    System.out.println("class=" + a.getClass());

                    IBeanModel beanModel = ReflectionManager.instance()
                            .getBeanModelForClass(ClassHelper.getObjClass(a));
                    System.out.println(beanModel.getProperty(a, "txnGroup"));

                    System.out.println("before");
                    return inv.proceed();
                }
            }});

            ((MyClass) bean).myMethod("a", 1, null, null);

            IClassModel classModel = ReflectionManager.instance().getClassModel(bean.getClass());
            classModel.getMethod("myMethod", 4).invoke(bean, new Object[]{"b", 2, null, null},
                    DisabledEvalScope.INSTANCE);
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }
}
