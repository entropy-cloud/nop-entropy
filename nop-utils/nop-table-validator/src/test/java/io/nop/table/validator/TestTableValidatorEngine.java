package io.nop.table.validator;

import io.nop.api.core.validate.ListValidationErrorCollector;
import io.nop.core.lang.eval.EvalExprProvider;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.xml.XNode;
import io.nop.core.model.validator.ModelBasedValidator;
import io.nop.core.model.validator.ValidatorCheckModel;
import io.nop.core.model.validator.ValidatorModel;
import io.nop.table.validator.compile.TableValidatorCompiled;
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

    static class AgeRow {
        final int age;
        AgeRow(int age) { this.age = age; }
    }

    static class AgeRowAdaptor implements IRowDataAdaptor<AgeRow> {
        @Override
        public Object getValue(AgeRow row, int columnIndex) {
            return columnIndex == 0 ? row.age : null;
        }
    }

    /**
     * 构建带行级校验的 validator：condition 为 gt(age, 20)，age 不大于 20 的行报错
     */
    static ModelBasedTableValidator<AgeRow> buildAgeValidator() {
        ValidatorModel vm = new ValidatorModel();
        ValidatorCheckModel check = new ValidatorCheckModel();
        check.setErrorCode("test.age-too-small");
        XNode cond = XNode.make("gt");
        cond.setAttr("name", "age");
        cond.setAttr("value", 20);
        check.setCondition(cond);
        vm.addCheck(check);

        ModelBasedValidator rv = new ModelBasedValidator(vm);
        TableValidatorCompiled compiled = new TableValidatorCompiled(
                "age-validator", new ModelBasedValidator[]{rv}, null, null);
        return new ModelBasedTableValidator<>(compiled, new AgeRowAdaptor());
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

    // ==================== 行级校验测试 ====================

    @Test
    public void testRowValidatorResolvesColumns() {
        // 行级校验的 condition 引用列名 age：修复前行数据从未进入 scope，
        // age 解析为 null 导致 gt 恒为 false，每行都误报错
        ModelBasedTableValidator<AgeRow> validator = buildAgeValidator();

        ListValidationErrorCollector collector = new ListValidationErrorCollector();
        validator.beginTable(new String[]{"age"}, collector);
        validator.validateRow(new AgeRow(25), null);
        validator.validateRow(new AgeRow(18), null);
        validator.endTable();

        assertEquals(1, collector.getErrors().size());
        assertEquals("test.age-too-small", collector.getErrors().get(0).getErrorCode());
    }

    @Test
    public void testRowValidatorWithEvalContext() {
        // 带外部上下文调用：行数据覆盖同名变量，同时外部上下文变量（minAge）仍然可见
        ValidatorModel vm = new ValidatorModel();
        ValidatorCheckModel check = new ValidatorCheckModel();
        check.setErrorCode("test.age-too-small");
        XNode cond = XNode.make("gt");
        cond.setAttr("name", "age");
        cond.setAttr("valueName", "minAge");
        check.setCondition(cond);
        vm.addCheck(check);

        ModelBasedValidator rv = new ModelBasedValidator(vm);
        TableValidatorCompiled compiled = new TableValidatorCompiled(
                "age-validator", new ModelBasedValidator[]{rv}, null, null);
        ModelBasedTableValidator<AgeRow> validator = new ModelBasedTableValidator<>(compiled, new AgeRowAdaptor());

        IEvalScope scope = EvalExprProvider.newEvalScope();
        scope.setLocalValue(null, "minAge", 20);

        ListValidationErrorCollector collector = new ListValidationErrorCollector();
        validator.beginTable(new String[]{"age"}, collector);
        validator.validateRow(new AgeRow(25), scope);
        validator.validateRow(new AgeRow(18), scope);
        validator.endTable();

        assertEquals(1, collector.getErrors().size());
        assertEquals("test.age-too-small", collector.getErrors().get(0).getErrorCode());
    }

    @Test
    public void testRowValidatorFromModelCompiler() {
        // 经 TableValidatorCompiler 的模型路径：XML 形态的 <check><condition><gt .../></condition></check>
        // 编译时需解包 condition 包裹节点，行级校验引用列名 age 正常生效
        XNode condWrapper = XNode.make("condition");
        XNode gt = XNode.make("gt");
        gt.setAttr("name", "age");
        gt.setAttr("value", 20);
        condWrapper.appendChild(gt);

        XNode validatorNode = XNode.make("validator");
        XNode checkNode = XNode.make("check");
        checkNode.setAttr("errorCode", "test.age-too-small");
        checkNode.appendChild(condWrapper);
        validatorNode.appendChild(checkNode);

        RowValidatorDef def = new RowValidatorDef();
        def.setId("r1");
        def.setValidator(validatorNode);

        TableValidatorModel model = new TableValidatorModel();
        model.setRowValidators(List.of(def));

        ModelBasedTableValidator<AgeRow> validator = new ModelBasedTableValidator<>(model, new AgeRowAdaptor());

        ListValidationErrorCollector collector = new ListValidationErrorCollector();
        validator.beginTable(new String[]{"age"}, collector);
        validator.validateRow(new AgeRow(25), null);
        validator.validateRow(new AgeRow(18), null);
        validator.endTable();

        assertEquals(1, collector.getErrors().size());
        assertEquals("test.age-too-small", collector.getErrors().get(0).getErrorCode());
    }
}
