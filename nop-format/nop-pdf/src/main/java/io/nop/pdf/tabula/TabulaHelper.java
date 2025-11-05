package io.nop.pdf.tabula;

import io.nop.pdf.tabula.Table;
import io.nop.pdf.tabula.extractors.SpreadsheetExtractionAlgorithm;
import io.nop.pdf.tabula.extractors.BasicExtractionAlgorithm;
import io.nop.pdf.tabula.detectors.*;

import java.util.ArrayList;
import java.util.List;

public class TabulaHelper {
    private static final int RELATIVE_AREA_CALCULATION_MODE = 0;
    private static final int ABSOLUTE_AREA_CALCULATION_MODE = 1;

    /**
     * 针对单个Page执行表格提取操作。
     *
     * @param page 当前页面
     * @param tableExtractor 表格提取器
     * @param pageAreas 区域定义，可以为空（提取整页）
     * @return 提取到的表格列表
     */
    public static List<Table> extractTablesFromPage(Page page, TableExtractor tableExtractor, List<Pair<Integer, Rectangle>> pageAreas) {
        List<Table> tables = new ArrayList<>();

        // 添加竖线（如果有定位线）
        if (tableExtractor.verticalRulingPositions != null) {
            for (Float verticalRulingPosition : tableExtractor.verticalRulingPositions) {
                page.addRuling(new Ruling(0, verticalRulingPosition, 0.0f, (float) page.getHeight()));
            }
        }

        if (pageAreas != null && !pageAreas.isEmpty()) {
            // 遍历每个区域，提取区域内的表格
            for (Pair<Integer, Rectangle> areaPair : pageAreas) {
                Rectangle area = areaPair.getRight();
                if (areaPair.getLeft() == RELATIVE_AREA_CALCULATION_MODE) {
                    // 将百分比区域转换为实际像素
                    area = new Rectangle(
                            (float) (area.getTop() / 100.0 * page.getHeight()),
                            (float) (area.getLeft() / 100.0 * page.getWidth()),
                            (float) (area.getWidth() / 100.0 * page.getWidth()),
                            (float) (area.getHeight() / 100.0 * page.getHeight())
                    );
                }
                tables.addAll(tableExtractor.extractTables(page.getArea(area)));
            }
        } else {
            // 未指定区域，则提取整页
            tables.addAll(tableExtractor.extractTables(page));
        }

        return tables;
    }


    public enum ExtractionMethod {
        BASIC,
        SPREADSHEET,
        DECIDE
    }

    public static class TableExtractor {
        private boolean guess = false;
        private boolean useLineReturns = false;
        private BasicExtractionAlgorithm basicExtractor = new BasicExtractionAlgorithm();
        private SpreadsheetExtractionAlgorithm spreadsheetExtractor = new SpreadsheetExtractionAlgorithm();

        private boolean verticalRulingPositionsRelative = false;
        private List<Float> verticalRulingPositions = null;

        private ExtractionMethod method = ExtractionMethod.BASIC;

        public TableExtractor() {
        }

        public void setVerticalRulingPositions(List<Float> positions) {
            this.verticalRulingPositions = positions;
        }

        public void setVerticalRulingPositionsRelative(boolean relative) {
            this.verticalRulingPositionsRelative = relative;
        }

        public void setGuess(boolean guess) {
            this.guess = guess;
        }

        public void setUseLineReturns(boolean useLineReturns) {
            this.useLineReturns = useLineReturns;
        }

        public void setMethod(ExtractionMethod method) {
            this.method = method;
        }

        public List<Table> extractTables(Page page) {
            ExtractionMethod effectiveMethod = this.method;
            if (effectiveMethod == ExtractionMethod.DECIDE) {
                effectiveMethod = spreadsheetExtractor.isTabular(page) ?
                        ExtractionMethod.SPREADSHEET :
                        ExtractionMethod.BASIC;
            }
            switch (effectiveMethod) {
                case BASIC:
                    return extractTablesBasic(page);
                case SPREADSHEET:
                    return extractTablesSpreadsheet(page);
                default:
                    return new ArrayList<>();
            }
        }

        public List<Table> extractTablesBasic(Page page) {
            if (guess) {
                // guess the page areas to extract using a detection algorithm
                // currently we only have a detector that uses spreadsheets to find table areas
                DetectionAlgorithm detector = new NurminenDetectionAlgorithm();
                List<Rectangle> guesses = detector.detect(page);
                List<Table> tables = new ArrayList<>();

                for (Rectangle guessRect : guesses) {
                    Page guess = page.getArea(guessRect);
                    tables.addAll(basicExtractor.extract(guess));
                }
                return tables;
            }

            if (verticalRulingPositions != null) {
                List<Float> absoluteRulingPositions;

                if (this.verticalRulingPositionsRelative) {
                    // convert relative to absolute
                    absoluteRulingPositions = new ArrayList<>(verticalRulingPositions.size());
                    for (float relative : this.verticalRulingPositions) {
                        float absolute = (float) (relative / 100.0 * page.getWidth());
                        absoluteRulingPositions.add(absolute);
                    }
                } else {
                    absoluteRulingPositions = this.verticalRulingPositions;
                }
                return basicExtractor.extract(page, absoluteRulingPositions);
            }

            return basicExtractor.extract(page);
        }

        public List<Table> extractTablesSpreadsheet(Page page) {
            // TODO add useLineReturns
            return spreadsheetExtractor.extract(page);
        }
    }
}
