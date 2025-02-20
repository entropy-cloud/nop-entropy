package io.nop.core.stat;

import java.util.List;

public interface IRpcServerStatManager {
    RpcServerStat getRpcServerStat(String operationName);

    List<RpcServerStat> getAllRpcServerStats(boolean orderByAvgTime);
}
