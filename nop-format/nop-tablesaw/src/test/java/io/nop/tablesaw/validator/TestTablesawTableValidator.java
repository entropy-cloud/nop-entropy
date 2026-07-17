package io.nop.tablesaw.validator;

import io.nop.api.core.validate.ListValidationErrorCollector;
import io.nop.table.validator.ITableValidator;
import io.nop.table.validator.ModelBasedTableValidator;
import io.nop.table.validator.model.TableGlobalCheckModel;
import io.nop.table.validator.model.TableStatCheckModel;
import io.nop.table.validator.model.TableValidatorModel;
import org.junit.jupiter.api.Test;
import tech.tablesaw.api.DoubleColumn;
import tech.tablesaw.api.Row;
import tech.tablesaw.api.StringColumn;
import tech.tablesaw.api.Table;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TestTablesawTableValidator {

    static Table makeScoreTable() {
        return Table.create("scores",
                StringColumn.create("name", new String[]{"Alice", "Bob", "Charlie", "David"}),
                DoubleColumn.create("score", new double[]{85.0, 92.0, 78.0, 95.0})
        );
    }

    static Table makeLowScoreTable() {
        return Table.create("scores",
                StringColumn.create("name", new String[]{"Alice", "Bob", "Charlie"}),
                DoubleColumn.create("score", new double[]{55.0, 60.0, 58.0})
        );
    }

    @Test
    public void testStatCheckGePass() {
        Table table = makeScoreTable();

        TableValidatorModel model = new TableValidatorModel();
        TableStatCheckModel check = new TableStatCheckModel();
        check.setId("s1");
        check.setColumn("score");
        check.setErrorCode("test.mean-too-low");
        check.setGeValue(70.0);
        model.setStatChecks(List.of(check));

        ITableValidator<Row> validator = new ModelBasedTableValidator<>(
                model, new TablesawRowDataAdaptor());

        ListValidationErrorCollector collector = new ListValidationErrorCollector();
        validator.beginTable(table.columnNames().toArray(new String[0]), collector);
        for (Row row : table) {
            validator.validateRow(row, null);
        }
        validator.endTable();
        assertTrue(collector.getErrors().isEmpty());
    }

    @Test
    public void testStatCheckGeFail() {
        Table table = makeLowScoreTable();

        TableValidatorModel model = new TableValidatorModel();
        TableStatCheckModel check = new TableStatCheckModel();
        check.setId("s1");
        check.setColumn("score");
        check.setErrorCode("test.mean-too-low");
        check.setGeValue(70.0);
        model.setStatChecks(List.of(check));

        ITableValidator<Row> validator = new ModelBasedTableValidator<>(
                model, new TablesawRowDataAdaptor());

        ListValidationErrorCollector collector = new ListValidationErrorCollector();
        validator.beginTable(table.columnNames().toArray(new String[0]), collector);
        for (Row row : table) {
            validator.validateRow(row, null);
        }
        validator.endTable();
        assertEquals(1, collector.getErrors().size());
        assertEquals("test.mean-too-low", collector.getErrors().get(0).getErrorCode());
    }

    @Test
    public void testTableCheckRowCount() {
        Table table = makeScoreTable();

        TableValidatorModel model = new TableValidatorModel();
        TableGlobalCheckModel check = new TableGlobalCheckModel();
        check.setId("t1");
        check.setErrorCode("test.too-few-rows");
        check.setRowCountMin(5);
        model.setTableChecks(List.of(check));

        ITableValidator<Row> validator = new ModelBasedTableValidator<>(
                model, new TablesawRowDataAdaptor());

        ListValidationErrorCollector collector = new ListValidationErrorCollector();
        validator.beginTable(table.columnNames().toArray(new String[0]), collector);
        for (Row row : table) {
            validator.validateRow(row, null);
        }
        validator.endTable();
        assertEquals(1, collector.getErrors().size());
        assertEquals("test.too-few-rows", collector.getErrors().get(0).getErrorCode());
    }

    @Test
    public void testTableCheckRowCountPass() {
        Table table = makeScoreTable();

        TableValidatorModel model = new TableValidatorModel();
        TableGlobalCheckModel check = new TableGlobalCheckModel();
        check.setId("t1");
        check.setErrorCode("test.too-few-rows");
        check.setRowCountMin(3);
        check.setRowCountMax(10);
        model.setTableChecks(List.of(check));

        ITableValidator<Row> validator = new ModelBasedTableValidator<>(
                model, new TablesawRowDataAdaptor());

        ListValidationErrorCollector collector = new ListValidationErrorCollector();
        validator.beginTable(table.columnNames().toArray(new String[0]), collector);
        for (Row row : table) {
            validator.validateRow(row, null);
        }
        validator.endTable();
        assertTrue(collector.getErrors().isEmpty());
    }
}
