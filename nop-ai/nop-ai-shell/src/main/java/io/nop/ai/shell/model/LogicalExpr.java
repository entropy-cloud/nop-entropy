package io.nop.ai.shell.model;

import java.util.Objects;

/**
 * 逻辑表达式 - 连接两个子表达式
 */
public final class LogicalExpr implements CommandExpression {

    public enum Operator {
        AND("&&", 4),
        OR("||", 3),
        SEMICOLON(";", 2);

        private final String symbol;
        private final int precedence;

        Operator(String symbol, int precedence) {
            this.symbol = symbol;
            this.precedence = precedence;
        }

        public String symbol() {
            return symbol;
        }

        public int precedence() {
            return precedence;
        }

        public static Operator fromSymbol(String symbol) {
            switch (symbol) {
                case "&&": return AND;
                case "||": return OR;
                case ";": return SEMICOLON;
                default: throw new IllegalArgumentException("Unknown operator: " + symbol);
            }
        }

        public static LogicalExpr and(CommandExpression left, CommandExpression right) {
            return new LogicalExpr(left, AND, right);
        }

        public static LogicalExpr or(CommandExpression left, CommandExpression right) {
            return new LogicalExpr(left, OR, right);
        }

        public static LogicalExpr sequence(CommandExpression left, CommandExpression right) {
            return new LogicalExpr(left, SEMICOLON, right);
        }
    }

    private final CommandExpression left;
    private final Operator operator;
    private final CommandExpression right;

    public LogicalExpr(CommandExpression left, Operator operator, CommandExpression right) {
        this.left = Objects.requireNonNull(left, "Left expression cannot be null");
        this.operator = Objects.requireNonNull(operator, "Operator cannot be null");
        this.right = Objects.requireNonNull(right, "Right expression cannot be null");
    }

    public CommandExpression left() {
        return left;
    }

    public Operator operator() {
        return operator;
    }

    public CommandExpression right() {
        return right;
    }

    @Override
    public String toString() {
        String leftStr = formatOperand(left, operator, false);
        String rightStr = formatOperand(right, operator, true);
        return leftStr + " " + operator.symbol() + " " + rightStr;
    }

    private String formatOperand(CommandExpression expr, Operator parentOp, boolean isRight) {
        if (expr instanceof LogicalExpr) {
            LogicalExpr logical = (LogicalExpr) expr;
            int childPrec = logical.operator.precedence();
            int parentPrec = parentOp.precedence();

            if (childPrec < parentPrec || (!isRight && childPrec == parentPrec)) {
                return "(" + expr + ")";
            }
        }
        return expr.toString();
    }

    @Override
    public <T> T accept(CommandVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
