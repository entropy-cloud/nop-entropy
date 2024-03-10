/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xdef;

import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.xml.XNode;
import io.nop.xlang.xdsl.IXDslModel;

import java.util.List;
import java.util.Set;

public interface IXDefinition extends IXDefNode, IXDslModel {
    String getXdefVersion();

    Boolean getXdefParseForHtml();

    Boolean getXdefParseKeepComment();

    String getXdefParserClass();

    String getXdefBase();

    /**
     * 包含所有refSchema以及当前schema的路径。可以用于判断xdef文件是否从特定的基础xdef继承而来。
     *
     * @return
     */
    Set<String> getAllRefSchemas();

    default boolean isRefTo(String schemaPath) {
        return getAllRefSchemas().contains(schemaPath);
    }

    String getXdefBeanPackage();

    IXDefNode getRootNode();

    default IXDefNode getSubComponent(String name) {
        return getXdefDefine(name);
    }

    List<? extends IXDefNode> getXdefDefines();

    IXDefNode getXdefDefine(String name);

    /**
     * 在checkNs指定的名字空间的属性或子节点需要在元模型中定义。如果明确指定了名字空间需要校验，则即使标记了xdef:unknown-tag和xdef:unknown-attr也无法跳过校验。
     *
     * @return 名字空间列表
     */
    Set<String> getXdefCheckNs();

    Set<String> getXdefPropNs();

    String getXdefDefaultExtends();

    IEvalAction getXdefPostParse();

    /**
     * 根据defaultExtends路径装载得到的缺省DSL节点。
     *
     * @return 如果defaultExtend为空，或者路径对应的文件不存在，则返回空
     */
    XNode getDefaultExtendsNode();

    XDefKeys getDefKeys();

    XNode toNode();

    /**
     * 是否是resolveAllRef返回的结果
     */
    boolean isRefResolved();
}