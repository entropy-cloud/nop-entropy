package io.nop.ai.core.prompt.node;

import io.nop.ai.core.xdef.AiXDefHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.component.parse.AbstractTextResourceParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static io.nop.ai.core.AiCoreConstants.PROMPT_PREFIX_INCLUDE;
import static io.nop.ai.core.AiCoreErrors.ARG_INPUT;
import static io.nop.ai.core.AiCoreErrors.ARG_PREFIX;
import static io.nop.ai.core.AiCoreErrors.ARG_VAR_NAME;
import static io.nop.ai.core.AiCoreErrors.ERR_AI_INVALID_EXPR_VAR_NAME;
import static io.nop.ai.core.AiCoreErrors.ERR_AI_PROMPT_EMPTY_EXPR;
import static io.nop.ai.core.AiCoreErrors.ERR_AI_PROMPT_UNCLOSED_EXPR;
import static io.nop.ai.core.AiCoreErrors.ERR_AI_UNKNOWN_PROMPT_EXPR_PREFIX;

public class PromptSyntaxParser extends AbstractTextResourceParser<IPromptSyntaxNode> {


    private static final Logger log = LoggerFactory.getLogger(PromptSyntaxParser.class);

    public static class TextNode implements IPromptSyntaxNode {
        private final String text;
        private final SourceLocation loc;

        TextNode(SourceLocation loc, String text) {
            this.loc = loc;
            this.text = text;
        }

        public String getText() {
            return text;
        }

        @Override
        public SourceLocation getLocation() {
            return loc;
        }

        @Override
        public void accept(IPromptSyntaxNodeVisitor visitor) {
            visitor.visitText(this);
        }
    }

    public static class VariableNode implements IPromptSyntaxNode {
        private final String varName;
        private final SourceLocation loc;

        VariableNode(SourceLocation loc, String varName) {
            this.loc = loc;
            this.varName = varName;
        }

        public String getVarName() {
            return varName;
        }

        @Override
        public SourceLocation getLocation() {
            return loc;
        }


        @Override
        public void accept(IPromptSyntaxNodeVisitor visitor) {
            visitor.visitVariable(this);
        }
    }

    public static class PrefixNode implements IPromptSyntaxNode {
        private final String prefix;
        private final String arg;
        private final SourceLocation loc;

        PrefixNode(SourceLocation loc, String prefix, String arg) {
            this.loc = loc;
            this.prefix = prefix;
            this.arg = arg;
        }

        public String getPrefix() {
            return prefix;
        }

        public String getArg() {
            return arg;
        }

        @Override
        public SourceLocation getLocation() {
            return loc;
        }

        @Override
        public void accept(IPromptSyntaxNodeVisitor visitor) {
            visitor.visitPrefix(this);
        }
    }

    public static class CompositeNode implements IPromptSyntaxNode {
        private final List<IPromptSyntaxNode> exprs;
        private final SourceLocation loc;

        CompositeNode(SourceLocation loc, List<IPromptSyntaxNode> exprs) {
            this.loc = loc;
            this.exprs = exprs;
        }

        public List<IPromptSyntaxNode> getExprs() {
            return exprs;
        }

        @Override
        public SourceLocation getLocation() {
            return loc;
        }

        @Override
        public void accept(IPromptSyntaxNodeVisitor visitor) {
            visitor.visitComposite(this);
        }
    }

    private boolean enableInclude;
    private boolean allowUnknownPrefix = false;

    public PromptSyntaxParser enableInclude(boolean enableInclude) {
        this.enableInclude = enableInclude;
        return this;
    }

    public PromptSyntaxParser allowUnknownPrefix(boolean allowUnknownPrefix) {
        this.allowUnknownPrefix = allowUnknownPrefix;
        return this;
    }

    protected IPromptSyntaxNode doParseText(SourceLocation loc, String input) {
        if (loc == null)
            loc = SourceLocation.fromPath("text");

        List<IPromptSyntaxNode> exprs = new ArrayList<>();
        parseExprs(exprs, loc, input);

        return new CompositeNode(loc, exprs);
    }

    void parseExprs(List<IPromptSyntaxNode> exprs, SourceLocation loc, String input) {
        if (input == null || input.isEmpty()) {
            return;
        }

        int pos = 0;
        final int len = input.length();

        while (pos < len) {
            // 查找下一个模板开始标记
            int start = input.indexOf("{{", pos);

            // 如果没有更多模板，添加剩余文本
            if (start < 0) {
                addTextNode(exprs, loc, input.substring(pos));
                break;
            }

            // 添加模板前的普通文本
            if (start > pos) {
                String text = input.substring(pos, start);
                addTextNode(exprs, loc, text);
                loc = updateLoc(loc, text, 0);
            }

            // 查找模板结束标记
            int end = input.indexOf("}}", start + 2);

            // 如果没有结束标记，报错并终止
            if (end < 0) {
                throw new NopException(ERR_AI_PROMPT_UNCLOSED_EXPR).loc(loc).param(ARG_INPUT, StringHelper.limitLen(input, start, 100));
            }

            // 检查模板内是否有换行符（不允许跨行）
            int newlinePos = input.indexOf('\n', start + 2);
            if (newlinePos >= 0 && newlinePos < end) {
                throw new NopException(ERR_AI_PROMPT_UNCLOSED_EXPR).loc(loc).param(ARG_INPUT, StringHelper.limitLen(input, start, Math.min(100, newlinePos - start + 5)));
            }

            // 提取模板内容
            String str = input.substring(start + 2, end);
            String templateContent = str.trim();
            if (templateContent.isEmpty()) {
                throw new NopException(ERR_AI_PROMPT_EMPTY_EXPR).loc(loc).param(ARG_INPUT, StringHelper.limitLen(input, start, 100));
            }

            // 解析模板内容
            parseExprContent(exprs, loc, templateContent);

            // 更新位置
            loc = updateLoc(loc, str, 2);
            pos = end + 2;
        }
    }

    // 辅助方法：添加文本节点
    private void addTextNode(List<IPromptSyntaxNode> exprs, SourceLocation loc, String text) {
        if (!text.isEmpty()) {
            exprs.add(new TextNode(loc, text));
        }
    }

    // 辅助方法：解析模板内容
    private void parseExprContent(List<IPromptSyntaxNode> exprs, SourceLocation loc, String content) {
        int colonPos = content.indexOf(':');
        if (colonPos > 0) {
            String prefix = content.substring(0, colonPos).trim();
            String arg = content.substring(colonPos + 1).trim();
            handlePrefixExpr(exprs, loc, prefix, arg);
        } else {
            exprs.add(buildVariableExpr(loc, content));
        }
    }

    SourceLocation updateLoc(SourceLocation loc, String str, int tailChars) {
        int n = StringHelper.countChar(str, '\n');
        if (n < 0)
            return loc.offset(0, str.length());
        int pos = str.lastIndexOf('\n');
        return loc.offset(n, str.length() + tailChars - pos - loc.getCol());
    }

    protected void handlePrefixExpr(List<IPromptSyntaxNode> exprs, SourceLocation loc, String prefix, String arg) {
        if (prefix.equals(PROMPT_PREFIX_INCLUDE)) {
            if (enableInclude) {
                handleIncludeExpr(exprs, loc, arg);
                return;
            }
        } else if (!allowUnknownPrefix) {
            throw new NopException(ERR_AI_UNKNOWN_PROMPT_EXPR_PREFIX)
                    .loc(loc).param(ARG_PREFIX, prefix);
        }
        PrefixNode expr = new PrefixNode(loc, prefix, arg);
        exprs.add(expr);
    }

    protected void handleIncludeExpr(List<IPromptSyntaxNode> exprs, SourceLocation loc, String arg) {
        String path = loc.getPath();
        if (path.equals("text")) {
            path = arg;
        } else {
            path = StringHelper.absolutePath(path, arg);
        }
        if (path.endsWith(".xdef")) {
            AiXDefHelper.loadXDefForAi(path).xml();

        } else {
            IResource resource = VirtualFileSystem.instance().getResource(path);
            String content = resource.readText();
            parseExprs(exprs, resource.location(), content);
        }
    }

    protected VariableNode buildVariableExpr(SourceLocation loc, String varName) {
        if (!StringHelper.isValidPropPath(varName))
            throw new NopException(ERR_AI_INVALID_EXPR_VAR_NAME)
                    .loc(loc).param(ARG_VAR_NAME, varName);
        return new VariableNode(loc, varName);
    }
}