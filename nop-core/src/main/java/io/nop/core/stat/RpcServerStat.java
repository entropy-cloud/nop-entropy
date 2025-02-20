package io.nop.core.stat;

public class RpcServerStat extends AbstractExecuteStat {
    private String operationName;

    public RpcServerStat(String operationName) {
        this.operationName = operationName;
    }

    public RpcServerStat() {
    }

    public String getOperationName() {
        return operationName;
    }

    public void setOperationName(String operationName) {
        this.operationName = operationName;
    }
}
