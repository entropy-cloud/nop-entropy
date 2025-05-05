/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.dict;

import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.annotations.core.Internal;
import io.nop.api.core.annotations.core.Label;
import io.nop.api.core.annotations.core.Locale;
import io.nop.api.core.annotations.core.Option;
import io.nop.api.core.beans.DictBean;
import io.nop.api.core.beans.DictOptionBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.context.IEvalContext;
import io.nop.core.reflect.IClassModel;
import io.nop.core.reflect.IFieldModel;
import io.nop.core.reflect.ReflectionManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.nop.core.CoreErrors.ARG_CLASS_NAME;
import static io.nop.core.CoreErrors.ERR_DICT_BUILD_DICT_FROM_ENUM_FAIL;
import static io.nop.core.CoreErrors.ERR_DICT_NOT_VALID_ENUM_CLASS;

public class EnumDictLoader implements IDictLoader {
    public static final EnumDictLoader INSTANCE = new EnumDictLoader();

    private Map<Class<?>, DictBean> cache = CollectionHelper.newConcurrentWeakMap();

    public boolean supportDict(String dictName) {
        return dictName.indexOf('.') > 0 && StringHelper.isValidClassName(dictName);
    }

    @Override
    public DictBean loadDict(String locale, String dictName, IEvalContext ctx) {
        IClassModel classModel = loadEnumClass(dictName);
        try {
            return cache.computeIfAbsent(classModel.getRawClass(), clazz -> {
                return buildDictFromEnum(dictName, classModel);
            });
        } catch (Exception e) {
            throw new NopException(ERR_DICT_BUILD_DICT_FROM_ENUM_FAIL).param(ARG_CLASS_NAME, dictName);
        }
    }

    @Override
    public boolean existsDict(String dictName) {
        try {
            IClassModel classModel = ReflectionManager.instance().loadClassModel(dictName);
            return classModel.getRawClass().isEnum();
        } catch (Exception e) {
            return false;
        }
    }

    IClassModel loadEnumClass(String dictName) {
        IClassModel classModel = null;
        try {
            classModel = ReflectionManager.instance().loadClassModel(dictName);
        } catch (Exception e) {
            throw new NopException(ERR_DICT_NOT_VALID_ENUM_CLASS, e).param(ARG_CLASS_NAME, dictName);
        }
        if (!classModel.getRawClass().isEnum())
            throw new NopException(ERR_DICT_NOT_VALID_ENUM_CLASS).param(ARG_CLASS_NAME, dictName);
        return classModel;
    }

    DictBean buildDictFromEnum(String dictName, IClassModel classModel) {
        DictBean bean = new DictBean();
        bean.setName(dictName);
        bean.setStatic(true);

        Locale locale = classModel.getAnnotation(Locale.class);
        if (locale != null)
            bean.setLocale(bean.getLocale());

        Label label = classModel.getAnnotation(Label.class);
        if (label != null) {
            bean.setLabel(label.value());
        }

        Description description = classModel.getAnnotation(Description.class);
        if (description != null) {
            bean.setDescription(description.value());
        }

        if (classModel.isAnnotationPresent(Deprecated.class)) {
            bean.setDeprecated(true);
        }

        if (classModel.isAnnotationPresent(Internal.class)) {
            bean.setInternal(true);
        }

        List<DictOptionBean> options = buildOptions(classModel);
        bean.setOptions(options);
        bean.freeze(true);
        return bean;
    }

    List<DictOptionBean> buildOptions(IClassModel classModel) {
        List<DictOptionBean> options = new ArrayList<>();

        Enum[] items = (Enum[]) classModel.getRawClass().getEnumConstants();
        for (Enum item: items) {
            IFieldModel field = classModel.getStaticField(item.name());
            if (field.isEnumConstant()) {
                DictOptionBean option = new DictOptionBean();
                Object fieldValue = field.getValue(null);
                String str = fieldValue.toString();
                option.setValue(str);

                Label label = field.getAnnotation(Label.class);
                if (label != null) {
                    option.setLabel(label.value());
                } else {
                    option.setLabel(str);
                }

                Option opt = field.getAnnotation(Option.class);
                if (opt != null) {
                    option.setValue(opt.value());
                    if (opt.code().length() > 0)
                        option.setCode(opt.code());
                    if (opt.group().length() > 0)
                        option.setGroup(opt.group());
                }

                Description description = field.getAnnotation(Description.class);
                if (description != null) {
                    option.setDescription(description.value());
                }

                if (field.isAnnotationPresent(Deprecated.class)) {
                    option.setDeprecated(true);
                }

                if (field.isAnnotationPresent(Internal.class)) {
                    option.setInternal(true);
                }
                options.add(option);
            }
        }

        return options;
    }
}