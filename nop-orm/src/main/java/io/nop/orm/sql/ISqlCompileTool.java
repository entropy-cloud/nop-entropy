package io.nop.orm.sql;

import io.nop.orm.eql.ICompiledSql;
import io.nop.orm.eql.IEqlAstTransformer;

public interface ISqlCompileTool {
    ICompiledSql compileSql(String name, String sqlText, boolean disableLogicalDelete,
                            IEqlAstTransformer astTransformer, boolean useCache,
                            boolean allowUnderscoreName, boolean enableFilter);
}
