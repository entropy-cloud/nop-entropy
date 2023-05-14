/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.dataset;

/**
 * 对JDBC多结果集的封装
 */
public interface IComplexDataSet extends AutoCloseable {
    IDataSet getResultSet();

    long getUpdateCount();

    long getReadCount();

    boolean getMoreResults();

    boolean isResultSet();

    void setMaxRows(long maxRows);

    void setFetchSize(int fetchSize);

    void cancel();
}