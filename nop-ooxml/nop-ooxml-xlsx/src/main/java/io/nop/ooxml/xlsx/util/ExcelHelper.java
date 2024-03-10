/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ooxml.xlsx.util;

import io.nop.core.lang.eval.DisabledEvalScope;
import io.nop.core.resource.IResource;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.ooxml.xlsx.imp.XlsxObjectLoader;
import io.nop.ooxml.xlsx.output.ExcelTemplate;
import io.nop.ooxml.xlsx.parse.ExcelWorkbookParser;

public class ExcelHelper {
    public static ExcelWorkbook parseExcel(IResource resource) {
        return new ExcelWorkbookParser().parseFromResource(resource);
    }

    public static void saveExcel(IResource resource, ExcelWorkbook workbook) {
        new ExcelTemplate(workbook, null).generateToResource(resource, DisabledEvalScope.INSTANCE);
    }

    /**
     * 根据imp模型定义解析Excel，返回领域对象
     *
     * @param impModelPath imp模型定义
     * @param resource     excel文件
     */
    public static Object loadXlsxObject(String impModelPath, IResource resource) {
        return new XlsxObjectLoader(impModelPath).parseFromResource(resource);
    }

}