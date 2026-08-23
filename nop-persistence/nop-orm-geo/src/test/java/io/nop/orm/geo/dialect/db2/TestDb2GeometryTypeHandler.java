/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.geo.dialect.db2;

import io.nop.dataset.binder.IDataParameters;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class TestDb2GeometryTypeHandler {

    @Test
    public void testSetValueNullWritesSetNull() {
        IDataParameters params = mock(IDataParameters.class);
        new Db2GeometryTypeHandler().setValue(params, 1, null);

        // 修复前 null 直接进 Db2ClobEncoder.encode，写空几何值时抛底层异常
        verify(params).setNull(1);
        verify(params, never()).setObject(anyInt(), anyString());
    }
}
