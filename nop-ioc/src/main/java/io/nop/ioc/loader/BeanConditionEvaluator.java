/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.ioc.loader;

import io.nop.api.core.config.AppConfig;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.ioc.IBeanContainer;
import io.nop.commons.lang.IClassLoader;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.ioc.impl.BeanDefinition;
import io.nop.ioc.model.BeanConditionModel;
import io.nop.ioc.model.BeanIfPropertyCondition;
import io.nop.ioc.model.BeanUnlessPropertyCondition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

import static io.nop.ioc.IocErrors.ARG_ALIAS;
import static io.nop.ioc.IocErrors.ARG_BEAN_NAME;
import static io.nop.ioc.IocErrors.ARG_LOC_A;
import static io.nop.ioc.IocErrors.ARG_LOC_B;
import static io.nop.ioc.IocErrors.ARG_TRACE;
import static io.nop.ioc.IocErrors.ERR_IOC_DUPLICATE_BEAN_DEFINITION;
import static io.nop.ioc.IocErrors.ERR_IOC_UNRESOLVED_ALIAS;

/**
 * 根据ioc:condition配置判断每个bean是否满足创建条件。所有满足创建条件的bean将被收集到enabledBeans集合中。 集合的key为bean的id或者name或者alias
 */
public class BeanConditionEvaluator {
    static final Logger LOG = LoggerFactory.getLogger(BeanConditionEvaluator.class);

    private final BeansDefinition beans;

    /**
     * 等待进行条件判断的bean
     */
    private final Set<BeanDefinition> candidateBeans = new HashSet<>();

    private final Map<String, Set<BeanDefinition>> candidateAliases = new HashMap<>();

    /**
     * 满足所有判断条件，确定需要创建的bean
     */
    private Map<String, BeanDefinition> enabledBeans;

    private Map<String, AliasName> aliases;

    private final IClassLoader classLoader;

    private final IBeanContainer parentContainer;

    public BeanConditionEvaluator(BeansDefinition beans, IClassLoader classLoader, IBeanContainer parentContainer) {
        this.beans = beans;
        this.classLoader = classLoader;
        this.parentContainer = parentContainer;
    }

    public void evaluate(Map<String, BeanDefinition> enabledBeans, Set<BeanDefinition> optionalBeans,
                         Map<String, AliasName> aliases) {
        this.enabledBeans = enabledBeans;
        this.aliases = aliases;

        for (BeanDefinition bean : beans.getBeans().values()) {
            addBean(bean);
        }

        aliases.putAll(beans.getAliases());
        processAlias();

        // class判断条件不会因为配置调整而改变
        processCandidates(this::checkStaticCondition);
        optionalBeans.addAll(candidateBeans);

        processCandidates(this::checkPropertyAndBeanCondition);
        // candidate有可能转变为enabled，这里重新检查alias
        processAlias();

        for (int i = 0; i < 5; i++) {
            if (!processCandidates(this::checkBeanCondition) && !this.processAlias()) {
                break;
            }
        }

        if (!aliases.isEmpty()) {
            for (Map.Entry<String, AliasName> entry : aliases.entrySet()) {
                AliasName aliasName = entry.getValue();
                if (parentContainer != null && parentContainer.containsBean(aliasName.getName()))
                    continue;
                throw new NopException(ERR_IOC_UNRESOLVED_ALIAS).source(aliasName)
                        .param(ARG_BEAN_NAME, aliasName.getName()).param(ARG_ALIAS, entry.getValue());
            }
        }

        if (!candidateBeans.isEmpty()) {
            for (BeanDefinition bean : this.candidateBeans) {
                LOG.warn("nop.ioc.candidate-check-fail:\nbean={},on-bean={},missing-bean={}", bean,
                        bean.getCondition().getOnBean(), bean.getCondition().getMissingBean());
                bean.getCondition().setDisabled(true);
            }
        }

        dumpConditional();
    }

    boolean processAlias() {
        boolean changed = false;

        Iterator<Map.Entry<String, AliasName>> it = aliases.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, AliasName> entry = it.next();
            String alias = entry.getKey();
            AliasName aliasName = entry.getValue();

            BeanDefinition bean = enabledBeans.get(aliasName.getName());
            if (bean != null) {
                addEnabled(alias, bean);
                it.remove();
                changed = true;
            }
        }

        return changed;
    }

    void addBean(BeanDefinition bean) {
        if (bean.getCondition() == null) {
            addEnabled(bean);
        } else {
            candidateBeans.add(bean);
            candidateAliases.computeIfAbsent(bean.getId(), k -> new HashSet<>()).add(bean);
            if (bean.getNames() != null) {
                for (String name : bean.getNames()) {
                    candidateAliases.computeIfAbsent(name, k -> new HashSet<>()).add(bean);
                }
            }
        }
    }

    void addEnabled(String beanName, BeanDefinition bean) {
        BeanDefinition old = enabledBeans.put(beanName, bean);
        if (old != null && old != bean)
            throw new NopException(ERR_IOC_DUPLICATE_BEAN_DEFINITION).source(bean).param(ARG_BEAN_NAME, beanName)
                    .param(ARG_LOC_A, old.getLocation()).param(ARG_LOC_B, bean.getLocation())
                    .param(ARG_TRACE, bean.getTrace());
    }

    void addEnabled(BeanDefinition bean) {
        addEnabled(bean.getId(), bean);

        if (bean.getNames() != null) {
            for (String name : bean.getNames()) {
                addEnabled(name, bean);
            }
        }
    }

    boolean processCandidates(Function<BeanDefinition, Boolean> predicate) {
        boolean changed = false;
        Iterator<BeanDefinition> it = candidateBeans.iterator();
        while (it.hasNext()) {
            BeanDefinition bean = it.next();
            Boolean b = predicate.apply(bean);
            if (b != null) {
                changed = true;
                it.remove();
                removeCandidate(bean);

                if (b) {
                    addEnabled(bean);
                } else {
                    bean.getCondition().setDisabled(true);
                }
            }
        }
        return changed;
    }

    Boolean checkStaticCondition(BeanDefinition bean) {
        BeanConditionModel conditionModel = bean.getCondition();
        if (conditionModel.getMissingClass() != null) {
            for (String className : conditionModel.getMissingClass()) {
                if (!isMissingClass(className))
                    return false;
            }
        }

        if (conditionModel.getOnClass() != null) {
            for (String className : conditionModel.getOnClass()) {
                if (isMissingClass(className))
                    return false;
            }
        }

        if (conditionModel.getMissingResource() != null) {
            for (String resource : conditionModel.getMissingResource()) {
                if (!isMissingResource(resource))
                    return false;
            }
        }

        if (conditionModel.getOnResource() != null) {
            for (String resource : conditionModel.getOnResource()) {
                if (isMissingResource(resource))
                    return false;
            }
        }

//        if (conditionModel.getOnExpr() != null) {
//
//        }

        // 如果需要检查property，则暂时返回null
        if (conditionModel.getIfProperty() != null)
            return null;

        if (conditionModel.getUnlessProperty() != null)
            return null;

        return checkBeanCondition(bean);
    }

    Boolean checkPropertyAndBeanCondition(BeanDefinition bean) {
        BeanConditionModel conditionModel = bean.getCondition();
        if (conditionModel.getIfProperty() != null) {
            BeanIfPropertyCondition ifProperty = conditionModel.getIfProperty();
            if (!checkProperty(ifProperty.getName(), ifProperty.getValue(),
                    ifProperty.isEnableIfMissing(), ifProperty.isEnableIfDebug())) {
                return false;
            }
        }

        if (conditionModel.getUnlessProperty() != null) {
            BeanIfPropertyCondition ifProperty = conditionModel.getIfProperty();
            if (checkProperty(ifProperty.getName(), ifProperty.getValue(),
                    ifProperty.isEnableIfMissing(), ifProperty.isEnableIfDebug())) {
                return false;
            }
        }

        return checkBeanCondition(bean);
    }

    Boolean checkBeanCondition(BeanDefinition bean) {
        BeanConditionModel conditionModel = bean.getCondition();
        boolean unknown = false;

        if (conditionModel.getOnBean() != null) {
            for (String name : conditionModel.getOnBean()) {
                if (!containsBean(name, bean)) {
                    if (!containsCandidate(name, bean))
                        return false;
                    unknown = true;
                }
            }
        }

        if (conditionModel.getMissingBean() != null) {
            for (String name : conditionModel.getMissingBean()) {
                if (containsBean(name, bean))
                    return false;
                if (containsCandidate(name, bean))
                    unknown = true;
            }
        }

        if (unknown)
            return null;
        return true;
    }

    boolean containsCandidate(String name, BeanDefinition excludeBean) {
        Set<BeanDefinition> beans = this.candidateAliases.get(name);
        if (beans == null || beans.isEmpty())
            return false;
        for (BeanDefinition candidate : beans) {
            if (candidate != excludeBean)
                return true;
        }
        return false;
    }

    boolean containsBean(String beanName, BeanDefinition excludeBean) {
        if (enabledBeans.containsKey(beanName))
            return true;

        AliasName alias = aliases.get(beanName);
        if (alias != null) {
            // 如果不是当前bean
            if (!alias.getName().equals(excludeBean.getId()))
                return true;
        }
        if (parentContainer != null)
            return parentContainer.containsBean(beanName);
        return false;
    }

    boolean isMissingResource(String resource) {
        return !VirtualFileSystem.instance().getResource(resource).exists();
    }

    boolean isMissingClass(String className) {
        try {
            classLoader.loadClass(className);
            return false;
        } catch (ClassNotFoundException e) {
            return true;
        }
    }

    boolean checkProperty(String name, String value, boolean enabledIfMissing, boolean enableIfDebug) {
        String var = ConvertHelper.toString(AppConfig.var(name));
        if (var == null) {
            var = "";
        }


        if (StringHelper.isEmpty(var)) {
            if (enabledIfMissing)
                return true;
            if (enableIfDebug)
                return AppConfig.isDebugMode();
            return false;
        }

        if (StringHelper.isEmpty(value))
            value = "true";

        return var.equals(value);
    }

    void removeCandidate(BeanDefinition bean) {
        Set<BeanDefinition> set = candidateAliases.get(bean.getId());
        if (set != null) {
            set.remove(bean);
            if (set.isEmpty())
                candidateAliases.remove(bean.getId());
        }

        if (bean.getNames() != null) {
            for (String name : bean.getNames()) {
                set = candidateAliases.get(name);
                if (set != null) {
                    set.remove(bean);
                    if (set.isEmpty())
                        candidateAliases.remove(name);
                }
            }
        }
    }

    void dumpConditional() {
        if (!LOG.isDebugEnabled())
            return;

        TreeMap<String, BeanDefinition> sortedBeans = new TreeMap<>(beans.getBeans());
        dumpEnabled(sortedBeans);
        dumpDisabled(sortedBeans);
    }

    private void dumpEnabled(Map<String, BeanDefinition> sortedBeans) {
        StringBuilder sb = new StringBuilder();
        for (BeanDefinition bean : sortedBeans.values()) {
            if (!bean.isDisabled() && bean.getCondition() != null) {
                sb.append("enabled-bean:id=").append(bean.getId());
                sb.append("\n    ").append("loc=").append(bean.getLocation()).append(",trace=").append(bean.getTrace());
                BeanConditionModel condition = bean.getCondition();
                if (condition.getIfProperty() != null) {
                    BeanIfPropertyCondition ifProperty = condition.getIfProperty();
                    sb.append("\n    check-if-property:").append(ifProperty.getName()).append('=')
                            .append(ifProperty.getValue());
                }

                if (condition.getUnlessProperty() != null) {
                    BeanUnlessPropertyCondition unlessProperty = condition.getUnlessProperty();
                    sb.append("\n    check-unless-property:").append(unlessProperty.getName()).append('=')
                            .append(unlessProperty.getValue());
                }

                if (condition.getOnClass() != null) {
                    sb.append("\n    check-on-class:").append(condition.getOnClass());
                }

                if (condition.getMissingClass() != null) {
                    sb.append("\n    check-missing-class:").append(condition.getMissingClass());
                }

                if (condition.getOnBean() != null) {
                    for (String beanName : condition.getOnBean()) {
                        sb.append("\n    check-on-bean:").append(beanName);
                    }
                }

                if (condition.getMissingBean() != null) {
                    for (String beanName : condition.getMissingBean()) {
                        sb.append("\n    check-missing-bean:").append(beanName);
                    }
                }
                sb.append("\n\n");
            }
        }

        if (sb.length() > 0) {
            LOG.debug("nop.ioc.enabled-beans:\n{}", sb);
        }
    }

    private void dumpDisabled(Map<String, BeanDefinition> sortedBeans) {
        StringBuilder sb = new StringBuilder();
        for (BeanDefinition bean : sortedBeans.values()) {
            if (bean.isDisabled()) {
                sb.append("disabled-bean:id=").append(bean.getId());
                sb.append("\n    ").append("loc=").append(bean.getLocation()).append(",trace=").append(bean.getTrace());
                BeanConditionModel condition = bean.getCondition();
                if (condition.getIfProperty() != null) {
                    BeanIfPropertyCondition ifProperty = condition.getIfProperty();
                    if (!checkProperty(ifProperty.getName(), ifProperty.getValue(),
                            ifProperty.isEnableIfMissing(), ifProperty.isEnableIfDebug())) {
                        sb.append("\n    check-if-property-fail:").append(ifProperty.getName()).append('=')
                                .append(ifProperty.getValue());
                    }
                }

                if (condition.getUnlessProperty() != null) {
                    BeanUnlessPropertyCondition unlessProperty = condition.getUnlessProperty();
                    if (checkProperty(unlessProperty.getName(), unlessProperty.getValue(),
                            unlessProperty.isEnableIfMissing(), unlessProperty.isEnableIfDebug())) {
                        sb.append("\n    check-unless-property-fail:").append(unlessProperty.getName()).append('=')
                                .append(unlessProperty.getValue());
                    }
                }

                if (condition.getOnClass() != null) {
                    for (String className : condition.getOnClass()) {
                        if (isMissingClass(className)) {
                            sb.append("\n    check-on-class-fail:").append(className);
                            break;
                        }
                    }
                }

                if (condition.getMissingClass() != null) {
                    for (String className : condition.getOnClass()) {
                        if (!isMissingClass(className)) {
                            sb.append("\n    check-missing-class-fail:").append(className);
                            break;
                        }
                    }
                }

                if (condition.getOnBean() != null) {
                    for (String beanName : condition.getOnBean()) {
                        if (!enabledBeans.containsKey(beanName)) {
                            sb.append("\n    check-on-bean-fail:").append(beanName);
                        }
                    }
                }

                if (condition.getMissingBean() != null) {
                    for (String beanName : condition.getMissingBean()) {
                        if (enabledBeans.containsKey(beanName)) {
                            sb.append("\n    check-missing-bean-fail:").append(beanName).append(",existing=").append(enabledBeans.get(beanName).getLocation());
                        }
                    }
                }
                sb.append("\n\n");
            }
        }

        if (sb.length() > 0) {
            LOG.debug("nop.ioc.disabled-beans:\n{}", sb);
        }
    }
}