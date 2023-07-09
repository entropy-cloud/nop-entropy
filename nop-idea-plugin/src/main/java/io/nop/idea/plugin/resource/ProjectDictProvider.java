/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.idea.plugin.resource;

import com.intellij.lang.jvm.JvmEnumField;
import com.intellij.lang.jvm.annotation.JvmAnnotationAttribute;
import com.intellij.lang.jvm.annotation.JvmAnnotationConstantValue;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.search.GlobalSearchScope;
import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.annotations.core.Label;
import io.nop.api.core.annotations.core.Option;
import io.nop.api.core.beans.DictBean;
import io.nop.api.core.beans.DictOptionBean;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.commons.cache.ICache;
import io.nop.core.context.IEvalContext;
import io.nop.core.dict.DictModel;
import io.nop.core.dict.DictModelParser;
import io.nop.core.dict.IDictLoader;
import io.nop.core.dict.IDictProvider;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.idea.plugin.services.NopProjectService;

import java.util.ArrayList;
import java.util.List;

public class ProjectDictProvider implements IDictProvider {
    @Override
    public DictBean getDict(String locale, String dictName, ICache<Object, Object> cache, IEvalContext context) {
        return NopProjectService.get().getDict(dictName);
    }

    @Override
    public boolean existsDict(String dictName) {
        return true;
    }

    @Override
    public void addDictLoader(String prefix, IDictLoader dictLoader) {

    }

    @Override
    public void removeDictLoader(String prefix, IDictLoader dictLoader) {

    }

    public static DictModel loadDictModel(String dictName) {
        Project project = ProjectEnv.currentProject();

        if (dictName.indexOf('/') > 0) {
            String path = "/dict/" + dictName + ".dict.yaml";
            IResource resource = VirtualFileSystem.instance().getResource(path);
            if (resource.exists()) {
                ResourceComponentManager.instance().traceDepends(resource.getPath());
                return new DictModelParser().parseFromResource(resource);
            }
        } else if (dictName.indexOf('.') > 0) {
            // load dict from class
            PsiClass clazz = JavaPsiFacade.getInstance(project).findClass(dictName, GlobalSearchScope.allScope(project));
            if (clazz != null) {
                DictBean dict = new DictBean();
                List<DictOptionBean> options = new ArrayList<>();
                for (PsiField field : clazz.getFields()) {
                    if (!(field instanceof JvmEnumField))
                        continue;

                    DictOptionBean option = buildOption(field);
                    options.add(option);
                }
                dict.setOptions(options);
                DictModel ret = new DictModel();
                ret.setDictBean(dict);

//                final PsiConstantEvaluationHelper evaluationHelper =
//                        JavaPsiFacade.getInstance(ProjectEnv.currentProject()).getConstantEvaluationHelper();
//                final Set<Object> enumValues = new HashSet<>();
//                for (PsiField enumConstant : clazz.getFields()) {
//                    if (enumConstant instanceof JvmEnumField) {
//                        enumValues.add(evaluationHelper.computeConstantExpression(enumConstant.getInitializer()));
//                    }
//                }
                return ret;
            }
        }

        return null;
    }

    private static DictOptionBean buildOption(PsiField field) {
        DictOptionBean option = new DictOptionBean();
        String value = getAnnotationValue(field, Option.class.getName());
        if (value == null)
            value = field.getName();

        String label = getAnnotationValue(field, Label.class.getName());
        if (label == null)
            label = value;
        String description = getAnnotationValue(field, Description.class.getName());
        option.setValue(value);
        option.setLabel(label);
        option.setDescription(description);
        return option;
    }

    private static String getAnnotationValue(PsiField field, String annName) {
        PsiAnnotation ann = field.getAnnotation(annName);
        if (ann == null)
            return null;
        for (JvmAnnotationAttribute attr : ann.getAttributes()) {
            if (attr.getAttributeName().equals("value")) {
                if (attr.getAttributeValue() instanceof JvmAnnotationConstantValue)
                    return ConvertHelper.toString(((JvmAnnotationConstantValue) attr.getAttributeValue()).getConstantValue());
            }
        }
        return null;
    }
}