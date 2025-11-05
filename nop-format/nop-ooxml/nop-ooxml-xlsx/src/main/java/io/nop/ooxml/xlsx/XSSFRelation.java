/* ====================================================================
   Licensed to the Apache Software Foundation (ASF) under one or more
   contributor license agreements.  See the NOTICE file distributed with
   this work for additional information regarding copyright ownership.
   The ASF licenses this file to You under the Apache License, Version 2.0
   (the "License"); you may not use this file except in compliance with
   the License.  You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
==================================================================== */

package io.nop.ooxml.xlsx;

import io.nop.excel.model.constants.PictureType;
import io.nop.ooxml.common.constants.OfficeXmlRelation;
import io.nop.ooxml.common.constants.PackageRelationshipTypes;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Defines namespaces, content types and normal file names / naming patterns, for the well-known XSSF format parts.
 */
public class XSSFRelation extends OfficeXmlRelation {

    // OLE embeddings relation name
    public static final String OLE_OBJECT_REL_TYPE = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/oleObject";

    // Embedded OPC documents relation name
    public static final String PACK_OBJECT_REL_TYPE = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/package";

    public static final String NS_SPREADSHEETML = "http://schemas.openxmlformats.org/spreadsheetml/2006/main";
    public static final String NS_DRAWINGML = "http://schemas.openxmlformats.org/drawingml/2006/main";
    public static final String NS_CHART = "http://schemas.openxmlformats.org/drawingml/2006/chart";

    /**
     * A map to lookup POIXMLRelation by its relation type
     */
    //private static final Map<String, XSSFRelation> _table = new HashMap<>();
    public XSSFRelation(String type, String rel, String defaultName) {
        super(type, rel, defaultName);
    }

    public static final XSSFRelation WORKBOOK = new XSSFRelation(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml",
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships/workbook", "/xl/workbook.xml");

    public static final XSSFRelation MACROS_WORKBOOK = new XSSFRelation(
            "application/vnd.ms-excel.sheet.macroEnabled.main+xml", PackageRelationshipTypes.CORE_DOCUMENT,
            "/xl/workbook.xml");

    public static final XSSFRelation TEMPLATE_WORKBOOK = new XSSFRelation(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.template.main+xml",
            PackageRelationshipTypes.CORE_DOCUMENT, "/xl/workbook.xml");

    public static final XSSFRelation MACRO_TEMPLATE_WORKBOOK = new XSSFRelation(
            "application/vnd.ms-excel.template.macroEnabled.main+xml", PackageRelationshipTypes.CORE_DOCUMENT,
            "/xl/workbook.xml");

    public static final XSSFRelation MACRO_ADDIN_WORKBOOK = new XSSFRelation(
            "application/vnd.ms-excel.addin.macroEnabled.main+xml", PackageRelationshipTypes.CORE_DOCUMENT,
            "/xl/workbook.xml");

    public static final XSSFRelation XLSB_BINARY_WORKBOOK = new XSSFRelation(
            "application/vnd.ms-excel.sheet.binary.macroEnabled.main", PackageRelationshipTypes.CORE_DOCUMENT,
            "/xl/workbook.bin");

    public static final XSSFRelation WORKSHEET = new XSSFRelation(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml",
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet",
            "/xl/worksheets/sheet#.xml");

    public static final XSSFRelation CHARTSHEET = new XSSFRelation(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.chartsheet+xml",
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships/chartsheet",
            "/xl/chartsheets/sheet#.xml");

    public static final XSSFRelation SHARED_STRINGS = new XSSFRelation(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml",
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings",
            "/xl/sharedStrings.xml");

    public static final XSSFRelation STYLES = new XSSFRelation(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml",
            PackageRelationshipTypes.STYLE_PART, "/xl/styles.xml");

    public static final XSSFRelation DRAWINGS = new XSSFRelation(
            "application/vnd.openxmlformats-officedocument.drawing+xml",
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships/drawing", "/xl/drawings/drawing#.xml");

    public static final XSSFRelation VML_DRAWINGS = new XSSFRelation(
            "application/vnd.openxmlformats-officedocument.vmlDrawing",
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships/vmlDrawing",
            "/xl/drawings/vmlDrawing#.vml");

    public static final XSSFRelation CHART = new XSSFRelation(
            "application/vnd.openxmlformats-officedocument.drawingml.chart+xml",
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships/chart", "/xl/charts/chart#.xml");

    public static final XSSFRelation CUSTOM_XML_MAPPINGS = new XSSFRelation("application/xml",
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships/xmlMaps", "/xl/xmlMaps.xml");

    public static final XSSFRelation SINGLE_XML_CELLS = new XSSFRelation(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.tableSingleCells+xml",
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships/tableSingleCells",
            "/xl/tables/tableSingleCells#.xml");

    public static final XSSFRelation TABLE = new XSSFRelation(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.table+xml",
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships/table", "/xl/tables/table#.xml");

    public static final XSSFRelation IMAGES = new XSSFRelation(null, PackageRelationshipTypes.IMAGE_PART, null);

    public static final XSSFRelation IMAGE_EMF = new XSSFRelation(PictureType.EMF.contentType,
            PackageRelationshipTypes.IMAGE_PART, "/xl/media/image#.emf");

    public static final XSSFRelation IMAGE_WMF = new XSSFRelation(PictureType.WMF.contentType,
            PackageRelationshipTypes.IMAGE_PART, "/xl/media/image#.wmf");

    public static final XSSFRelation IMAGE_PICT = new XSSFRelation(PictureType.PICT.contentType,
            PackageRelationshipTypes.IMAGE_PART, "/xl/media/image#.pict");

    public static final XSSFRelation IMAGE_JPEG = new XSSFRelation(PictureType.JPEG.contentType,
            PackageRelationshipTypes.IMAGE_PART, "/xl/media/image#.jpeg");

    public static final XSSFRelation IMAGE_PNG = new XSSFRelation(PictureType.PNG.contentType,
            PackageRelationshipTypes.IMAGE_PART, "/xl/media/image#.png");

    public static final XSSFRelation IMAGE_DIB = new XSSFRelation(PictureType.DIB.contentType,
            PackageRelationshipTypes.IMAGE_PART, "/xl/media/image#.dib");

    public static final XSSFRelation IMAGE_GIF = new XSSFRelation(PictureType.GIF.contentType,
            PackageRelationshipTypes.IMAGE_PART, "/xl/media/image#.gif");

    public static final XSSFRelation IMAGE_TIFF = new XSSFRelation(PictureType.TIFF.contentType,
            PackageRelationshipTypes.IMAGE_PART, "/xl/media/image#.tiff");

    public static final XSSFRelation IMAGE_EPS = new XSSFRelation(PictureType.EPS.contentType,
            PackageRelationshipTypes.IMAGE_PART, "/xl/media/image#.eps");

    public static final XSSFRelation IMAGE_BMP = new XSSFRelation(PictureType.BMP.contentType,
            PackageRelationshipTypes.IMAGE_PART, "/xl/media/image#.bmp");

    public static final XSSFRelation IMAGE_WPG = new XSSFRelation(PictureType.WPG.contentType,
            PackageRelationshipTypes.IMAGE_PART, "/xl/media/image#.wpg");

    public static final XSSFRelation HDPHOTO_WDP = new XSSFRelation(PictureType.WDP.contentType,
            PackageRelationshipTypes.HDPHOTO_PART, "/xl/media/hdphoto#.wdp");

    public static final XSSFRelation SHEET_COMMENTS = new XSSFRelation(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.comments+xml",
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships/comments", "/xl/comments#.xml");

    public static final XSSFRelation SHEET_HYPERLINKS = new XSSFRelation(null, PackageRelationshipTypes.HYPERLINK_PART,
            null);

    public static final XSSFRelation OLEEMBEDDINGS = new XSSFRelation(
            "application/vnd.openxmlformats-officedocument.oleObject", OLE_OBJECT_REL_TYPE,
            "/xl/embeddings/oleObject#.bin");

    public static final XSSFRelation PACKEMBEDDINGS = new XSSFRelation(null, PACK_OBJECT_REL_TYPE, null);

    public static final XSSFRelation VBA_MACROS = new XSSFRelation("application/vnd.ms-office.vbaProject",
            "http://schemas.microsoft.com/office/2006/relationships/vbaProject", "/xl/vbaProject.bin");

    public static final XSSFRelation ACTIVEX_CONTROLS = new XSSFRelation("application/vnd.ms-office.activeX+xml",
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships/control", "/xl/activeX/activeX#.xml");

    public static final XSSFRelation ACTIVEX_BINS = new XSSFRelation("application/vnd.ms-office.activeX",
            "http://schemas.microsoft.com/office/2006/relationships/activeXControlBinary", "/xl/activeX/activeX#.bin");

    public static final XSSFRelation MACRO_SHEET_BIN = new XSSFRelation(null, // TODO: figure out what this should be?
            "http://schemas.microsoft.com/office/2006/relationships/xlMacrosheet", "/xl/macroSheets/sheet#.bin");

    public static final XSSFRelation INTL_MACRO_SHEET_BIN = new XSSFRelation(null, // TODO: figure out what this should
            // be?
            "http://schemas.microsoft.com/office/2006/relationships/xlIntlMacrosheet", "/xl/macroSheets/sheet#.bin");

    public static final XSSFRelation DIALOG_SHEET_BIN = new XSSFRelation(null, // TODO: figure out what this should be?
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships/dialogsheet",
            "/xl/dialogSheets/sheet#.bin");

    public static final XSSFRelation THEME = new XSSFRelation("application/vnd.openxmlformats-officedocument.theme+xml",
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships/theme", "/xl/theme/theme#.xml");

    public static final XSSFRelation CALC_CHAIN = new XSSFRelation(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.calcChain+xml",
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships/calcChain", "/xl/calcChain.xml");

    public static final XSSFRelation EXTERNAL_LINKS = new XSSFRelation(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.externalLink+xml",
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships/externalLink",
            "/xl/externalLinks/externalLink#.xml");

    public static final XSSFRelation PRINTER_SETTINGS = new XSSFRelation(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.printerSettings",
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships/printerSettings",
            "/xl/printerSettings/printerSettings#.bin");
    public static final XSSFRelation PIVOT_TABLE = new XSSFRelation(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.pivotTable+xml",
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships/pivotTable",
            "/xl/pivotTables/pivotTable#.xml");
    public static final XSSFRelation PIVOT_CACHE_DEFINITION = new XSSFRelation(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.pivotCacheDefinition+xml",
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships/pivotCacheDefinition",
            "/xl/pivotCache/pivotCacheDefinition#.xml");
    public static final XSSFRelation PIVOT_CACHE_RECORDS = new XSSFRelation(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.pivotCacheRecords+xml",
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships/pivotCacheRecords",
            "/xl/pivotCache/pivotCacheRecords#.xml");

    public static final XSSFRelation CTRL_PROP_RECORDS = new XSSFRelation(null,
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships/ctrlProp",
            "/xl/ctrlProps/ctrlProp#.xml");

    public static final XSSFRelation CUSTOM_PROPERTIES = new XSSFRelation(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.customProperty",
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships/customProperty",
            "/xl/customProperty#.bin");

    public static final Set<String> WORKSHEET_RELS = Collections
            .unmodifiableSet(new HashSet<>(Arrays.asList(XSSFRelation.WORKSHEET.getRelation(),
                    XSSFRelation.CHARTSHEET.getRelation(), XSSFRelation.MACRO_SHEET_BIN.getRelation())));
}
