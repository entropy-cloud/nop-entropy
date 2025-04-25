package io.nop.biz.report.importexport;

public interface IBizEntityExecutorConfig {
    int getConcurrency();

    int getBatchSize();
}
