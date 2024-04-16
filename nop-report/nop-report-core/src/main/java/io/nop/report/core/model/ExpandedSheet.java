/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.report.core.model;

import io.nop.excel.model.ExcelImage;
import io.nop.excel.model.ExcelPageBreaks;
import io.nop.excel.model.ExcelPageMargins;
import io.nop.excel.model.ExcelPageSetup;
import io.nop.excel.model.ExcelSheet;
import io.nop.excel.model.IExcelSheet;
import io.nop.excel.model.XptSheetModel;

import java.util.ArrayList;
import java.util.List;

public class ExpandedSheet implements IExcelSheet {
    private XptSheetModel model;
    private final ExpandedTable table;
    private String name;
    private ExcelPageMargins pageMargins;
    private ExcelPageSetup pageSetup;
    private ExcelPageBreaks pageBreaks;
    private Double defaultRowHeight;
    private Double defaultColumnWidth;

    private List<ExcelImage> images;

    public ExpandedSheet(XptSheetModel model, ExpandedTable table) {
        this.model = model;
        this.table = table;
    }

    public ExpandedSheet(ExcelSheet sheet) {
        this(sheet.getModel(), new ExpandedTable(sheet.getTable()));
        setName(sheet.getName());
        setPageMargins(sheet.getPageMargins());
        setPageBreaks(sheet.getPageBreaks());
        setPageSetup(sheet.getPageSetup());
        setDefaultRowHeight(sheet.getDefaultRowHeight());
        setDefaultColumnWidth(sheet.getDefaultColumnWidth());
    }

    @Override
    public List<ExcelImage> getImages() {
        return images;
    }

    public void setImages(List<ExcelImage> images) {
        this.images = images;
    }

    public List<ExcelImage> makeImages() {
        if (images == null)
            images = new ArrayList<>();
        return images;
    }

    public XptSheetModel getModel() {
        return model;
    }

    public void setModel(XptSheetModel model) {
        this.model = model;
    }

    @Override
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public ExcelPageMargins getPageMargins() {
        return pageMargins;
    }

    public void setPageMargins(ExcelPageMargins pageMargins) {
        this.pageMargins = pageMargins;
    }

    @Override
    public ExcelPageSetup getPageSetup() {
        return pageSetup;
    }

    public void setPageSetup(ExcelPageSetup pageSetup) {
        this.pageSetup = pageSetup;
    }

    @Override
    public ExcelPageBreaks getPageBreaks() {
        return pageBreaks;
    }

    public void setPageBreaks(ExcelPageBreaks pageBreaks) {
        this.pageBreaks = pageBreaks;
    }

    @Override
    public Double getDefaultRowHeight() {
        return defaultRowHeight;
    }

    public void setDefaultRowHeight(Double defaultRowHeight) {
        this.defaultRowHeight = defaultRowHeight;
    }

    @Override
    public Double getDefaultColumnWidth() {
        return defaultColumnWidth;
    }

    public void setDefaultColumnWidth(Double defaultColumnWidth) {
        this.defaultColumnWidth = defaultColumnWidth;
    }

    @Override
    public ExpandedTable getTable() {
        return table;
    }
}