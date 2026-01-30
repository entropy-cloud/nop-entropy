package io.nop.ai.shell.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.nop.ai.shell.model.CommandFactory.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for bash command line model classes
 */
class CommandModelTest {

    @Test
    void testSimpleCommand() {
        SimpleCommand cmd = SimpleCommand.builder("ls")
                .arg("-l")
                .arg("-a")
                .build();

        assertEquals("ls", cmd.command());
        assertEquals(List.of("-l", "-a"), cmd.args());
        assertTrue(cmd.envVars().isEmpty());
        assertTrue(cmd.redirects().isEmpty());
        assertEquals("ls -l -a", cmd.toString());
    }

    @Test
    void testSimpleCommandWithEnvVars() {
        SimpleCommand cmd = SimpleCommand.builder("echo")
                .envVar("VAR1", "value1")
                .envVar(EnvVar.export("VAR2", "value2"))
                .arg("test")
                .build();

        assertEquals(2, cmd.envVars().size());
        assertEquals(EnvVar.Type.LOCAL, cmd.envVars().get(0).type());
        assertEquals(EnvVar.Type.EXPORT, cmd.envVars().get(1).type());
        assertEquals("VAR1=value1 export VAR2=value2 echo test", cmd.toString());
    }

    @Test
    void testSimpleCommandWithRedirects() {
        SimpleCommand cmd = SimpleCommand.builder("cmd")
                .arg("arg1")
                .redirect(Redirect.stdoutToFile("out.txt"))
                .redirect(Redirect.stderrToStdout())
                .build();

        assertEquals(2, cmd.redirects().size());
        assertEquals(Redirect.Type.OUTPUT, cmd.redirects().get(0).type());
        assertEquals(Redirect.Type.FD_OUTPUT, cmd.redirects().get(1).type());
        assertEquals("cmd arg1 > out.txt 2>&1", cmd.toString());
    }

    @Test
    void testSimpleCommandBuilderNullCommand() {
        assertThrows(NullPointerException.class, () -> SimpleCommand.builder(null));
    }

    @Test
    void testPipelineExpr() {
        SimpleCommand cmd1 = SimpleCommand.builder("cmd1").build();
        SimpleCommand cmd2 = SimpleCommand.builder("cmd2").build();
        SimpleCommand cmd3 = SimpleCommand.builder("cmd3").build();

        PipelineExpr pipeline = PipelineExpr.builder()
                .command(cmd1)
                .command(cmd2)
                .command(cmd3)
                .build();

        assertEquals(3, pipeline.commands().size());
        assertEquals("cmd1 | cmd2 | cmd3", pipeline.toString());
    }

    @Test
    void testPipelineExprRequiresTwoCommands() {
        SimpleCommand cmd1 = SimpleCommand.builder("cmd1").build();

        assertThrows(IllegalArgumentException.class, () ->
            PipelineExpr.builder().command(cmd1).build()
        );
    }

    @Test
    void testLogicalExprAnd() {
        SimpleCommand cmd1 = SimpleCommand.builder("cmd1").build();
        SimpleCommand cmd2 = SimpleCommand.builder("cmd2").build();

        LogicalExpr logical = and(cmd1, cmd2);

        assertEquals(LogicalExpr.Operator.AND, logical.operator());
        assertEquals(cmd1, logical.left());
        assertEquals(cmd2, logical.right());
        assertEquals("cmd1 && cmd2", logical.toString());
    }

    @Test
    void testLogicalExprOr() {
        SimpleCommand cmd1 = SimpleCommand.builder("cmd1").build();
        SimpleCommand cmd2 = SimpleCommand.builder("cmd2").build();

        LogicalExpr logical = or(cmd1, cmd2);

        assertEquals(LogicalExpr.Operator.OR, logical.operator());
        assertEquals("cmd1 || cmd2", logical.toString());
    }

    @Test
    void testLogicalExprSequence() {
        SimpleCommand cmd1 = SimpleCommand.builder("cmd1").build();
        SimpleCommand cmd2 = SimpleCommand.builder("cmd2").build();

        LogicalExpr logical = sequence(cmd1, cmd2);

        assertEquals(LogicalExpr.Operator.SEMICOLON, logical.operator());
        assertEquals("cmd1 ; cmd2", logical.toString());
    }

    @Test
    void testLogicalExprPrecedence() {
        SimpleCommand cmd1 = SimpleCommand.builder("cmd1").build();
        SimpleCommand cmd2 = SimpleCommand.builder("cmd2").build();
        SimpleCommand cmd3 = SimpleCommand.builder("cmd3").build();

        LogicalExpr left = or(cmd1, cmd2);
        LogicalExpr right = sequence(left, cmd3);

        assertEquals("cmd1 || cmd2 ; cmd3", right.toString());
    }

    @Test
    void testLogicalExprSamePrecedenceRight() {
        SimpleCommand cmd1 = SimpleCommand.builder("cmd1").build();
        SimpleCommand cmd2 = SimpleCommand.builder("cmd2").build();
        SimpleCommand cmd3 = SimpleCommand.builder("cmd3").build();

        LogicalExpr left = sequence(cmd1, cmd2);
        LogicalExpr right = sequence(left, cmd3);

        assertEquals("(cmd1 ; cmd2) ; cmd3", right.toString());
    }

    @Test
    void testLogicalExprNullValidation() {
        SimpleCommand cmd1 = SimpleCommand.builder("cmd1").build();

        assertThrows(NullPointerException.class, () -> new LogicalExpr(null, LogicalExpr.Operator.AND, cmd1));
        assertThrows(NullPointerException.class, () -> new LogicalExpr(cmd1, null, cmd1));
        assertThrows(NullPointerException.class, () -> new LogicalExpr(cmd1, LogicalExpr.Operator.AND, null));
    }

    @Test
    void testGroupExpr() {
        SimpleCommand cmd1 = SimpleCommand.builder("echo").arg("start").build();
        SimpleCommand cmd2 = SimpleCommand.builder("ls").build();
        SimpleCommand cmd3 = SimpleCommand.builder("echo").arg("end").build();

        GroupExpr group = group(cmd1, cmd2, cmd3);

        assertEquals(3, group.commands().size());
        assertEquals("{ echo start; ls; echo end; }", group.toString());
    }

    @Test
    void testGroupExprWithRedirects() {
        SimpleCommand cmd1 = SimpleCommand.builder("echo").arg("test").build();
        List<Redirect> redirects = List.of(Redirect.stdoutToFile("out.txt"));

        GroupExpr group = new GroupExpr(List.of(cmd1), redirects);

        assertEquals(1, group.redirects().size());
        assertTrue(group.toString().contains("> out.txt"));
    }

    @Test
    void testGroupExprRequiresCommands() {
        assertThrows(IllegalArgumentException.class, () -> new GroupExpr(List.of(), List.of()));
    }

    @Test
    void testSubshellExpr() {
        SimpleCommand cmd1 = SimpleCommand.builder("cmd1").build();
        SimpleCommand cmd2 = SimpleCommand.builder("cmd2").build();

        LogicalExpr inner = and(cmd1, cmd2);
        SubshellExpr subshell = subshell(inner);

        assertEquals(inner, subshell.inner());
        assertEquals("(cmd1 && cmd2)", subshell.toString());
    }

    @Test
    void testSubshellExprWithRedirects() {
        SimpleCommand cmd = SimpleCommand.builder("cmd").arg("arg").build();
        List<Redirect> redirects = List.of(Redirect.stdoutToFile("out.txt"));

        SubshellExpr subshell = new SubshellExpr(cmd, redirects);

        assertEquals(1, subshell.redirects().size());
        assertEquals("(cmd arg) > out.txt", subshell.toString());
    }

    @Test
    void testSubshellExprNullValidation() {
        assertThrows(NullPointerException.class, () -> new SubshellExpr(null, List.of()));
    }

    @Test
    void testBackgroundExpr() {
        SimpleCommand cmd = SimpleCommand.builder("sleep").arg("10").build();
        BackgroundExpr bg = background(cmd);

        assertEquals(cmd, bg.inner());
        assertEquals("sleep 10 &", bg.toString());
    }

    @Test
    void testBackgroundExprWithLogicalExpr() {
        SimpleCommand cmd1 = SimpleCommand.builder("cmd1").build();
        SimpleCommand cmd2 = SimpleCommand.builder("cmd2").build();

        LogicalExpr logical = and(cmd1, cmd2);
        BackgroundExpr bg = background(logical);

        assertEquals("(cmd1 && cmd2) &", bg.toString());
    }

    @Test
    void testBackgroundExprNullValidation() {
        assertThrows(NullPointerException.class, () -> new BackgroundExpr(null));
    }

    @Test
    void testEnvVarLocal() {
        EnvVar envVar = EnvVar.local("NAME", "value");

        assertEquals("NAME", envVar.name());
        assertEquals("value", envVar.value());
        assertEquals(EnvVar.Type.LOCAL, envVar.type());
        assertFalse(envVar.expand());
    }

    @Test
    void testEnvVarExport() {
        EnvVar envVar = EnvVar.export("PATH", "/bin");

        assertEquals("PATH", envVar.name());
        assertEquals("/bin", envVar.value());
        assertEquals(EnvVar.Type.EXPORT, envVar.type());
        assertFalse(envVar.expand());
    }

    @Test
    void testEnvVarExpand() {
        EnvVar envVar = EnvVar.expand("VAR", "$PATH:/new");

        assertEquals(EnvVar.Type.LOCAL, envVar.type());
        assertTrue(envVar.expand());
    }

    @Test
    void testEnvVarEquals() {
        EnvVar env1 = EnvVar.local("NAME", "value");
        EnvVar env2 = EnvVar.local("NAME", "value");
        EnvVar env3 = EnvVar.local("NAME", "other");

        assertEquals(env1, env2);
        assertNotEquals(env1, env3);
    }

    @Test
    void testRedirectOutput() {
        Redirect redirect = Redirect.stdoutToFile("output.txt");

        assertNull(redirect.sourceFd());
        assertEquals(Redirect.Type.OUTPUT, redirect.type());
        assertEquals("output.txt", redirect.target());
        assertEquals("> output.txt", redirect.toString());
    }

    @Test
    void testRedirectAppend() {
        Redirect redirect = Redirect.stdoutAppend("output.txt");

        assertEquals(Redirect.Type.APPEND, redirect.type());
        assertEquals(">> output.txt", redirect.toString());
    }

    @Test
    void testRedirectInput() {
        Redirect redirect = Redirect.stdinFromFile("input.txt");

        assertEquals(Redirect.Type.INPUT, redirect.type());
        assertEquals("< input.txt", redirect.toString());
    }

    @Test
    void testRedirectStderrToStdout() {
        Redirect redirect = Redirect.stderrToStdout();

        assertEquals(2, redirect.sourceFd());
        assertEquals(Redirect.Type.FD_OUTPUT, redirect.type());
        assertEquals("1", redirect.target());
        assertEquals("2>&1", redirect.toString());
    }

    @Test
    void testRedirectMerge() {
        Redirect redirect = Redirect.mergeToFile("all.log");

        assertNull(redirect.sourceFd());
        assertEquals(Redirect.Type.MERGE, redirect.type());
        assertEquals("&> all.log", redirect.toString());
    }

    @Test
    void testRedirectFdOutput() {
        Redirect redirect = Redirect.fdOutput(2, 1);

        assertEquals(2, redirect.sourceFd());
        assertEquals(Redirect.Type.FD_OUTPUT, redirect.type());
        assertEquals("2>&1", redirect.toString());
    }

    @Test
    void testRedirectNullValidation() {
        assertThrows(NullPointerException.class, () -> new Redirect(1, null, "file"));
        assertThrows(NullPointerException.class, () -> new Redirect(1, Redirect.Type.OUTPUT, null));
    }

    @Test
    void testRedirectEquals() {
        Redirect r1 = Redirect.stdoutToFile("out.txt");
        Redirect r2 = Redirect.stdoutToFile("out.txt");
        Redirect r3 = Redirect.stdoutToFile("other.txt");

        assertEquals(r1, r2);
        assertNotEquals(r1, r3);
    }

    @Test
    void testCommandFactorySimple() {
        CommandExpression expr = cmd("ls", "-l", "-a");

        assertTrue(expr instanceof SimpleCommand);
        assertEquals("ls -l -a", expr.toString());
    }

    @Test
    void testCommandFactoryWithEnvVars() {
        CommandExpression expr = cmdWithEnvVars("cmd", Map.of("VAR", "value"), "arg");

        assertTrue(expr instanceof SimpleCommand);
        SimpleCommand cmd = (SimpleCommand) expr;
        assertEquals(1, cmd.envVars().size());
        assertEquals("VAR=value cmd arg", cmd.toString());
    }

    @Test
    void testCommandFactoryPipeline() {
        CommandExpression expr = pipeline(
                cmd("cmd1"),
                cmd("cmd2"),
                cmd("cmd3")
        );

        assertTrue(expr instanceof PipelineExpr);
        assertEquals("cmd1 | cmd2 | cmd3", expr.toString());
    }

    @Test
    void testCommandFactoryGroup() {
        CommandExpression expr = group(
                cmd("echo", "start"),
                cmd("ls")
        );

        assertTrue(expr instanceof GroupExpr);
        assertEquals("{ echo start; ls; }", expr.toString());
    }

    @Test
    void testCommandFactorySubshell() {
        CommandExpression expr = subshell(and(cmd("cmd1"), cmd("cmd2")));

        assertTrue(expr instanceof SubshellExpr);
        assertEquals("(cmd1 && cmd2)", expr.toString());
    }

    @Test
    void testCommandFactoryBackground() {
        CommandExpression expr = background(cmd("sleep", "10"));

        assertTrue(expr instanceof BackgroundExpr);
        assertEquals("sleep 10 &", expr.toString());
    }

    @Test
    void testCommandFactoryRedirects() {
        assertEquals("> out.txt", stdoutToFile("out.txt").toString());
        assertEquals(">> out.txt", stdoutAppend("out.txt").toString());
        assertEquals("< in.txt", stdinFromFile("in.txt").toString());
        assertEquals("2>&1", stderrToStdout().toString());
        assertEquals("&> all.log", mergeToFile("all.log").toString());
    }

    @Test
    void testComplexExpression() {
        CommandExpression expr = or(
                subshell(and(cmd("cmd1"), cmd("cmd2"))),
                group(cmd("echo", "fail"), cmd("exit", "1"))
        );

        assertTrue(expr instanceof LogicalExpr);
        assertTrue(expr.toString().contains("||"));
        assertTrue(expr.toString().contains("&&"));
        assertEquals("(cmd1 && cmd2) || { echo fail; exit 1; }", expr.toString());
    }

    @Test
    void testComplexExpressionWithBackground() {
        CommandExpression expr = sequence(
                or(
                        and(
                                group(cmd("echo", "start")),
                                subshell(pipeline(cmd("ls"), cmd("grep", "txt")))
                        ),
                        cmd("echo", "none")
                ),
                background(cmd("sleep", "5"))
        );

        String result = expr.toString();
        assertTrue(result.contains("{"));
        assertTrue(result.contains("}"));
        assertTrue(result.contains("("));
        assertTrue(result.contains(")"));
        assertTrue(result.contains("&&"));
        assertTrue(result.contains("||"));
        assertTrue(result.contains("|"));
        assertTrue(result.contains(";"));
        assertTrue(result.contains("&"));
    }

    @Test
    void testVisitorPattern() {
        SimpleCommand cmd = SimpleCommand.builder("test").build();

        CommandVisitor<String> visitor = new CommandVisitor<>() {
            @Override
            public String visit(SimpleCommand cmd) {
                return "SimpleCommand";
            }

            @Override
            public String visit(PipelineExpr pipe) {
                return "PipelineExpr";
            }

            @Override
            public String visit(LogicalExpr logical) {
                return "LogicalExpr";
            }

            @Override
            public String visit(GroupExpr group) {
                return "GroupExpr";
            }

            @Override
            public String visit(SubshellExpr subshell) {
                return "SubshellExpr";
            }

            @Override
            public String visit(BackgroundExpr background) {
                return "BackgroundExpr";
            }
        };

        assertEquals("SimpleCommand", cmd.accept(visitor));
    }

    @Test
    void testImmutableCollections() {
        SimpleCommand cmd = SimpleCommand.builder("test").arg("arg1").build();
        List<String> args = cmd.args();

        assertThrows(UnsupportedOperationException.class, () -> args.add("arg2"));
    }

    @Test
    void testLogicalExprOperatorPrecedence() {
        assertEquals(4, LogicalExpr.Operator.AND.precedence());
        assertEquals(3, LogicalExpr.Operator.OR.precedence());
        assertEquals(2, LogicalExpr.Operator.SEMICOLON.precedence());
    }

    @Test
    void testLogicalExprOperatorSymbol() {
        assertEquals("&&", LogicalExpr.Operator.AND.symbol());
        assertEquals("||", LogicalExpr.Operator.OR.symbol());
        assertEquals(";", LogicalExpr.Operator.SEMICOLON.symbol());
    }

    @Test
    void testLogicalExprOperatorFromSymbol() {
        assertEquals(LogicalExpr.Operator.AND, LogicalExpr.Operator.fromSymbol("&&"));
        assertEquals(LogicalExpr.Operator.OR, LogicalExpr.Operator.fromSymbol("||"));
        assertEquals(LogicalExpr.Operator.SEMICOLON, LogicalExpr.Operator.fromSymbol(";"));

        assertThrows(IllegalArgumentException.class, () -> LogicalExpr.Operator.fromSymbol("&"));
    }

    @Test
    void testRedirectTypeFromSymbol() {
        assertEquals(Redirect.Type.OUTPUT, Redirect.Type.fromSymbol(">"));
        assertEquals(Redirect.Type.APPEND, Redirect.Type.fromSymbol(">>"));
        assertEquals(Redirect.Type.INPUT, Redirect.Type.fromSymbol("<"));
        assertEquals(Redirect.Type.FD_OUTPUT, Redirect.Type.fromSymbol(">&"));
        assertEquals(Redirect.Type.FD_INPUT, Redirect.Type.fromSymbol("<&"));
        assertEquals(Redirect.Type.MERGE, Redirect.Type.fromSymbol("&>"));
        assertEquals(Redirect.Type.MERGE_APPEND, Redirect.Type.fromSymbol("&>>"));
        assertEquals(Redirect.Type.HERE_DOC, Redirect.Type.fromSymbol("<<"));
        assertEquals(Redirect.Type.HERE_STRING, Redirect.Type.fromSymbol("<<<"));

        assertThrows(IllegalArgumentException.class, () -> Redirect.Type.fromSymbol("unknown"));
    }
}
