/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.report.core.build;

import io.nop.core.initialize.CoreInitialization;
import io.nop.excel.model.ExcelImage;
import io.nop.excel.model.ExcelSheet;
import io.nop.report.core.XptConstants;
import io.nop.xlang.api.XLang;
import io.nop.xlang.xdsl.json.DslXNodeToJsonTransformer;
import io.nop.xlang.xmeta.SchemaLoader;
import io.nop.xlang.xdef.IXDefinition;
import io.nop.xlang.xdef.IXDefNode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 图片扩展配置解析不应因单个图片缺少"----"分隔符而中断后续图片的解析。
 */
public class TestImageConfigParseLoop {
    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testContinueParsingImagesWithoutMarker() throws Exception {
        IXDefinition xptXDef = SchemaLoader.loadXDefinition(XptConstants.XDSL_SCHEMA_WORKBOOK);
        IXDefNode imageNode = xptXDef.getXdefDefine(XptConstants.XDEF_NODE_EXCEL_IMAGE);
        DslXNodeToJsonTransformer transformer = new DslXNodeToJsonTransformer(false, xptXDef,
                XLang.newCompileTool().allowUnregisteredScopeVar(true));

        ExcelSheet sheet = new ExcelSheet();
        ExcelImage noMarker = new ExcelImage();
        noMarker.setName("img1");
        noMarker.setDescription("plain image without config");
        ExcelImage withConfig = new ExcelImage();
        withConfig.setName("img2");
        withConfig.setDescription("photo\n----\ntestExpr=1>0");
        sheet.setImages(Arrays.asList(noMarker, withConfig));

        Method method = ExcelToXptModelTransformer.class.getDeclaredMethod("parseImageModel",
                ExcelSheet.class, IXDefNode.class, DslXNodeToJsonTransformer.class);
        method.setAccessible(true);
        method.invoke(ExcelToXptModelTransformer.INSTANCE, sheet, imageNode, transformer);

        // 无分隔符的图片应被跳过，后续图片的testExpr必须仍然被解析
        assertNotNull(withConfig.getTestExpr());
    }
}
