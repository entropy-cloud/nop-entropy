/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.xmeta;

import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.beans.ITreeBean;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.beans.query.OrderFieldBean;
import io.nop.commons.lang.ITagSetSupport;
import io.nop.core.lang.xml.IXNodeGenerator;
import io.nop.core.lang.xml.XNode;
import io.nop.core.reflect.hook.IExtensibleObject;
import io.nop.xlang.xdsl.IXDslModel;
import io.nop.xlang.xmeta.impl.ObjKeyModel;
import io.nop.xlang.xmeta.impl.ObjSelectionMeta;
import io.nop.xlang.xmeta.impl.ObjTreeModel;

import java.util.List;
import java.util.Set;

public interface IObjMeta extends IXDslModel, IObjSchema, ITagSetSupport, IExtensibleObject {
    String getVersion();

    List<OrderFieldBean> getOrderBy();

    Set<String> getPrimaryKey();

    default IObjPropMeta getIdProp() {
        Set<String> pk = getPrimaryKey();
        if (pk == null || pk.isEmpty())
            return null;
        if (pk.size() == 1)
            return getProp(getPrimaryKey().iterator().next());
        return null;
    }

    ObjTreeModel getTree();

    default boolean isPrimaryKeyProp(String propName) {
        Set<String> keys = getPrimaryKey();
        if (keys == null)
            return false;
        return keys.contains(propName);
    }

    String getDisplayProp();

    Boolean getParseForHtml();

    Boolean getParseKeepComment();

    String getParserClass();

    String getClassName();

    String getEntityName();

    String getXmlName();

    ISchema getRootSchema();

    XNode getFilter();

    List<? extends ISchema> getDefines();

    ISchema getDefine(String name);

    /**
     * 在checkNs指定的名字空间的属性或子节点需要在元模型中定义。如果明确指定了名字空间需要校验，则即使标记了xdef:unknown-tag和xdef:unknown-attr也无法跳过校验。
     *
     * @return 名字空间列表
     */
    Set<String> getCheckNs();

    String getDefaultExtends();

    /**
     * 返回当前meta中定义的所有objSchema，包括defines段中定义的，以及内部嵌套定义的objSchema，以及objMeta对象自身。
     * 用于代码生成时，每一个objSchema对应一个java类定义，ref对应于基类的定义
     */
    List<IObjSchema> getDefinedObjSchemas();

    XNode toNode();

    ObjSelectionMeta getSelection(String selectionId);

    default FieldSelectionBean getFieldSelection(String selectionId) {
        ObjSelectionMeta selection = getSelection(selectionId);
        return selection == null ? null : selection.getMapping();
    }

    List<ObjKeyModel> getKeys();
}