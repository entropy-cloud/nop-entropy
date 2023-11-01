package io.nop.report.core.engine;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.ProcessResult;
import io.nop.commons.bytes.ByteString;
import io.nop.commons.mutable.MutableInt;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.utils.Underscore;
import io.nop.core.model.table.CellPosition;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.excel.format.ExcelFormatHelper;
import io.nop.excel.model.ExcelClientAnchor;
import io.nop.excel.model.ExcelImage;
import io.nop.excel.model.ExcelStyle;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.excel.model.XptCellModel;
import io.nop.excel.model.XptRowModel;
import io.nop.report.core.dataset.KeyedReportDataSet;
import io.nop.report.core.dataset.ReportDataSet;
import io.nop.report.core.model.ExpandedCell;
import io.nop.report.core.model.ExpandedCol;
import io.nop.report.core.model.ExpandedRow;
import io.nop.report.core.model.ExpandedSheet;
import io.nop.report.core.model.ExpandedTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.Format;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.nop.core.CoreErrors.ARG_CELL;
import static io.nop.report.core.XptErrors.ARG_CELL_POS;
import static io.nop.report.core.XptErrors.ARG_SHEET_NAME;
import static io.nop.report.core.XptErrors.ERR_XPT_INVALID_IMAGE_DATA;

public class ExpandedSheetEvaluator {
    public static final ExpandedSheetEvaluator INSTANCE = new ExpandedSheetEvaluator();
    private static final Logger LOG = LoggerFactory.getLogger(ExpandedSheetEvaluator.class);

    public void evaluateSheetCells(ExpandedSheet expandedSheet, IXptRuntime xptRt) {
        for (ExpandedRow row : expandedSheet.getTable().getRows()) {
            xptRt.setRow(row);
            XptRowModel rowModel = row.getModel();

            row.forEachRealCell(cell -> {
                evaluateCell(cell, xptRt);
            });

            if (rowModel != null) {
                xptRt.setRow(row);
                Boolean visible = ConvertHelper.toBoolean(runXpl(rowModel.getVisibleExpr(), xptRt));
                if (visible != null) {
                    row.setHidden(!visible);
                }

                String styleId = ConvertHelper.toString(runXpl(rowModel.getStyleIdExpr(), xptRt));
                row.setStyleId(styleId);
            }
        }
    }

    public void evaluateImages(ExpandedSheet sheet, List<ExcelImage> images, IXptRuntime xptRt) {
        if (images == null || images.isEmpty())
            return;

        Map<CellPosition, List<ExcelImage>> map = new HashMap<>();
        for (ExcelImage image : images) {
            CellPosition pos = image.getAnchor().getStartPosition();
            map.computeIfAbsent(pos, k -> new ArrayList<>(1)).add(image);
            image.calcSize(sheet);
        }

        List<ExcelImage> genImages = new ArrayList<>();
        MutableInt index = new MutableInt(0);
        sheet.getTable().forEachCell((cell, rowIndex, colIndex) -> {
            ExpandedCell ec = (ExpandedCell) cell.getRealCell();
            if (ec != null) {
                XptCellModel cm = ec.getModel();
                if (cm != null) {
                    List<ExcelImage> list = map.get(cm.getCellPosition());
                    if (list != null) {
                        for (ExcelImage image : list) {
                            image = genImage(ec, image, xptRt, index);
                            if (image != null) {
                                image.calcSize(sheet);
                                index.incrementAndGet();
                                genImages.add(image);
                            }
                        }
                    }
                }
            }
            return ProcessResult.CONTINUE;
        });
        sheet.setImages(genImages);
    }

    public Object evaluateCell(ExpandedCell cell, IXptRuntime xptRt) {
        if (cell.isEvaluated()) {
            return cell.getValue();
        }

        // 避免循环依赖时导致死循环
        cell.setEvaluated(true);
        XptCellModel cellModel = cell.getModel();
        if (cellModel == null) {
            return null;
        }

        ExpandedCell curCell = xptRt.getCell();
        ExcelWorkbook workbook = xptRt.getWorkbook();
        ExpandedSheet sheet = xptRt.getSheet();

        try {
            xptRt.setCell(cell);

            IEvalAction valueExpr = cellModel.getValueExpr();
            if (valueExpr != null) {
                Object value = valueExpr.invoke(xptRt);
                cell.setValue(value);
            } else if (cellModel.getExpandType() != null) {
                // 对于展开单元格，如果没有特别指定valueExpr，则以展开值为单元格的值
                Object value = cell.getExpandValue();
                if (value instanceof KeyedReportDataSet) {
                    cell.setValue(((KeyedReportDataSet) value).getKey());
                } else {
                    Object expandValue = cell.getExpandValue();
                    if (expandValue instanceof ReportDataSet) {
                        ReportDataSet ds = (ReportDataSet) expandValue;
                        if (cellModel.getField() != null) {
                            cell.setValue(ds.field(cellModel.getField()));
                        } else {
                            cell.setValue(ds.first());
                        }
                    } else {
                        if (cellModel.getField() != null) {
                            cell.setValue(Underscore.getFieldValue(expandValue, cellModel.getField()));
                        } else {
                            cell.setValue(expandValue);
                        }
                    }
                }
            } else if (cellModel.getField() != null) {
                // 如果指定了field,则按照坐标条件先查找上下文关联对象，如果未找到，则取全局的变量
                cell.setValue(xptRt.field(cellModel.getField()));
            }

            IEvalAction styleExpr = cellModel.getStyleIdExpr();
            if (styleExpr != null) {
                String styleId = ConvertHelper.toString(styleExpr.invoke(xptRt));
                if (styleId != null)
                    cell.setStyleId(styleId);
            }

            IEvalAction formatExpr = cellModel.getFormatExpr();
            if (formatExpr != null) {
                Object formattedValue = formatExpr.invoke(xptRt);
                cell.setFormattedValue(formattedValue);
            } else if (cell.getStyleId() != null && workbook != null && cell.getValue() instanceof Number) {
                ExcelStyle style = workbook.getStyle(cell.getStyleId());
                if (style != null && style.getNumberFormat() != null) {
                    Format format = ExcelFormatHelper.getFormat(style.getNumberFormat());
                    if (format != null) {
                        cell.setFormattedValue(format.format(cell.getValue()));
                    }
                }
            }

            IEvalAction linkExpr = cellModel.getLinkExpr();
            if (linkExpr != null) {
                String linkUrl = ConvertHelper.toString(linkExpr.invoke(xptRt));
                cell.setLinkUrl(linkUrl);
            }
            evalTestExpr(cellModel, xptRt);

            if (LOG.isTraceEnabled())
                LOG.trace("nop.xpt.eval-cell:cell={},value={}", cell.getName(), cell.getValue());
            return cell.getValue();
        } catch (NopException e) {
            e.param(ARG_CELL_POS, cell.getName())
                    .param(ARG_SHEET_NAME, sheet.getName());
            throw e;
        } finally {
            xptRt.setCell(curCell);
        }
    }

    private void evalTestExpr(XptCellModel cellModel, IXptRuntime xptRt) {
        if (cellModel.getRowTestExpr() != null) {
            if (!cellModel.getRowTestExpr().passConditions(xptRt)) {
                removeRow(xptRt.getCell());
            }
        }

        if (cellModel.getColTestExpr() != null) {
            if (!cellModel.getColTestExpr().passConditions(xptRt)) {
                removeCol(xptRt.getCell());
            }
        }
    }

    private void removeRow(ExpandedCell cell) {
        int startIndex = cell.getRowIndex();
        int lastIndex = startIndex + cell.getMergeDown();
        ExpandedTable table = cell.getTable();
        for (int i = startIndex; i <= lastIndex; i++) {
            ExpandedRow row = table.getRow(i);
            row.setRemoved(true);
        }
    }

    private void removeCol(ExpandedCell cell) {
        int startIndex = cell.getColIndex();
        int lastIndex = startIndex + cell.getMergeAcross();
        ExpandedTable table = cell.getTable();
        for (int i = startIndex; i <= lastIndex; i++) {
            ExpandedCol col = table.getCol(i);
            col.setRemoved(true);
        }
    }

    private Object runXpl(IEvalAction action, IXptRuntime xptRt) {
        if (action == null)
            return null;
        return action.invoke(xptRt);
    }


    private ExcelImage genImage(ExpandedCell cell,
                                ExcelImage model, IXptRuntime xptRt, MutableInt index) {
        xptRt.setCell(cell);
        xptRt.setRow(cell.getRow());

        if (model.getTestExpr() != null) {
            if (!model.getTestExpr().passConditions(xptRt))
                return null;
        }

        ExcelImage ret = newExcelImage(cell, model, index);
        xptRt.setImage(ret);

        if (model.getDataExpr() != null) {
            Object data = model.getDataExpr().invoke(xptRt);
            if (data == null)
                return null;

            if (data instanceof ExcelImage)
                return (ExcelImage) data;

            if (data instanceof IResource) {
                IResource resource = (IResource) data;
                byte[] bytes = ResourceHelper.readBytes(resource);
                ret.setData(ByteString.of(bytes));
                String fileExt = StringHelper.fileExt(resource.getPath());
                if (!StringHelper.isEmpty(fileExt)) {
                    ret.setImgType(fileExt);
                }
            } else if (data instanceof ByteString) {
                ret.setData((ByteString) data);
            } else if (data instanceof byte[]) {
                ret.setData(ByteString.of((byte[]) data));
            } else {
                throw new NopException(ERR_XPT_INVALID_IMAGE_DATA)
                        .param(ARG_CELL, cell);
            }
        }

        return ret;
    }

    private static ExcelImage newExcelImage(ExpandedCell cell, ExcelImage model, MutableInt index) {
        ExcelImage ret = new ExcelImage();
        ret.setName(model.getName() + '-' + index);
        ret.setDescription(model.getDescription());
        ret.setImgType(model.getImgType());
        ret.setRotateDegree(model.getRotateDegree());
        ret.setNoChangeAspect(model.isNoChangeAspect());
        ret.setPrint(model.isPrint());
        ret.setLinkUrl(model.getLinkUrl());

        ExcelClientAnchor anchor = model.getAnchor();
        ExcelClientAnchor retAnchor = anchor.copy();
        retAnchor.setRow1(cell.getRowIndex());
        retAnchor.setCol1(cell.getColIndex());
        ret.setAnchor(retAnchor);

        ret.setData(model.getData());
        return ret;
    }
}
