/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.report.core.build;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.excel.ExcelConstants;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.ooxml.xlsx.parse.ExcelWorkbookParser;
import io.nop.report.core.XptConstants;
import io.nop.xlang.api.XLang;
import io.nop.xlang.api.XLangCompileTool;
import io.nop.xlang.xdsl.DslModelHelper;
import io.nop.xlang.xdsl.DslModelParser;

import static io.nop.report.core.XptConstants.ALLOWED_XPT_FILE_TYPES;
import static io.nop.report.core.XptErrors.ARG_ALLOWED_FILE_TYPES;
import static io.nop.report.core.XptErrors.ARG_FILE_TYPE;
import static io.nop.report.core.XptErrors.ERR_XPT_UNSUPPORTED_XPT_FILE_TYPE;

/**
 * 加载xml格式或者xlsx格式的模型文件。对应文件名规则为xxx.xpt.xml或者xxx.xpt.xlsx
 */
public class XptModelLoader {
    static XptModelLoader _instance = new XptModelLoader();

    public static XptModelLoader instance() {
        return _instance;
    }

    public ExcelWorkbook parseFromVirtualPath(String reportPath) {
        IResource resource = VirtualFileSystem.instance().getResource(reportPath);
        return loadModelFromResource(resource);
    }

    public ExcelWorkbook loadModelFromResource(IResource resource) {
        String fileType = StringHelper.fileType(resource.getPath());
        ExcelWorkbook workbook;
        if (XptConstants.FILE_TYPE_XPT_XLSX.equals(fileType)) {
            workbook = new ExcelWorkbookParser().parseFromResource(resource);
            new ExcelToXptModelTransformer().transform(workbook);
        } else if (XptConstants.FILE_TYPE_XPT_XML.equals(fileType)) {
            workbook = (ExcelWorkbook) new DslModelParser().parseFromResource(resource);
        } else {
            throw new NopException(ERR_XPT_UNSUPPORTED_XPT_FILE_TYPE)
                    .param(ARG_FILE_TYPE, fileType)
                    .param(ARG_ALLOWED_FILE_TYPES, ALLOWED_XPT_FILE_TYPES);
        }

        // 分析rowParent/colParent设置，建立单元格的父子关系
        XLangCompileTool cp = XLang.newCompileTool().allowUnregisteredScopeVar(true);
        new XptModelInitializer(cp).initialize(workbook);
        return workbook;
    }

    public XNode loadModelNodeFromResource(IResource resource) {
        ExcelWorkbook wk = loadModelFromResource(resource);
        return DslModelHelper.dslModelToXNode(ExcelConstants.XDSL_SCHEMA_WORKBOOK, wk);
    }
}
