package io.nop.biz.dev;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.annotations.core.Locale;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.core.Optional;
import io.nop.core.stat.GlobalStatManager;
import io.nop.core.stat.JdbcSqlStatValue;
import io.nop.core.stat.RpcClientStat;
import io.nop.core.stat.RpcServerStat;

import java.util.List;

@Locale("zh-CN")
@BizModel("DevStat")
public class DevStatBizModel {
    @BizQuery
    @Description("jdbc调用的统计信息")
    public List<JdbcSqlStatValue> jdbcSqlStats(@Name("orderByAvgTime") @Optional Boolean orderByAvgTime) {
        if (orderByAvgTime == null)
            orderByAvgTime = true;
        return GlobalStatManager.instance().getAllJdbcSqlStat(orderByAvgTime);
    }

    @BizQuery
    @Description("rpc服务调用的统计信息")
    public List<RpcServerStat> rpcServerStats(@Name("orderByAvgTime") @Optional Boolean orderByAvgTime) {
        if (orderByAvgTime == null)
            orderByAvgTime = true;
        return GlobalStatManager.instance().getAllRpcServerStats(orderByAvgTime);
    }

    @BizQuery
    @Description("rpc客户端调用的统计信息")
    public List<RpcClientStat> rpcClientStats(@Name("orderByAvgTime") @Optional Boolean orderByAvgTime) {
        if (orderByAvgTime == null)
            orderByAvgTime = true;
        return GlobalStatManager.instance().getAllRpcClientStats(orderByAvgTime);
    }
}