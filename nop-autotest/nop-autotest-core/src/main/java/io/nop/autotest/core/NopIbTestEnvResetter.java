package io.nop.autotest.core;

import io.nop.core.initialize.CoreInitialization;

/**
 * nop-ib 测试 worker 的 Nop 平台重置钩子（设计 06 的反射契约：可选静态方法
 * resetBefore/resetAfter，零编译依赖——本类不 import 任何 nop-ib 类型）。
 *
 * 契约语义：
 * - resetBefore：平台未初始化时（前一个测试类触发了 destroy 且未自恢复）重初始化，
 *   保证下一个测试类拿到可用的 VFS/IoC 容器；
 * - resetAfter：默认不动作——模块化测试普遍自管 init/destroy，重复 reinitialize
 *   是纯开销；nop-ib 的复用盈亏判据会按实测重置成本决定是否继续复用。
 */
public class NopIbTestEnvResetter {

    public static void resetBefore(String testClass) {
        if (!CoreInitialization.isInitialized()) {
            CoreInitialization.reinitialize();
        }
    }

    public static void resetAfter(String testClass) {
        // no-op by default: tests manage init/destroy themselves (reuseForks-proven)
    }
}
