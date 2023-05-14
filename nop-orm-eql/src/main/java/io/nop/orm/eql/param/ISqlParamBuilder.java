/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.eql.param;

import java.util.List;

public interface ISqlParamBuilder {
    /**
     * 从输入的参数集合中读取某个参数，然后按照实体模型进行分析，如果是涉及到多字段的组件对象或者关联实体对象，则拆解为多个字段参数。
     */
    void buildParams(List<Object> input, List<Object> params);
}