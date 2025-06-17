package io.nop.ooxml.docx.parse;

public interface IContentHandler {
    void br();

    void beginParagraph();

    void endParagraph();

    void content(String text);

    void image(String imageId);
}
