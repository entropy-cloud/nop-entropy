/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.ioc.impl;

import io.nop.commons.util.CollectionHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BeanFinder {
    static final Logger LOG = LoggerFactory.getLogger(BeanFinder.class);

    public static List<BeanDefinition> collectBeans(Map<Class<?>, BeanTypeMapping> typeMappings,
                                                    Map<Class<? extends Annotation>, List<BeanDefinition>> annMappings, Map<String, BeanDefinition> allBeans,
                                                    Class<?> beanType, Class<? extends Annotation> annType) {
        List<BeanDefinition> ret = new ArrayList<>();
        if (beanType != null) {
            if (beanType == Object.class) {
                ret.addAll(allBeans.values());
            } else {
                BeanTypeMapping mapping = getBeansByType(typeMappings, allBeans, beanType);
                ret.addAll(mapping.getBeans());
            }
        }

        if (annType != null) {
            List<BeanDefinition> beans = getBeansByAnnotation(annMappings, allBeans, annType);
            if (beanType == null) {
                ret.addAll(beans);
            } else {
                // 同时满足annotation和beanType条件
                ret.retainAll(beans);
            }
        }
        return ret;
    }

    public static BeanTypeMapping getBeansByType(Map<Class<?>, BeanTypeMapping> mappings,
                                                 Map<String, BeanDefinition> allBeans, Class<?> beanType) {
        return mappings.computeIfAbsent(beanType, k -> findByType(allBeans, beanType));
    }

    public static BeanTypeMapping findByType(Map<String, BeanDefinition> allBeans, Class<?> beanType) {
        Set<BeanDefinition> beans = new HashSet<>();
        BeanDefinition primary = null;
        BeanDefinition otherPrimary = null;

        for (BeanDefinition bean : allBeans.values()) {
            if (bean.isAbstract() || bean.isDisabled() || bean.isEmbedded())
                continue;

            if (!bean.getBeanModel().isAutowireCandidate()) {
                if (isAssignable(bean, beanType)) {
                    LOG.info("nop.ioc.ignore-bean-not-autowire-candidate:expectedType={},bean={}", beanType, bean);
                }
                continue;
            }

            for (Class<?> type : bean.getBeanTypes()) {
                if (beanType.isAssignableFrom(type)) {
                    beans.add(bean);

                    if (bean.isPrimary()) {
                        if (primary == null) {
                            primary = bean;
                        } else {
                            otherPrimary = bean;
                        }
                    }
                    break;
                }
            }
        }

        if (primary == null) {
            if (beans.size() == 1)
                primary = CollectionHelper.first(beans);
        }

        return new BeanTypeMapping(beans, primary, otherPrimary);
    }

    static boolean isAssignable(BeanDefinition bean, Class<?> beanType) {
        for (Class<?> type : bean.getBeanTypes()) {
            if (beanType.isAssignableFrom(type)) {
                return true;
            }
        }
        return false;
    }

    public static List<BeanDefinition> getBeansByAnnotation(
            Map<Class<? extends Annotation>, List<BeanDefinition>> mappings, Map<String, BeanDefinition> allBeans,
            Class<? extends Annotation> annType) {
        return mappings.computeIfAbsent(annType, k -> findByAnnotation(allBeans, annType));
    }

    public static List<BeanDefinition> findByAnnotation(Map<String, BeanDefinition> allBeans,
                                                        Class<? extends Annotation> annType) {
        List<BeanDefinition> beans = new ArrayList<>();

        for (BeanDefinition bean : allBeans.values()) {
            if (bean.isAbstract() || bean.isDisabled() || bean.isEmbedded())
                continue;

            if (!bean.getBeanModel().isAutowireCandidate()) {
                continue;
            }

            if (bean.getBeanClass() != null && bean.getBeanClass().isAnnotationPresent(annType)) {
                beans.add(bean);
                continue;
            }

            for (Class<?> type : bean.getBeanTypes()) {
                if (type.isAnnotationPresent(annType)) {
                    beans.add(bean);
                    break;
                }
            }
        }
        return beans;
    }
}
