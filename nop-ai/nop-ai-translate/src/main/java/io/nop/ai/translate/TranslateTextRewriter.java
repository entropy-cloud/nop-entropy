package io.nop.ai.translate;

import io.nop.ai.core.api.processor.IAiTextRewriter;

public class TranslateTextRewriter implements IAiTextRewriter {
    @Override
    public String rewriteRequestText(String originalText) {
        return originalText;
    }

    @Override
    public String correctResponseText(String responseText) {
        if (responseText.startsWith("```markdown")) {
            int pos = responseText.indexOf('\n');
            if (pos < 0)
                return responseText;

            int pos2 = responseText.lastIndexOf("\n```");
            if (pos2 < pos)
                return responseText;

            return responseText.substring(pos + 1, pos2);
        }
        return responseText;
    }
}
