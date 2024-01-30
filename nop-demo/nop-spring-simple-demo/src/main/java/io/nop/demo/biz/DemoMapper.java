package io.nop.demo.biz;

import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.orm.SqlLibMapper;
import io.nop.orm.IOrmEntity;

@SqlLibMapper("/nop/demo/sql/demo.sql-lib.xml")
public interface DemoMapper {
    IOrmEntity findFirstByName(@Name("name") String name);
}
