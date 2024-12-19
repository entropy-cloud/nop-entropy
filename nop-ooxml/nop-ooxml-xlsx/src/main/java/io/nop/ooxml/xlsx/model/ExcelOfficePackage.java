/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ooxml.xlsx.model;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.excel.model.ExcelStyle;
import io.nop.ooxml.common.IOfficePackagePart;
import io.nop.ooxml.common.OfficePackage;
import io.nop.ooxml.common.constants.ContentTypes;
import io.nop.ooxml.common.model.ContentTypesPart;
import io.nop.ooxml.common.model.OfficeRelsPart;
import io.nop.ooxml.xlsx.XSSFRelation;
import io.nop.ooxml.xlsx.parse.StylesPartParser;

import java.util.List;

import static io.nop.ooxml.common.OfficeErrors.ARG_FILE_EXT;
import static io.nop.ooxml.common.OfficeErrors.ARG_PATH;
import static io.nop.ooxml.common.OfficeErrors.ERR_OOXML_UNSUPPORTED_CONTENT_TYPE;
import static io.nop.ooxml.common.model.PackagingURIHelper.createPartName;
import static io.nop.ooxml.xlsx.XlsxConstants.EMPTY_TEMPLATE_PATH;

public class ExcelOfficePackage extends OfficePackage {

    public static ExcelOfficePackage loadEmpty() {
        IResource resource = VirtualFileSystem.instance().getResource(EMPTY_TEMPLATE_PATH);
        ExcelOfficePackage pkg = new ExcelOfficePackage();
        pkg.loadFromResource(resource);
        return pkg;
    }

    public ExcelOfficePackage clearSheets(){
        getWorkbook().clearSheets();
        return this;
    }

    @Override
    public ExcelOfficePackage copy() {
        ExcelOfficePackage pkg = new ExcelOfficePackage();
        copyTo(pkg);
        return pkg;
    }


    public WorkbookPart getWorkbook() {
        IOfficePackagePart part = getFile("xl/workbook.xml");
        WorkbookPart ret = WorkbookPart.toWorkbookPart(part);
        addFile(ret);
        return ret;
    }

    public StylesPart getStyles() {
        String path = "xl/styles.xml";
        IOfficePackagePart part = getFile(path);
        if (part instanceof StylesPart)
            return (StylesPart) part;

        List<ExcelStyle> styles = new StylesPartParser(this).parseFromNode(part.loadXml());
        StylesPart stylesPart = new StylesPart(path, styles);
        addFile(stylesPart);
        return stylesPart;
    }

    public ThemesPart getTheme1() {
        String path = "xl/theme/theme1.xml";
        IOfficePackagePart part = getFile(path);
        if (part == null)
            return null;

        if (part instanceof ThemesPart)
            return (ThemesPart) part;

        ThemesPart themes = new ThemesPart(part.getPath(), part.loadXml());
        addFile(themes);
        return themes;
    }

    public CommentsPart getCommentsTable(IOfficePackagePart sheetPart) {
        IOfficePackagePart commentsPart = getRelPartByType(sheetPart, XSSFRelation.SHEET_COMMENTS.getRelation());
        if (commentsPart == null)
            return null;
        return CommentsPart.parse(commentsPart);
    }

    public String addImage(IResource resource) {
        return addImage(StringHelper.fileExt(resource.getPath()), resource);
    }

    public String addImage(String fileExt, IResource resource) {
        ContentTypesPart contentTypes = getContentTypes();
        String contentType = ContentTypes.getContentTypeFromFileExtension(fileExt);
        if (contentType == null)
            throw new NopException(ERR_OOXML_UNSUPPORTED_CONTENT_TYPE).param(ARG_PATH, resource.getPath())
                    .param(ARG_FILE_EXT, fileExt);

        contentTypes.addDefaultContentType(fileExt.toLowerCase(), contentType);

        String target = addNewFile("xl/media/image1." + fileExt, resource);
        return target;
    }

    public String addSheet(int index, String sheetName) {
        return addSheet(index, sheetName, true);
    }

    public String addSheet(int index, String sheetName, boolean addComment) {
        ContentTypesPart contentTypes = getContentTypes();
        int sheetId = index + 1;
        String sheetPath = "/xl/worksheets/sheet" + sheetId + ".xml";
        contentTypes.addContentType(createPartName(sheetPath), XSSFRelation.WORKSHEET.getType());

        if (addComment) {
            String commentPath = "/xl/comments" + sheetId + ".xml";
            contentTypes.addContentType(createPartName(commentPath), XSSFRelation.SHEET_COMMENTS.getType());
        }

        WorkbookPart workbook = getWorkbook();
        OfficeRelsPart rels = makeRelsForPart(workbook);
        String relPath = "worksheets/sheet" + sheetId + ".xml";
        String relId = rels.addRelationship(XSSFRelation.WORKSHEET.getRelation(), relPath, null);
        workbook.addSheet(relId, sheetId, sheetName);
        return sheetPath;
    }
}