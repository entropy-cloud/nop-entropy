/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xmeta;

import io.nop.api.core.util.IFreezable;
import io.nop.api.core.util.ISourceLocationGetter;
import io.nop.commons.type.StdDataType;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.eval.IEvalFunction;
import io.nop.core.lang.xml.XNode;
import io.nop.core.model.validator.ValidatorModel;
import io.nop.core.type.IGenericType;
import io.nop.xlang.xdef.IStdDomainOptions;
import io.nop.xlang.xdef.SchemaKind;
import io.nop.xlang.xdef.impl.XDefHelper;

import java.util.Map;

/**
 * 对应于类型定义
 */
public interface ISchemaNode extends ISourceLocationGetter, IFreezable {
    SchemaKind getSchemaKind();

    String getBizObjName();

    default boolean isObjSchema() {
        return getSchemaKind() == SchemaKind.OBJ;
    }

    default boolean isSimpleSchema() {
        return getSchemaKind() == SchemaKind.SIMPLE;
    }

    default boolean isListSchema() {
        return getSchemaKind() == SchemaKind.LIST;
    }

    default boolean isUnionSchema() {
        return getSchemaKind() == SchemaKind.UNION;
    }

    String getId();

    String getName();

    String getDisplayName();

    String getDescription();

    String getClassName();

    /**
     * 对应程序语言中的泛型类型
     */
    IGenericType getType();

    default StdDataType getStdDataType() {
        IGenericType type = getType();
        return type == null ? null : type.getStdDataType();
    }

    /**
     * 自定义的类型域名称，例如role, dept等，可以由程序自定义并负责解释。
     */
    String getDomain();

    /**
     * 在{@link io.nop.xlang.xdef.domain.StdDomainRegistry}中注册的系统内置类型域。解析数据时会按照指定规则进行类型转换
     */
    String getStdDomain();

    IStdDomainOptions getStdDomainOptionsObj();

    /**
     * 扩展验证条件，xml格式
     *
     * @return
     */
    IEvalFunction getValidator();

    /**
     * 类型的基类，从基类会继承相关domain, type，props等属性。 因为根据schema会生成java类时ref会通过基类来实现，所以当前对象的属性与refSchema中的属性必须不冲突， 否则生成代码时会出现类型冲突。
     */
    String getRef();

    /**
     * 如果ref指向局部引用，则返回局部引用的name，否则返回null
     */
    default String getLocalRef() {
        return XDefHelper.getLocalRef(getRef());
    }

    /**
     * 根据ref确定的schema对象
     */
    ISchema getRefSchema();

    /**
     * 是否已经根据ref属性加载了对应的schema对象，并和当前对象属性合并
     */
    boolean isRefResolved();

    void setRefResolved(Boolean refResolved);

    void setRefSchema(ISchema refSchema);

    XNode toNode(Map<ISchemaNode, XNode> nodeRefs);
}