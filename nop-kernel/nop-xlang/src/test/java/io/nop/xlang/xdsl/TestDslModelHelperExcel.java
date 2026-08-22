package io.nop.xlang.xdsl;

import io.nop.api.core.exceptions.NopException;
import org.junit.jupiter.api.Test;

import static io.nop.xlang.XLangErrors.ERR_XDSL_NOT_SUPPORT_EXCEL_MODEL_LOADER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * nop-xlang 测试环境不引入 nop-ooxml-xlsx，此时 newExcelModelLoader 应抛 NopException + ErrorCode，
 * 而不是 bare IllegalArgumentException
 */
public class TestDslModelHelperExcel {
    @Test
    public void testNewExcelModelLoaderThrowsNopExceptionWhenNotSupported() {
        if (DslModelHelper.supportExcelModelLoader())
            return; // 环境中存在Excel加载器实现时无法验证不支持分支

        NopException e = assertThrows(NopException.class, () -> DslModelHelper.newExcelModelLoader("/test/imp.xlsx"));
        assertEquals(ERR_XDSL_NOT_SUPPORT_EXCEL_MODEL_LOADER.getErrorCode(), e.getErrorCode());
    }
}
