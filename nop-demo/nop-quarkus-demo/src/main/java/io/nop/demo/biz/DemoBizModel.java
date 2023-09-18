package io.nop.demo.biz;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.util.LogLevel;
import io.nop.core.context.IServiceContext;
import io.nop.demo.domain.Material;
import io.nop.demo.domain.ProcessCard;
import io.nop.demo.domain.ProductionOrder;
import io.nop.log.core.LoggerConfigurator;
import io.nop.xlang.filter.BizValidatorHelper;

import java.util.HashMap;
import java.util.Map;

@BizModel("Demo")
public class DemoBizModel {

    @BizQuery
    public void changeLogLevel(@Name("logLevel") LogLevel logLevel, @Name("loggerName") String loggerName) {
        LoggerConfigurator.instance().changeLogLevel(loggerName, logLevel);
    }


    @BizQuery
    public String testValidate(@Name("orderId") String orderId,
                               @Name("materialId") String materialId,
                               IServiceContext context) {
        ProcessCard entity = loadProcessCard(orderId, materialId);
        Map<String, Object> vars = prepareVars(entity);
        BizValidatorHelper.runValidator("/nop/demo/validator/process-card.validator.xml",
                vars, context);
        return "Valid";
    }

    public ProcessCard loadProcessCard(String orderId, String materialId) {
        ProcessCard entity = new ProcessCard();
        ProductionOrder order = new ProductionOrder();
        order.setOrderId(orderId);
        entity.setProductionOrder(order);

        entity.setMaterialId(materialId);
        return entity;
    }

    private Map<String, Object> prepareVars(ProcessCard card) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("entity", card);
        vars.put("firstProductionOrder", card.getProductionOrder());
        Material material = new Material();
        material.setMaterialId("2");
        vars.put("firstMaterial", material);
        return vars;
    }
}
