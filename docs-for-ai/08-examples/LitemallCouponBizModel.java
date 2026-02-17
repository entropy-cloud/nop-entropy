
package app.mall.service.entity;

import app.mall.biz.ILitemallCouponBiz;
import app.mall.biz.ILitemallCouponUserBiz;
import app.mall.dao.dto.CouponClaimResult;
import app.mall.dao.dto.CouponValidateResult;
import app.mall.dao.entity.LitemallCoupon;
import app.mall.dao.entity.LitemallCouponUser;
import app.mall.service.AppMallErrors;
import io.nop.api.core.annotations.biz.BizAction;
import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.biz.crud.CrudBizModel;
import io.nop.core.context.IServiceContext;
import jakarta.inject.Inject;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static app.mall.dao.AppMallDaoConstants.COUPON_USE_STATUS_UNUSED;

@BizModel("LitemallCoupon")
public class LitemallCouponBizModel extends CrudBizModel<LitemallCoupon> implements ILitemallCouponBiz {

    @Inject
    protected ILitemallCouponUserBiz couponUserBiz;

    public LitemallCouponBizModel() {
        setEntityName(LitemallCoupon.class.getName());
    }

    /**
     * 优惠券码兑换
     */
    @BizMutation
    public CouponClaimResult redeemCouponCode(@Name("code") String code,
                                              IServiceContext context) {
        String userId = context.getUserId();

        // 1. 查找优惠券
        LitemallCoupon coupon = findCouponByCode(code, context);

        // 2. 验证优惠券是否可兑换
        validateCouponForRedeem(coupon);

        // 3. 检查库存
        checkStockAvailable(coupon, context);

        // 4. 检查是否已领取
        checkNotAlreadyClaimed(coupon, userId, context);

        // 5. 创建用户优惠券
        LitemallCouponUser userCoupon = createUserCoupon(coupon, userId);

        couponUserBiz.saveEntity(userCoupon, "redeemCoupon", context);

        return buildClaimResult(userCoupon);
    }

    /**
     * 领取优惠券
     */
    @BizMutation
    public CouponClaimResult claimCoupon(@Name("couponId") String couponId,
                                         IServiceContext context) {
        String userId = context.getUserId();

        // 1. 获取并验证优惠券
        LitemallCoupon coupon = requireEntity(couponId, "read", context);

        // 使用 Entity 的只读方法
        if (!coupon.canBeClaimed()) {
            throw new NopException(AppMallErrors.ERR_COUPON_EXPIRED);
        }

        // 2. 检查库存
        checkStockAvailable(coupon, context);

        // 3. 检查用户领取限制
        checkUserClaimLimit(coupon, userId, context);

        // 4. 创建用户优惠券
        LitemallCouponUser userCoupon = createUserCoupon(coupon, userId);

        couponUserBiz.saveEntity(userCoupon, "claimCoupon", context);

        return buildClaimResult(userCoupon);
    }

    /**
     * 验证优惠券是否可用于订单
     */
    @BizQuery
    public CouponValidateResult validateCouponForOrder(@Name("couponId") String couponId,
                                                       @Name("userCouponId") String userCouponId,
                                                       @Name("orderAmount") BigDecimal orderAmount,
                                                       IServiceContext context) {
        String userId = context.getUserId();

        // 1. 验证优惠券
        LitemallCoupon coupon = requireEntity(couponId, "read", context);
        if (!coupon.isValidStatus() || coupon.isDeleted()) {
            throw new NopException(AppMallErrors.ERR_COUPON_NOT_USABLE);
        }

        // 2. 验证用户优惠券
        LitemallCouponUser userCoupon = couponUserBiz.get(userCouponId, false, context);

        // 使用 Entity 的只读方法进行验证
        if (!userCoupon.isUsable()) {
            throw new NopException(AppMallErrors.ERR_COUPON_NOT_USABLE);
        }

        if (!userCoupon.belongsToUser(userId)) {
            throw new NopException(AppMallErrors.ERR_COUPON_NOT_USABLE);
        }

        // 3. 验证最低消费金额
        if (!coupon.isAmountEligible(orderAmount)) {
            throw new NopException(AppMallErrors.ERR_COUPON_MIN_AMOUNT);
        }

        // 4. 返回验证结果
        return buildValidateResult(coupon, userCoupon);
    }

    /**
     * 验证优惠券商品限制（用于订单商品列表验证）
     * 商品限制类型: 0=全商品, 1=类目限制, 2=商品限制
     */
    @BizAction
    public boolean validateGoodsRestriction(@Name("coupon") LitemallCoupon coupon,
                                            @Name("goodsIds") List<String> goodsIds,
                                            @Name("categoryIds") List<String> categoryIds) {
        // 使用 Entity 的只读方法
        if (coupon.isForAllGoods()) {
            return true;
        }

        String goodsValue = coupon.getGoodsValue();
        if (goodsValue == null || goodsValue.isEmpty()) {
            return true;
        }

        List<String> restrictValues = io.nop.commons.util.StringHelper.split(goodsValue, ',');
        if (restrictValues.isEmpty()) {
            return true;
        }

        if (coupon.isCategoryRestricted()) {
            return hasIntersection(restrictValues, categoryIds);
        } else if (coupon.isGoodsRestricted()) {
            return hasIntersection(restrictValues, goodsIds);
        }

        return true;
    }

    // ==================== 私有帮助方法 ====================

    private LitemallCoupon findCouponByCode(String code, IServiceContext context) {
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq("code", code));
        List<LitemallCoupon> coupons = doFindList(query, null, null, context);

        if (coupons.isEmpty()) {
            throw new NopException(AppMallErrors.ERR_COUPON_NOT_FOUND);
        }
        return coupons.get(0);
    }

    private void validateCouponForRedeem(LitemallCoupon coupon) {
        // 使用 Entity 的只读方法
        if (coupon.isDeleted()) {
            throw new NopException(AppMallErrors.ERR_COUPON_NOT_FOUND);
        }

        if (!coupon.isRedeemableByCode()) {
            throw new NopException(AppMallErrors.ERR_COUPON_NOT_USABLE);
        }

        if (!coupon.isValidStatus()) {
            throw new NopException(AppMallErrors.ERR_COUPON_EXPIRED);
        }

        if (coupon.isExpired()) {
            throw new NopException(AppMallErrors.ERR_COUPON_EXPIRED);
        }
    }

    private void checkStockAvailable(LitemallCoupon coupon, IServiceContext context) {
        if (coupon.isUnlimitedStock()) {
            return;
        }

        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq("couponId", coupon.getId()));
        Long claimedCount = couponUserBiz.findCount(query, context);

        if (claimedCount >= coupon.getTotal()) {
            throw new NopException(AppMallErrors.ERR_COUPON_OUT_OF_STOCK);
        }
    }

    private void checkNotAlreadyClaimed(LitemallCoupon coupon, String userId, IServiceContext context) {
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq("userId", userId));
        query.addFilter(FilterBeans.eq("couponId", coupon.getId()));
        Long claimed = couponUserBiz.findCount(query, context);

        if (claimed > 0) {
            throw new NopException(AppMallErrors.ERR_COUPON_ALREADY_CLAIMED);
        }
    }

    private void checkUserClaimLimit(LitemallCoupon coupon, String userId, IServiceContext context) {
        if (!coupon.hasClaimLimit()) {
            return;
        }

        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq("userId", userId));
        query.addFilter(FilterBeans.eq("couponId", coupon.getId()));
        Long userClaimed = couponUserBiz.findCount(query, context);

        if (userClaimed >= coupon.getClaimLimit()) {
            throw new NopException(AppMallErrors.ERR_COUPON_LIMIT_EXCEEDED);
        }
    }

    private LitemallCouponUser createUserCoupon(LitemallCoupon coupon, String userId) {
        LitemallCouponUser userCoupon = couponUserBiz.newEntity();
        userCoupon.setUserId(userId);
        userCoupon.setCouponId(coupon.getId());
        userCoupon.setStatus((short) COUPON_USE_STATUS_UNUSED);

        // 设置有效期
        if (coupon.isRelativeExpiration()) {
            LocalDateTime now = LocalDateTime.now();
            userCoupon.setStartTime(now);
            userCoupon.setEndTime(now.plusDays(coupon.getExpirationDays()));
        } else {
            userCoupon.setStartTime(coupon.getStartTime());
            userCoupon.setEndTime(coupon.getEndTime());
        }

        return userCoupon;
    }

    private CouponClaimResult buildClaimResult(LitemallCouponUser userCoupon) {
        CouponClaimResult result = new CouponClaimResult();
        result.setSuccess(true);
        result.setUserCouponId(userCoupon.getId());
        return result;
    }

    private CouponValidateResult buildValidateResult(LitemallCoupon coupon, LitemallCouponUser userCoupon) {
        CouponValidateResult result = new CouponValidateResult();
        result.setValid(true);
        result.setCouponId(Integer.valueOf(coupon.getId()));
        result.setUserCouponId(Integer.valueOf(userCoupon.getId()));
        result.setDiscount(coupon.getDiscount());
        result.setMin(coupon.getMin());
        return result;
    }

    private boolean hasIntersection(List<String> list1, List<String> list2) {
        if (list1 == null || list2 == null) {
            return false;
        }
        for (String item : list2) {
            if (list1.contains(item)) {
                return true;
            }
        }
        return false;
    }
}
