// ====== 1. 纯逻辑测试（BaseTestCase，不需要数据库和 IoC） ======
package demo.service;

import io.nop.core.unittest.BaseTestCase;
import io.nop.core.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TestPriceCalc extends BaseTestCase {

    @BeforeAll
    static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testRecalcTotalPrice() {
        Order order = new Order();
        order.addItem(new BigDecimal("10.00"), 2);
        order.addItem(new BigDecimal("5.00"), 3);
        order.recalcTotalPrice();

        assertEquals(0, new BigDecimal("35.00").compareTo(order.getTotalPrice()));
    }
}

// 要点:
// - BaseTestCase: 不需要数据库和 IoC，只测纯逻辑
// - CoreInitialization.initialize()/destroy() 在 @BeforeAll/@AfterAll 中初始化/销毁平台核心
// - 适合测试实体领域方法、工具类、纯计算逻辑
// - 比 JunitBaseTestCase 更轻量，不启动容器

// ====== 2. 简单集成测试（JunitBaseTestCase，需要容器+DB） ======
package demo.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import demo.dao.entity.Product;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestProduct extends JunitBaseTestCase {

    @Inject
    IDaoProvider daoProvider;

    @Test
    public void testSave() {
        IEntityDao<Product> dao = daoProvider.daoFor(Product.class);
        Product p = dao.newEntity();
        p.setName("test");
        p.setPrice(java.math.BigDecimal.TEN);
        dao.saveEntity(p);

        Product loaded = dao.get(p.orm_idString());
        assertEquals("test", loaded.getName());
        assertEquals(0, java.math.BigDecimal.TEN.compareTo(loaded.getPrice()));
    }
}

// 要点:
// - JunitBaseTestCase: 不需要快照录制，只需容器+DB
// - @NopTestConfig(localDb=true): 使用 H2 内存库
// - initDatabaseSchema=TRUE: 首次创建表结构
// - 直接用 DAO 操作，JUnit 断言

// ====== 2. 快照录制回放测试（JunitAutoTestCase） ======
// 快照数据目录结构:
//   _cases/包名/TestClassName/方法名/
//     input/
//       request.json5          ← 输入请求
//       tables/product.csv     ← 种子数据(CSV)
//     output/
//       response.json5         ← 录制的输出
//       tables/product.csv     ← 录制的DB状态(带_chgType列)

package demo.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.ast.GraphQLOperationType;
import io.nop.graphql.core.engine.IGraphQLEngine;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestProductBizSnapshot extends JunitAutoTestCase {

    @Inject
    IGraphQLEngine graphQLEngine;

    @Test
    public void testFindPage() {
        // input(): 从 _cases/.../input/request.json5 读取输入
        ApiRequest<?> request = input("request.json5", ApiRequest.class);

        IGraphQLExecutionContext ctx = graphQLEngine.newRpcContext(
                GraphQLOperationType.query, "Product__findPage", request);
        ApiResponse<?> result = graphQLEngine.executeRpc(ctx);

        // output(): 录制模式保存到 _cases/.../output/; 校验模式自动比对
        output("response.json5", result);
    }

    @Test
    public void testSave() {
        ApiRequest<?> request = input("request.json5", ApiRequest.class);

        IGraphQLExecutionContext ctx = graphQLEngine.newRpcContext(
                GraphQLOperationType.mutation, "Product__save", request);
        ApiResponse<?> result = graphQLEngine.executeRpc(ctx);

        output("response.json5", result);
    }
}

// 要点:
// - JunitAutoTestCase: 支持快照录制/校验
// - input("file", Type): 从 _cases/.../input/ 读取输入文件(json5/yaml)
// - output("file", obj): 录制模式保存结果; 校验模式自动比对
// - 首次录制: @NopTestConfig(snapshotTest=SnapshotTest.RECORDING)
// - 日常校验: 默认 CHECKING 模式，自动加载快照数据到 H2 并比对输出
// - @var:xxx 占位符: 输出文件中用 @var:Product@id 匹配动态生成的ID

// ====== 3. 多步骤业务流程测试 ======
package demo.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.ast.GraphQLOperationType;
import io.nop.graphql.core.engine.IGraphQLEngine;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestOrderFlow extends JunitAutoTestCase {

    @Inject
    IGraphQLEngine graphQLEngine;

    @Test
    public void testPlaceAndCancel() {
        // 步骤 1: 创建商品
        ApiRequest<?> createProduct = input("1_createProduct.json5", ApiRequest.class);
        IGraphQLExecutionContext ctx1 = graphQLEngine.newRpcContext(
                GraphQLOperationType.mutation, "Product__save", createProduct);
        ApiResponse<?> productResult = graphQLEngine.executeRpc(ctx1);
        output("1_productResponse.json5", productResult);

        // 步骤 2: 下单（用编号命名输入输出文件区分步骤）
        ApiRequest<?> placeOrder = input("2_placeOrder.json5", ApiRequest.class);
        IGraphQLExecutionContext ctx2 = graphQLEngine.newRpcContext(
                GraphQLOperationType.mutation, "Order__placeOrder", placeOrder);
        ApiResponse<?> orderResult = graphQLEngine.executeRpc(ctx2);
        output("2_orderResponse.json5", orderResult);

        // 步骤 3: 取消订单
        ApiRequest<?> cancelOrder = input("3_cancelOrder.json5", ApiRequest.class);
        IGraphQLExecutionContext ctx3 = graphQLEngine.newRpcContext(
                GraphQLOperationType.mutation, "Order__cancelOrder", cancelOrder);
        ApiResponse<?> cancelResult = graphQLEngine.executeRpc(ctx3);
        output("3_cancelResponse.json5", cancelResult);
    }
}

// 要点:
// - 多步骤用编号命名文件: 1_xxx.json5, 2_xxx.json5, 3_xxx.json5
// - 上一步的 @var: 占位符可在下一步的 input 中引用，自动替换为录制时的实际值
// - 适合测试完整业务流程: 创建→操作→验证→清理

// ====== 4. 复杂测试（直接断言 + IGraphQLEngine） ======
package demo.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.dao.api.IDaoProvider;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.ast.GraphQLOperationType;
import io.nop.graphql.core.engine.IGraphQLEngine;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestOrderBizLogic extends JunitBaseTestCase {

    @Inject
    IGraphQLEngine graphQLEngine;

    @Inject
    IDaoProvider daoProvider;

    @Test
    public void testCancelOrderStatusCheck() {
        ApiRequest<Map<String, Object>> request = ApiRequest.build(
                Map.of("orderId", "test-order-1", "reason", "test cancel"));
        IGraphQLExecutionContext ctx = graphQLEngine.newRpcContext(
                GraphQLOperationType.mutation, "Order__cancelOrder", request);

        // executeRpc 同步返回 ApiResponse<?>
        ApiResponse<?> result = graphQLEngine.executeRpc(ctx);
        assertNotNull(result);
        assertEquals(0, result.getStatus());
    }

    @Test
    public void testCheckAvailability() {
        ApiRequest<Map<String, Object>> request = ApiRequest.build(
                Map.of("productId", "p1"));
        IGraphQLExecutionContext ctx = graphQLEngine.newRpcContext(
                GraphQLOperationType.query, "Order__checkAvailability", request);

        ApiResponse<?> result = graphQLEngine.executeRpc(ctx);
        assertFalse((Boolean) result.getData());
    }
}

// 要点:
// - 不需要快照时用 JunitBaseTestCase + 直接 JUnit 断言
// - graphQLEngine.newRpcContext(type, "BizObj__method", request) 调用 BizModel
// - graphQLEngine.executeRpc(ctx) 同步返回 ApiResponse<?>
// - 适合需要精确断言逻辑结果的场景

// ====== 总结: 选哪个基类 ======
// | 场景 | 基类 | 特点 |
// |------|------|------|
// | 纯逻辑（无DB无IoC） | BaseTestCase + CoreInitialization | 最轻量 |
// | 需要容器+DB，不需要快照 | JunitBaseTestCase | @Inject 可用，localDb |
// | 需要录制回放 | JunitAutoTestCase | input/output + _cases 目录 |
// | 多步骤流程 | JunitAutoTestCase | 编号文件 + @var: 占位符 |
// | 需要精确断言 | JunitBaseTestCase | 直接 JUnit assertXxx |
