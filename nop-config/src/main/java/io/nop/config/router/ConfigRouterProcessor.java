/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.config.router;

import io.nop.api.core.util.IVariableScope;
import io.nop.commons.util.StringHelper;
import io.nop.commons.util.objects.ValueWithLocation;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.model.query.FilterBeanEvaluator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 实现灰度配置发布
 */
public class ConfigRouterProcessor {

    /**
     * 识别nop.config.router配置变量，按照json格式将其解析到NopConfigRouter类型，然后执行router判断，查找到对应灰度配置项。
     * <p>
     * 例如需要对于版本2.0的应用使用某些配置，可以通过设置router来实现。
     *
     * <pre>
     *     nop.config.router= "{
     *       routes: [{
     *          condition: {
     *              $type: 'eq', name:'nop.application.version', value:'2.0'
     *          },
     *          routeName: 'app2'
     *     }]}"
     *
     *    nop.application.version=2.0
     *    nop.xxx = 1
     *    app2.nop.xxx = 2
     * </pre>
     *
     * @param vars 当前所有配置变量
     * @return 返回灰度配置匹配到的所有配置项。配置项名称已经去除routerName前缀。
     */
    public Map<String, ValueWithLocation> getRouteConfigVars(Map<String, ValueWithLocation> vars, String routerVar) {
        ValueWithLocation ref = vars.get(routerVar);
        if (ref == null)
            return null;

        String text = StringHelper.toString(ref.getValue(), null);
        if (StringHelper.isEmpty(text))
            return null;

        NopConfigRouter router = (NopConfigRouter) JsonTool.parseBeanFromText(text, NopConfigRouter.class);
        String routeName = chooseRoute(router, new VarScope(vars));
        if (StringHelper.isEmpty(routeName))
            return null;

        String prefix = routeName + '.';
        Map<String, ValueWithLocation> routeVars = new HashMap<>();
        for (Map.Entry<String, ValueWithLocation> entry : vars.entrySet()) {
            String name = entry.getKey();
            if (name.startsWith(prefix)) {
                routeVars.put(name.substring(prefix.length()), entry.getValue());
            }
        }
        return routeVars;
    }

    static class VarScope implements IVariableScope {
        private final Map<String, ValueWithLocation> vars;

        public VarScope(Map<String, ValueWithLocation> vars) {
            this.vars = vars;
        }

        @Override
        public boolean containsValue(String name) {
            return vars.containsValue(name);
        }

        @Override
        public Object getValue(String name) {
            ValueWithLocation ref = vars.get(name);
            return ref == null ? null : ref.getValue();
        }

        @Override
        public Object getValueByPropPath(String propPath) {
            return getValue(propPath);
        }
    }

    private String chooseRoute(NopConfigRouter router, IVariableScope scope) {
        List<NopConfigRoute> routes = router.getRoutes();
        if (routes == null || routes.isEmpty())
            return null;

        FilterBeanEvaluator evaluator = newConditionEvaluator();

        for (NopConfigRoute route : routes) {
            if (route.getCondition() == null)
                continue;

            if (Boolean.TRUE.equals(evaluator.visit(route.getCondition(), scope))) {
                return route.getRouteName();
            }
        }

        return null;
    }

    protected FilterBeanEvaluator newConditionEvaluator() {
        return new FilterBeanEvaluator();
    }

}
