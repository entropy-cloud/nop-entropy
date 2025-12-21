package io.nop.batch.biz.importexport;

public interface IBizEntityExecutorConfig {
    int getConcurrency();

    int getBatchSize();
}
