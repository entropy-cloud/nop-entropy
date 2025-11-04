package io.nop.core.stat;

import java.util.List;

public interface IJdbcStatManager {
    JdbcSqlStat getJdbcSqlStat(String sql);

    /**
     * 返回所有SQL的统计信息。
     *
     * @param orderByAvgTime 是否按照消耗时间排序，如果为false，则按照sql的字符串顺序排序
     * @return 所有统计信息
     */
    List<JdbcSqlStatValue> getAllJdbcSqlStat(boolean orderByAvgTime);
}
