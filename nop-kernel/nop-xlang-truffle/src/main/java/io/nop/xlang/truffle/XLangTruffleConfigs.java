package io.nop.xlang.truffle;

import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.annotations.core.Locale;
import io.nop.api.core.config.IConfigReference;
import io.nop.api.core.util.SourceLocation;

import static io.nop.api.core.config.AppConfig.varRef;

/**
 * truffle 后端运行时配置（plan I8 Phase 1 §4/§5 定稿；varRef 模式同 {@code XLangConfigs} 先例）。
 *
 * <p>缺省值口径：池大小缺省 = JVM 并行度（{@code availableProcessors()}，即"随并发工作线程规模"
 * 的 live 取值来源，类初始化时求值一次）；两配置的数值调优归 I12（设计 truffle 02 §五"本层
 * 不发明数值"裁定保持）。
 */
@Locale("zh-CN")
public interface XLangTruffleConfigs {
    SourceLocation s_loc = SourceLocation.fromClass(XLangTruffleConfigs.class);

    @Description("Truffle后端Context池最大容量(共享Engine下可同时租借的Context数)")
    IConfigReference<Integer> CFG_TRUFFLE_CONTEXT_POOL_MAX_SIZE = varRef(s_loc,
            "nop.xlang.truffle.context-pool.max-size", Integer.class,
            Runtime.getRuntime().availableProcessors());

    @Description("Truffle翻译缓存最大条目数(超出按LRU淘汰,被淘汰单元下次求值时重翻译)")
    IConfigReference<Integer> CFG_TRUFFLE_TRANSLATION_CACHE_MAX_ENTRIES = varRef(s_loc,
            "nop.xlang.truffle.translation-cache.max-entries", Integer.class, 1024);
}
