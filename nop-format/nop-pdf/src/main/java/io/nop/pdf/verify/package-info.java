/**
 * PDF 视觉验收支撑工具（acceptance-support primitives, not domain API）。
 *
 * <p>定位裁定（plan 2463）：本包落在生产 jar 中，使 nop-report 侧测试可用普通 test-scope 依赖消费；
 * 断言辅助抛 {@link AssertionError}（供测试代码调用的验收原语），IO/运行时错误沿用
 * {@code NopException.adapt} 惯例，二者分工不以业务错误码通道承载。</p>
 *
 * <p>口径裁定（ai-dev/analysis/2026-10/2026-10-19-pdf-visual-acceptance-toolchain.md）：
 * 默认渲染 DPI=110；抗锯齿归一化 = 逐像素通道容差；golden 快照落 {@code _tmp/} 不入库，
 * 跨环境可移植门是区域结构断言（{@link RegionProbe}）。</p>
 */
package io.nop.pdf.verify;
