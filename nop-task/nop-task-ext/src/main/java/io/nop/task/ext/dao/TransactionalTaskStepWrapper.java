package io.nop.task.ext.dao;

import io.nop.api.core.annotations.txn.TransactionPropagation;
import io.nop.dao.txn.ITransactionTemplate;
import io.nop.task.ITaskStep;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.TaskStepReturn;
import io.nop.task.step.DelegateTaskStep;
import org.jetbrains.annotations.NotNull;

public class TransactionalTaskStepWrapper extends DelegateTaskStep {
    private final ITransactionTemplate transactionTemplate;
    private final String txnGroup;
    private final TransactionPropagation propagation;

    private final boolean sync;

    public TransactionalTaskStepWrapper(ITaskStep taskStep, ITransactionTemplate transactionTemplate,
                                        String txnGroup, TransactionPropagation propagation, boolean sync) {
        super(taskStep);
        this.transactionTemplate = transactionTemplate;
        this.txnGroup = txnGroup;
        this.propagation = propagation;
        this.sync = sync;
    }

    @NotNull
    @Override
    public TaskStepReturn execute(ITaskStepRuntime stepRt) {
        if (sync) {
            return transactionTemplate.runInTransaction(txnGroup, propagation, txn -> {
                return getTaskStep().execute(stepRt).sync();
            });
        }

        return TaskStepReturn.ASYNC(null, transactionTemplate.runInTransactionAsync(txnGroup, propagation,
                txn -> {
                    return getTaskStep().execute(stepRt).getReturnPromise();
                }));
    }
}