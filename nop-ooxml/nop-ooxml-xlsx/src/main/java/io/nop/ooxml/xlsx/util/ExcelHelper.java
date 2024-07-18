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
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.IResource;
import io.nop.core.resource.impl.FileResource;
import io.nop.core.resource.impl.UnknownResource;
import io.nop.core.resource.record.csv.CsvResourceRecordIO;
import io.nop.core.resource.record.list.HeaderListRecordOutput;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.ooxml.xlsx.imp.XlsxObjectLoader;
import io.nop.ooxml.xlsx.output.ExcelTemplate;
import io.nop.ooxml.xlsx.parse.ExcelWorkbookParser;
import io.nop.ooxml.xlsx.parse.XlsxToRecordOutput;
import io.nop.xlang.api.XLang;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ExcelHelper {
    public static ExcelWorkbook parseExcel(IResource resource) {
        return new ExcelWorkbookParser().parseFromResource(resource);
    }

    public static void saveExcel(IResource resource, ExcelWorkbook workbook) {
        new ExcelTemplate(workbook, null).generateToResource(resource, XLang.newEvalScope());
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

    public static List<ExcelSheetData> readAllSheets(IResource xlsx) {
        List<ExcelSheetData> ret = new ArrayList<>();

        new XlsxToRecordOutput((r, e) -> new HeaderListRecordOutput<>(xlsx, null, CollectionHelper::toMap) {
            @Override
            public void close() {
                ExcelSheetData data = new ExcelSheetData();
                data.setName(r.getName());
                data.setData(this.getResult());
                ret.add(data);
            }
        },
                null, sheetName -> {
            return new UnknownResource("/" + sheetName);
        }).parseFromResource(xlsx);
        return ret;
    }

    public static List<Map<String, Object>> readSheet(IResource xlsx, String selectedSheetName) {
        HeaderListRecordOutput<Map<String, Object>> output = new HeaderListRecordOutput<>(xlsx, null, CollectionHelper::toMap);

        MutableInt index = new MutableInt();
        new XlsxToRecordOutput((r, e) -> output, null, sheetName -> {
            if (selectedSheetName == null) {
                if (index.get() > 0)
                    return null;
                index.incrementAndGet();
                return xlsx;
            } else {
                if (selectedSheetName.equals(sheetName)) {
                    return xlsx;
                }
                return null;
            }
        }).parseFromResource(xlsx);

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
        new XlsxToRecordOutput(recordIO, encoding, sheetName -> {
            String outputName;
            if (multiSheet) {
                outputName = baseName + "-" + StringHelper.safeFileName(sheetName) + (fileExt.isEmpty() ? ".csv" : fileExt);
            } else {
                if (index.get() > 0)
                    return null;
                outputName = baseName + (fileExt.isEmpty() ? ".csv" : fileExt);
                index.incrementAndGet();
            }
            return new FileResource(new File(dir, outputName));
        }).parseFromResource(xlsx);
    }
}