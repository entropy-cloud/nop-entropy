package io.nop.rg.core.coordinator;

import java.util.List;
import java.util.Map;

/**
 * 一次搜索的联合结果（plan 2564 W-05 部分成功语义）：
 * 正常命中按相对路径排序（仅含有命中的文件），单文件失败归入 {@link FileError} 错误项——
 * 首错不弃全，部分结果照常返回；调用方（CLI）消费 errors 决定 stderr 汇总与 exit 2。
 *
 * @param results 有命中的文件结果（相对路径 → 行级聚合，按路径排序）
 * @param errors  失败文件错误项（收集序，可能为空）
 */
public record SearchOutcome(Map<String, FileMatches> results, List<FileError> errors) {

    public SearchOutcome {
        results = Map.copyOf(results);
        errors = List.copyOf(errors);
    }
}
