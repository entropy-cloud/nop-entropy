/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.codegen.maven.parse;

import io.nop.codegen.maven.model.PomArtifactKey;
import io.nop.codegen.maven.model.PomDependencyModel;
import io.nop.codegen.maven.model.PomModel;
import io.nop.commons.util.CollectionHelper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PomModelMerger {
    private static final PomModelMerger _instance = new PomModelMerger();

    public static PomModelMerger instance() {
        return _instance;
    }

    private PomModelMerger() {
    }

    public PomModel merge(PomModel model, PomModel parentModel) {
        if (parentModel == null)
            return model;

        PomModel ret = new PomModel();
        ret.setArtifactKey(model.getArtifactKey());

        ret.setPomFile(model.getPomFile());
        ret.setVersion(model.getVersion());
        if (ret.getVersion() == null)
            ret.setVersion(parentModel.getVersion());

        ret.setPackaging(model.getPackaging());
        // 不应该使用父的packaging
        // if (ret.getPackaging() == null)
        // ret.setPackaging(parentModel.getPackaging());

        // Maven 语义中 modules 不从 parent 继承；把 parent 的 modules 并入 child 会导致
        // PomModelResolver 以 child pom 目录解析 parent 的 module 相对路径而报错
        ret.setModules(cloneList(model.getModules()));

        Map<String, String> props = new LinkedHashMap<>();
        CollectionHelper.mergeMap(props, parentModel.getProperties(), model.getProperties());

        ret.setProperties(props);

        Map<PomArtifactKey, PomDependencyModel> deps = new LinkedHashMap<>();
        deps.putAll(parentModel.getDependencies());

        for (PomDependencyModel dep : model.getDependencies().values()) {
            PomDependencyModel oldDep = deps.get(dep.getArtifactKey());
            if (oldDep != null) {
                deps.put(dep.getArtifactKey(), mergeDep(oldDep, dep));
            } else {
                // child 独有的依赖（不在 parent 中）也必须进入合并结果
                deps.put(dep.getArtifactKey(), dep);
            }
        }

        ret.addDependencies(deps.values());
        return ret;
    }

    PomDependencyModel mergeDep(PomDependencyModel dep1, PomDependencyModel dep2) {
        if (dep1 == null)
            return dep2;
        if (dep2 == null)
            return dep1;

        if (dep2.getVersion() == null)
            return dep1;
        return dep2;
    }

    <T> List<T> cloneList(List<T> list) {
        if (list == null)
            return null;
        return new ArrayList<>(list);
    }
}
