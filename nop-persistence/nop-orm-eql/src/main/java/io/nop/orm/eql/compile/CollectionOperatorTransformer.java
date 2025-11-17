package io.nop.orm.eql.compile;

import io.nop.commons.util.objects.PropPath;
import io.nop.orm.eql.ast.SqlSingleTableSource;
import io.nop.orm.eql.enums.SqlCollectionOperator;
import io.nop.orm.model.IEntityRelationModel;

public class CollectionOperatorTransformer {
    private final EqlTransformVisitor transformVisitor;

    public CollectionOperatorTransformer(EqlTransformVisitor transformVisitor) {
        this.transformVisitor = transformVisitor;
    }

    public SqlCollectionOperator getCollectionOperator(PropPath propPath) {
        if (propPath.getNext() == null) {
            return null;
        }
        return SqlCollectionOperator.fromText(propPath.getNext().getName());
    }

    public SqlPropJoin addCollectionOperatorJoin(SqlSingleTableSource source, IEntityRelationModel ref,
                                                 SqlCollectionOperator collectionOperator) {
        return null;
    }
}
