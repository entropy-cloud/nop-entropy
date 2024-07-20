/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ioc.impl;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.collections.IntArray;
import io.nop.commons.collections.IntHashMap;
import io.nop.commons.collections.MapOfInt;
import io.nop.core.model.graph.DefaultDirectedGraph;
import io.nop.core.model.graph.DefaultEdge;
import io.nop.core.model.graph.TopologicalOrderIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static io.nop.ioc.IocConfigs.CFG_IOC_BEAN_DEPENDS_GRAPH_ALLOW_CYCLE;
import static io.nop.ioc.IocErrors.ARG_BEAN_DEPENDS_CYCLE;
import static io.nop.ioc.IocErrors.ARG_BEAN_NAME;
import static io.nop.ioc.IocErrors.ARG_DEPEND;
import static io.nop.ioc.IocErrors.ARG_TRACE;
import static io.nop.ioc.IocErrors.ERR_IOC_BEAN_DEPENDS_GRAPH_CONTAINS_CYCLE;
import static io.nop.ioc.IocErrors.ERR_IOC_BEAN_DEPEND_ON_HIGH_ORDER_BEAN;

/**
 * 按照依赖关系对bean进行排序，创建bean的时候从前向后进行
 */
public class BeanTopologySorter {
    static final Logger LOG = LoggerFactory.getLogger(BeanTopologySorter.class);

    public static BeanTopologySorter INSTANCE = new BeanTopologySorter();

    public List<BeanDefinition> sort(Map<String, BeanDefinition> beans) {
        // 先按照名称排序
        TreeMap<String, BeanDefinition> map = new TreeMap<>();
        for (BeanDefinition bean : beans.values()) {
            // beans中可能存在多个名称指向同一个bean，只保留id
            map.put(bean.getId(), bean);
        }

        MapOfInt<List<BeanDefinition>> orderMap = new IntHashMap<>();
        for (BeanDefinition bean : map.values()) {
            if (bean.isAbstract() || bean.isDisabled())
                continue;

            orderMap.computeIfAbsent(bean.getBeanModel().getIocInitOrder(), k -> new ArrayList<>()).add(bean);
        }

        // 值越小优先级越高
        IntArray orders = orderMap.keySet().sort();
        List<BeanDefinition> ret = new ArrayList<>();

        Set<String> lowOrderIds = new HashSet<>();

        // 分层进行拓扑排序。bean只能依赖初始化顺序小于等于自己的bean。
        for (int k : orders) {
            List<BeanDefinition> ordered = sortBeans(orderMap.get(k), lowOrderIds, beans);
            ret.addAll(ordered);
        }

        return ret;
    }

    private List<BeanDefinition> sortBeans(List<BeanDefinition> beans, Set<String> lowOrderIds,
                                           Map<String, BeanDefinition> allBeans) {
        DefaultDirectedGraph<String, DefaultEdge<String>> graph = DefaultDirectedGraph.create();
        for (BeanDefinition bean : beans) {
            lowOrderIds.add(bean.getId());
            graph.addVertex(bean.getId());
        }

        for (BeanDefinition bean : beans) {
            Set<String> deps = new HashSet<>();
            if (bean.getBeanModel().getDependsOn() != null)
                deps.addAll(bean.getBeanModel().getDependsOn());

            bean.collectDepends(deps);

            for (String dep : deps) {
                if (bean.getId().equals(dep))
                    continue;

                dep = normalizeBeanId(dep, allBeans);
                // 如果是父容器中的定义的bean
                if (dep == null)
                    continue;

                if (!lowOrderIds.contains(dep)) {
                    throw new NopException(ERR_IOC_BEAN_DEPEND_ON_HIGH_ORDER_BEAN).source(bean)
                            .param(ARG_BEAN_NAME, bean.getId()).param(ARG_TRACE, bean.getTrace())
                            .param(ARG_DEPEND, dep);
                }

                graph.addEdge(dep, bean.getId());
            }
        }

        if (LOG.isTraceEnabled())
            LOG.trace(graph.toDot(node -> node, "beans"));

        boolean allowLoop = CFG_IOC_BEAN_DEPENDS_GRAPH_ALLOW_CYCLE.get();
        TopologicalOrderIterator<String> it = graph.topologicalOrderIterator(allowLoop);
        List<BeanDefinition> ret = new ArrayList<>();
        while (it.hasNext()) {
            String beanId = it.next();
            ret.add(allBeans.get(beanId));
        }

        if (!allowLoop && it.containsCycle()) {
            String cycle = it.displayOneCycle();
            LOG.info("nop.beans.depends-cycle:{}", cycle);
            throw new NopException(ERR_IOC_BEAN_DEPENDS_GRAPH_CONTAINS_CYCLE)
                    .param(ARG_BEAN_DEPENDS_CYCLE, cycle);
        }

        return ret;
    }

    String normalizeBeanId(String beanId, Map<String, BeanDefinition> allBeans) {
        BeanDefinition resolved = allBeans.get(beanId);
        if (resolved == null)
            return null;
        return resolved.getId();
    }
}