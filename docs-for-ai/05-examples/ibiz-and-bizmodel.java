// ====== IBiz 接口 + BizModel 实现（一个文件展示多种参数风格） ======

// ---------- IBiz 接口 ----------
package demo.biz;

import demo.dao.dto.PlaceOrderRequest;
import io.nop.api.core.annotations.biz.BizAction;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.biz.RequestBean;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.core.Optional;
import io.nop.orm.biz.ICrudBiz;
import demo.dao.entity.Order;
import io.nop.core.context.IServiceContext;
import java.math.BigDecimal;

public interface IOrderBiz extends ICrudBiz<Order> {

    /** @RequestBean: 请求字段直接平铺映射到 bean，GraphQL 调用形如 placeOrder(productId:"1", quantity:2) */
    @BizMutation
    Order placeOrder(@RequestBean PlaceOrderRequest request, IServiceContext context);

    /** @Name + @Optional: 单个参数，reason 可选 */
    @BizMutation
    Order cancelOrder(@Name("orderId") String orderId,
                      @Optional @Name("reason") String reason,
                      IServiceContext context);

    /** @Name + @Optional + 非 Entity 返回值 */
    @BizQuery
    boolean checkAvailability(@Name("productId") String productId,
                              @Optional @Name("quantity") Integer quantity,
                              IServiceContext context);

    /** 聚合查询，返回 BigDecimal */
    @BizQuery
    BigDecimal calcUserTotalSpent(@Name("userId") String userId, IServiceContext context);

    /** @BizAction: 内部动作，不暴露 GraphQL 端点。所有方法都可以有返回值也可以没有返回值 */
    @BizAction
    void reserveStock(@Name("productId") String productId,
                      @Name("quantity") int quantity,
                      IServiceContext context);
}

// ---------- BizModel 实现 ----------
package demo.service.entity;

import demo.biz.IOrderBiz;
import demo.biz.IProductBiz;
import demo.dao.dto.PlaceOrderRequest;
import demo.dao.entity.Order;
import demo.dao.entity.Product;
import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.biz.BizAction;
import io.nop.api.core.annotations.biz.RequestBean;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.core.Optional;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.biz.crud.CrudBizModel;
import io.nop.core.context.IServiceContext;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.util.List;

import static demo.service.DemoErrors.*;

@BizModel("Order")
public class OrderBizModel extends CrudBizModel<Order> implements IOrderBiz {

    @Inject
    IProductBiz productBiz;

    public OrderBizModel() {
        setEntityName(Order.class.getName());
    }

    // --- @RequestBean: GraphQL 调用时字段平铺，不嵌套在 data 子对象下 ---
    @Override @BizMutation
    public Order placeOrder(@RequestBean PlaceOrderRequest req, IServiceContext ctx) {
        Product product = productBiz.get(req.getProductId(), false, ctx);
        if (product == null)
            throw new NopException(ERR_PRODUCT_NOT_FOUND).param("productId", req.getProductId());
        if (product.getStock() < req.getQuantity())
            throw new NopException(ERR_STOCK_NOT_ENOUGH)
                    .param("productId", product.getId())
                    .param("need", req.getQuantity())
                    .param("stock", product.getStock());

        reserveStock(product.getId(), req.getQuantity(), ctx);

        Order order = newEntity();
        order.setUserId(ctx.getUserId());
        order.addItem(product, req.getQuantity());
        order.setAddress(req.getAddress());
        saveEntity(order, "placeOrder", ctx);
        return order;
    }

    // --- @Name 单参数 + @Optional ---
    @Override @BizMutation
    public Order cancelOrder(@Name("orderId") String orderId,
                             @Optional @Name("reason") String reason,
                             IServiceContext ctx) {
        Order order = requireEntity(orderId, "cancelOrder", ctx);
        if (!order.isStatus(STATUS_CREATED))
            throw new NopException(ERR_ORDER_STATUS_INVALID)
                    .param("orderId", orderId)
                    .param("currentStatus", order.getStatus());
        order.setStatus(STATUS_CANCELLED);
        if (reason != null) {
            order.setCancelReason(reason);
        }
        // updateEntity 的目的是触发权限检查和 defaultPrepareUpdate 回调，不是因为需要手动 update
        updateEntity(order, "cancelOrder", ctx);
        return order;
    }

    // --- @Optional quantity: 不传查有无货，传了查够不够 ---
    @Override @BizQuery
    public boolean checkAvailability(@Name("productId") String productId,
                                     @Optional @Name("quantity") Integer quantity,
                                     IServiceContext ctx) {
        Product product = productBiz.get(productId, false, ctx);
        if (product == null || !Boolean.TRUE.equals(product.getOnSale()))
            return false;
        if (quantity == null)
            return product.getStock() != null && product.getStock() > 0;
        return product.getStock() != null && product.getStock() >= quantity;
    }

    // --- 返回 BigDecimal 聚合值，非实体 ---
    @Override @BizQuery
    public BigDecimal calcUserTotalSpent(@Name("userId") String userId, IServiceContext ctx) {
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(Order.PROP_NAME_userId, userId));
        q.addFilter(FilterBeans.eq(Order.PROP_NAME_status, STATUS_DONE));
        List<Order> orders = findList(q, null, ctx);
        return orders.stream()
                .map(Order::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // --- @BizAction: 内部动作，仅供其他方法或 XPL 调用 ---
    @Override @BizAction
    public void reserveStock(@Name("productId") String productId,
                             @Name("quantity") int quantity,
                             IServiceContext ctx) {
        Product product = productBiz.get(productId, true, ctx);
        product.setStock(product.getStock() - quantity);
        // 无需 updateEntity: NopOrm 自动脏检查，session flush 时持久化
    }
}

// ====== 第二个 BizModel: 演示生命期钩子 + sql-lib Mapper ======

// ---------- 简单 IBiz（纯 CRUD，无需自定义方法） ----------
package demo.biz;

import io.nop.orm.biz.ICrudBiz;
import demo.dao.entity.Product;

public interface IProductBiz extends ICrudBiz<Product> {}

// ---------- BizModel: 钩子示例 ----------
package demo.service.entity;

import demo.biz.IProductBiz;
import demo.dao.entity.Product;
import demo.dao.entity.ProductItem;
import demo.dao.mapper.ProductMapper;
import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.exceptions.NopException;
import io.nop.biz.crud.CrudBizModel;
import io.nop.biz.crud.EntityData;
import io.nop.core.context.IServiceContext;
import jakarta.inject.Inject;

import java.util.List;
import java.util.stream.Collectors;

import static demo.service.DemoErrors.ERR_PRODUCT_ON_SALE;

@BizModel("Product")
public class ProductBizModel extends CrudBizModel<Product> implements IProductBiz {

    @Inject
    ProductMapper productMapper;

    public ProductBizModel() {
        setEntityName(Product.class.getName());
    }

    /** 保存前: 计算派生字段 */
    @Override
    protected void defaultPrepareSave(EntityData<Product> data, IServiceContext ctx) {
        data.getEntity().syncPrice();
    }

    /** 更新前: 计算派生字段 + 同步脏数据到关联表 */
    @Override
    protected void defaultPrepareUpdate(EntityData<Product> data, IServiceContext ctx) {
        data.getEntity().syncPrice();

        List<ProductItem> dirty = data.getEntity().getItems().stream()
                .filter(ProductItem::orm_dirty)
                .collect(Collectors.toList());
        // 手动 flush: 因为后续 productMapper 直接执行 SQL 访问数据库，
        // 必须先把 ORM session 中的脏数据刷入数据库，否则 mapper 看到的是旧数据
        orm().flushSession();
        dirty.forEach(item -> productMapper.syncCartPrice(item));
    }

    /** 删除前: 业务校验 */
    @Override
    protected void defaultPrepareDelete(@Name("entity") Product entity, IServiceContext ctx) {
        if (Boolean.TRUE.equals(entity.getOnSale()))
            throw new NopException(ERR_PRODUCT_ON_SALE).param("productId", entity.getId());
    }
}

// 要点:
// 1. NopOrm 自动脏检查，修改实体属性后无需手动 update，session flush 时自动持久化
//    updateEntity/saveEntity 的存在是为了触发权限检查和钩子回调(defaultPrepareUpdate 等)
//    内部 @BizAction 不需要权限检查时，直接改属性即可
// 2. defaultPrepareSave/Update/Delete 是 CrudBizModel 的钩子，在标准 CRUD 流程中自动触发
//    自定义 @BizMutation 方法不会自动触发钩子，需自行决定是否调用 updateEntity
// 3. @RequestBean: 参数较多（>5）时用 @RequestBean + @DataBean DTO 封装
//    参数少时直接用 @Name 标注每个参数
// 4. @Optional: 前端可不传，方法内判空 | 非 @Optional: 框架自动校验必传
// 5. 返回值可以是 Entity / boolean / BigDecimal / List 等，不必总是 Entity
// 6. @BizAction 不暴露为 GraphQL 端点，仅供内部调用
// 7. @Inject 不能用 private
// 8. orm().flushSession() 仅在后续需要直接执行 SQL（如 mapper）时才需要，
//    目的是先同步 ORM 脏数据到数据库，否则 SQL 看到的是旧数据。一般不需要手动 flush
