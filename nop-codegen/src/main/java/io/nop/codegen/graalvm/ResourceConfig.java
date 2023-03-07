/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.codegen.graalvm;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.commons.collections.KeyedList;

import java.util.List;

@DataBean
public class ResourceConfig {
    private KeyedList<BundleFile> bundles = KeyedList.emptyList();

    private KeyedList<ResourceFile> resources = KeyedList.emptyList();

    public List<BundleFile> getBundles() {
        return bundles;
    }

    public void setBundles(List<BundleFile> bundles) {
        this.bundles = KeyedList.fromList(bundles, BundleFile::getName);
    }

    public List<ResourceFile> getResources() {
        return resources;
    }

    public void setResources(List<ResourceFile> resources) {
        this.resources = KeyedList.fromList(resources, ResourceFile::getPattern);
    }
}
