-- ====== SQL 库: _vfs/demo/sql/Product.sql-lib.xml ======
-- <eql> 与 <sql> 的选择：
--   <eql> ：使用 实体属性名（驼峰, 如 productName），支持自动关联查询
--          Nop 自动转成数据库列名
--   <sql> ：直接写原生 SQL，使用 数据库列名（大写蛇形, 如 PRODUCT_NAME）
--          不经过实体解析，适合复杂原生查询
--   无特殊需求优先用 <eql>，保持与 ORM 实体模型一致
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
