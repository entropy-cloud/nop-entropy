/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ooxml.xlsx.util;

import io.nop.commons.mutable.MutableInt;
import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.IoHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.IResource;
import io.nop.core.resource.impl.FileResource;
import io.nop.core.resource.record.csv.CsvResourceRecordIO;
import io.nop.core.resource.record.list.HeaderListRecordOutput;
import io.nop.excel.ExcelConstants;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.ooxml.xlsx.imp.XlsxObjectLoader;
import io.nop.ooxml.xlsx.output.ExcelTemplate;
import io.nop.ooxml.xlsx.parse.ExcelWorkbookParser;
import io.nop.ooxml.xlsx.parse.XlsxToRecordOutput;
import io.nop.xlang.api.XLang;
import io.nop.xlang.xdsl.DslModelHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

public class ExcelHelper {
    public static ExcelWorkbook parseExcel(IResource resource) {
        return new ExcelWorkbookParser().parseFromResource(resource);
    }

    public static void saveExcel(IResource resource, ExcelWorkbook workbook) {
        new ExcelTemplate(workbook, null).generateToResource(resource, XLang.newEvalScope());
    }

    public static XNode toWorkbookXmlNode(ExcelWorkbook wk) {
        return DslModelHelper.dslModelToXNode(ExcelConstants.XDSL_SCHEMA_WORKBOOK, wk);
    }

    /**
     * 根据imp模型定义解析Excel，返回领域对象
     *
     * @param impModelPath imp模型定义
     * @param resource     excel文件
     */
    public static Object loadXlsxObject(String impModelPath, IResource resource) {
        return new XlsxObjectLoader(impModelPath).loadObjectFromResource(resource);
    }

    public static Object loadXlsxObject(String impModelPath, IResource resource, IEvalScope scope) {
        return new XlsxObjectLoader(impModelPath).parseFromResource(resource, scope);
    }

    public static List<ExcelSheetData> readAllSheets(IResource xlsx) {
        List<ExcelSheetData> ret = new ArrayList<>();
        new XlsxToRecordOutput(sheetName -> new HeaderListRecordOutput<Map<String,Object>>(1, CollectionHelper::toNonEmptyKeyMap) {
            @Override
            public void close() {
                ExcelSheetData data = new ExcelSheetData();
                data.setName(sheetName);
                data.setHeaders(this.getHeaders());
                data.setHeaderLabels(this.getHeaderLabels());
                data.setData(this.getResult());
                ret.add(data);
            }
        }).parseFromResource(xlsx);
        return ret;
    }

    public static List<Map<String, Object>> readSheet(IResource xlsx, String selectedSheetName, int skipCount) {
        return readSheet(xlsx, selectedSheetName, skipCount, CollectionHelper::toNonEmptyKeyMap);
    }

    public static List<Map<String, Object>> readSheet(IResource xlsx, String selectedSheetName, int skipCount,
                                                      BiFunction<List<String>, List<Object>, Map<String, Object>> rowBuilder) {
        HeaderListRecordOutput<Map<String, Object>> output = new HeaderListRecordOutput<>(skipCount + 1, rowBuilder);

        XlsxToRecordOutput parser = new XlsxToRecordOutput(sheetName -> {
            return output;
        });

        try {
            parser.loadFromResource(xlsx);

            parser.parseSheet(selectedSheetName);
        } finally {
            IoHelper.safeCloseObject(parser);
        }

        return output.getResult();
    }

    public static void xlsxToCsv(IResource xlsx, File output, boolean multiSheet, String encoding) {
        CsvResourceRecordIO<List<Object>> recordIO = new CsvResourceRecordIO<>();
        recordIO.setRecordType(List.class);

        String fileName = output.getName();
        String fileExt = fileName.endsWith(".csv") ? ".csv" : (fileName.endsWith(".csv.gz") ? ".csv.gz" : "");
        String baseName = fileName.substring(0, fileName.length() - fileExt.length());

        File dir = output.getParentFile();

        MutableInt index = new MutableInt();
        new XlsxToRecordOutput(sheetName -> {
            String outputName;
            if (multiSheet) {
                outputName = baseName + "-" + StringHelper.safeFileName(sheetName) + (fileExt.isEmpty() ? ".csv" : fileExt);
            } else {
                if (index.get() > 0)
                    return null;
                outputName = baseName + (fileExt.isEmpty() ? ".csv" : fileExt);
                index.incrementAndGet();
            }
            return recordIO.openOutput(new FileResource(new File(dir, outputName)), encoding);
        }).parseFromResource(xlsx);
    }
}