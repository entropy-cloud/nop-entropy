package io.nop.orm.eql.enums;

import io.nop.api.core.annotations.core.StaticFactoryMethod;

public enum SqlCollectionOperator {
    SOME("_some"), ALL("_all");

    private final String text;
    private final String pathPattern;

    SqlCollectionOperator(String text) {
        this.text = text;
        this.pathPattern = "." + text + ".";
    }

    public String getText() {
        return text;
    }

    public String getPathPattern() {
        return pathPattern;
    }

    @Override
    public String toString() {
        return getText();
    }

    @StaticFactoryMethod
    public static SqlCollectionOperator fromText(String text) {
        if (SOME.getText().equals(text))
            return SOME;

        if (ALL.getText().equals(text))
            return ALL;

        return null;
    }
}
