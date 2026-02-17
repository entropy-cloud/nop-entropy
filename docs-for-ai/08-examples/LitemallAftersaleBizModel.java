
package app.mall.service.entity;

import app.mall.dao.AppMallDaoConstants;
import app.mall.dao.entity.LitemallAftersale;
import app.mall.dao.entity.LitemallOrder;
import app.mall.dao.entity.LitemallOrderGoods;
import app.mall.dao.manager.MallLogManager;
import app.mall.dao.mapper.LitemallGoodsProductMapper;
import app.mall.pay.PayRefundRequestBean;
import app.mall.pay.PayService;
import app.mall.service.consts.NotifyType;
import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.exceptions.NopException;
import io.nop.biz.crud.CrudBizModel;
import app.mall.biz.ILitemallAftersaleBiz;
import io.nop.commons.util.DateHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.context.IServiceContext;
import io.nop.integration.api.sms.ISmsSender;
import io.nop.integration.api.sms.SmsMessage;

import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static app.mall.service.AppMallErrors.ERR_AFTERSALE_NOT_ALLOW_REFUND;

@BizModel("LitemallAftersale")
public class LitemallAftersaleBizModel extends CrudBizModel<LitemallAftersale> implements ILitemallAftersaleBiz {

    @Inject
    @Nullable
    ISmsSender smsSender;

    @Inject
    PayService payService;

    @Inject
    MallLogManager logManager;

    @Inject
    LitemallGoodsProductMapper goodsProductMapper;

    public LitemallAftersaleBizModel() {
        setEntityName(LitemallAftersale.class.getName());
    }


    @BizMutation
    public void batchApprove(@Name("ids") Set<String> ids, IServiceContext context) {
        List<LitemallAftersale> list = batchGet(ids, false, context);

        for (LitemallAftersale entity : list) {
            int status = entity.getStatus();
            if (status != AppMallDaoConstants.AFTERSALE_STATUS_REQUEST) {
                continue;
            }
            entity.setStatus((short) AppMallDaoConstants.AFTERSALE_STATUS_APPROVED);
            entity.setHandleTime(DateHelper.currentDateTime());

            // 订单也要更新售后状态
            entity.getOrder().setAftersaleStatus(entity.getStatus());
        }
    }

    @BizMutation
    public void batchReject(@Name("ids") Set<String> ids, IServiceContext context) {
        List<LitemallAftersale> list = batchGet(ids, false, context);

        for (LitemallAftersale entity : list) {
            int status = entity.getStatus();
            if (status != AppMallDaoConstants.AFTERSALE_STATUS_REQUEST) {
                continue;
            }

            entity.setStatus((short) AppMallDaoConstants.AFTERSALE_STATUS_REJECT);
            entity.setHandleTime(DateHelper.currentDateTime());

            entity.getOrder().setAftersaleStatus(entity.getStatus());
        }
    }

    @BizMutation
    public void refund(@Name("id") String id, IServiceContext context) {
        LitemallAftersale entity = get(id, false, context);
        int status = entity.getStatus();
        if (status != AppMallDaoConstants.AFTERSALE_STATUS_APPROVED) {
            throw new NopException(ERR_AFTERSALE_NOT_ALLOW_REFUND);
        }

        LitemallOrder order = entity.getOrder();

        // 微信退款
        PayRefundRequestBean wxPayRefundRequest = new PayRefundRequestBean();
        wxPayRefundRequest.setOutTradeNo(order.getOrderSn());
        wxPayRefundRequest.setOutRefundNo("refund_" + order.getOrderSn());
        wxPayRefundRequest.setTotalFee(order.getActualPrice());
        wxPayRefundRequest.setRefundFee(entity.getAmount());

        // 如果失败会抛出异常
        payService.refund(ApiRequest.build(wxPayRefundRequest)).get();

        entity.setStatus((short) AppMallDaoConstants.AFTERSALE_STATUS_REFUND);
        entity.setHandleTime(DateHelper.currentDateTime());
        entity.getOrder().setAftersaleStatus(entity.getStatus());

        // NOTE
        // 如果是“退货退款”类型的售后，这里退款说明用户的货已经退回，则需要商品货品数量增加
        // 开发者也可以删除一下代码，在其他地方增加商品货品入库操作
        if (entity.getType() == AppMallDaoConstants.AFTERSALE_TYPE_GOODS_REQUIRED) {
            Set<LitemallOrderGoods> orderGoodsList = entity.getOrder().getOrderGoods();
            for (LitemallOrderGoods orderGoods : orderGoodsList) {
                String productId = orderGoods.getProductId();
                Short number = orderGoods.getNumber();
                // 使用set x = x + delta语法，允许并发编辑。这是少数使用Mapper直接执行EQL语句的场景
                goodsProductMapper.addStock(productId, number);
            }
        }

        // 发送短信通知，这里采用异步发送
        // 退款成功通知用户, 例如“您申请的订单退款 [ 单号:{1} ] 已成功，请耐心等待到账。”
        // TODO 注意订单号只发后6位
        if (smsSender != null) {
            SmsMessage smsMessage = new SmsMessage();
            smsMessage.setMobile(order.getMobile());
            smsMessage.setType(NotifyType.REFUND.ordinal());
            smsMessage.setParams(Arrays.asList(StringHelper.tail(order.getOrderSn(), 6)));
            smsSender.sendMessage(smsMessage);
        }

        logManager.logOrderSucceed("退款", "订单编号 " + order.getOrderSn() + " 售后编号 " + entity.getAftersaleSn());
    }
}