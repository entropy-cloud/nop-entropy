package io.nop.xlang.truffle.translate;

/**
 * 单元级翻译失败消费接口（plan I8 Phase 1 §6 定稿；I9 决策树动态路径第三分支的回调签名）。
 *
 * <p>注册入口 = {@code XLangLanguage.addTranslationFailureListener}（语言实例作用域，
 * SHARED 下跨 Context 生效）；无注册消费者时事件仍进入语言实例内置记录器（显式可查询，
 * 非静默丢弃）。
 */
@FunctionalInterface
public interface TranslationFailureListener {

    /**
     * 单元级翻译失败通知（fail-fast 抛错路径的观测增量；实现不得抛出——抛出会破坏
     * fail-fast 主语义，消费方自行吞掉自身异常）。
     */
    void onTranslationFailure(TranslationFailureEvent event);
}
