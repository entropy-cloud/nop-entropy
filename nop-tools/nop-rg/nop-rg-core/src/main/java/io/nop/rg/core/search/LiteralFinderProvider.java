package io.nop.rg.core.search;

/**
 * 字面量查找器提供方 SPI（plan 2266 裁定：ServiceLoader 发现机制）。
 *
 * <p>nop-rg-vector 模块经 META-INF/services 注册实现；coordinator VECTOR 策略经
 * {@link java.util.ServiceLoader} 发现。两条失败面可区分：
 * <ul>
 *   <li>ServiceLoader 无 provider = classpath 缺 nop-rg-vector（调用方显式报错）；</li>
 *   <li>{@link #available()} 为 false = 孵化模块未 add-modules（实现内部降级标量，
 *       降级原因经 {@link #unavailableReason()} 可获取，不静默吞）。</li>
 * </ul>
 */
public interface LiteralFinderProvider {

    /**
     * 编译预查找器；实现内部可降级（如 Vector API 不可用时返回标量等价实现）。
     */
    PreparedFinder compile(byte[] pattern, boolean ignoreCase);

    /**
     * 加速实现（Vector API）当前是否可用。false 时 compile 返回标量等价实现。
     */
    boolean available();

    /**
     * available() 为 false 时的原因说明；true 时返回空串。
     */
    String unavailableReason();
}
