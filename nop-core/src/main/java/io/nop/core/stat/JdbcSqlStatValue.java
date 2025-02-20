package io.nop.core.stat;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.beans.ErrorBean;

@DataBean
public class JdbcSqlStatValue {
    private long id;
    private String sql;
    private String dataSource;
    private long executeLastStartTime;

    private long executeBatchSizeTotal;
    private int executeBatchSizeMax;

    private long executeSuccessCount;
    private long executeSpanNanoTotal;
    private long executeSpanNanoMax;
    private int runningCount;
    private int concurrentMax;
    private long resultSetHoldTimeNano;
    private long executeAndResultSetHoldTimeNano;
    private String name;
    private String file;
    private String dbType;

    private long executeNanoSpanMaxOccurTime;

    private long executeErrorCount;
    private ErrorBean executeErrorLast;
    private long executeErrorLastTime;

    private long updateCount;
    private long updateCountMax;
    private long fetchRowCount;
    private long fetchRowCountMax;

    private long inTransactionCount;

    private String lastSlowParameters;

    private long clobOpenCount;
    private long blobOpenCount;
    private long readStringLength;
    private long readBytesLength;

    private long inputStreamOpenCount;
    private long readerOpenCount;
    private long histogram_0_1;
    private long histogram_1_10;
    private int histogram_10_100;
    private int histogram_100_1000;
    private int histogram_1000_10000;
    private int histogram_10000_100000;
    private int histogram_100000_1000000;
    private int histogram_1000000_more;


    private long executeAndResultHoldTime_0_1;
    private long executeAndResultHoldTime_1_10;
    private int executeAndResultHoldTime_10_100;
    private int executeAndResultHoldTime_100_1000;
    private int executeAndResultHoldTime_1000_10000;
    private int executeAndResultHoldTime_10000_100000;
    private int executeAndResultHoldTime_100000_1000000;
    private int executeAndResultHoldTime_1000000_more;

    private long fetchRowCount_0_1;
    private long fetchRowCount_1_10;
    private long fetchRowCount_10_100;
    private int fetchRowCount_100_1000;
    private int fetchRowCount_1000_10000;
    private int fetchRowCount_10000_more;

    private long updateCount_0_1;
    private long updateCount_1_10;
    private long updateCount_10_100;
    private int updateCount_100_1000;
    private int updateCount_1000_10000;
    private int updateCount_10000_more;

    public JdbcSqlStatValue() {

    }

    public long[] getUpdateHistogram() {
        return new long[]{updateCount_0_1, //
                updateCount_1_10, //
                updateCount_10_100, //
                updateCount_100_1000, //
                updateCount_1000_10000, //
                updateCount_10000_more, //
        };
    }

    public long[] getHistogramValues() {
        return new long[]{
                //
                histogram_0_1, //
                histogram_1_10, //
                histogram_10_100, //
                histogram_100_1000, //
                histogram_1000_10000, //
                histogram_10000_100000, //
                histogram_100000_1000000, //
                histogram_1000000_more //
        };
    }

    public long[] getFetchRowCountHistogramValues() {
        return new long[]{
                //
                fetchRowCount_0_1, //
                fetchRowCount_1_10, //
                fetchRowCount_10_100, //
                fetchRowCount_100_1000, //
                fetchRowCount_1000_10000, //
                fetchRowCount_10000_more //
        };
    }

    public long[] getUpdateCountHistogramValues() {
        return new long[]{
                //
                updateCount_0_1, //
                updateCount_1_10, //
                updateCount_10_100, //
                updateCount_100_1000, //
                updateCount_1000_10000, //
                updateCount_10000_more //
        };
    }

    public long[] getExecuteAndResultHoldTimeHistogramValues() {
        return new long[]{
                //
                executeAndResultHoldTime_0_1, //
                executeAndResultHoldTime_1_10, //
                executeAndResultHoldTime_10_100, //
                executeAndResultHoldTime_100_1000, //
                executeAndResultHoldTime_1000_10000, //
                executeAndResultHoldTime_10000_100000, //
                executeAndResultHoldTime_100000_1000000, //
                executeAndResultHoldTime_1000000_more //
        };
    }

    public long getExecuteAvgTime() {
        long totalTime = getExecuteSpanNanoTotal();
        long totalCount = getExecuteCount();
        if (totalCount == 0)
            return 0;
        return totalTime / totalCount;
    }

    public long getExecuteCount() {
        return getExecuteErrorCount() + getExecuteSuccessCount();
    }

    public long getResultSetHoldTimeMilis() {
        return getResultSetHoldTimeNano() / (1000 * 1000);
    }

    public long getExecuteAndResultSetHoldTimeMilis() {
        return getExecuteAndResultSetHoldTimeNano() / (1000 * 1000);
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getSql() {
        return sql;
    }

    public void setSql(String sql) {
        this.sql = sql;
    }

    public String getDataSource() {
        return dataSource;
    }

    public void setDataSource(String dataSource) {
        this.dataSource = dataSource;
    }

    public long getExecuteLastStartTime() {
        return executeLastStartTime;
    }

    public void setExecuteLastStartTime(long executeLastStartTime) {
        this.executeLastStartTime = executeLastStartTime;
    }

    public long getExecuteBatchSizeTotal() {
        return executeBatchSizeTotal;
    }

    public void setExecuteBatchSizeTotal(long executeBatchSizeTotal) {
        this.executeBatchSizeTotal = executeBatchSizeTotal;
    }

    public int getExecuteBatchSizeMax() {
        return executeBatchSizeMax;
    }

    public void setExecuteBatchSizeMax(int executeBatchSizeMax) {
        this.executeBatchSizeMax = executeBatchSizeMax;
    }

    public long getExecuteSuccessCount() {
        return executeSuccessCount;
    }

    public void setExecuteSuccessCount(long executeSuccessCount) {
        this.executeSuccessCount = executeSuccessCount;
    }

    public long getExecuteSpanNanoTotal() {
        return executeSpanNanoTotal;
    }

    public void setExecuteSpanNanoTotal(long executeSpanNanoTotal) {
        this.executeSpanNanoTotal = executeSpanNanoTotal;
    }

    public long getExecuteSpanNanoMax() {
        return executeSpanNanoMax;
    }

    public void setExecuteSpanNanoMax(long executeSpanNanoMax) {
        this.executeSpanNanoMax = executeSpanNanoMax;
    }

    public int getRunningCount() {
        return runningCount;
    }

    public void setRunningCount(int runningCount) {
        this.runningCount = runningCount;
    }

    public int getConcurrentMax() {
        return concurrentMax;
    }

    public void setConcurrentMax(int concurrentMax) {
        this.concurrentMax = concurrentMax;
    }

    public long getResultSetHoldTimeNano() {
        return resultSetHoldTimeNano;
    }

    public void setResultSetHoldTimeNano(long resultSetHoldTimeNano) {
        this.resultSetHoldTimeNano = resultSetHoldTimeNano;
    }

    public long getExecuteAndResultSetHoldTimeNano() {
        return executeAndResultSetHoldTimeNano;
    }

    public void setExecuteAndResultSetHoldTimeNano(long executeAndResultSetHoldTimeNano) {
        this.executeAndResultSetHoldTimeNano = executeAndResultSetHoldTimeNano;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFile() {
        return file;
    }

    public void setFile(String file) {
        this.file = file;
    }

    public String getDbType() {
        return dbType;
    }

    public void setDbType(String dbType) {
        this.dbType = dbType;
    }

    public long getExecuteNanoSpanMaxOccurTime() {
        return executeNanoSpanMaxOccurTime;
    }

    public void setExecuteNanoSpanMaxOccurTime(long executeNanoSpanMaxOccurTime) {
        this.executeNanoSpanMaxOccurTime = executeNanoSpanMaxOccurTime;
    }

    public long getExecuteErrorCount() {
        return executeErrorCount;
    }

    public void setExecuteErrorCount(long executeErrorCount) {
        this.executeErrorCount = executeErrorCount;
    }

    public ErrorBean getExecuteErrorLast() {
        return executeErrorLast;
    }

    public void setExecuteErrorLast(ErrorBean executeErrorLast) {
        this.executeErrorLast = executeErrorLast;
    }

    public long getExecuteErrorLastTime() {
        return executeErrorLastTime;
    }

    public void setExecuteErrorLastTime(long executeErrorLastTime) {
        this.executeErrorLastTime = executeErrorLastTime;
    }

    public long getUpdateCount() {
        return updateCount;
    }

    public void setUpdateCount(long updateCount) {
        this.updateCount = updateCount;
    }

    public long getUpdateCountMax() {
        return updateCountMax;
    }

    public void setUpdateCountMax(long updateCountMax) {
        this.updateCountMax = updateCountMax;
    }

    public long getFetchRowCount() {
        return fetchRowCount;
    }

    public void setFetchRowCount(long fetchRowCount) {
        this.fetchRowCount = fetchRowCount;
    }

    public long getFetchRowCountMax() {
        return fetchRowCountMax;
    }

    public void setFetchRowCountMax(long fetchRowCountMax) {
        this.fetchRowCountMax = fetchRowCountMax;
    }

    public long getInTransactionCount() {
        return inTransactionCount;
    }

    public void setInTransactionCount(long inTransactionCount) {
        this.inTransactionCount = inTransactionCount;
    }

    public String getLastSlowParameters() {
        return lastSlowParameters;
    }

    public void setLastSlowParameters(String lastSlowParameters) {
        this.lastSlowParameters = lastSlowParameters;
    }

    public long getClobOpenCount() {
        return clobOpenCount;
    }

    public void setClobOpenCount(long clobOpenCount) {
        this.clobOpenCount = clobOpenCount;
    }

    public long getBlobOpenCount() {
        return blobOpenCount;
    }

    public void setBlobOpenCount(long blobOpenCount) {
        this.blobOpenCount = blobOpenCount;
    }

    public long getReadStringLength() {
        return readStringLength;
    }

    public void setReadStringLength(long readStringLength) {
        this.readStringLength = readStringLength;
    }

    public long getReadBytesLength() {
        return readBytesLength;
    }

    public void setReadBytesLength(long readBytesLength) {
        this.readBytesLength = readBytesLength;
    }

    public long getInputStreamOpenCount() {
        return inputStreamOpenCount;
    }

    public void setInputStreamOpenCount(long inputStreamOpenCount) {
        this.inputStreamOpenCount = inputStreamOpenCount;
    }

    public long getReaderOpenCount() {
        return readerOpenCount;
    }

    public void setReaderOpenCount(long readerOpenCount) {
        this.readerOpenCount = readerOpenCount;
    }

    public long getHistogram_0_1() {
        return histogram_0_1;
    }

    public void setHistogram_0_1(long histogram_0_1) {
        this.histogram_0_1 = histogram_0_1;
    }

    public long getHistogram_1_10() {
        return histogram_1_10;
    }

    public void setHistogram_1_10(long histogram_1_10) {
        this.histogram_1_10 = histogram_1_10;
    }

    public int getHistogram_10_100() {
        return histogram_10_100;
    }

    public void setHistogram_10_100(int histogram_10_100) {
        this.histogram_10_100 = histogram_10_100;
    }

    public int getHistogram_100_1000() {
        return histogram_100_1000;
    }

    public void setHistogram_100_1000(int histogram_100_1000) {
        this.histogram_100_1000 = histogram_100_1000;
    }

    public int getHistogram_1000_10000() {
        return histogram_1000_10000;
    }

    public void setHistogram_1000_10000(int histogram_1000_10000) {
        this.histogram_1000_10000 = histogram_1000_10000;
    }

    public int getHistogram_10000_100000() {
        return histogram_10000_100000;
    }

    public void setHistogram_10000_100000(int histogram_10000_100000) {
        this.histogram_10000_100000 = histogram_10000_100000;
    }

    public int getHistogram_100000_1000000() {
        return histogram_100000_1000000;
    }

    public void setHistogram_100000_1000000(int histogram_100000_1000000) {
        this.histogram_100000_1000000 = histogram_100000_1000000;
    }

    public int getHistogram_1000000_more() {
        return histogram_1000000_more;
    }

    public void setHistogram_1000000_more(int histogram_1000000_more) {
        this.histogram_1000000_more = histogram_1000000_more;
    }

    public long getExecuteAndResultHoldTime_0_1() {
        return executeAndResultHoldTime_0_1;
    }

    public void setExecuteAndResultHoldTime_0_1(long executeAndResultHoldTime_0_1) {
        this.executeAndResultHoldTime_0_1 = executeAndResultHoldTime_0_1;
    }

    public long getExecuteAndResultHoldTime_1_10() {
        return executeAndResultHoldTime_1_10;
    }

    public void setExecuteAndResultHoldTime_1_10(long executeAndResultHoldTime_1_10) {
        this.executeAndResultHoldTime_1_10 = executeAndResultHoldTime_1_10;
    }

    public int getExecuteAndResultHoldTime_10_100() {
        return executeAndResultHoldTime_10_100;
    }

    public void setExecuteAndResultHoldTime_10_100(int executeAndResultHoldTime_10_100) {
        this.executeAndResultHoldTime_10_100 = executeAndResultHoldTime_10_100;
    }

    public int getExecuteAndResultHoldTime_100_1000() {
        return executeAndResultHoldTime_100_1000;
    }

    public void setExecuteAndResultHoldTime_100_1000(int executeAndResultHoldTime_100_1000) {
        this.executeAndResultHoldTime_100_1000 = executeAndResultHoldTime_100_1000;
    }

    public int getExecuteAndResultHoldTime_1000_10000() {
        return executeAndResultHoldTime_1000_10000;
    }

    public void setExecuteAndResultHoldTime_1000_10000(int executeAndResultHoldTime_1000_10000) {
        this.executeAndResultHoldTime_1000_10000 = executeAndResultHoldTime_1000_10000;
    }

    public int getExecuteAndResultHoldTime_10000_100000() {
        return executeAndResultHoldTime_10000_100000;
    }

    public void setExecuteAndResultHoldTime_10000_100000(int executeAndResultHoldTime_10000_100000) {
        this.executeAndResultHoldTime_10000_100000 = executeAndResultHoldTime_10000_100000;
    }

    public int getExecuteAndResultHoldTime_100000_1000000() {
        return executeAndResultHoldTime_100000_1000000;
    }

    public void setExecuteAndResultHoldTime_100000_1000000(int executeAndResultHoldTime_100000_1000000) {
        this.executeAndResultHoldTime_100000_1000000 = executeAndResultHoldTime_100000_1000000;
    }

    public int getExecuteAndResultHoldTime_1000000_more() {
        return executeAndResultHoldTime_1000000_more;
    }

    public void setExecuteAndResultHoldTime_1000000_more(int executeAndResultHoldTime_1000000_more) {
        this.executeAndResultHoldTime_1000000_more = executeAndResultHoldTime_1000000_more;
    }

    public long getFetchRowCount_0_1() {
        return fetchRowCount_0_1;
    }

    public void setFetchRowCount_0_1(long fetchRowCount_0_1) {
        this.fetchRowCount_0_1 = fetchRowCount_0_1;
    }

    public long getFetchRowCount_1_10() {
        return fetchRowCount_1_10;
    }

    public void setFetchRowCount_1_10(long fetchRowCount_1_10) {
        this.fetchRowCount_1_10 = fetchRowCount_1_10;
    }

    public long getFetchRowCount_10_100() {
        return fetchRowCount_10_100;
    }

    public void setFetchRowCount_10_100(long fetchRowCount_10_100) {
        this.fetchRowCount_10_100 = fetchRowCount_10_100;
    }

    public int getFetchRowCount_100_1000() {
        return fetchRowCount_100_1000;
    }

    public void setFetchRowCount_100_1000(int fetchRowCount_100_1000) {
        this.fetchRowCount_100_1000 = fetchRowCount_100_1000;
    }

    public int getFetchRowCount_1000_10000() {
        return fetchRowCount_1000_10000;
    }

    public void setFetchRowCount_1000_10000(int fetchRowCount_1000_10000) {
        this.fetchRowCount_1000_10000 = fetchRowCount_1000_10000;
    }

    public int getFetchRowCount_10000_more() {
        return fetchRowCount_10000_more;
    }

    public void setFetchRowCount_10000_more(int fetchRowCount_10000_more) {
        this.fetchRowCount_10000_more = fetchRowCount_10000_more;
    }

    public long getUpdateCount_0_1() {
        return updateCount_0_1;
    }

    public void setUpdateCount_0_1(long updateCount_0_1) {
        this.updateCount_0_1 = updateCount_0_1;
    }

    public long getUpdateCount_1_10() {
        return updateCount_1_10;
    }

    public void setUpdateCount_1_10(long updateCount_1_10) {
        this.updateCount_1_10 = updateCount_1_10;
    }

    public long getUpdateCount_10_100() {
        return updateCount_10_100;
    }

    public void setUpdateCount_10_100(long updateCount_10_100) {
        this.updateCount_10_100 = updateCount_10_100;
    }

    public int getUpdateCount_100_1000() {
        return updateCount_100_1000;
    }

    public void setUpdateCount_100_1000(int updateCount_100_1000) {
        this.updateCount_100_1000 = updateCount_100_1000;
    }

    public int getUpdateCount_1000_10000() {
        return updateCount_1000_10000;
    }

    public void setUpdateCount_1000_10000(int updateCount_1000_10000) {
        this.updateCount_1000_10000 = updateCount_1000_10000;
    }

    public int getUpdateCount_10000_more() {
        return updateCount_10000_more;
    }

    public void setUpdateCount_10000_more(int updateCount_10000_more) {
        this.updateCount_10000_more = updateCount_10000_more;
    }
}
