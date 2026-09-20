/**
 * JMH 基准（运行方式见模块 README，禁止 exec:java）：Scalar/Glob/CoordinatorEndToEnd/RgCompare/
 * VectorCompare 五组基准 + HotspotProfiler（JFR ExecutionSample 业务热点采样）+ CorpusUtil
 * （固定种子逐字节可再生 corpus）。
 */
package io.nop.rg.benchmark;
