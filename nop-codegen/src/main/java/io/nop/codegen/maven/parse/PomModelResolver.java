/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.codegen.maven.parse;

import io.nop.api.core.exceptions.NopException;
import io.nop.codegen.maven.model.PomArtifactKey;
import io.nop.codegen.maven.model.PomDependencyModel;
import io.nop.codegen.maven.model.PomModel;
import io.nop.codegen.maven.model.PomParentModel;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.impl.FileResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.nop.codegen.CodeGenErrors.ARG_ARTIFACT;
import static io.nop.codegen.CodeGenErrors.ERR_POM_REFERENCE_CONTAINS_LOOP;

public class PomModelResolver {
    static final Logger LOG = LoggerFactory.getLogger(PomModelResolver.class);

    Map<PomArtifactKey, PomModel> resolvedModels = new HashMap<>();
    Map<PomArtifactKey, PomModel> resolvingModels = new HashMap<>();

    public PomModel resolveModel(File resource) {
        LOG.info("resource.parse_pom:path={}", resource.getAbsolutePath());
        PomModel model = new PomModelParser().parseFromResource(new FileResource(resource));
        model.setPomFile(resource);
        PomModel resolved = resolvedModels.get(model.getArtifactKey());
        if (resolved != null)
            return resolved;
        return _resolve(model);
    }

    public Map<PomArtifactKey, PomModel> getResolvedModels() {
        return resolvedModels;
    }

    PomModel _resolve(PomModel model) {
        if (resolvingModels.containsKey(model.getArtifactKey()))
            throw new NopException(ERR_POM_REFERENCE_CONTAINS_LOOP).param(ARG_ARTIFACT, model.getArtifactKey());

        resolvingModels.put(model.getArtifactKey(), model);
        PomParentModel parent = model.getParent();
        if (parent != null) {
            PomModel parentModel = _resolveParent(model.getModuleDir(), parent);
            model = _mergeParent(model, parentModel);
        }
        resolvingModels.remove(model.getArtifactKey());
        resolvedModels.put(model.getArtifactKey(), model);

        if (model.getModules() != null) {
            List<PomModel> mods = new ArrayList<>();
            for (String module : model.getModules()) {
                File path = new File(model.getModuleDir(), module);
                File resource = new File(path, "pom.xml");
                PomModel modPom = resolveModel(resource);
                mods.add(modPom);
            }
            model.setResolvedModules(mods);
        }
        resolveProps(model);
        return model;
    }

    void resolveProps(PomModel model) {
        if (model.getProperties() == null || model.getProperties().isEmpty())
            return;

        for (Map.Entry<String, String> entry : model.getProperties().entrySet()) {
            Set<String> processing = new HashSet<>();
            String value = resolveValue(entry.getValue(), model, processing);
            entry.setValue(value);
        }

        for (PomDependencyModel dep : model.getDependencies().values()) {
            Set<String> processing = new HashSet<>();
            String value = resolveValue(dep.getVersion(), model, processing);
            dep.setVersion(value);
        }
    }

    String resolveValue(String value, PomModel model, Set<String> processing) {
        if (value == null)
            return null;

        if (value.indexOf("${") < 0)
            return value;

        value = StringHelper.renderTemplate(value, "${", "}", var -> {
            if (!processing.add(var))
                return var;

            String s = model.getProperty((String) var);
            return resolveValue(s, model, processing);
        });

        return value;
    }

    PomModel _resolveParent(File dir, PomParentModel parent) {
        PomModel model = resolvedModels.get(parent.getArtifactKey());
        if (model != null)
            return model;

        File path = new File(dir, parent.getRelativePath());
        File resource = new File(path, "pom.xml");
        LOG.debug("resource.resolve_parent:path={},artifact={}", path, parent.getArtifactKey());
        return resolveModel(resource);
    }

    PomModel _mergeParent(PomModel model, PomModel parentModel) {
        return PomModelMerger.instance().merge(model, parentModel);
    }
}