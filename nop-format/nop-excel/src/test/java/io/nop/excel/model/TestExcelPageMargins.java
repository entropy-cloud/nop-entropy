package io.nop.excel.model;

import io.nop.excel.model.constants.ExcelModelConstants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 页边距模型的缺省值与英寸/磅双向换算语义（1 英寸 = 72 磅）。
 */
public class TestExcelPageMargins {

    // 未设置时 getXXXWithDefault 返回 Excel 缺省页边距（1 英寸）/页眉页脚（0.5 英寸）
    @Test
    public void testDefaultMargins() {
        ExcelPageMargins margins = new ExcelPageMargins();
        assertEquals(ExcelModelConstants.DEFAULT_MARGIN, margins.getLeftWithDefault(), 1e-9);
        assertEquals(ExcelModelConstants.DEFAULT_MARGIN, margins.getTopWithDefault(), 1e-9);
        assertEquals(ExcelModelConstants.DEFAULT_MARGIN, margins.getRightWithDefault(), 1e-9);
        assertEquals(ExcelModelConstants.DEFAULT_MARGIN, margins.getBottomWithDefault(), 1e-9);
        assertEquals(ExcelModelConstants.DEFAULT_HEADER_FOOTER, margins.getHeaderWithDefault(), 1e-9);
        assertEquals(ExcelModelConstants.DEFAULT_HEADER_FOOTER, margins.getFooterWithDefault(), 1e-9);
    }

    // setXXXInches / getXXXInches 是磅值的英寸视图，roundtrip 恒等；null 透传
    @Test
    public void testInchesRoundtripAndNullPassthrough() {
        ExcelPageMargins margins = new ExcelPageMargins();
        margins.setLeftInches(1.0);
        assertEquals(72.0, margins.getLeft(), 1e-9);
        assertEquals(1.0, margins.getLeftInches(), 1e-9);

        margins.setTopInches(0.5);
        assertEquals(36.0, margins.getTop(), 1e-9);

        margins.setRightInches(1.25);
        margins.setBottomInches(2.0);
        margins.setHeaderInches(0.75);
        margins.setFooterInches(0.25);
        assertEquals(1.25, margins.getRightInches(), 1e-9);
        assertEquals(2.0, margins.getBottomInches(), 1e-9);
        assertEquals(0.75, margins.getHeaderInches(), 1e-9);
        assertEquals(0.25, margins.getFooterInches(), 1e-9);

        // null 直接置空，get 返回 null 而不是缺省值
        margins.setLeftInches(null);
        assertNull(margins.getLeft());
        assertNull(margins.getLeftInches());
    }
}
