/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ooxml.xlsx.output;

import io.nop.commons.bytes.ByteString;
import io.nop.core.context.IEvalContext;
import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.IResource;
import io.nop.core.resource.impl.ByteArrayResource;
import io.nop.core.resource.impl.FileResource;
import io.nop.excel.ExcelConstants;
import io.nop.excel.model.ExcelChartModel;
import io.nop.excel.model.ExcelImage;
import io.nop.excel.model.ExcelSheet;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.excel.model.IExcelSheet;
import io.nop.excel.renderer.IExcelSheetGenerator;
import io.nop.ooxml.common.IOfficePackagePart;
import io.nop.ooxml.common.OfficeConstants;
import io.nop.ooxml.common.constants.ContentTypes;
import io.nop.ooxml.common.impl.XmlOfficePackagePart;
import io.nop.ooxml.common.model.ContentTypesPart;
import io.nop.ooxml.common.model.OfficeRelsPart;
import io.nop.ooxml.common.output.AbstractOfficeTemplate;
import io.nop.ooxml.xlsx.XSSFRelation;
import io.nop.ooxml.xlsx.chart.DrawingChartBuilder;
import io.nop.ooxml.xlsx.model.ExcelOfficePackage;
import io.nop.ooxml.xlsx.model.StylesPart;
import io.nop.ooxml.xlsx.model.drawing.DrawingBuilder;

import java.io.File;
import java.util.List;
import java.util.Map;

import static io.nop.ooxml.common.model.PackagingURIHelper.createPartName;

public class ExcelTemplate extends AbstractOfficeTemplate {

    private final ExcelWorkbook workbook;
    private final ExcelOfficePackage modelPkg;
    private final IExcelSheetGenerator sheetGenerator;

    public ExcelTemplate(ExcelOfficePackage pkg, ExcelWorkbook workbook,
                         IExcelSheetGenerator sheetGenerator) {
        this.workbook = workbook;
        this.modelPkg = pkg;
        this.sheetGenerator = sheetGenerator;
        pkg.loadInMemory();
    }

    public ExcelTemplate(ExcelWorkbook workbook,
                         IExcelSheetGenerator sheetGenerator) {
        this(ExcelOfficePackage.loadEmpty(), workbook, sheetGenerator);
    }

    public ExcelTemplate(ExcelWorkbook workbook) {
        this(workbook, null);
    }

    @Override
    public void generateToDir(File dir, IEvalContext context) {
        ExcelOfficePackage pkg = this.modelPkg.copy();

        context.getEvalScope().setLocalValue(null, OfficeConstants.VAR_OFC_PKG, pkg);

        pkg.getWorkbook().clearSheets();

        GenState genState = new GenState(pkg);

        if (sheetGenerator != null) {
            sheetGenerator.generate(context, (sheet, ctx) -> {
                generateSheet(dir, sheet, ctx, genState);
            });
        } else if (workbook != null) {
            for (ExcelSheet sheet : workbook.getSheets()) {
                generateSheet(dir, sheet, context, genState);
            }
        }

        if (workbook != null) {
            pkg.addFile(new StylesPart(workbook.getStyles()));
        }
        pkg.generateToDir(dir, context.getEvalScope());
    }

    public void generateSheet(File dir, IExcelSheet sheet,
                              IEvalContext context, GenState genState) {

        int index = genState.genSheetIndex();
        ExcelOfficePackage pkg = genState.pkg;
        String sheetPath = pkg.addSheet(index, normalizeSheetName(sheet.getName(), index, context));

        int sheetId = index + 1;
        ContentTypesPart contentTypes = pkg.getContentTypes();
        String commentPath = "/xl/comments" + sheetId + ".xml";
        contentTypes.addContentType(createPartName(commentPath), XSSFRelation.SHEET_COMMENTS.getType());

        IResource resource = new FileResource(new File(dir, sheetPath));
        ExcelSheetWriter writer = new ExcelSheetWriter(sheet, index == 0, index, this.workbook);
        writer.indent(isIndent()).generateToResource(resource, context);
        IOfficePackagePart sheetPart = pkg.addFile(sheetPath, resource);

        generateDrawings(sheet.getImages(), sheet.getCharts(), writer.getDrawingRelId(), sheetPart, genState);

        IResource commentResource = new FileResource(new File(dir, commentPath));
        new ExcelCommentsWriter(sheet).indent(isIndent()).generateToResource(commentResource, context);
        pkg.addFile(commentPath, commentResource);

        String relCommentsPath = "../comments" + sheetId + ".xml";
        OfficeRelsPart sheetRels = pkg.makeRelsForPart(sheetPart);
        sheetRels.removeRelationshipByType(XSSFRelation.SHEET_COMMENTS.getRelation());
        sheetRels.addRelationship(XSSFRelation.SHEET_COMMENTS.getRelation(), relCommentsPath, null);
    }

    public String normalizeSheetName(String sheetName, int index, IEvalContext context) {
        Map<String, String> mapping = (Map<String, String>) context.getEvalScope().getValue(ExcelConstants.VAR_SHEET_NAME_MAPPING);
        if (mapping != null) {
            String mappedName = mapping.get(sheetName);
            if (mappedName != null)
                return mappedName;
        }
        return XlsxGenHelper.normalizeSheetName(sheetName, index, workbook);
    }

    public void generateDrawings(List<ExcelImage> images,
                                 List<ExcelChartModel> charts,
                                 String drawingRelId, IOfficePackagePart sheetPart, GenState genState) {
        if ((images == null || images.isEmpty()) && (charts == null || charts.isEmpty()))
            return;

        ExcelOfficePackage pkg = genState.pkg;

        int drawingIndex = genState.nextDrawingIndex++;
        String drawingPath = "/xl/drawings/drawing" + (drawingIndex + 1) + ".xml";

        pkg.getContentTypes().addOverrideContentType(drawingPath, XSSFRelation.DRAWINGS.getType());



        OfficeRelsPart relPart = pkg.makeRelsForPart(sheetPart);
        relPart.addRelationship(drawingRelId, XSSFRelation.DRAWINGS.getRelation(), "../drawings/drawing" + (drawingIndex + 1) + ".xml", null);

        OfficeRelsPart drawingRelPart = pkg.makeRelsForPartPath(drawingPath);

        if (images != null) {
            for (ExcelImage image : images) {
                if (image.getData() == null && image.getShape() == null)
                    continue;
                String path = addImageData(pkg, image.getData(), image.getImgType(), genState);
                String id = drawingRelPart.addImage("/" + path).getId();
                image.setEmbedId(id);
            }
        }

        if (charts != null && !charts.isEmpty()) {
            for (ExcelChartModel chart : charts) {
                String chartPath = addChartData(pkg, chart, genState);
                // 使用addRelationship方法创建图表关系
                drawingRelPart.addRelationship(drawingRelPart.newId(),
                        "http://schemas.openxmlformats.org/officeDocument/2006/relationships/chart",
                        "../" + chartPath.substring(4), null);
            }
        }

        XNode node = new DrawingBuilder().buildWithCharts(images, charts);
        XmlOfficePackagePart part = new XmlOfficePackagePart(drawingPath.substring(1), node);
        pkg.addFile(part);

    }

    private String addChartData(ExcelOfficePackage pkg, ExcelChartModel chart, GenState genState) {
        String path = genState.charts.get(chart);
        if (path == null) {
            int index = genState.nextChartIndex++;
            XNode chartNode = DrawingChartBuilder.INSTANCE.build(chart);
            String chartPath = "/xl/charts/chart" + (index + 1) + ".xml";
            XmlOfficePackagePart part = new XmlOfficePackagePart(chartPath.substring(1), chartNode);
            pkg.addFile(part);
            genState.charts.put(chart, chartPath);

            // <Override PartName="/xl/charts/chart1.xml" ContentType="application/vnd.openxmlformats-officedocument.drawingml.chart+xml"/>
            pkg.getContentTypes().addOverrideContentType(chartPath, XSSFRelation.CHART.getType());
            return chartPath;
        }
        return path;
    }

    private String addImageData(ExcelOfficePackage pkg, ByteString data, String imgType, GenState genState) {
        // 同样的图片数据如果出现多次，则会复用此前添加的图片路径
        String path = genState.images.get(data);
        if (path == null) {
            int index = genState.nextImageIndex++;
            if (imgType == null)
                imgType = ContentTypes.EXTENSION_PNG;
            IResource resource = new ByteArrayResource("/" + index, data.toByteArray(), -1);
            path = pkg.addImage(imgType, resource);
            genState.images.put(data, path);
        }
        return path;
    }
}