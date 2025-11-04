/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.codegen.graalvm;

import io.nop.api.core.annotations.data.DataBean;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@DataBean
public class ProxyConfig {
    private Set<List<String>> proxyClasses;

    public Set<List<String>> getProxyClasses() {
        if (proxyClasses == null)
            proxyClasses = new LinkedHashSet<>();
        return proxyClasses;
    }

    public void setProxyClasses(Set<List<String>> proxyClasses) {
        this.proxyClasses = proxyClasses;
    }

    public void sort() {
        if (proxyClasses == null)
            proxyClasses = new LinkedHashSet<>();

        List<List<String>> list = new ArrayList<>(proxyClasses);
        list.sort(this::compareList);
        this.proxyClasses = new LinkedHashSet<>(list);
    }

    int compareList(List<String> listA, List<String> listB) {
        if (listA.size() < listB.size())
            return -1;

        if (listA.size() > listB.size())
            return 1;

        for (int i = 0, n = listA.size(); i < n; i++) {
            int cmp = listA.get(i).compareTo(listB.get(i));
            if (cmp != 0)
                return cmp;
        }
        return 0;
    }

    public void remove(ProxyConfig config) {
        if (proxyClasses == null)
            return;

        if (config.proxyClasses == null)
            return;

        this.proxyClasses.removeAll(config.proxyClasses);
    }

    public void merge(ProxyConfig config) {
        if (this.proxyClasses == null)
            this.proxyClasses = new LinkedHashSet<>();

        if (config.proxyClasses == null)
            return;

        this.proxyClasses.addAll(config.proxyClasses);
    }
}
