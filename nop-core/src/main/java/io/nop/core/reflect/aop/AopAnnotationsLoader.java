/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.reflect.aop;

import io.nop.commons.util.ClassHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.CoreConstants;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.VirtualFileSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AopAnnotationsLoader {
    static final Logger LOG = LoggerFactory.getLogger(AopAnnotationsLoader.class);

    private static List<Class<?>> annotationClasses;

    public static List<Class<?>> getAnnotationClasses() {
        if (annotationClasses == null)
            annotationClasses = loadAnnotationClasses();
        return annotationClasses;
    }

    static List<Class<?>> loadAnnotationClasses() {
        try {
            List<? extends IResource> list = VirtualFileSystem.instance()
                    .getChildren(CoreConstants.ANNOTATION_REGISTRY_PATH);
            Set<String> classNames = parseClassNames(list);
            List<Class<?>> classes = new ArrayList<>(classNames.size());
            for (String className : classNames) {
                try {
                    Class<?> clazz = ClassHelper.safeLoadClass(className);
                    classes.add(clazz);
                } catch (Exception e) {
                    LOG.warn("nop.aop.ignore-unknown-annotation:{}", className);
                }
            }
            return classes;
        } catch (Exception e) {
            LOG.warn("nop.aop.load-annotation-registry-fail", e);
            return Collections.emptyList();
        }
    }

    static Set<String> parseClassNames(List<? extends IResource> resources) {
        Set<String> ret = new HashSet<>();
        if (resources == null)
            return ret;

        for (IResource resource : resources) {
            if (!resource.getName().endsWith(CoreConstants.FILE_POSTFIX_ANNOTATIONS))
                continue;
            try {
                String text = ResourceHelper.readText(resource);
                String[] lines = StringHelper.splitToLines(text);
                ret.addAll(Arrays.asList(lines));
            } catch (Exception e) {
                LOG.debug("nop.aop.parse-annotation-registry", e);
            }
        }
        return ret;
    }
}