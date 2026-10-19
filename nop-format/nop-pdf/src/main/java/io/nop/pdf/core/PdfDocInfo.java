package io.nop.pdf.core;

/**
 * WI13：PDF 文档元数据（Document Information Dictionary 六字段）。
 * null 字段=未设置（setInfo 时不写入对应键）。
 */
public class PdfDocInfo {
    private String title;
    private String author;
    private String subject;
    private String keywords;
    private String creator;
    private String producer;

    public String getTitle() {
        return title;
    }

    public PdfDocInfo setTitle(String title) {
        this.title = title;
        return this;
    }

    public String getAuthor() {
        return author;
    }

    public PdfDocInfo setAuthor(String author) {
        this.author = author;
        return this;
    }

    public String getSubject() {
        return subject;
    }

    public PdfDocInfo setSubject(String subject) {
        this.subject = subject;
        return this;
    }

    public String getKeywords() {
        return keywords;
    }

    public PdfDocInfo setKeywords(String keywords) {
        this.keywords = keywords;
        return this;
    }

    public String getCreator() {
        return creator;
    }

    public PdfDocInfo setCreator(String creator) {
        this.creator = creator;
        return this;
    }

    public String getProducer() {
        return producer;
    }

    public PdfDocInfo setProducer(String producer) {
        this.producer = producer;
        return this;
    }
}
