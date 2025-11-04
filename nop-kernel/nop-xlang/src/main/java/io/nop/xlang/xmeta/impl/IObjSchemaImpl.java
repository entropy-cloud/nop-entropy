/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xmeta.impl;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalFunction;
import io.nop.core.type.IGenericType;
import io.nop.xlang.xmeta.IObjSchema;
import io.nop.xlang.xmeta.ISchema;

import java.util.List;

public interface IObjSchemaImpl extends IObjSchema {
    void setLocation(SourceLocation loc);

    void setAbstract(Boolean value);

    void setInterface(Boolean value);

    void setExtendsType(IGenericType type);

    void setImplementsTypes(List<IGenericType> types);

    /**
     * 最少有多少个属性
     */
    void setMinProperties(Integer value);

    /**
     * 最多有多少个属性
     */
    void setMaxProperties(Integer value);

    void setUniqueProp(String uniqueProp);

    void setProps(List<ObjPropMetaImpl> props);

    void setName(String name);

    void setDisplayName(String displayName);

    void setDescription(String description);

    /**
     * 对应程序语言中的泛型类型
     */
    void setType(IGenericType type);

    /**
     * 自定义的类型域名称，例如role, dept等，可以由程序自定义并负责解释。
     */
    void setDomain(String domain);

    /**
     * 在{@link io.nop.xlang.xdef.domain.StdDomainRegistry}中注册的系统内置类型域。解析数据时会按照指定规则进行类型转换
     */
    void setStdDomain(String stdDomain);

    /**
     * 扩展验证条件，xml格式
     *
     * @param validator
     */
    void setValidator(IEvalFunction validator);

    /**
     * 类型的基类，从基类会继承相关domain, type，props等属性。 因为根据schema会生成java类时ref会通过基类来实现，所以当前对象的属性与refSchema中的属性必须不冲突， 否则生成代码时会出现类型冲突。
     */
    void setRef(String ref);

    /**
     * 根据ref确定的schema对象
     */
    void setRefSchema(ISchema refSchema);

    /**
     * 是否已经根据ref属性加载了对应的schema对象，并和当前对象属性合并
     */
    void setRefResolved(Boolean resolved);

    void setSupportExtends(Boolean supportExtends);

    void setUnknownTagSchema(ISchema unknownTagSchema);

    void setUnknownAttrSchema(ISchema unknownAttrSchema);
}
