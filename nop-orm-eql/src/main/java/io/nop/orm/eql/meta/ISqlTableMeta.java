package io.nop.orm.eql.meta;

import io.nop.dao.dialect.IDialect;

public interface ISqlTableMeta extends ISqlSelectionMeta {
    String getEntityName();

    String getQuerySpace();

    ISqlExprMeta getEntityExprMeta();

    boolean isUseLogicalDelete();

    //   ISqlExprMeta getDeleteFlagPropMeta();

    String getDeleteFlagPropName();

    Object getDeleteFlagValue(boolean b, IDialect dialect);
}
