package io.nop.orm.sql;

import io.nop.commons.text.marker.IMarkedString;
import io.nop.core.lang.sql.SQL;
import io.nop.core.lang.sql.SyntaxMarker;

public interface IEntityFilterProvider {
    IMarkedString getEntityFilter(SyntaxMarker marker, SQL sql, ISqlCompileTool compiler);
}
