package io.nop.biz.lib;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.ServiceContextImpl;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.resource.IResource;
import io.nop.core.unittest.BaseTestCase;
import io.nop.xlang.api.XLang;
import io.nop.xlang.ast.XLangOutputMode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestBizValidatorLib extends BaseTestCase {
    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testValidate() {
        IResource xplFile = attachmentResource("test-validator.xpl");
        IEvalAction xpl = XLang.parseXpl(xplFile, XLangOutputMode.none);
        IServiceContext ctx = new ServiceContextImpl();

        ProcessCard entity = new ProcessCard();
        ProductionOrder order = new ProductionOrder();
        order.setOrderId("1");
        entity.setProductionOrder(order);

        Material material = new Material();
        material.setMaterialId("2");
        entity.setMaterialId("3");

        ctx.getEvalScope().setLocalValue("entity", entity);
        ctx.getEvalScope().setLocalValue("firstProductionOrder", "1");
        ctx.getEvalScope().setLocalValue("firstMaterial", material);

        try {
            xpl.invoke(ctx);
        } catch (NopException e) {
            assertEquals("test.inconsistent-material", e.getErrorCode());
        }
    }

    public static class ProcessCard {
        private int flowMode = 1;
        private int quantity = 3;

        private ProductionOrder productionOrder;

        private String materialId;

        public int getFlowMode() {
            return flowMode;
        }

        public void setFlowMode(int flowMode) {
            this.flowMode = flowMode;
        }

        public int getQuantity() {
            return quantity;
        }

        public void setQuantity(int quantity) {
            this.quantity = quantity;
        }

        public ProductionOrder getProductionOrder() {
            return productionOrder;
        }

        public void setProductionOrder(ProductionOrder productionOrder) {
            this.productionOrder = productionOrder;
        }

        public String getMaterialId() {
            return materialId;
        }

        public void setMaterialId(String materialId) {
            this.materialId = materialId;
        }
    }

    public static class ProductionOrder {
        private String orderId;
        private boolean closed;

        public boolean isClosed() {
            return closed;
        }

        public void setClosed(boolean closed) {
            this.closed = closed;
        }

        public String getOrderId() {
            return orderId;
        }

        public void setOrderId(String orderId) {
            this.orderId = orderId;
        }
    }

    public static class Material {
        private String materialId;

        public String getMaterialId() {
            return materialId;
        }

        public void setMaterialId(String materialId) {
            this.materialId = materialId;
        }
    }
}
