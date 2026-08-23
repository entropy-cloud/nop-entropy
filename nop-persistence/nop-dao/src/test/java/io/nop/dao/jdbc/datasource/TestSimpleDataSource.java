/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.jdbc.datasource;

import io.nop.api.core.exceptions.NopException;
import org.junit.jupiter.api.Test;

import static io.nop.dao.DaoErrors.ERR_DAO_MISSING_DRIVER_CLASS_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * driverClassName漏配时应抛出带错误码的NopException，而不是不带上下文的裸NPE
 */
public class TestSimpleDataSource {

    @Test
    public void testNullDriverClassNameFailsWithErrorCode() {
        SimpleDataSource ds = new SimpleDataSource();
        NopException e = assertThrows(NopException.class, () -> ds.setDriverClassName(null));
        assertEquals(ERR_DAO_MISSING_DRIVER_CLASS_NAME.getErrorCode(), e.getErrorCode());
    }

    @Test
    public void testBlankDriverClassNameFailsWithErrorCode() {
        SimpleDataSource ds = new SimpleDataSource();
        assertThrows(NopException.class, () -> ds.setDriverClassName("  "));
    }

    @Test
    public void testValidDriverClassNameIsTrimmedAndLoaded() {
        SimpleDataSource ds = new SimpleDataSource();
        ds.setDriverClassName(" org.h2.Driver ");
        assertEquals("org.h2.Driver", ds.getDriverClassName());
    }
}
