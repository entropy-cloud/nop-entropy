package io.nop.biz.dev;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.annotations.core.Locale;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.core.Optional;
import io.nop.api.core.annotations.directive.Auth;
import io.nop.core.stat.GlobalStatManager;
import io.nop.core.stat.JdbcSqlStatValue;
import io.nop.core.stat.RpcClientStat;
import io.nop.core.stat.RpcServerStat;

import java.util.List;

/**
 * JDBC/RPC统计信息包含SQL文本、数据源、慢查询绑定参数等敏感内容，且clearStats为破坏性操作，
 * 因此所有操作均要求admin角色（auth==null时平台按公开访问处理）。
 */
@Locale("zh-CN")
@BizModel("DevStat")
public class DevStatBizModel {
    @BizMutation
    @Auth(roles = "admin")
    @Description("清空所有统计信息")
    public void clearStats() {
        GlobalStatManager.instance().clear();
    }

    @BizQuery
    @Auth(roles = "admin")
    @Description("jdbc调用的统计信息")
    public List<JdbcSqlStatValue> jdbcSqlStats(@Name("orderByAvgTime") @Optional Boolean orderByAvgTime) {
        if (orderByAvgTime == null)
            orderByAvgTime = true;
        return GlobalStatManager.instance().getAllJdbcSqlStat(orderByAvgTime);
    }

    @BizQuery
    @Auth(roles = "admin")
    @Description("rpc服务调用的统计信息")
    public List<RpcServerStat> rpcServerStats(@Name("orderByAvgTime") @Optional Boolean orderByAvgTime) {
        if (orderByAvgTime == null)
            orderByAvgTime = true;
        return GlobalStatManager.instance().getAllRpcServerStats(orderByAvgTime);
    }

    @BizQuery
    @Auth(roles = "admin")
    @Description("rpc客户端调用的统计信息")
    public List<RpcClientStat> rpcClientStats(@Name("orderByAvgTime") @Optional Boolean orderByAvgTime) {
        if (orderByAvgTime == null)
            orderByAvgTime = true;
        return GlobalStatManager.instance().getAllRpcClientStats(orderByAvgTime);
    }
}