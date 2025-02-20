package io.nop.core.stat;

import java.util.List;

public interface IRpcStatManager {
    RpcClientStat getRpcClientStat(String serviceName, String serviceAction);

    List<RpcClientStat> getAllRpcClientStats(boolean orderByAvgTime);
}
