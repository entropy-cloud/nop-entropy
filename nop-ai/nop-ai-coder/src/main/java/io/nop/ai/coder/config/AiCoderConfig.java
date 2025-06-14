package io.nop.ai.coder.config;

import io.nop.api.core.annotations.data.DataBean;


/**
 * AI Coder 配置类，用于管理和配置不同类型的AI模型参数
 * 遵循JavaBean规范，提供所有字段的getter和setter方法
 */
@DataBean
public class AiCoderConfig {
    // 通用任务的默认模型
    private String model = "default";
    // AI聊天交互模型
    private String chatModel;
    // 代码生成模型
    private String codeModel;
    // 代码索引模型
    private String indexModel;
    // Git提交消息生成模型
    private String commitModel;
    // 用于RAG的嵌入生成模型
    private String embModel;
    // 用于RAG的文档检索模型
    private String recallModel;
    // 片段重新排序模型
    private String chunkModel;
    // 用于RAG的问答模型
    private String qaModel;
    // 视觉-语言多模态模型
    private String vlModel;

    /**
     * 默认构造函数
     */
    public AiCoderConfig() {
    }

    // ---------- Getter 和 Setter 方法 ----------

    /**
     * 获取通用任务的默认模型
     *
     * @return 当前设置的默认模型
     */
    public String getModel() {
        return model;
    }

    /**
     * 设置通用任务的默认模型
     *
     * @param model 要设置的默认模型
     */
    public void setModel(String model) {
        this.model = model;
    }

    /**
     * 获取AI聊天交互模型
     *
     * @return 当前设置的聊天模型
     */
    public String getChatModel() {
        return chatModel;
    }

    /**
     * 设置AI聊天交互模型
     *
     * @param chatModel 要设置的聊天模型
     */
    public void setChatModel(String chatModel) {
        this.chatModel = chatModel;
    }

    /**
     * 获取代码生成模型
     *
     * @return 当前设置的代码生成模型
     */
    public String getCodeModel() {
        return codeModel;
    }

    /**
     * 设置代码生成模型
     *
     * @param codeModel 要设置的代码生成模型
     */
    public void setCodeModel(String codeModel) {
        this.codeModel = codeModel;
    }

    /**
     * 获取代码索引模型
     *
     * @return 当前设置的代码索引模型
     */
    public String getIndexModel() {
        return indexModel;
    }

    /**
     * 设置代码索引模型
     *
     * @param indexModel 要设置的代码索引模型
     */
    public void setIndexModel(String indexModel) {
        this.indexModel = indexModel;
    }

    /**
     * 获取Git提交消息生成模型
     *
     * @return 当前设置的提交消息模型
     */
    public String getCommitModel() {
        return commitModel;
    }

    /**
     * 设置Git提交消息生成模型
     *
     * @param commitModel 要设置的提交消息模型
     */
    public void setCommitModel(String commitModel) {
        this.commitModel = commitModel;
    }

    /**
     * 获取RAG嵌入生成模型
     *
     * @return 当前设置的嵌入生成模型
     */
    public String getEmbModel() {
        return embModel;
    }

    /**
     * 设置RAG嵌入生成模型
     *
     * @param embModel 要设置的嵌入生成模型
     */
    public void setEmbModel(String embModel) {
        this.embModel = embModel;
    }

    /**
     * 获取RAG文档检索模型
     *
     * @return 当前设置的文档检索模型
     */
    public String getRecallModel() {
        return recallModel;
    }

    /**
     * 设置RAG文档检索模型
     *
     * @param recallModel 要设置的文档检索模型
     */
    public void setRecallModel(String recallModel) {
        this.recallModel = recallModel;
    }

    /**
     * 获取片段重新排序模型
     *
     * @return 当前设置的片段重新排序模型
     */
    public String getChunkModel() {
        return chunkModel;
    }

    /**
     * 设置片段重新排序模型
     *
     * @param chunkModel 要设置的片段重新排序模型
     */
    public void setChunkModel(String chunkModel) {
        this.chunkModel = chunkModel;
    }

    /**
     * 获取RAG问答模型
     *
     * @return 当前设置的问答模型
     */
    public String getQaModel() {
        return qaModel;
    }

    /**
     * 设置RAG问答模型
     *
     * @param qaModel 要设置的问答模型
     */
    public void setQaModel(String qaModel) {
        this.qaModel = qaModel;
    }

    /**
     * 获取视觉-语言多模态模型
     *
     * @return 当前设置的多模态模型
     */
    public String getVlModel() {
        return vlModel;
    }

    /**
     * 设置视觉-语言多模态模型
     *
     * @param vlModel 要设置的多模态模型
     */
    public void setVlModel(String vlModel) {
        this.vlModel = vlModel;
    }

}