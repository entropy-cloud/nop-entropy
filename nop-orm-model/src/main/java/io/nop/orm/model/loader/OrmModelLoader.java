/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.model.loader;

import io.nop.api.core.beans.DictBean;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.module.ModuleManager;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.orm.model.OrmDomainModel;
import io.nop.orm.model.OrmEntityModel;
import io.nop.orm.model.OrmModel;
import io.nop.orm.model.OrmModelConstants;
import io.nop.orm.model.OrmModelErrors;
import io.nop.xlang.xdsl.DslModelHelper;
import io.nop.xlang.xdsl.DslModelParser;

public class OrmModelLoader {
    static final SourceLocation merged_loc = SourceLocation.fromPath("/nop/main/orm/merged-app.orm.xml");

    private OrmModel loadFromResource(IResource resource, boolean ignoreUnknown) {
        OrmModel model = (OrmModel) new DslModelParser(OrmModelConstants.XDSL_SCHEMA_ORM).parseFromResource(resource,
                ignoreUnknown);
        return model;
    }

    public OrmModel loadOrmModel() {
        OrmModel model = new OrmModel();
        model.setLocation(merged_loc);
        model.setMerged(true);

        ModuleManager.instance().getAllModuleResources("orm/app.orm.xml").forEach(resource -> {
            OrmModel moduleModel = loadFromResource(resource,true);
            if (moduleModel != null) {
                merge(model, moduleModel, false);
            }
        });

        IResource mainResource = VirtualFileSystem.instance().getResource("/main/orm/app.orm.xml");
        OrmModel mainModel = loadFromResource(mainResource, true);
        if (mainModel != null) {
            merge(model, mainModel, true);
        }

        model.init();
        model.freeze(true);

        if (AppConfig.isDebugMode()) {
            String dumpPath = ResourceHelper.getDumpPath(merged_loc.getPath());
            IResource resource = VirtualFileSystem.instance().getResource(dumpPath);
            DslModelHelper.saveDslModel(OrmModelConstants.XDSL_SCHEMA_ORM, model, resource);
        }
        return model;
    }

    private void merge(OrmModel baseModel, OrmModel extModel, boolean replace) {
        for (OrmDomainModel domainModel : extModel.getDomains()) {
            baseModel.addDomain(domainModel);
        }

        for (DictBean dictBean : extModel.getDicts()) {
            baseModel.addDict(dictBean);
        }

        for (OrmEntityModel entityModel : extModel.getEntities()) {
            OrmEntityModel baseEntity = baseModel.getEntity(entityModel.getName());
            if (baseEntity == null) {
                baseModel.addEntity(entityModel);
            } else if (baseEntity.isNotGenCode()) {
                baseModel.addEntity(entityModel);
            } else {
                if (!entityModel.isNotGenCode()) {
                    if (replace) {
                        baseModel.addEntity(entityModel);
                    } else {
                        throw new NopException(OrmModelErrors.ERR_ORM_MODEL_ENTITY_NAME_CONFLICTED).source(entityModel)
                                .param(OrmModelErrors.ARG_OTHER_LOC, baseEntity.getLocation())
                                .param(OrmModelErrors.ARG_ENTITY_NAME, entityModel.getName());
                    }
                }
            }
        }
    }
}