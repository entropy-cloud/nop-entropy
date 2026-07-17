package io.nop.table.validator;

import io.nop.api.core.validate.ListValidationErrorCollector;
import io.nop.table.validator.model.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestTableValidatorEngine {

    static class SimpleRow {
        final String name;
        final Double score;
        SimpleRow(String name, Double score) { this.name = name; this.score = score; }
    }

    static class SimpleRowAdaptor implements IRowDataAdaptor<SimpleRow> {
        @Override
        public Object getValue(SimpleRow row, int columnIndex) {
            if (columnIndex == 0) return row.name;
            if (columnIndex == 1) return row.score;
            return null;
        }
    }

    @Test
    public void testStatCheckGePass() {
        TableValidatorModel model = new TableValidatorModel();
        TableStatCheckModel check = new TableStatCheckModel();
        check.setId("s1");
        check.setColumn("score");
        check.setErrorCode("test.score-too-low");
        check.setGeValue(70.0);
        model.setStatChecks(List.of(check));

        ITableValidator<SimpleRow> validator = new ModelBasedTableValidator<>(
                model, new SimpleRowAdaptor());

        ListValidationErrorCollector collector = new ListValidationErrorCollector();
        validator.beginTable(new String[]{"name", "score"}, collector);
        validator.validateRow(new SimpleRow("a", 80.0), null);
        validator.validateRow(new SimpleRow("b", 90.0), null);
        validator.validateRow(new SimpleRow("c", 95.0), null);
        validator.endTable();
        assertEquals(0, collector.getErrors().size());
    }

    @Test
    public void testStatCheckGeFail() {
        TableValidatorModel model = new TableValidatorModel();
        TableStatCheckModel check = new TableStatCheckModel();
        check.setId("s1");
        check.setColumn("score");
        check.setErrorCode("test.score-too-low");
        check.setGeValue(70.0);
        model.setStatChecks(List.of(check));

        ITableValidator<SimpleRow> validator = new ModelBasedTableValidator<>(
                model, new SimpleRowAdaptor());

        ListValidationErrorCollector collector = new ListValidationErrorCollector();
        validator.beginTable(new String[]{"name", "score"}, collector);
        validator.validateRow(new SimpleRow("a", 50.0), null);
        validator.validateRow(new SimpleRow("b", 60.0), null);
        validator.validateRow(new SimpleRow("c", 55.0), null);
        validator.endTable();
        assertEquals(1, collector.getErrors().size());
        assertEquals("test.score-too-low", collector.getErrors().get(0).getErrorCode());
    }

    @Test
    public void testTableCheckRowCount() {
        TableValidatorModel model = new TableValidatorModel();
        TableGlobalCheckModel check = new TableGlobalCheckModel();
        check.setId("t1");
        check.setErrorCode("test.too-few-rows");
        check.setRowCountMin(3);
        model.setTableChecks(List.of(check));

        ITableValidator<SimpleRow> validator = new ModelBasedTableValidator<>(
                model, new SimpleRowAdaptor());

        ListValidationErrorCollector collector = new ListValidationErrorCollector();
        validator.beginTable(new String[]{"name", "score"}, collector);
        validator.validateRow(new SimpleRow("a", 1.0), null);
        validator.validateRow(new SimpleRow("b", 2.0), null);
        validator.endTable();
        assertEquals(1, collector.getErrors().size());
        assertEquals("test.too-few-rows", collector.getErrors().get(0).getErrorCode());
    }

    @Test
    public void testEmptyTable() {
        TableValidatorModel model = new TableValidatorModel();
        ITableValidator<SimpleRow> validator = new ModelBasedTableValidator<>(
                model, new SimpleRowAdaptor());

        ListValidationErrorCollector collector = new ListValidationErrorCollector();
        validator.beginTable(new String[]{"name", "score"}, collector);
        validator.endTable();
        assertEquals(0, collector.getErrors().size());
    }

    @Test
    public void testTableReuseAcrossTables() {
        TableValidatorModel model = new TableValidatorModel();
        TableGlobalCheckModel check = new TableGlobalCheckModel();
        check.setId("t1");
        check.setErrorCode("test.too-few-rows");
        check.setRowCountMin(2);
        model.setTableChecks(List.of(check));

        ITableValidator<SimpleRow> validator = new ModelBasedTableValidator<>(
                model, new SimpleRowAdaptor());

        ListValidationErrorCollector c1 = new ListValidationErrorCollector();
        validator.beginTable(new String[]{"name"}, c1);
        validator.validateRow(new SimpleRow("a", null), null);
        validator.endTable();
        assertEquals(1, c1.getErrors().size());

        ListValidationErrorCollector c2 = new ListValidationErrorCollector();
        validator.beginTable(new String[]{"name"}, c2);
        validator.validateRow(new SimpleRow("a", null), null);
        validator.validateRow(new SimpleRow("b", null), null);
        validator.validateRow(new SimpleRow("c", null), null);
        validator.endTable();
        assertEquals(0, c2.getErrors().size());
    }
}
