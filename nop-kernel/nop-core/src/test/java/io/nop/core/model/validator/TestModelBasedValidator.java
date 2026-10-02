package io.nop.core.model.validator;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.exceptions.NopValidateException;
import io.nop.core.lang.xml.XNode;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestModelBasedValidator {

    public static class Order {
        private int amount = 100;
        private String status = "NEW";

        public int getAmount() {
            return amount;
        }

        public String getStatus() {
            return status;
        }
    }

    private static XNode eqCondition(String name, Object value) {
        XNode node = XNode.make("eq");
        node.setAttr("name", name);
        node.setAttr("value", value);
        return node;
    }

    @Test
    public void testModelLevelConditionFailureAddsDefaultError() {
        ValidatorModel model = new ValidatorModel();
        // 条件不满足（amount != 200）-> 校验失败
        model.setCondition(eqCondition("amount", 200));

        ModelBasedValidator validator = new ModelBasedValidator(model);
        DefaultValidationErrorCollector collector = new DefaultValidationErrorCollector(100);
        validator.validate(io.nop.core.model.query.BeanVariableScope.makeScope(new Order()), collector);

        assertEquals(1, collector.getErrors().size());
        // 未设置 errorCode 时使用缺省错误码
        assertEquals("nop.err.core.validate.check-fail", collector.getErrors().get(0).getErrorCode());
    }

    @Test
    public void testConditionPassAddsNoError() {
        ValidatorModel model = new ValidatorModel();
        model.setCondition(eqCondition("amount", 100));

        ModelBasedValidator validator = new ModelBasedValidator(model);
        DefaultValidationErrorCollector collector = new DefaultValidationErrorCollector(100);
        validator.validate(io.nop.core.model.query.BeanVariableScope.makeScope(new Order()), collector);

        assertEquals(0, collector.getErrors().size());
    }

    @Test
    public void testCheckLevelConditionAndCustomErrorCode() {
        ValidatorModel model = new ValidatorModel();
        ValidatorCheckModel check = new ValidatorCheckModel();
        check.setId("amount-check");
        check.setErrorCode("nop.test.amount-invalid");
        // status=NEW 与条件 DONE 不符 -> check 失败
        check.setCondition(eqCondition("status", "DONE"));
        check.setErrorDescription("amount must be 100");
        check.setSeverity(3);
        model.addCheck(check);

        ModelBasedValidator validator = new ModelBasedValidator(model);
        DefaultValidationErrorCollector collector = new DefaultValidationErrorCollector(100);
        validator.validate(io.nop.core.model.query.BeanVariableScope.makeScope(new Order()), collector);

        assertEquals(1, collector.getErrors().size());
        assertEquals("nop.test.amount-invalid", collector.getErrors().get(0).getErrorCode());
        assertEquals("amount must be 100", collector.getErrors().get(0).getDescription());
        assertEquals(3, collector.getErrors().get(0).getSeverity());
    }

    @Test
    public void testErrorParamsResolvedFromScope() {
        ValidatorModel model = new ValidatorModel();
        model.setCondition(eqCondition("amount", 999));
        Map<String, String> params = new HashMap<>();
        // errorParams: 目标参数名 -> scope 属性路径
        params.put("actualAmount", "amount");
        params.put("currentStatus", "status");
        model.setErrorParams(params);

        ModelBasedValidator validator = new ModelBasedValidator(model);
        DefaultValidationErrorCollector collector = new DefaultValidationErrorCollector(100);
        validator.validate(io.nop.core.model.query.BeanVariableScope.makeScope(new Order()), collector);

        Map<String, Object> errorParams = collector.getErrors().get(0).getParams();
        assertEquals(100, errorParams.get("actualAmount"));
        assertEquals("NEW", errorParams.get("currentStatus"));
    }

    @Test
    public void testValidateWithDefaultCollectorThrowsOnFatal() {
        ValidatorModel model = new ValidatorModel();
        model.setCondition(eqCondition("amount", 999));
        model.setErrorCode("nop.test.must-fail");

        ModelBasedValidator validator = new ModelBasedValidator(model);
        // fatalSeverity 很高时错误被收集，end() 抛 NopValidateException
        try {
            validator.validateWithDefaultCollector(new Order(), 100);
            org.junit.jupiter.api.Assertions.fail("should throw NopValidateException");
        } catch (NopValidateException e) {
            assertTrue(e.getErrors() != null && !e.getErrors().isEmpty());
            assertEquals("nop.test.must-fail", e.getErrors().get(0).getErrorCode());
        }
    }

    @Test
    public void testCheckBizFatalAndStatusPropagated() {
        ValidatorModel model = new ValidatorModel();
        ValidatorCheckModel check = new ValidatorCheckModel();
        check.setErrorCode("nop.test.biz");
        check.setCondition(eqCondition("amount", 999));
        check.setBizFatal(Boolean.TRUE);
        check.setErrorStatus(401);
        model.addCheck(check);

        ModelBasedValidator validator = new ModelBasedValidator(model);
        DefaultValidationErrorCollector collector = new DefaultValidationErrorCollector(100);
        validator.validate(io.nop.core.model.query.BeanVariableScope.makeScope(new Order()), collector);

        var errorBean = collector.getErrors().get(0);
        assertEquals(401, errorBean.getStatus(), "errorStatus should be propagated to ErrorBean");
    }

    @Test
    public void testNoConditionMeansPass() {
        ValidatorModel model = new ValidatorModel();
        ModelBasedValidator validator = new ModelBasedValidator(model);
        DefaultValidationErrorCollector collector = new DefaultValidationErrorCollector(100);
        validator.validate(io.nop.core.model.query.BeanVariableScope.makeScope(new Order()), collector);
        assertEquals(0, collector.getErrors().size(), "null condition should always pass");
    }
}
