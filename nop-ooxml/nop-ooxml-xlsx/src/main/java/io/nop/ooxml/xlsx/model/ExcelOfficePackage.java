/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.ooxml.xlsx.model;

import io.nop.core.resource.impl.ClassPathResource;
import io.nop.excel.model.ExcelStyle;
import io.nop.ooxml.common.IOfficePackagePart;
import io.nop.ooxml.common.OfficePackage;
import io.nop.ooxml.xlsx.parse.StylesPartParser;

import java.util.List;

public class ExcelOfficePackage extends OfficePackage {

    public static ExcelOfficePackage loadEmpty() {
        ClassPathResource resource = new ClassPathResource("classpath:nop/empty.xlsx");
        ExcelOfficePackage pkg = new ExcelOfficePackage();
        pkg.loadFromResource(resource);
        return pkg;
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
        if (part instanceof ThemesPart)
            return (ThemesPart) part;

        ThemesPart themes = new ThemesPart(part.getPath(), part.loadXml());
        addFile(themes);
        return themes;
    }
}