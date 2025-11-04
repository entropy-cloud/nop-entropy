package io.nop.xlang.expr;

import io.nop.api.core.util.SourceLocation;
import io.nop.commons.text.tokenizer.TextScanner;
import io.nop.core.reflect.impl.FunctionArgument;
import io.nop.core.type.IGenericType;
import io.nop.core.type.IRawTypeResolver;
import io.nop.core.type.parse.GenericTypeParser;

import java.util.ArrayList;
import java.util.List;

public class ArgDeclarationsParser {
    private GenericTypeParser typeParser = new GenericTypeParser();

    public ArgDeclarationsParser rawTypeResolver(IRawTypeResolver rawTypeResolver) {
        this.typeParser.rawTypeResolver(rawTypeResolver);
        return this;
    }

    public List<FunctionArgument> parseArgDeclarations(SourceLocation loc, String text) {
        TextScanner sc = TextScanner.fromString(loc, text);
        List<FunctionArgument> args = argDeclarations(sc);
        sc.checkEnd();
        return args;
    }

    public List<FunctionArgument> argDeclarations(TextScanner sc) {
        List<FunctionArgument> exprs = new ArrayList<>();
        do {
            FunctionArgument x = argDeclaration(sc);
            exprs.add(x);
        } while (sc.tryMatch(','));
        return exprs;
    }

    FunctionArgument argDeclaration(TextScanner sc) {
        // SourceLocation loc = sc.location();
        String name = sc.nextJavaVar();
        boolean nullable = false;
        if (sc.tryMatch('?'))
            nullable = true;
        sc.match(':');
        IGenericType type = typeParser.parseGenericType(sc);

        FunctionArgument ret = new FunctionArgument();
        ret.setType(type);
        ret.setName(name);
        ret.setNullable(nullable);
        return ret;
    }
}
