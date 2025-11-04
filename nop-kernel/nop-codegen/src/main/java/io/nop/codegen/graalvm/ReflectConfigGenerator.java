/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.codegen.graalvm;

import io.nop.core.initialize.ICoreInitializer;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.reflect.IClassModel;
import io.nop.core.reflect.IFieldModel;
import io.nop.core.reflect.IFunctionArgument;
import io.nop.core.reflect.IFunctionModel;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.impl.FileResource;
import io.nop.core.resource.impl.URLResource;
import io.nop.core.resource.scan.ClassPathScanner;
import io.nop.core.type.utils.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;

/**
 * META-INF/native-image/foo_groupID/foo_artifactID
 */
public class ReflectConfigGenerator {
    static final Logger LOG = LoggerFactory.getLogger(ReflectConfigGenerator.class);

    public static ReflectConfigGenerator instance() {
        return new ReflectConfigGenerator();
    }

    private ReflectConfig _defaultConfig;

    public ReflectConfig loadDefaultConfig() {
        if (_defaultConfig == null) {
            ReflectConfig config = new ReflectConfig();
            new ClassPathScanner().scanPath("META-INF/native-image/", (path, url) -> {
                if (path.endsWith("reflect-config.json")) {
                    ReflectConfig existing = loadConfig(new URLResource("classpath:" + path, url));
                    config.merge(existing);
                }
            });
            _defaultConfig = config;
        }
        return _defaultConfig;
    }

    public void generateToResource(IResource resource) {
        ReflectConfig config = generate();
        saveConfig(resource, config);
    }

    public void generateDeltaToDir(File dir) {
        generateDeltaToResource(new FileResource(new File(dir, "reflect-config.json")));
    }

    public void generateDeltaToResource(IResource resource) {
        ReflectConfig config = generate();
        config.merge(generateServiceLoader());
        config.remove(loadDefaultConfig());

        if (resource.length() > 0) {
            ReflectConfig oldConfig = loadConfig(resource);
            config.merge(oldConfig);
        }
        saveConfig(resource, config);
    }

    ReflectConfig loadConfig(IResource resource) {
        ReflectConfig ret = new ReflectConfig();
        if (resource.length() <= 0) {
            ret.setClassList(new ArrayList<>(0));
            return ret;
        }
        List<ReflectClass> list = JsonTool.parseBeanFromResource(resource, new TypeReference<List<ReflectClass>>() {
        }.getType());
        list.forEach(clazz -> ret.addClass(clazz));
        return ret;
    }

    void saveConfig(IResource resource, ReflectConfig config) {
        config.sort();
        String json = JsonTool.serialize(config.getClassList(), true);

        LOG.info("nop.codegen.save-reflect-config:{}", resource);
        ResourceHelper.writeText(resource, json);
    }

    ReflectConfig generateServiceLoader() {
        ReflectConfig config = new ReflectConfig();
        ServiceLoader<ICoreInitializer> initializers = ServiceLoader.load(ICoreInitializer.class);
        for (ICoreInitializer initializer : initializers) {
            ReflectClass reflectClass = new ReflectClass();
            reflectClass.setName(initializer.getClass().getName());
            config.addClass(reflectClass);
        }
        return config;
    }

    public ReflectConfig generate() {
        ReflectConfig config = new ReflectConfig();

        Set<Class<?>> classes = ReflectionManager.instance().getReflectClasses();
        for (Class<?> clazz : classes) {
            // 跳过所有动态生成的代理类
            if (Proxy.class.isAssignableFrom(clazz))
                continue;
            if (clazz.isPrimitive())
                continue;

            IClassModel classModel = ReflectionManager.instance().getClassModel(clazz);
            ReflectClass reflectClass = buildReflectClass(classModel);
            config.addClass(reflectClass);
        }

        return config;
    }

    ReflectClass buildReflectClass(IClassModel classModel) {
        ReflectClass reflectClass = new ReflectClass();
        reflectClass.setName(classModel.getRawClass().getName());
        reflectClass.setAllPublicConstructors(true);
        reflectClass.setAllPublicMethods(true);
        reflectClass.setAllPublicFields(true);

        for (IFieldModel field : classModel.getDeclaredFields().values()) {
            if (field.getName().startsWith("$"))
                continue;

            // Inject注解等可能用于package protected字段
            if (field.isPrivate() || field.isPublic())
                continue;

            ReflectField f = new ReflectField();
            f.setName(field.getName());
            if (field.isWritable()) {
                f.setAllowWrite(true);
            }
            reflectClass.addField(f);
        }

//        for (IFieldModel field : classModel.getDeclaredStaticFields().values()) {
//            if (field.getName().startsWith("$"))
//                continue;
//
//            ReflectField f = new ReflectField();
//            f.setName(field.getName());
//            if (field.isWritable()) {
//                f.setAllowWrite(true);
//            }
//            reflectClass.addField(f);
//        }
//
        for (IFunctionModel method : classModel.getDeclaredMethods()) {
            if (method.getImplName().startsWith("$"))
                continue;
            if (method.isProtected()) {
                ReflectMethod m = new ReflectMethod();
                m.setName(method.getImplName());
                m.setParameterTypes(getParamTypes(method));
                reflectClass.addMethod(m);
            }
        }

        return reflectClass;
    }

    List<String> getParamTypes(IFunctionModel method) {
        List<String> types = new ArrayList<>(method.getArgCount());
        if (method.isEvalMethod()) {
            types.add(IEvalScope.class.getName());
        }
        for (IFunctionArgument arg : method.getArgs()) {
            types.add(arg.getRawClass().getName());
        }
        return types;
    }
}