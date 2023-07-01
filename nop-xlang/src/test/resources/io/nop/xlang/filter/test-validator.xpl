<!--
validator的结构由/nop/schema/validator.xdef元模型定义。过滤条件格式由/nop/schema/query/filter.xdef定义
-->
<biz:Validator xpl:lib="/nop/core/xlib/biz.xlib" fatalSeverity="100">
    <!--
    扫码限制:
    1.只有流程卡上流程卡模式字段为流转码”的流程卡才能扫入。不然提示扫入的码不是流转码!
    2.流程卡当前数量>0的流程卡才能扫入，不然提示流转码数量不足!”
    3.判断流程卡上的生产订单是否已关闭，如果已关闭，则不允许扫入，提示"当前订单已关闭!
    4.判断扫入的流转码生产订单与第一个是不是一致，不是的话提示扫入的流转码生产订单不一致!
    5.判断扫入的流程卡物料与第一个是不是一致，不是的话提示扫入的流转码物料不一致”
    -->
    <check id="checkTransferCode" errorCode="test.not-transfer-code" errorDescription="扫入的码不是流转码">
        <eq name="entity.flowMode" value="1"/>
    </check>

    <check id="checkQuantity" errorCode="test.insufficient-quantity" errorDescription="流转码数量不足">
        <gt name="entity.quantity" value="0"/>
    </check>

    <check id="checkProductionOrderOpen" errorCode="test.production-order-closed" errorDescription="当前订单已关闭">
        <isFalse name="entity.productionOrder.closed"/>
    </check>

    <check id="checkProductionOrderConsistent" errorCode="test.inconsistent-production-order"
           errorDescription="扫入的流转码与生产订单不一致">
        <eq name="entity.productionOrder" valueName="firstProductionOrder"/>
    </check>

    <check id="checkMaterial" errorCode="test.inconsistent-material"
           errorDescription="扫入的流转码物料不一致" severity="100">
        <eq name="entity.materialId" valueName="firstMaterial.materialId"/>
    </check>

</biz:Validator>