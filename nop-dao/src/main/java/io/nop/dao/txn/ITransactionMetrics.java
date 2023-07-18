package io.nop.dao.txn;

public interface ITransactionMetrics {
    void onTransactionOpen(String txnGroup);

    void onTransactionSuccess(String txnGroup);

    void onTransactionFailure(String txnGroup);
}
