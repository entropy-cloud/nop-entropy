package io.nop.batch.core.history;

import io.nop.core.lang.eval.IEvalFunction;
import io.nop.core.reflect.hook.IPropGetMissingHook;

public interface IBatchHistoryStoreModel extends IPropGetMissingHook {
    IEvalFunction getRecordKeyExpr();

    IEvalFunction getRecordInfoExpr();

    boolean isOnlySaveLastError();
}
