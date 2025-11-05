package io.nop.graphql.core.biz;

public interface IBizObjectQueryProcessorBuilder {
    <T> IBizObjectQueryProcessor<T> buildQueryProcessor(String bizObjName);
}
