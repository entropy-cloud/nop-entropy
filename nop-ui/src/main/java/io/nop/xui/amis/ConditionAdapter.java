/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xui.amis;

import io.nop.api.core.beans.TreeBean;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 实现amis的QueryBuilder结构与Nop平台内置的FilterBean结构之间的互相转换
 */
public class ConditionAdapter {

    public static Map<String, Object> treeToCondition(TreeBean tree) {
        Map<String, Object> cond = new LinkedHashMap<>();
        if ("and".equals(tree.getTagName()) || "or".equals(tree.getTagName()) || "not".equals(tree.getTagName())) {
            cond.put("conjunction", tree.getTagName());
            cond.put("children", transform(tree.getChildren(), ConditionAdapter::treeToCondition));
        } else {
            cond.put("op", tree.getTagName());
            Map<String, Object> left = new LinkedHashMap<>();
            left.put("type", "field");
            left.put("field", tree.getAttr("name"));
            cond.put("left", left);
            cond.put("right", tree.getAttr("value"));
        }
        return cond;
    }

    private static <R, T> List<R> transform(List<T> list, Function<T, R> fn) {
        if (list == null)
            return Collections.emptyList();
        return list.stream().map(fn).collect(Collectors.toList());
    }

    public static TreeBean conditionToTree(Map<String, Object> cond) {
        TreeBean tree = new TreeBean();
        String conjunction = (String) cond.get("conjunction");
        if (conjunction != null) {
            tree.setTagName(conjunction);
            List<Map<String, Object>> list = (List<Map<String, Object>>) cond.get("children");
            tree.setChildren(transform(list, ConditionAdapter::conditionToTree));
        } else {
            tree.setTagName((String) cond.get("op"));
            Map<String, Object> left = (Map<String, Object>) cond.get("left");
            tree.setAttr("name", left.get("field"));
            tree.setAttr("value", cond.get("right"));
        }
        return tree;
    }
}
