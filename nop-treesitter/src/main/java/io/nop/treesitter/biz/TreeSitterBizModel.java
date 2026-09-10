package io.nop.treesitter.biz;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.context.IServiceContext;
import io.nop.treesitter.TSParser;
import io.nop.treesitter.language.Language;
import io.nop.treesitter.provider.ITreeSitterLanguageProvider;
import jakarta.inject.Inject;

import static io.nop.treesitter.biz.TreeSitterErrors.ARG_LANGUAGE;
import static io.nop.treesitter.biz.TreeSitterErrors.ERR_TREE_SITTER_UNKNOWN_LANGUAGE;

/**
 * GraphQL surface over the tree-sitter runtime: {@code TreeSitter__parseTreeSitter}
 * parses a source string with a registered grammar and returns the tree in the
 * canonical s-expression form.
 */
@BizModel("TreeSitter")
public class TreeSitterBizModel {

    protected ITreeSitterLanguageProvider languageProvider;

    @Inject
    public void setLanguageProvider(ITreeSitterLanguageProvider languageProvider) {
        this.languageProvider = languageProvider;
    }

    @BizQuery
    public String parseTreeSitter(@Name("source") String source, @Name("language") String language,
                                  IServiceContext ctx) {
        Language lang = languageProvider == null ? null : languageProvider.getLanguage(language);
        if (lang == null) {
            throw new NopException(ERR_TREE_SITTER_UNKNOWN_LANGUAGE).param(ARG_LANGUAGE, language);
        }
        return TSParser.parse(lang, source == null ? "" : source).toSExpression();
    }
}
