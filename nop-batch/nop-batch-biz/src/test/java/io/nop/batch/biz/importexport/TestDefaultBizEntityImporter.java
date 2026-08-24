/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.biz.importexport;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 平台缺省导入器是占位实现，调用必须显式抛出"未实现"异常，
 * 不能返回null导致下游thenApply直接NPE且无从定位
 */
public class TestDefaultBizEntityImporter {

    @Test
    public void testImportFileThrowsNotImplemented() {
        DefaultBizEntityImporter importer = new DefaultBizEntityImporter();
        UnsupportedOperationException ex = assertThrows(UnsupportedOperationException.class,
                () -> importer.importFile(null, "TestBizObj", null, null));
        assertTrue(ex.getMessage().contains("not implemented"));
    }
}
