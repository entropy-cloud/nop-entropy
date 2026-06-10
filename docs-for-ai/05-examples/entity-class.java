// ====== 1. 简单实体（多数实体只需这样） ======
package demo.dao.entity;

import io.nop.api.core.annotations.biz.BizObjName;
import demo.dao.entity._gen._Product;

@BizObjName("Product")
public class Product extends _Product {
    public Product() {}
}

// ====== 2. 含领域方法 + requireBiz 只读查询关联实体 ======
package demo.dao.entity;

import demo.biz.IOrderItemBiz;
import demo.dao.entity._gen._Order;
import io.nop.api.core.annotations.biz.BizObjName;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.context.IServiceContext;
import io.nop.orm.biz.ICrudBiz;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

@BizObjName("Order")
public class Order extends _Order {
    public Order() {}

    public boolean isStatus(int status) {
        return getOrderStatus() != null && getOrderStatus() == status;
    }

    public void recalcTotalPrice() {
        BigDecimal total = getOrderItems().stream()
                .map(i -> i.getPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        setTotalPrice(total);
    }

    /** 通过 requireBiz 获取关联 Biz，用复杂条件查询关联实体（只读） */
    public List<OrderItem> getActiveItems() {
        IOrderItemBiz biz = requireBiz(IOrderItemBiz.class);
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq("orderId", orm_idString()));
        query.addFilter(FilterBeans.in("status", Arrays.asList(1, 2)));
        return biz.findList(query, null, IServiceContext.requireCtx());
    }

    /** 带缓存的版本，避免重复查询 */
    public List<OrderItem> getActiveItemsCached() {
        return computeIfAbsent("activeItems", k -> {
            IOrderItemBiz biz = requireBiz(IOrderItemBiz.class);
            QueryBean query = new QueryBean();
            query.addFilter(FilterBeans.eq("orderId", orm_idString()));
            query.addFilter(FilterBeans.in("status", Arrays.asList(1, 2)));
            return biz.findList(query, null, IServiceContext.requireCtx());
        });
    }
}

// 要点:
// 1. requireBiz(IBiz.class) 获取关联实体的 Biz
// 2. 只能做只读查询，不能做写操作（写操作必须走 BizModel 保证事务/权限管道）
// 3. IServiceContext.requireCtx() 确保有上下文，无上下文直接抛错而非静默返回 null
// 4. computeIfAbsent(key, fn) 缓存结果到 _t 临时属性，实体 reset/unload 时自动清空
