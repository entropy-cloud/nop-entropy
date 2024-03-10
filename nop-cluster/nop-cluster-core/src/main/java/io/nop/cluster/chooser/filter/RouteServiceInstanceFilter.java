/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.cluster.chooser.filter;

import com.vdurmont.semver4j.Requirement;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.util.ApiHeaders;
import io.nop.cluster.chooser.IRequestServiceInstanceFilter;
import io.nop.cluster.discovery.ServiceInstance;
import io.nop.commons.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * 通过ApiRequest的nop-svc-route这个header来传递路由条件，格式为 svcName:semVer,svcName:semVer。 semVer格式满足NPM语义版本号规则，例如^1.2表示1.2版本以上。
 * <p>
 * 只允许满足版本过滤规则的服务实例。
 */
public class RouteServiceInstanceFilter implements IRequestServiceInstanceFilter<ApiRequest<?>> {
    static final Logger LOG = LoggerFactory.getLogger(RouteServiceInstanceFilter.class);

    private boolean enabled = true;
    private Map<String, String> staticRoute;

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setStaticRouteConfig(String route) {
        this.staticRoute = ApiHeaders.parseRoute(route);
    }

    public void setStaticRoute(Map<String, String> route) {
        this.staticRoute = route;
    }

    /**
     * 判断服务实例的版本号是否满足路由要求
     */
    @Override
    public void filter(List<ServiceInstance> serviceInstances, ApiRequest<?> request, boolean onlyPreferred) {
        String serviceName = serviceInstances.get(0).getNormalizedServiceName();
        String svcRoute = getSvcRoute(serviceName, request);
        if (svcRoute == null)
            return;

        serviceInstances.removeIf(instance -> {
            String ver = instance.getMetadata(ServiceInstance.META_VERSION);
            if (StringHelper.isEmpty(ver))
                ver = "1.0.0";

            Requirement requirement = Requirement.buildNPM(svcRoute);
            boolean b = requirement.isSatisfiedBy(ver);

            LOG.info("nop.cluster.check-svc-route:route={},ver={},result={}", svcRoute, ver, b);
            return !b;
        });
    }

    private String getSvcRoute(String serviceName, ApiRequest<?> request) {
        Map<String, String> route = ApiHeaders.getSvcRoute(request);
        String svcRoute = null;
        if (route != null && !route.isEmpty())
            svcRoute = route.get(serviceName);

        if (StringHelper.isEmpty(svcRoute)) {
            if (staticRoute != null)
                svcRoute = staticRoute.get(serviceName);
        }
        return svcRoute;
    }
}
