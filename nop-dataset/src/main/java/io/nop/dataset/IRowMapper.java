/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.dataset;

/**
 * 将数据行映射为对象
 *
 * @param <T>
 */
public interface IRowMapper<T> {
    /**
     * 根据dataRow构造返回对象
     *
     * @param row       行对象
     * @param rowNumber 行号，从1开始
     * @return 根据DataRow构造的结果对象
     */
    T mapRow(IDataRow row, long rowNumber, IFieldMapper colMapper);
}