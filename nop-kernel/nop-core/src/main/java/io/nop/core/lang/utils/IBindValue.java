/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.lang.utils;

/**
 * vue组件的v-bind:xx="a"语法解析得到的结果为 xx=VBindValue("a")
 */
public interface IBindValue {
    /**
     * 将部分信息编码到key中输出
     */
    String getBindKey(String key);

    Object getBindValue();
}