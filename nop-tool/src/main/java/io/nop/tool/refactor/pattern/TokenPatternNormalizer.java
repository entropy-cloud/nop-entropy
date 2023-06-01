package io.nop.tool.refactor.pattern;

import io.nop.api.core.util.SourceLocation;
import io.nop.commons.text.tokenizer.ITextTokenizer;
import io.nop.commons.text.tokenizer.IToken;
import io.nop.commons.text.tokenizer.TextToken;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class TokenPatternNormalizer implements ITextTokenizer {
    private final Map<String, List<TokenPattern>> allPatterns = new HashMap<>();
    private final ITextTokenizer tokenizer;

    public TokenPatternNormalizer(ITextTokenizer tokenizer) {
        this.tokenizer = tokenizer;
    }

    public void addReplaced(String matched, String text) {
        List<IToken> tokens = tokenizer.tokenizeToList(null, matched);
        TokenPattern pattern = new TokenPattern(tokens, new TextToken(null, text));
        allPatterns.computeIfAbsent(tokens.get(0).getText(), k -> new ArrayList<>()).add(pattern);
    }

    public void addReplacedMap(Map<String, String> map) {
        map.forEach(this::addReplaced);
    }

    @Override
    public Iterator<IToken> tokenize(SourceLocation loc, String text) {
        return replacePatterns(tokenizer.tokenizeToList(loc, text)).iterator();
    }

    public List<IToken> replacePatterns(List<IToken> list) {
        List<IToken> ret = new ArrayList<>(list.size());
        for (int i = 0, n = list.size(); i < n; i++) {
            String text = list.get(i).getText();
            List<TokenPattern> patterns = allPatterns.get(text);
            if (patterns != null) {
                for (TokenPattern pattern : patterns) {
                    if (isMatch(list, i, pattern.getMatched())) {
                        ret.add(pattern.getReplaced());
                        i += pattern.getMatched().size() - 1;
                        break;
                    }
                }
            }
        }
        return ret;
    }

    boolean isMatch(List<IToken> list, int fromIndex, List<IToken> patterns) {
        if (list.size() < fromIndex + patterns.size())
            return false;

        for (int i = 1, n = patterns.size(); i < n; i++) {
            String text = list.get(i + fromIndex).getText();
            String patternText = patterns.get(i).getText();
            if (!text.equals(patternText))
                return false;
        }
        return true;
    }
}
