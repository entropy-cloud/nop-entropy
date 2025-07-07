/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.idea.plugin.resource;

import java.util.ArrayList;
import java.util.List;

import com.intellij.lang.jvm.JvmEnumField;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.search.GlobalSearchScope;
import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.annotations.core.Label;
import io.nop.api.core.annotations.core.Option;
import io.nop.api.core.beans.DictBean;
import io.nop.api.core.beans.DictOptionBean;
import io.nop.commons.cache.ICache;
import io.nop.commons.util.StringHelper;
import io.nop.core.context.IEvalContext;
import io.nop.core.dict.DictModel;
import io.nop.core.dict.DictModelParser;
import io.nop.core.dict.IDictLoader;
import io.nop.core.dict.IDictProvider;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.idea.plugin.services.NopProjectService;
import io.nop.idea.plugin.utils.PsiClassHelper;

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
        }
        // 从枚举类中得到字典信息
        else if (dictName.indexOf('.') > 0 && StringHelper.isValidClassName(dictName)) {
            GlobalSearchScope scope = GlobalSearchScope.allScope(project);

            PsiClass clazz = PsiClassHelper.findClass(project, dictName, scope);
            if (clazz == null) {
                return null;
            }

            List<DictOptionBean> options = new ArrayList<>();
            for (PsiField field : clazz.getFields()) {
                if (!(field instanceof JvmEnumField)) {
                    continue;
                }

                DictOptionBean option = buildOption(field);
                options.add(option);
            }

            DictBean dict = new DictBean();
            dict.setOptions(options);

            DictModel ret = new DictModel();
            ret.setDictBean(dict);

            return ret;
        }

        return null;
    }

    private static DictOptionBean buildOption(PsiField field) {
        DictOptionBean option = new EnumDictOptionBean(field);

        String value = (String) PsiClassHelper.getAnnotationValue(field, Option.class.getName());
        if (value == null) {
            value = field.getName();
        }
        option.setValue(value);

        String label = (String) PsiClassHelper.getAnnotationValue(field, Label.class.getName());
        if (label == null) {
            label = value;
        }
        option.setLabel(label);

        String description = (String) PsiClassHelper.getAnnotationValue(field, Description.class.getName());
        option.setDescription(description);

        return option;
    }
}
