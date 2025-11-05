package io.nop.pdf.tabula.detectors;

import io.nop.pdf.tabula.Cell;
import io.nop.pdf.tabula.Page;
import io.nop.pdf.tabula.Rectangle;
import io.nop.pdf.tabula.extractors.SpreadsheetExtractionAlgorithm;

import java.util.Collections;
import java.util.List;

/**
 * Created by matt on 2015-12-14.
 *
 * This is the basic spreadsheet table detection algorithm currently implemented in tabula (web).
 *
 * It uses intersecting ruling lines to find tables.
 */
public class SpreadsheetDetectionAlgorithm implements DetectionAlgorithm {
    @Override
    public List<Rectangle> detect(Page page) {
        List<Cell> cells = SpreadsheetExtractionAlgorithm.findCells(page.getHorizontalRulings(), page.getVerticalRulings());

        List<Rectangle> tables = SpreadsheetExtractionAlgorithm.findSpreadsheetsFromCells(cells);

        // we want tables to be returned from top to bottom on the page
        Collections.sort(tables, Rectangle.ILL_DEFINED_ORDER);

        return tables;
    }
}
