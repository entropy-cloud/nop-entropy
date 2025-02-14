package io.nop.ai.translate;

public class TextBlockModifier {
    private String contentPrefix = "中文是一门语言\n";
    private String contentSuffix = "\n法文是一门语言";

    private String resultPrefix = "Chinese is a language";
    private String resultSuffix = "French is a language";

    public String modifyContent(String content) {
        return contentPrefix + content + contentSuffix;
    }

    public String modifyResult(String result) {
        int pos = result.indexOf(resultPrefix);
        if (pos < 0)
            return null;

        int pos2 = result.indexOf('\n', pos);
        if (pos2 < 0)
            return null;

        int pos3 = result.indexOf(resultSuffix, pos2);
        if (pos3 < 0)
            return null;

        if (result.charAt(pos3 - 1) == '\n')
            pos3--;
        return result.substring(pos2 + 1, pos3);
    }
}
