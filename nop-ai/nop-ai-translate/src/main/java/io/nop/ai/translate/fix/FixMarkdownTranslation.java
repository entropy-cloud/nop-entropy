package io.nop.ai.translate.fix;

import io.nop.ai.core.api.messages.AiChatExchange;
import io.nop.ai.core.commons.debug.DebugMessageHelper;
import io.nop.commons.util.FileHelper;
import io.nop.markdown.simple.MarkdownDocumentParser;
import io.nop.markdown.simple.MarkdownSection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.FileVisitResult;
import java.util.List;

public class FixMarkdownTranslation {
    static final Logger LOG = LoggerFactory.getLogger(FixMarkdownTranslation.class);

    public void fixDir(File sourceDir, File targetDir) {
        FileHelper.walk2(sourceDir, targetDir, (f1, f2) -> {
            if (!f1.getName().endsWith(".md"))
                return FileVisitResult.CONTINUE;

            boolean changed = false;
            List<AiChatExchange> messages = DebugMessageHelper.parseDebugFile(f1);
            for (AiChatExchange message : messages) {
                String sourceText = getSourceText(message);
                String resultText = getResultText(message);

                String fixed = fix(sourceText, resultText);
                if (fixed != null) {
                    changed = true;
                    message.setContent(fixed);

                    LOG.info("nop.ai.translate.fix: source=\n{}\n,translatedText=\n{}\n,fixed=\n{}\n",
                            sourceText, resultText, fixed);
                }
            }

            if (changed)
                DebugMessageHelper.writeDebugFile(f2, messages);

            return FileVisitResult.CONTINUE;
        });
    }

    protected String getSourceText(AiChatExchange message) {
        String text = message.getBlockFromPrompt("待翻译的内容如下：\n", "\n[EndOfData]");
        if (text == null) {
            text = message.getBlockFromPrompt("<TRANSLATE_SOURCE>\n", "\n</TRANSLATE_SOURCE>", 1);
            if (text == null)
                text = message.getBlockFromPrompt("<TRANSLATE_SOURCE>\n", "\n</TRANSLATE_SOURCE>", 0);
        }
        return text;
    }

    protected String getResultText(AiChatExchange message) {
        String content = message.getContent();
        if (content == null)
            content = "";
        content = content.trim();

        if (content.startsWith("<TRANSLATE_RESULT>")) {
            int pos = content.indexOf('\n');
            if (pos < 0)
                return "";

            int pos2 = content.lastIndexOf("</TRANSLATE_RESULT>");
            if (pos2 < pos) {
                return content.substring(pos);
            }

            pos2 = content.lastIndexOf('\n', pos2);
            return content.substring(pos, pos2);
        }
        return content;
    }

    public String fix(String sourceText, String translatedText) {
        if (sourceText == null)
            return null;

        MarkdownDocumentParser parser = new MarkdownDocumentParser();
        List<MarkdownSection> sourceBlocks = parser.parseSections(sourceText);
        List<MarkdownSection> targetBlocks = parser.parseSections(translatedText);

        if (sourceBlocks.size() + 1 == targetBlocks.size()) {
            MarkdownSection targetBlock = targetBlocks.get(0);
            MarkdownSection sourceBlock = sourceBlocks.get(0);
            if (!targetBlock.hasContent() && sourceBlock.getLevel() != targetBlock.getLevel()) {
                targetBlocks.remove(0);
            } else {
                return null;
            }
        } else if (sourceBlocks.size() != targetBlocks.size()) {
            return null;
        }

        boolean changed = false;

        int n = Math.min(sourceBlocks.size(), targetBlocks.size());
        for (int i = 0; i < n; i++) {
            MarkdownSection sourceBlock = sourceBlocks.get(i);
            MarkdownSection targetBlock = targetBlocks.get(i);
            if (targetBlock.getLevel() != sourceBlock.getLevel())
                changed = true;

            targetBlock.setLevel(sourceBlock.getLevel());

            // 第一个block有可能会增加title
            if (i == 0) {
                if (sourceBlock.getLevel() == 0) {
                    targetBlock.setLevel(0);
                    targetBlock.setTitle(null);
                }
            }
        }

        if (!changed)
            return null;

        return buildText(targetBlocks, false);
    }

    String buildText(List<MarkdownSection> blocks, boolean includeTags) {
        StringBuilder sb = new StringBuilder();
        for (MarkdownSection block : blocks) {
            block.buildText(sb, includeTags);
        }

        return sb.toString();
    }
}
