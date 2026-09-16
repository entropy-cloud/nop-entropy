/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.excel.imp;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.eval.EvalExprProvider;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.model.table.CellRange;
import io.nop.core.model.table.ICellView;
import io.nop.excel.imp.model.IFieldContainer;
import io.nop.excel.imp.model.ImportFieldModel;
import io.nop.excel.imp.model.ImportSheetModel;
import io.nop.excel.model.ExcelCell;
import io.nop.excel.model.ExcelTable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static io.nop.excel.ExcelErrors.ERR_IMPORT_INVALID_DATA_ROW;
import static io.nop.excel.ExcelErrors.ERR_IMPORT_UNKNOWN_FIELD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class TestTreeTableDataParser {

    static class RecordingListener implements ITableDataEventListener {
        final List<String> events = new ArrayList<>();

        @Override
        public void beginSheet(String sheetName, ImportSheetModel sheetModel) {
        }

        @Override
        public void endSheet(ImportSheetModel sheetModel) {
        }

        @Override
        public void beginList(int rowIndex, int colIndex, int maxRowIndex, int maxColIndex,
                              IFieldContainer fieldModel, boolean cardList) {
        }

        @Override
        public void endList(int maxRowIndex, int maxColIndex, IFieldContainer fieldModel) {
        }

        @Override
        public void beginObject(int rowIndex, int colIndex, int maxRowIndex, int maxColIndex,
                                IFieldContainer fieldModel) {
        }

        @Override
        public void endObject(IFieldContainer fieldModel) {
        }

        @Override
        public void simpleField(int rowIndex, int colIndex, ICellView cell, LabelData labelData) {
            events.add(labelData.getField().getName() + "="
                    + (cell == null ? null : cell.getValue()) + "@" + rowIndex + "," + colIndex);
        }
    }

    private static ExcelCell cell(ExcelTable table, int rowIndex, int colIndex, Object value) {
        ExcelCell cell = new ExcelCell();
        cell.setValue(value);
        table.setCell(rowIndex, colIndex, cell);
        return cell;
    }

    private static ImportFieldModel field(String name, ImportFieldModel... children) {
        ImportFieldModel field = new ImportFieldModel();
        field.setName(name);
        if (children.length > 0)
            field.setFields(Arrays.asList(children));
        return field;
    }

    private static ImportSheetModel sheetModel(ImportFieldModel... fields) {
        ImportSheetModel model = new ImportSheetModel();
        model.setName("test");
        model.setFields(new ArrayList<>(Arrays.asList(fields)));
        model.init();
        return model;
    }

    private static TreeTableDataParser parser() {
        IEvalScope scope = EvalExprProvider.newEvalScope();
        return new TreeTableDataParser(scope);
    }

    /**
     * 同一行内两个简单字段之间隔了一个空列时，右侧字段仍然要被解析
     */
    @Test
    public void testParseNextFieldsSkipsEmptyGapColumn() {
        ExcelTable table = new ExcelTable();
        cell(table, 0, 0, "A");
        cell(table, 0, 1, "a");
        // 第2列为空列
        cell(table, 0, 3, "B");
        cell(table, 0, 4, "b");

        ImportFieldModel fa = field("A");
        ImportFieldModel fb = field("B");
        RecordingListener listener = new RecordingListener();
        parser().parse("test", table, sheetModel(fa, fb), listener);

        assertEquals(Arrays.asList("A=a@0,1", "B=b@0,4"), listener.events);
    }

    /**
     * 最后一列是保留的注解列（如配置sheet的"说明"列），不属于字段区：
     * parseNextFields不解析最后一列，其中的任意文本不影响字段解析
     */
    @Test
    public void testLastColumnTreatedAsAnnotationColumn() {
        ExcelTable table = new ExcelTable();
        cell(table, 0, 0, "A");
        cell(table, 0, 1, "a");
        // 最后一列是说明文本，不是字段
        cell(table, 0, 2, "任意说明文字");

        ImportFieldModel fa = field("A");
        RecordingListener listener = new RecordingListener();
        parser().parse("test", table, sheetModel(fa), listener);

        assertEquals(Arrays.asList("A=a@0,1"), listener.events);
    }

    /**
     * 对象字段的区域在空行处结束，空行之后的兄弟字段不属于对象内部字段
     */
    @Test
    public void testObjectFieldRegionEndsAtEmptyRow() {
        ExcelTable table = new ExcelTable();
        cell(table, 0, 0, "obj");
        cell(table, 1, 0, "x");
        cell(table, 1, 1, "1");
        cell(table, 2, 0, "y");
        cell(table, 2, 1, "2");
        // 第3行为空行
        cell(table, 4, 0, "z");
        cell(table, 4, 1, "3");

        ImportFieldModel obj = field("obj", field("x"), field("y"));
        ImportFieldModel fz = field("z");
        RecordingListener listener = new RecordingListener();
        parser().parse("test", table, sheetModel(obj, fz), listener);

        assertEquals(Arrays.asList("x=1@1,1", "y=2@2,1", "z=3@4,1"), listener.events);
    }

    /**
     * 竖排label的list字段（alignRight），数据区提前结束后，下方的兄弟字段正常解析，
     * 且不会被错位识别为list表头
     */
    @Test
    public void testAlignRightListFieldEndsAtDataEnd() {
        ExcelTable table = new ExcelTable();
        ExcelCell items = cell(table, 0, 0, "items");
        items.setMergeDown(3);
        cell(table, 0, 1, "seq");
        cell(table, 0, 2, "f1");
        cell(table, 1, 1, "1");
        cell(table, 1, 2, "a");
        // 第2行起数据区结束，第4行有兄弟字段z
        cell(table, 4, 0, "z");
        cell(table, 4, 1, "v");

        ImportFieldModel seq = field("seq");
        ImportFieldModel f1 = field("f1");
        ImportFieldModel list = field("items", seq, f1);
        list.setList(true);
        ImportFieldModel fz = field("z");
        RecordingListener listener = new RecordingListener();
        parser().parse("test", table, sheetModel(list, fz), listener);

        assertEquals(Arrays.asList("seq=1@1,1", "f1=a@1,2", "z=v@4,1"), listener.events);
    }

    /**
     * sheet级list且label竖排（alignRight）时，数据区结束后的残留数据行应报错，
     * 与label横排（值在下方）的sheet级list行为一致
     */
    @Test
    public void testAlignRightListSheetRejectsTrailingDataRow() {
        ExcelTable table = new ExcelTable();
        ExcelCell items = cell(table, 0, 0, "items");
        items.setMergeDown(1);
        cell(table, 1, 1, "f1");
        cell(table, 2, 0, "1");
        cell(table, 2, 1, "a");
        cell(table, 3, 0, "extra");

        ImportFieldModel f1 = field("f1");
        ImportSheetModel model = new ImportSheetModel();
        model.setName("test");
        model.setList(true);
        model.setField("items");
        model.setFields(new ArrayList<>(Arrays.asList(f1)));
        model.init();

        try {
            parser().parse("test", table, model, new RecordingListener());
            fail("should fail because of trailing data row");
        } catch (NopException e) {
            assertEquals(ERR_IMPORT_INVALID_DATA_ROW.getErrorCode(), e.getErrorCode());
        }
    }

    /**
     * parseListData中单元格解析出错时，错误栈应指向实际出错的单元格（1-based），
     * 而不是表头起始位置
     */
    @Test
    public void testListDataErrorStackPointsToActualCell() {
        ExcelTable table = new ExcelTable();
        cell(table, 0, 0, "f1");
        cell(table, 1, 0, "bad");

        ImportFieldModel f1 = field("f1");
        ImportSheetModel model = new ImportSheetModel();
        model.setName("test");
        model.setList(true);
        model.setNoSeqCol(true);
        model.setField("items");
        model.setFields(new ArrayList<>(Arrays.asList(f1)));
        model.init();

        RecordingListener listener = new RecordingListener() {
            @Override
            public void simpleField(int rowIndex, int colIndex, ICellView cell, LabelData labelData) {
                if (cell != null && "bad".equals(cell.getValue()))
                    throw new NopException(ERR_IMPORT_INVALID_DATA_ROW);
                super.simpleField(rowIndex, colIndex, cell, labelData);
            }
        };

        try {
            parser().parse("test", table, model, listener);
            fail("should fail");
        } catch (NopException e) {
            List<String> stack = e.getXplStack();
            String text = String.join(";", stack);
            assertTrue(text.contains("row=2,col=1"), "xplStack should point to actual cell B2, but was: " + text);
        }
    }

    /**
     * list（二维表）模式下，若模板定义了 virtual 的 unknownField，
     * 表头中无法匹配到字段的未知列应被路由到 unknownField 而非抛 unknown-field 错误。
     * （与对象模式 parseField 的 unknownField 兜底行为对齐）
     */
    @Test
    public void testListModeSkipsUnknownColumnWithUnknownField() {
        ExcelTable table = new ExcelTable();
        cell(table, 0, 0, "f1");
        cell(table, 0, 1, "extraCol"); // 未知列
        cell(table, 0, 2, "f2");
        cell(table, 1, 0, "a");
        cell(table, 1, 1, "x");
        cell(table, 1, 2, "b");

        ImportFieldModel f1 = field("f1");
        ImportFieldModel f2 = field("f2");
        ImportFieldModel skip = field("__skip");
        skip.setVirtual(true);

        ImportSheetModel model = new ImportSheetModel();
        model.setName("test");
        model.setList(true);
        model.setNoSeqCol(true);
        model.setField("items");
        model.setFields(new ArrayList<>(Arrays.asList(f1, f2)));
        model.setUnknownField(skip);
        model.init();

        RecordingListener listener = new RecordingListener();
        parser().parse("test", table, model, listener);

        // 未知列 extraCol 被路由到 virtual 的 unknownField（列头顺序保持 f1, __skip, f2），
        // 不再抛错；下游 ImportDataCollector 对 virtual 字段不会写入实体，即完成"跳过"
        assertEquals(Arrays.asList("f1=a@1,0", "__skip=x@1,1", "f2=b@1,2"), listener.events);
    }

    /**
     * 回归保护：list 模式下若模板未定义 unknownField，未知列（非首列）仍应抛 unknown-field 错误，
     * 确保新增加 unknownField 兜底不会 silently 吞掉本应报错的数据问题。
     */
    @Test
    public void testListModeUnknownColumnThrowsWithoutUnknownField() {
        ExcelTable table = new ExcelTable();
        cell(table, 0, 0, "f1");
        cell(table, 0, 1, "extraCol");
        cell(table, 0, 2, "f2");
        cell(table, 1, 0, "a");
        cell(table, 1, 1, "x");
        cell(table, 1, 2, "b");

        ImportFieldModel f1 = field("f1");
        ImportFieldModel f2 = field("f2");

        ImportSheetModel model = new ImportSheetModel();
        model.setName("test");
        model.setList(true);
        model.setNoSeqCol(true);
        model.setField("items");
        model.setFields(new ArrayList<>(Arrays.asList(f1, f2)));
        model.init();

        try {
            parser().parse("test", table, model, new RecordingListener());
            fail("should fail because unknown column has no unknownField defined");
        } catch (NopException e) {
            assertEquals(ERR_IMPORT_UNKNOWN_FIELD.getErrorCode(), e.getErrorCode());
        }
    }
}
