/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.factory;

import io.nop.api.core.util.OrderedComparator;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.module.ModuleManager;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.orm.IOrmInterceptor;
import io.nop.orm.OrmConstants;
import io.nop.orm.interceptor.XplOrmInterceptor;
import io.nop.orm.model.interceptor.OrmInterceptorActionModel;
import io.nop.orm.model.interceptor.OrmInterceptorEntityModel;
import io.nop.orm.model.interceptor.OrmInterceptorModel;
import io.nop.xlang.xdsl.DslModelParser;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class XplOrmInterceptorFactoryBean {

    private XplOrmInterceptor interceptor;

    @PostConstruct
    public void init() {

        // event -> entityName -> actions
        Map<String, Map<String, List<OrmInterceptorActionModel>>> allActions = new HashMap<>();

        ModuleManager.instance().getEnabledModuleIds().forEach(moduleId -> {
            String path = "/" + moduleId + "/orm/app.orm-interceptor.xml";

            IResource resource = VirtualFileSystem.instance().getResource(path);
            if (resource.exists()) {
                OrmInterceptorModel interceptorModel = (OrmInterceptorModel) new DslModelParser(
                        OrmConstants.XDSL_SCHEMA_ORM_INTERCEPTOR).parseFromResource(resource);

                for (OrmInterceptorEntityModel entity : interceptorModel.getEntities()) {
                    String entityName = entity.getName();
                    for (OrmInterceptorActionModel action : entity.getActions()) {
                        Map<String, List<OrmInterceptorActionModel>> entityActions = allActions
                                .computeIfAbsent(action.getEvent(), k -> new HashMap<>());
                        entityActions.computeIfAbsent(entityName, k -> new ArrayList<>()).add(action);
                    }
                }
            }
        });

        interceptor = new XplOrmInterceptor();

        for (Map.Entry<String, Map<String, List<OrmInterceptorActionModel>>> mapEntry : allActions.entrySet()) {
            Map<String, List<OrmInterceptorActionModel>> map = mapEntry.getValue();

            Map<String, List<IEvalAction>> actions = new HashMap<>();
            for (Map.Entry<String, List<OrmInterceptorActionModel>> entry : map.entrySet()) {
                List<OrmInterceptorActionModel> list = entry.getValue();
                list.sort(OrderedComparator.instance());

                actions.put(entry.getKey(), list.stream().map(m -> m.getSource()).collect(Collectors.toList()));
            }
            interceptor.setActions(mapEntry.getKey(), actions);
        }
    }

    public IOrmInterceptor getObject() {
        return interceptor;
    }
}
