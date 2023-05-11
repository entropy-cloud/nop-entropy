/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.ioc.loader;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.ioc.impl.BeanDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static io.nop.ioc.IocErrors.ARG_ALIAS;
import static io.nop.ioc.IocErrors.ARG_BEAN_NAME;
import static io.nop.ioc.IocErrors.ARG_LOC_A;
import static io.nop.ioc.IocErrors.ARG_LOC_B;
import static io.nop.ioc.IocErrors.ARG_TRACE;
import static io.nop.ioc.IocErrors.ERR_IOC_ALIAS_CONFLICT;
import static io.nop.ioc.IocErrors.ERR_IOC_DUPLICATE_BEAN_DEFINITION;

/**
 * 根据BeansModel初步解析得到的结果
 */
class BeansDefinition {
    static final Logger LOG = LoggerFactory.getLogger(BeansDefinition.class);

    private final Set<String> importedResources = new HashSet<>();

    private final Map<String, BeanDefinition> beans = new HashMap<>();
    /**
     * 新的名称 ==> 已有的bean的名称以及Alias定义的位置
     */
    private final Map<String, AliasName> aliases = new HashMap<>();

    Map<String, AliasName> getAliases() {
        return aliases;
    }

    Map<String, BeanDefinition> getBeans() {
        return beans;
    }

    Set<String> getImportedResources() {
        return importedResources;
    }

    boolean addImported(String path) {
        return importedResources.add(path);
    }

    boolean containsImported(String path) {
        return importedResources.contains(path);
    }

    void addAlias(String alias, AliasName aliasName) {
        AliasName old = aliases.put(alias, aliasName);
        if (old != null) {
            throw new NopException(ERR_IOC_ALIAS_CONFLICT).loc(aliasName.getLocation())
                    .param(ARG_BEAN_NAME, aliasName.getName()).param(ARG_ALIAS, alias)
                    .param(ARG_LOC_A, old.getLocation()).param(ARG_LOC_B, aliasName.getLocation())
                    .param(ARG_TRACE, aliasName.getTrace());
        }
    }

    void addAlias(SourceLocation loc, String name, String alias, String trace) {
        addAlias(alias, new AliasName(loc, name, trace));
    }

    void addBean(BeanDefinition bean) {
        BeanDefinition old = beans.put(bean.getId(), bean);
        if (old != null) {
            old.setRemoved(true);
//            if (old.isIocDefault()) {
//                if (!bean.isIocDefault())
//                    return;
//            } else {
//                if (bean.isIocDefault()) {
//                    beans.put(old.getId(), old);
//                    return;
//                }
//            }

            if (!bean.isIocAllowOverride()) {
                throw new NopException(ERR_IOC_DUPLICATE_BEAN_DEFINITION).source(bean)
                        .param(ARG_BEAN_NAME, bean.getId()).param(ARG_LOC_A, old.getLocation())
                        .param(ARG_LOC_B, bean.getLocation());
            }
            LOG.info("nop.bean.override:id={},locA={},locB={},trace={}", bean.getId(), old.getLocation(),
                    bean.getLocation(), bean.getTrace());
        }
    }

    void replaceBean(BeanDefinition bean) {
        BeanDefinition old = beans.put(bean.getId(), bean);
        if (old != null) {
            old.setRemoved(true);
            LOG.info("nop.bean.replace-bean:id={},locA={},locB={},trace={}", bean.getId(), old.getLocation(),
                    bean.getLocation(), bean.getTrace());
        }
    }

    void merge(BeansDefinition beans) {
        for (Map.Entry<String, AliasName> entry : beans.getAliases().entrySet()) {
            addAlias(entry.getKey(), entry.getValue());
        }

        for (BeanDefinition bean : beans.getBeans().values()) {
            addBean(bean);
        }

        this.importedResources.addAll(beans.getImportedResources());
    }
}