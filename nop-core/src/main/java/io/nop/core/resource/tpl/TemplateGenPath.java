/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.resource.tpl;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.util.Guard;
import io.nop.api.core.util.IVariableScope;
import io.nop.commons.collections.ListFunctions;
import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.reflect.bean.BeanTool;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static io.nop.core.CoreConstants.XGEN_MARK_IGNORE;

/**
 * 递归遍历模板目录时维护的生成路径堆栈
 */
public class TemplateGenPath {
    /**
     * 每个元素为目录名
     */
    private final List<String> tplPath = new ArrayList<>();

    /**
     * tplPath中变量经替换后得到的结果，如果为空则表示本层目录对应于开关变量，且开关打开，允许处理下级目录。 例如 /{webEnabled}/xxx
     * 当webEnabled返回为true时targetPath的实际对应部分为空字符串，而当webEnabled为false时， targetPath的对应部分为常量
     * XGEN_MARK_IGNORE，它表示该路径下的所有模板文件都被忽略，不需要进行处理。
     */
    private final List<String> targetPath = new ArrayList<>();

    public void push(String path) {
        Guard.notEmpty(path, "path");
        tplPath.add(path);
        targetPath.add(null);
    }

    public void pop(String name) {
        String prev = ListFunctions.pop(tplPath);
        if (!Objects.equals(prev, name))
            throw new IllegalStateException("nop.err.tpl.template-path-stack-mismatch:" + name);
        ListFunctions.pop(targetPath);
    }

    public String getTplPath() {
        return StringHelper.join(tplPath, "/");
    }

    public String getTargetPath() {
        String path = StringHelper.join(targetPath, "/", true);
        return StringHelper.normalizePath(path);
    }

    public List<String> getTopVarNames() {
        List<String> ret = new ArrayList<>();
        String top = tplPath.get(tplPath.size() - 1);
        StringHelper.forEachTemplateVar(top, name -> {
            String topName = StringHelper.firstPart(name, '.');
            if (topName.startsWith("!"))
                topName = topName.substring(1);
            if (topName.startsWith("!"))
                topName = topName.substring(1);
            ret.add(topName);
        });
        return ret;
    }

    /**
     * 根据模板路径生成目标路径。仅处理最后一级目录
     *
     * @param scope 变量域
     * @return 如果返回false, 则表示该目标路径应该被忽略
     */
    public boolean resolveTop(IVariableScope scope) {
        String top = CollectionHelper.last(tplPath);
        String resolved = StringHelper.renderTemplate(top, varName -> {
            if (varName.equals("false"))
                return XGEN_MARK_IGNORE;

            Object value;
            if (varName.startsWith("!")) {
                varName = varName.substring(1);
                if (varName.startsWith("!")) {
                    value = BeanTool.getValueByPath(scope, varName.substring(1));
                    value = ConvertHelper.toTruthy(value);
                } else {
                    value = BeanTool.getValueByPath(scope, varName);
                    value = ConvertHelper.toFalsy(value);
                }
            } else {
                value = BeanTool.getValueByPath(scope, varName);
            }

            // bool变量作为开关使用，false表示不生成目录下的内容，而true则表示生成
            if (Boolean.FALSE.equals(value)) {
                return XGEN_MARK_IGNORE;
            }
            if (Boolean.TRUE.equals(value))
                return "";
            return value;
        });

        targetPath.set(targetPath.size() - 1, resolved);

        if (resolved.indexOf(XGEN_MARK_IGNORE) >= 0)
            return false;

        return true;
    }
}