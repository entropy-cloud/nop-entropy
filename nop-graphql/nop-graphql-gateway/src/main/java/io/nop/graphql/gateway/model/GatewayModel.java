/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.graphql.gateway.model;

import io.nop.api.core.util.INeedInit;
import io.nop.graphql.gateway.model._gen._GatewayModel;
import io.nop.router.TriePathRouter;

public class GatewayModel extends _GatewayModel implements INeedInit {

    private TriePathRouter<GatewayRouteModel> router;

    public GatewayModel() {

    }

    @Override
    public void init() {
        router = new TriePathRouter<>();
        for (GatewayRouteModel route : getRoutes()) {
            for (GatewayOnPathModel onPath : route.getOnPaths()) {
                router.addPathPattern(onPath.getPath(), route);
            }
        }
    }

    public TriePathRouter<GatewayRouteModel> getRouter() {
        return router;
    }
}
