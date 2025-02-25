package io.nop.search.lucene;

import io.nop.api.core.beans.TreeBean;
import io.nop.core.lang.eval.DisabledEvalScope;
import org.apache.lucene.search.Query;

public class LuceneHelper {
    public static Query buildQueryFromFilter(TreeBean filter) {
        if (filter == null)
            return null;

        return new LuceneFilterBeanTransformer().visitRoot(filter, DisabledEvalScope.INSTANCE);
    }
}
