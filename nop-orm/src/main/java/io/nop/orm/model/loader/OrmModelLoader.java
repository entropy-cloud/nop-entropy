/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.model.loader;

import io.nop.api.core.beans.DictBean;
import io.nop.api.core.config.AppConfig;
import io.nop.core.module.ModuleManager;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.orm.OrmConstants;
import io.nop.orm.exceptions.OrmException;
import io.nop.orm.model.OrmDomainModel;
import io.nop.orm.model.OrmEntityModel;
import io.nop.orm.model.OrmModel;
import io.nop.xlang.xdsl.DslModelHelper;
import io.nop.xlang.xdsl.DslModelParser;

import static io.nop.orm.OrmErrors.ARG_ENTITY_NAME;
import static io.nop.orm.OrmErrors.ARG_OTHER_LOC;
import static io.nop.orm.OrmErrors.ERR_ORM_MODEL_ENTITY_NAME_CONFLICTED;

public class OrmModelLoader {

    public OrmModel loadFromResource(IResource resource, boolean ignoreUnknown) {
        OrmModel model = (OrmModel) new DslModelParser(OrmConstants.XDSL_SCHEMA_ORM).parseFromResource(resource,
                ignoreUnknown);
        return model;
    }

    public OrmModel loadOrmModel() {
        OrmModel model = new OrmModel();
        model.setMerged(true);

        ModuleManager.instance().getEnabledModuleIds().forEach(moduleId -> {
            OrmModel moduleModel = loadModuleOrmModel(moduleId);
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
            String dumpPath = ResourceHelper.getDumpPath("/nop/main/orm/merged-app.orm.xml");
            IResource resource = VirtualFileSystem.instance().getResource(dumpPath);
            DslModelHelper.saveDslModel(OrmConstants.XDSL_SCHEMA_ORM, model, resource);
        }
        return model;
    }

    private OrmModel loadModuleOrmModel(String moduleId) {
        String path = '/' + moduleId + "/orm/app.orm.xml";
        IResource resource = VirtualFileSystem.instance().getResource(path);
        return loadFromResource(resource, true);
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
                        throw new OrmException(ERR_ORM_MODEL_ENTITY_NAME_CONFLICTED).source(entityModel)
                                .param(ARG_OTHER_LOC, baseEntity.getLocation())
                                .param(ARG_ENTITY_NAME, entityModel.getName());
                    }
                }
            }
        }
    }
}