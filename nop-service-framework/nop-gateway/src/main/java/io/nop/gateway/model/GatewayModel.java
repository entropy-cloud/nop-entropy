/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.gateway.model;

import io.nop.api.core.util.INeedInit;
import io.nop.gateway.model._gen._GatewayModel;
import io.nop.router.TriePathRouter;

public class GatewayModel extends _GatewayModel implements INeedInit {

    private TriePathRouter<GatewayRouteModel> router;
    private TriePathRouter<GatewayInterceptorModel> interceptorRouter;

    public GatewayModel() {

    }

    @Override
    public void init() {
        router = new TriePathRouter<>();
        if (getRoutes() != null) {
            for (GatewayRouteModel route : getRoutes()) {
                GatewayMatchModel match = route.getMatch();
                if (match != null && match.getPath() != null) {
                    router.addMultiPathPattern(match.getPath(), route);
                } else {
                    router.addMatchAll(route);
                }
            }
        }

        interceptorRouter = new TriePathRouter<>();

        if (getInterceptors() != null) {
            for (GatewayInterceptorModel interceptorModel : getInterceptors()) {
                GatewayMatchModel match = interceptorModel.getMatch();
                if (match != null && match.getPath() != null) {
                    interceptorRouter.addMultiPathPattern(match.getPath(), interceptorModel);
                } else {
                    interceptorRouter.addMatchAll(interceptorModel);
                }
            }
        }
    }

    public TriePathRouter<GatewayRouteModel> getRouter() {
        return router;
    }

    public TriePathRouter<GatewayInterceptorModel> getInterceptorRouter() {
        return interceptorRouter;
    }
}