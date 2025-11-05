/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ooxml.xlsx.model;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.xml.XNode;
import io.nop.ooxml.common.IOfficePackagePart;
import io.nop.ooxml.common.impl.XmlOfficePackagePart;

import java.util.ArrayList;
import java.util.List;

import static io.nop.ooxml.xlsx.XlsxErrors.ARG_SHEET_NAME;
import static io.nop.ooxml.xlsx.XlsxErrors.ERR_XLSX_UNKNOWN_SHEET_NAME;

public class WorkbookPart extends XmlOfficePackagePart {
    private List<XSSFSheetRef> sheets;

    public WorkbookPart(String path, XNode node) {
        super(path, node);
    }

    public static WorkbookPart toWorkbookPart(IOfficePackagePart workbookPart) {
        if (workbookPart instanceof WorkbookPart)
            return (WorkbookPart) workbookPart;

        WorkbookPart part = new WorkbookPart(workbookPart.getPath(), workbookPart.loadXml());
        return part;
    }

    public void clearSheets() {
        sheets = null;
        getNode().makeChild("sheets").clearBody();
    }

    public XSSFSheetRef getFirstSheet() {
        List<XSSFSheetRef> sheets = getSheets();
        if (sheets.isEmpty())
            return null;
        return sheets.get(0);
    }

    public String getFirstSheetName() {
        XSSFSheetRef sheet = getFirstSheet();
        return sheet == null ? null : sheet.getName();
    }

    public int getSheetCount() {
        return getSheets().size();
    }

    public XSSFSheetRef getSheetByIndex(int index) {
        return getSheets().get(index);
    }

    public XSSFSheetRef getSheetByName(String sheetName) {
        for (XSSFSheetRef sheetRef : getSheets()) {
            if (sheetRef.getName().equals(sheetName))
                return sheetRef;
        }
        return null;
    }

    public XSSFSheetRef requireSheetByName(String sheetName) {
        XSSFSheetRef sheetRef = getSheetByName(sheetName);
        if (sheetRef == null)
            throw new NopException(ERR_XLSX_UNKNOWN_SHEET_NAME).source(this.getNode()).param(ARG_SHEET_NAME, sheetName);
        return sheetRef;
    }

    public List<XSSFSheetRef> getSheets() {
        if (sheets != null) {
            return sheets;
        }

        sheets = new ArrayList<>();
        XNode sheetsN = getNode().childByTag("sheets");
        if (sheetsN != null) {
            for (XNode sheetN : sheetsN.getChildren()) {
                XSSFSheetRef sheetRef = parseSheetRef(sheetN);
                sheets.add(sheetRef);
            }
        }
        return sheets;
    }

    public void addSheet(String rId, int sheetId, String sheetName) {
        XNode sheets = getNode().makeChild("sheets");
        XNode sheetN = XNode.make("sheet");
        sheetN.setAttr("name", sheetName);
        sheetN.setAttr("sheetId", sheetId);
        sheetN.setAttr("r:id", rId);
        sheets.appendChild(sheetN);

        if (this.sheets != null)
            this.sheets.add(new XSSFSheetRef(rId, sheetName, String.valueOf(sheetId)));
    }

    private XSSFSheetRef parseSheetRef(XNode node) {
        XSSFSheetRef sheet = new XSSFSheetRef();
        sheet.setRelId(node.attrText("r:id"));
        sheet.setSheetId(node.attrText("sheetId"));
        sheet.setName(node.attrText("name"));
        return sheet;
    }
}
