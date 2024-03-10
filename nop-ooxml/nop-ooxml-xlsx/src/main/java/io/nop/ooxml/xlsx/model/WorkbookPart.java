/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ooxml.xlsx.model;

import io.nop.core.lang.xml.XNode;
import io.nop.ooxml.common.IOfficePackagePart;
import io.nop.ooxml.common.impl.XmlOfficePackagePart;

import java.util.ArrayList;
import java.util.List;

public class WorkbookPart extends XmlOfficePackagePart {

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
        getNode().makeChild("sheets").clearBody();
    }

    public List<XSSFSheetRef> getSheets() {
        List<XSSFSheetRef> sheets = new ArrayList<>();
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
    }

    private XSSFSheetRef parseSheetRef(XNode node) {
        XSSFSheetRef sheet = new XSSFSheetRef();
        sheet.setRelId(node.attrText("r:id"));
        sheet.setSheetId(node.attrText("sheetId"));
        sheet.setName(node.attrText("name"));
        return sheet;
    }
}
