/**
 * CLI 入口（picocli）：参数解析、四种输出模式（ResultPrinter/JsonOutput，rg --json 兼容子集）、
 * --jfr 诊断（JfrSupport）。退出码对齐 rg（0/1/2）。
 *
 * <p>与系统 rg 的 A/B 对比属测试设施，不在命令面内——见 test scope 的
 * {@code SystemRgDelegate}。
 */
package io.nop.rg.cli;
