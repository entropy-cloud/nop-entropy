package io.nop.datav.service.chatbi;

import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.report.dao.entity.NopReportDataset;

/**
 * ChatBI 数据集可见性判定（P1-03 修复，plan 2026-08-15-2146-1，裁定 D4 选项 B）。
 *
 * <p>可见性模型：admin 角色全量可见；非 admin 仅 {@code NopReportDataset.createdBy == 当前 operator}
 * 可见。身份（operator + admin 标志）经 {@link ChatBiToolExecuteContext} 显式传递（镜像裁定 G 的
 * 强转耦合契约），由 {@code NopDatavChatBiBizModel} 从 {@code IServiceContext} 解析——executor
 * 不读线程变量，单一事实来源。</p>
 *
 * <p>消费约定：list 侧静默过滤（枚举不泄露存在性）；describe/query 侧显式拒绝
 * （{@code ERR_DATAV_CHATBI_DATASET_NO_ACCESS}）。无身份（operator 空且非 admin）时 fail-closed
 * （不可见任何数据集）。</p>
 */
public final class ChatBiDatasetVisibility {

    private ChatBiDatasetVisibility() {
    }

    /**
     * 从工具执行上下文解析 operator（仅 {@link ChatBiToolExecuteContext} 携带；其他实现返回 null）。
     */
    public static String resolveOperator(IToolExecuteContext context) {
        if (context instanceof ChatBiToolExecuteContext) {
            return ((ChatBiToolExecuteContext) context).getOperator();
        }
        return null;
    }

    /**
     * 从工具执行上下文解析 admin 标志（仅 {@link ChatBiToolExecuteContext} 携带；其他实现恒 false）。
     */
    public static boolean resolveAdmin(IToolExecuteContext context) {
        return context instanceof ChatBiToolExecuteContext && ((ChatBiToolExecuteContext) context).isAdmin();
    }

    /**
     * 是否携带任何可见性身份（admin 或非空 operator）。无身份 → fail-closed（list 返回空、
     * describe/query 拒绝）。
     */
    public static boolean hasIdentity(String operator, boolean admin) {
        return admin || (operator != null && !operator.isEmpty());
    }

    /**
     * 数据集对给定身份是否可见：admin 全量；非 admin 仅 createdBy 匹配 operator。
     * createdBy 为空的历史数据集对非 admin 不可见（fail-closed，由 admin 重新保存认领）。
     */
    public static boolean isVisible(NopReportDataset dataset, String operator, boolean admin) {
        if (admin) {
            return true;
        }
        return operator != null && operator.equals(dataset.getCreatedBy());
    }
}
