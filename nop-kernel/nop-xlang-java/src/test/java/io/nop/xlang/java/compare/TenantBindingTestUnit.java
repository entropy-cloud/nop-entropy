package io.nop.xlang.java.compare;

import io.nop.xlang.ast.XLangOutputMode;

/**
 * 租户隔离绑定测试单元（I10 Phase 2，D5 裁定机制的真实构造载体）：
 * 同一路径两租户——基资源（租户 A 视角 = 基树，清单指纹源）与租户 B 覆盖资源
 * （`/_tenant/B/...` 覆盖，经测试 ITenantResourceProvider + DeltaResourceStore 租户分路）。
 * 生成类夹具 = 基树转译产物（租户差异化树结构性无生成类 → 稳态降级的机制依据）。
 */
public final class TenantBindingTestUnit {

    public static final String PATH = "/itest-xlang/tenant/unit.itxpl";

    public static final XLangOutputMode MODE = XLangOutputMode.none;

    public static final String TENANT_A = "tenant-a";

    public static final String TENANT_B = "tenant-b";

    /** 基资源源码：x=10 → 21（与生成类夹具一致） */
    public static final String BASE_SOURCE = "<c:script>x * 2 + 1</c:script>";

    /** 租户 B 覆盖源码：x=10 → 31（与基树不同 → 指纹失配 → 稳态降级解释器） */
    public static final String TENANT_B_SOURCE = "<c:script>x * 3 + 1</c:script>";

    public static final int BASE_RESULT = 21;

    public static final int TENANT_B_RESULT = 31;

    private TenantBindingTestUnit() {
    }
}
