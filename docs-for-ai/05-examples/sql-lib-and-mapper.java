-- ====== SQL 库: _vfs/demo/sql/Product.sql-lib.xml ======
<?xml version="1.0" encoding="UTF-8"?>
<sql-lib x:schema="/nop/schema/orm/sql-lib.xdef" xmlns:x="/nop/schema/xdsl.xdef">
    <sqls>
        <eql name="syncCartPrice" sqlMethod="execute">
            <arg name="product"/>
            <source>
                update Cart o
                set o.price = ${product.price},
                    o.productName = ${product.name}
                where o.productId = ${product.id}
            </source>
        </eql>
    </sqls>
</sql-lib>

// ====== Mapper 接口 ======
package demo.dao.mapper;

import demo.dao.entity.Product;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.orm.SqlLibMapper;

@SqlLibMapper("/demo/sql/Product.sql-lib.xml")
public interface ProductMapper {
    void syncCartPrice(@Name("product") Product product);
}

// BizModel 中使用: @Inject ProductMapper productMapper;  productMapper.syncCartPrice(product);
