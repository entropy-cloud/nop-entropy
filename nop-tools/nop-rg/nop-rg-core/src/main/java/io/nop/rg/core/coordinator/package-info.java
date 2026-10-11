/**
 * 搜索编排：SearchCoordinator（glob 过滤 → 并行遍历 → 两级映射搜索 → 策略选择）、
 * ParallelFileWalker 的消费侧 LineCursor/MatchAggregator 聚合、结果类型 FileMatches/LineMatch/Submatch。
 */
package io.nop.rg.core.coordinator;
