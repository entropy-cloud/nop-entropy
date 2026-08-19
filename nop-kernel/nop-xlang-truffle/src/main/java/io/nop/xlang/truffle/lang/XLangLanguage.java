package io.nop.xlang.truffle.lang;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.ContextPolicy;
import com.oracle.truffle.api.TruffleLanguage.Registration;
import com.oracle.truffle.api.source.Source;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.xlang.truffle.eval.EvalHandoff;
import io.nop.xlang.truffle.translate.TranslationCache;
import io.nop.xlang.truffle.translate.TranslatedUnit;

/**
 * XLang 的 Truffle 语言入口（设计 truffle 02 §三）。
 *
 * <p>注册形态（plan I5 决策 D1）：id {@code xl}、无 parser——{@code parse(ParsingRequest)}
 * 按合成 Source 名查翻译缓存（待翻译树经 {@link EvalHandoff} 同线程交接）；本 plan 以
 * EXCLUSIVE 过渡形态落地（翻译正确性对拍保守载体），形态取值为 {@code contextPolicy}
 * 注解编译期常量（下方 {@code ContextPolicy.EXCLUSIVE}）——I8 切 SHARED 仅变更该注解取值后
 * 重新编译，注册结构不重构；{@code initializeMultipleContexts} 继承基类默认实现
 * （EXCLUSIVE 下的最小合规形态，SHARED 切换时按需覆写）。
 *
 * <p>语言实例只存可共享数据：翻译缓存（resourcePath/源内容键 + 树指纹，language 实例作用域，
 * 跨 Context 复用；缓存淘汰归 I8）。
 */
@Registration(id = XLangLanguage.ID, name = "XLang", version = "1.0.0",
        defaultMimeType = XLangLanguage.MIME_TYPE, characterMimeTypes = {XLangLanguage.MIME_TYPE},
        contextPolicy = ContextPolicy.EXCLUSIVE)
public final class XLangLanguage extends TruffleLanguage<XLangContext> {

    public static final String ID = "xl";

    public static final String MIME_TYPE = "application/x-xlang";

    private final TranslationCache translationCache = new TranslationCache();

    public TranslationCache getTranslationCache() {
        return translationCache;
    }

    @Override
    protected XLangContext createContext(Env env) {
        return new XLangContext(env);
    }

    @Override
    protected CallTarget parse(ParsingRequest request) throws Exception {
        Source source = request.getSource();
        EvalHandoff.Pending pending = EvalHandoff.takePending(source.getName());
        IExecutableExpression tree = pending.getTree();
        TranslatedUnit unit = translationCache.getOrBuild(pending.getSourceKey(), tree, this);
        pending.setResolvedUnit(unit);
        return unit.getCallTarget();
    }

    /**
     * 节点侧 context 读取入口（programmatic 节点无 DSL 生成的 ContextReference，
     * 经基类 protected 静态查询开放给同包外节点）。
     */
    public static XLangContext currentContext() {
        return getCurrentContext(XLangLanguage.class);
    }
}
