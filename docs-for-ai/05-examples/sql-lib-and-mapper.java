-- ====== SQL 库: _vfs/demo/sql/Product.sql-lib.xml ======
--
-- == 核心认知：sql-lib 使用 XPL 模板，不是 MyBatis ==
-- MyBatis:   ${} = 原样替换(有注入风险)   #{} = 参数绑定
-- XPL sql:   ${} = 自动参数化(转?+JDBC参数,安全)  raw($expr) = 原样拼接
-- 两者默认行为相反。sql-lib 的 <source> 是 xpl-sql 类型(xpl:outputMode="sql")。
--
-- == <eql> 优先于 <sql> ==
-- EQL = 标准 SQL + AutoJoin。EQL 不是新语言，就是在标准 SQL 基础上支持实体关联属性导航：
--   完整支持: SELECT / FROM / WHERE / GROUP BY / HAVING / ORDER BY / 子查询 / 窗口函数
--   扩展:     o.关联属性.字段 → 自动 LEFT JOIN 关联表，无需手写 JOIN ON
--   比 JPQL 更简单: JPQL 要手写 JOIN FETCH，EQL 直接点属性就自动展开
--
--   SELECT o.id, o.customer.name FROM Order o WHERE o.customer.status = 1
--   → 自动 JOIN 客户表，自动处理关联条件
--
--   <eql> ：使用 实体名 + 属性名（驼峰），支持关联属性自动 JOIN
--   <sql> ：直接写原生 SQL，使用 数据库列名（大写蛇形），无实体解析
--   规则：能写 <eql> 就不用 <sql>，<sql> 仅用于 EQL 无法表达的原生方言/复杂聚合
--
-- == XPL sql 模式自动参数化规则 ==
-- source 中是 xpl-sql 模板(即 xpl:outputMode="sql")，CollectSqlOutput 处理输出：
--   ${expr}             → JDBC 参数 ? (默认，防注入)
--   ${raw(expr)}        → 原样拼接 SQL 文本 (RawText 包装，跳过参数化)
--   ${...} 展开 Collection → 展开为多个 ? 参数 (IN 子句)
--   ${...} 展开 ISqlExpr → 自定义 SQL 表达式拼接
-- ${} 外部的纯文本保持为 SQL 字面文本。
--
-- == 输出 raw SQL 的方式 ==
-- 使用 GlobalFunctions.raw() 将值包装为 RawText：
--     SELECT * FROM ${raw(tableName)} WHERE id = ${id}
--   → tableName 原样拼接，id 仍参数化为 ?
--   仅用于动态表名/列名等必须原样拼接的场景。
--
-- == 语法结构：查看 sql-lib.xdef 为准 ==
-- 所有 XDSL 文件以对应 XDEF 为语法权威来源，文档仅辅助说明。
-- 路径: nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/orm/sql-lib.xdef
-- 顶层结构:
--   <sql-lib>
--     <fragments>                    -- 可复用 SQL 片段
--       <fragment id="xx">xpl-sql</fragment>
--     </fragments>
--     <sqls>                          -- SQL 语句定义
--       <sql name=".." sqlMethod="findAll|findFirst|findPage|exists|execute"
--            rowType=".." colNameCamelCase="false" ...>
--         <source xdef:mandatory="true">xpl-sql</source>
--         <fields>                     -- 列类型声明
--           <field name=".." stdSqlType="DATETIME|VARCHAR|.." stdDataType=".."/>
--         </fields>
--         <arg name=".."/>             -- 入参声明
--         <validate-input>xpl</validate-input>
--         <buildRowMapper>xpl-fn</buildRowMapper>
--         <buildResult>xpl-fn</buildResult>
--         <batchLoadSelection>field-selection</batchLoadSelection>
--       </sql>
--       <eql name=".." allowUnderscoreName="false" enableFilter="false" ...>
--         <source xdef:mandatory="true">xpl-sql</source>
--       </eql>
--       <query name="..">              -- 复杂查询(QueryBean 模式)
--         <source xdef:mandatory="true">xpl-node</source>
--       </query>
--     </sqls>
--   </sql-lib>
--
-- == 常用属性速查(sql/eql 共享, 来自 SqlItemModel) ==
-- @name            SQL 片段名称
-- @sqlMethod       执行方法: findAll/findFirst/findPage/exists/execute
-- @rowType         返回行包装类(按字段别名映射)——优先使用，避免返回 Map
-- @colNameCamelCase sql 时是否下划线转驼峰(默认 false)
-- @querySpace      查询空间(对应数据库)
-- @cacheName+@cacheKeyExpr  结果缓存
-- @fetchSize       JDBC fetchSize
-- @timeout         超时毫秒
-- @disableLogicalDelete  禁用逻辑删除过滤
-- @ormEntityRefreshBehavior 实体刷新策略
-- @fields          列类型声明(stdSqlType 控制 DataSet 读取方法)
-- @batchLoadSelection 查询后自动批量加载关联属性
-- @buildRowMapper  自定义行映射(xpl-fn:(sqlItemModel)=>any)
-- @buildResult     行结果后处理(xpl-fn:(row,sqlItemModel)=>any)
--
-- == rowType：用 DTO 封装结果，不要返回 Map ==
-- SmartRowMapper 按 select 别名 → setter 映射，目标类需有默认构造器和 setter。
--   <eql name="sumCost" rowType="demo.dao.mapper.CostSummary">
--     <source>select sum(o.costCpu) as cpu, sum(o.costMemory) as memory from ...</source>
--   </eql>
-- Mapper 方法签名: CostSummary sumCost(...)
-- 目标类: public class CostSummary { int cpu; int memory; // getter/setter }
-- 如果目标类是不可变值类型（无默认构造器），用 buildResult 在 XML 内转换：
--   <eql name="sumCost" buildResult="${io.nop.app.api.resource.ResourceVector.of(row.cpu, row.memory)}">
--     <source>...</source>
--   </eql>
<?xml version="1.0" encoding="UTF-8"?>
<sql-lib x:schema="/nop/schema/orm/sql-lib.xdef" xmlns:x="/nop/schema/xdsl.xdef">
    <sqls>
        <!-- eql 使用实体属性名（驼峰） -->
        <eql name="syncCartPrice" sqlMethod="execute">
            <arg name="product"/>
            <source>
                update Cart o
                set o.price = ${product.price},
                    o.productName = ${product.name}
                where o.productId = ${product.id}
            </source>
        </eql>

        <!-- sql 使用数据库列名（大写蛇形） -->
        <sql name="updateStockDirect" sqlMethod="execute">
            <arg name="id"/>
            <arg name="num"/>
            <source>
                UPDATE litemall_goods_product
                SET NUMBER = NUMBER - ${num},
                    UPDATE_TIME = now()
                WHERE ID = ${id} AND NUMBER >= ${num}
            </source>
        </sql>
    </sqls>
</sql-lib>

-- ====== Mapper 接口 ======
-- 
-- == 如何创建 Mapper：在 ORM 实体上加 tagSet="mapper" ==
-- 不要手写 Mapper 接口和 bean 注册。在 model/*.orm.xml 的实体上加 tagSet="mapper"：
--   <entity name="..." tableName="..." tagSet="mapper" ...>
-- 然后运行 mvn install，codegen 自动生成：
--   1. {ShortName}Mapper.java 空接口（retention，不覆盖已有）
--   2. {ShortName}.sql-lib.xml 空骨架（retention，不覆盖已有）
--   3. _dao.beans.xml 中的 SqlLibProxyFactoryBean 注册（每次 codegen 重新生成）
-- 开发者在生成的 Mapper.java 和 sql-lib.xml 上添加方法和查询即可。
-- 详见 orm-model-design.md "Sql-Lib Mapper 自动生成"。
--
package demo.dao.mapper;

import demo.dao.entity.Product;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.orm.SqlLibMapper;

@SqlLibMapper("/demo/sql/Product.sql-lib.xml")
public interface ProductMapper {
    void syncCartPrice(@Name("product") Product product);
    void updateStockDirect(@Name("id") String id, @Name("num") int num);
}

-- BizModel 中使用: @Inject ProductMapper productMapper;  productMapper.syncCartPrice(product);
