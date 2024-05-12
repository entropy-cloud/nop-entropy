package io.nop.graphql.core.utils;

import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.beans.FilterBeanConstants;
import io.nop.api.core.beans.graphql.GraphQLConnectionInput;
import io.nop.api.core.beans.query.OrderFieldBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.core.CoreConstants;
import io.nop.core.model.query.OrderBySqlParser;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.reflect.bean.IBeanModel;
import io.nop.graphql.core.GraphQLConstants;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GraphQLArgsHelper {
    public static void normalizeSubArgs(FieldSelectionBean selectionBean, Map<String, Object> args) {
        if (selectionBean == null)
            return;

        Map<String, Map<String, Object>> subArgs = new HashMap<>();
        Iterator<Map.Entry<String, Object>> it = args.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Object> entry = it.next();
            String name = entry.getKey();
            if (name.startsWith(GraphQLConstants.SUB_PARAMS_PREFIX)) {
                Object value = entry.getValue();
                int pos = name.lastIndexOf('.');
                String subName = name.substring(GraphQLConstants.SUB_PARAMS_PREFIX.length(), pos);
                String paramName = name.substring(pos + 1);
                subArgs.computeIfAbsent(subName, k -> new LinkedHashMap<>()).put(paramName, value);
                it.remove();
            }
        }

        if (!subArgs.isEmpty()) {
            subArgs.forEach((name, argMap) -> {
                if(name.endsWith(GraphQLConstants.POSTFIX_CONNECTION)){
                    argMap = normalizeConnectionArgs(argMap);
                }else {
                    argMap = normalizeQueryArgs(argMap);
                }
                selectionBean.makeSubField(name, true).setArgs(argMap);
            });
        }
    }

    public static Map<String, Object> normalizeQueryArgs(Map<String, Object> args) {
        if (isNormalized(args))
            return args;

        IBeanModel beanModel = ReflectionManager.instance().getBeanModelForClass(QueryBean.class);

        Map<String, Object> ret = new LinkedHashMap<>();

        Map<String, Object> query = (Map<String, Object>) args.get(GraphQLConstants.ARG_QUERY);
        if (query == null) {
            query = new LinkedHashMap<>();
        } else {
            query = new LinkedHashMap<>(query);
        }

        List<Map<String, Object>> filters = new ArrayList<>();

        for (Map.Entry<String, Object> entry : args.entrySet()) {
            String name = entry.getKey();
            if (name.equals(GraphQLConstants.QUERY_ORDER_BY_KEY)) {
                List<OrderFieldBean> orderBy = OrderBySqlParser.INSTANCE.parseFromText(null, ConvertHelper.toString(entry.getValue()));
                query.put(GraphQLConstants.PROP_ORDER_BY, orderBy);
                continue;
            }
            if (name.startsWith("_"))
                continue;

            if (beanModel.getPropertyModel(name) != null) {
                query.put(name, entry.getValue());
            } else if (name.startsWith(GraphQLConstants.FILTER_PREFIX)) {
                Map<String, Object> filter = GraphQLArgsHelper.getFilterMap(name, entry.getValue());
                filters.add(filter);
            } else {
                ret.put(name, entry.getValue());
            }
        }

        if (!filters.isEmpty()) {
            Map<String, Object> filter = new LinkedHashMap<>();
            filter.put(CoreConstants.XML_PROP_TYPE, FilterBeanConstants.FILTER_OP_AND);
            filter.put(CoreConstants.XML_PROP_BODY, filters);
            query.put(GraphQLConstants.PROP_FILTER, filter);
        }

        ret.put(GraphQLConstants.ARG_QUERY, query);
        return ret;
    }

    public static Map<String, Object> normalizeConnectionArgs(Map<String, Object> args) {
        if (isNormalized(args))
            return args;

        IBeanModel beanModel = ReflectionManager.instance().getBeanModelForClass(GraphQLConnectionInput.class);

        Map<String, Object> ret = new LinkedHashMap<>();

        List<Map<String, Object>> filters = new ArrayList<>();

        for (Map.Entry<String, Object> entry : args.entrySet()) {
            String name = entry.getKey();
            if (name.equals(GraphQLConstants.QUERY_ORDER_BY_KEY)) {
                List<OrderFieldBean> orderBy = OrderBySqlParser.INSTANCE.parseFromText(null, ConvertHelper.toString(entry.getValue()));
                ret.put(GraphQLConstants.PROP_ORDER_BY, orderBy);
                continue;
            }
            if (name.startsWith("_"))
                continue;

            if (beanModel.getPropertyModel(name) != null) {
                ret.put(name, entry.getValue());
            } else if (name.startsWith(GraphQLConstants.FILTER_PREFIX)) {
                Map<String, Object> filter = GraphQLArgsHelper.getFilterMap(name, entry.getValue());
                filters.add(filter);
            } else {
                ret.put(name, entry.getValue());
            }
        }

        if (!filters.isEmpty()) {
            Map<String, Object> filter = new LinkedHashMap<>();
            filter.put(CoreConstants.XML_PROP_TYPE, FilterBeanConstants.FILTER_OP_AND);
            filter.put(CoreConstants.XML_PROP_BODY, filters);
            ret.put(GraphQLConstants.PROP_FILTER, filter);
        }

        return ret;
    }

    private static boolean isNormalized(Map<String, Object> args) {
        if (args.isEmpty())
            return true;

        if (args.size() == 1 && args.containsKey(GraphQLConstants.ARG_QUERY))
            return true;

        for (String name : args.keySet()) {
            if (!name.equals(GraphQLConstants.ARG_QUERY) && !name.startsWith(GraphQLConstants.V_PREFIX))
                return false;
        }
        return true;
    }

    public static Map<String, Object> getFilterMap(String name, Object value) {
        String filterName = name.substring(GraphQLConstants.FILTER_PREFIX.length());
        String op = FilterBeanConstants.FILTER_OP_EQ;
        int pos = filterName.indexOf("__");
        if (pos > 0) {
            op = filterName.substring(pos + 2);
            filterName = filterName.substring(0, pos);
        }
        if (GraphQLConstants.SPECIAL_VALUE_EMPTY.equals(value)) {
            value = "";
        } else if (GraphQLConstants.SPECIAL_VALUE_NULL.equals(value)) {
            value = null;
        }
        Map<String, Object> filter = new LinkedHashMap<>();
        filter.put(CoreConstants.XML_PROP_TYPE, op);
        filter.put(CoreConstants.ATTR_NAME, filterName);
        filter.put(CoreConstants.ATTR_VALUE, value);
        return filter;
    }

}
