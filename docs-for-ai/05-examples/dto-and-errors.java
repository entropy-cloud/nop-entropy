// ====== DTO ======
package demo.dao.dto;

import io.nop.api.core.annotations.data.DataBean;

@DataBean
public class PlaceOrderRequest {
    private String productId;
    private int quantity;
    private String address;

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
}

// ====== 错误码 ======
package demo.service;

import io.nop.api.core.exceptions.ErrorCode;
import static io.nop.api.core.exceptions.ErrorCode.define;

public interface DemoErrors {

    ErrorCode ERR_PRODUCT_NOT_FOUND =
            define("demo.err.product.not-found", "商品不存在: productId={productId}");

    ErrorCode ERR_STOCK_NOT_ENOUGH =
            define("demo.err.stock.not-enough", "库存不足: productId={productId}, 需要={need}, 库存={stock}");

    ErrorCode ERR_ORDER_STATUS_INVALID =
            define("demo.err.order.status-invalid", "订单状态不允许此操作: orderId={orderId}, 当前状态={currentStatus}");
}

// 使用: throw new NopException(ERR_STOCK_NOT_ENOUGH).param("productId", id).param("need", n).param("stock", s);
// 消息模板 {paramName} 自动从 .param() 填充
