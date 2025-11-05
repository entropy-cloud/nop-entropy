package io.nop.record.match;

public interface IPeekMatchCondition {
    int getOffset();

    int getLength();

    String getOperator();

    String getValue();

    byte[] getBytes();
}
