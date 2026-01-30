package io.nop.ai.shell.parser;

import io.nop.ai.shell.model.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bash语法解析器单元测试
 */
class BashSyntaxParserTest {

    @Test
    void testSimpleCommand() {
        BashSyntaxParser parser = new BashSyntaxParser("ls -la");
        CommandExpression expr = parser.parse();

        assertTrue(expr instanceof SimpleCommand);
        SimpleCommand cmd = (SimpleCommand) expr;
        assertEquals("ls", cmd.command());
        assertEquals(List.of("-la"), cmd.args());
    }

    @Test
    void testSimpleCommandWithArguments() {
        BashSyntaxParser parser = new BashSyntaxParser("echo hello world");
        CommandExpression expr = parser.parse();

        assertTrue(expr instanceof SimpleCommand);
        SimpleCommand cmd = (SimpleCommand) expr;
        assertEquals("echo", cmd.command());
        assertEquals(List.of("hello", "world"), cmd.args());
    }

    @Test
    void testSimpleCommandWithEnvVars() {
        BashSyntaxParser parser = new BashSyntaxParser("VAR=value echo test");
        CommandExpression expr = parser.parse();

        assertTrue(expr instanceof SimpleCommand);
        SimpleCommand cmd = (SimpleCommand) expr;
        assertEquals("echo", cmd.command());
        assertEquals(1, cmd.envVars().size());
        assertEquals("VAR", cmd.envVars().get(0).name());
        assertEquals("value", cmd.envVars().get(0).value());
    }

    @Test
    void testSimpleCommandWithExportEnvVar() {
        BashSyntaxParser parser = new BashSyntaxParser("export PATH=/bin echo test");
        CommandExpression expr = parser.parse();

        assertTrue(expr instanceof SimpleCommand);
        SimpleCommand cmd = (SimpleCommand) expr;
        assertEquals("export PATH=/bin echo test", cmd.toString());
    }

    @Test
    void testSimpleCommandWithRedirect() {
        BashSyntaxParser parser = new BashSyntaxParser("cat > output.txt");
        CommandExpression expr = parser.parse();

        assertTrue(expr instanceof SimpleCommand);
        SimpleCommand cmd = (SimpleCommand) expr;
        assertEquals(1, cmd.redirects().size());
        Assertions.assertEquals(Redirect.Type.OUTPUT, cmd.redirects().get(0).type());
    }

    @Test
    void testSimpleCommandWithMultipleRedirects() {
        BashSyntaxParser parser = new BashSyntaxParser("cmd > out.txt 2>&1");
        CommandExpression expr = parser.parse();

        assertTrue(expr instanceof SimpleCommand);
        SimpleCommand cmd = (SimpleCommand) expr;
        assertEquals(2, cmd.redirects().size());
    }

    @Test
    void testPipeline() {
        BashSyntaxParser parser = new BashSyntaxParser("cat file.txt | grep pattern");
        CommandExpression expr = parser.parse();

        assertTrue(expr instanceof PipelineExpr);
        PipelineExpr pipe = (PipelineExpr) expr;
        assertEquals(2, pipe.commands().size());

        SimpleCommand cmd1 = (SimpleCommand) pipe.commands().get(0);
        assertEquals("cat", cmd1.command());
        assertEquals(List.of("file.txt"), cmd1.args());

        SimpleCommand cmd2 = (SimpleCommand) pipe.commands().get(1);
        assertEquals("grep", cmd2.command());
        assertEquals(List.of("pattern"), cmd2.args());
    }

    @Test
    void testThreePipePipeline() {
        BashSyntaxParser parser = new BashSyntaxParser("cmd1 | cmd2 | cmd3");
        CommandExpression expr = parser.parse();

        assertTrue(expr instanceof PipelineExpr);
        PipelineExpr pipe = (PipelineExpr) expr;
        assertEquals(3, pipe.commands().size());
    }

    @Test
    void testLogicalAnd() {
        BashSyntaxParser parser = new BashSyntaxParser("cmd1 && cmd2");
        CommandExpression expr = parser.parse();

        assertTrue(expr instanceof LogicalExpr);
        LogicalExpr logical = (LogicalExpr) expr;
        assertEquals(LogicalExpr.Operator.AND, logical.operator());
    }

    @Test
    void testLogicalOr() {
        BashSyntaxParser parser = new BashSyntaxParser("cmd1 || cmd2");
        CommandExpression expr = parser.parse();

        assertTrue(expr instanceof LogicalExpr);
        LogicalExpr logical = (LogicalExpr) expr;
        assertEquals(LogicalExpr.Operator.OR, logical.operator());
    }

    @Test
    void testLogicalSequence() {
        BashSyntaxParser parser = new BashSyntaxParser("cmd1 ; cmd2");
        CommandExpression expr = parser.parse();

        assertTrue(expr instanceof LogicalExpr);
        LogicalExpr logical = (LogicalExpr) expr;
        assertEquals(LogicalExpr.Operator.SEMICOLON, logical.operator());
    }

    @Test
    void testPrecedencePipeVsAnd() {
        BashSyntaxParser parser = new BashSyntaxParser("cmd1 | cmd2 && cmd3");
        CommandExpression expr = parser.parse();

        assertTrue(expr instanceof LogicalExpr);
        LogicalExpr logical = (LogicalExpr) expr;
        assertEquals(LogicalExpr.Operator.AND, logical.operator());

        assertTrue(logical.left() instanceof PipelineExpr);
        assertTrue(logical.right() instanceof SimpleCommand);
    }

    @Test
    void testPrecedenceAndVsOr() {
        BashSyntaxParser parser = new BashSyntaxParser("cmd1 && cmd2 || cmd3");
        CommandExpression expr = parser.parse();

        assertTrue(expr instanceof LogicalExpr);
        LogicalExpr logical = (LogicalExpr) expr;
        assertEquals(LogicalExpr.Operator.OR, logical.operator());

        assertTrue(logical.left() instanceof LogicalExpr);
        assertTrue(logical.right() instanceof SimpleCommand);
    }

    @Test
    void testBackgroundCommand() {
        BashSyntaxParser parser = new BashSyntaxParser("sleep 10 &");
        CommandExpression expr = parser.parse();

        assertTrue(expr instanceof BackgroundExpr);
    }

    @Test
    void testBackgroundWithParentheses() {
        BashSyntaxParser parser = new BashSyntaxParser("(cmd1 && cmd2) &");
        CommandExpression expr = parser.parse();

        assertTrue(expr instanceof BackgroundExpr);
    }

    @Test
    void testSubshell() {
        BashSyntaxParser parser = new BashSyntaxParser("(cd /tmp && ls)");
        CommandExpression expr = parser.parse();

        assertTrue(expr instanceof SubshellExpr);
        SubshellExpr subshell = (SubshellExpr) expr;
        assertTrue(subshell.inner() instanceof LogicalExpr);
    }

    @Test
    void testGroup() {
        BashSyntaxParser parser = new BashSyntaxParser("{ echo start; echo end; }");
        CommandExpression expr = parser.parse();

        assertTrue(expr instanceof GroupExpr);
        GroupExpr group = (GroupExpr) expr;
        assertEquals(2, group.commands().size());
    }

    @Test
    void testGroupWithRedirect() {
        BashSyntaxParser parser = new BashSyntaxParser("{ ls; cat; } > output.txt");
        CommandExpression expr = parser.parse();

        assertTrue(expr instanceof GroupExpr);
        GroupExpr group = (GroupExpr) expr;
        assertEquals(1, group.redirects().size());
    }

    @Test
    void testComplexExpression() {
        String input = "VAR=value cmd1 && cmd2 | cmd3 || { echo fail; exit 1; }";
        BashSyntaxParser parser = new BashSyntaxParser(input);
        CommandExpression expr = parser.parse();

        assertNotNull(expr);
    }

    @Test
    void testParseWithSingleQuotes() {
        BashSyntaxParser parser = new BashSyntaxParser("echo 'hello world'");
        CommandExpression expr = parser.parse();

        assertTrue(expr instanceof SimpleCommand);
    }

    @Test
    void testParseWithDoubleQuotes() {
        BashSyntaxParser parser = new BashSyntaxParser("echo \"hello world\"");
        CommandExpression expr = parser.parse();

        assertTrue(expr instanceof SimpleCommand);
    }

    @Test
    void testParseError() {
        BashSyntaxParser parser = new BashSyntaxParser("VAR= | echo");
        assertThrows(BashSyntaxParser.ParseException.class, parser::parse);
    }

    @Test
    void testPrecedenceParensHighest() {
        BashSyntaxParser parser = new BashSyntaxParser("(cmd1 && cmd2) | cmd3");
        CommandExpression expr = parser.parse();

        assertTrue(expr instanceof PipelineExpr);
        PipelineExpr pipe = (PipelineExpr) expr;
        assertTrue(pipe.commands().get(0) instanceof SubshellExpr);
    }

    @Test
    void testFindWithTailCommand() {
        BashSyntaxParser parser = new BashSyntaxParser("find /tmp -name '*.log' -type f | tail -10");
        CommandExpression expr = parser.parse();

        assertTrue(expr instanceof PipelineExpr);
        PipelineExpr pipeline = (PipelineExpr) expr;
        assertEquals(2, pipeline.commands().size());

        SimpleCommand findCmd = (SimpleCommand) pipeline.commands().get(0);
        assertEquals("find", findCmd.command());
        assertEquals(5, findCmd.args().size());
        assertTrue(findCmd.args().contains("/tmp"));
        assertTrue(findCmd.args().contains("-name"));
        assertTrue(findCmd.args().contains("*.log"));

        SimpleCommand tailCmd = (SimpleCommand) pipeline.commands().get(1);
        assertEquals("tail", tailCmd.command());
        assertEquals(1, tailCmd.args().size());
        assertTrue(tailCmd.args().contains("-10"));
    }

    @Test
    void testFindWithHeadCommand() {
        BashSyntaxParser parser = new BashSyntaxParser("find . -name '*.java' -type f | head -5");
        CommandExpression expr = parser.parse();

        assertTrue(expr instanceof PipelineExpr);
        PipelineExpr pipeline = (PipelineExpr) expr;
        assertEquals(2, pipeline.commands().size());

        SimpleCommand findCmd = (SimpleCommand) pipeline.commands().get(0);
        assertEquals("find", findCmd.command());

        SimpleCommand headCmd = (SimpleCommand) pipeline.commands().get(1);
        assertEquals("head", headCmd.command());
    }

    @Test
    void testFindWithSortCommand() {
        BashSyntaxParser parser = new BashSyntaxParser("find /var/log -type f -name '*.log' | sort");
        CommandExpression expr = parser.parse();

        assertTrue(expr instanceof PipelineExpr);
        SimpleCommand sortCmd = (SimpleCommand) ((PipelineExpr) expr).commands().get(1);
        assertEquals("sort", sortCmd.command());
    }

    @Test
    void testFindWithXargsCommand() {
        BashSyntaxParser parser = new BashSyntaxParser("find . -name '*.tmp' | xargs rm -f");
        CommandExpression expr = parser.parse();

        assertTrue(expr instanceof PipelineExpr);
        SimpleCommand xargsCmd = (SimpleCommand) ((PipelineExpr) expr).commands().get(1);
        assertEquals("xargs", xargsCmd.command());
        assertEquals(2, xargsCmd.args().size());
        assertTrue(xargsCmd.args().contains("rm"));
        assertTrue(xargsCmd.args().contains("-f"));
    }

    @Test
    void testFindWithWcCommand() {
        BashSyntaxParser parser = new BashSyntaxParser("find . -name '*.txt' | wc -l");
        CommandExpression expr = parser.parse();

        assertTrue(expr instanceof PipelineExpr);
        SimpleCommand wcCmd = (SimpleCommand) ((PipelineExpr) expr).commands().get(1);
        assertEquals("wc", wcCmd.command());
        assertEquals(List.of("-l"), wcCmd.args());
    }

    @Test
    void testGrepRecursiveCommand() {
        BashSyntaxParser parser = new BashSyntaxParser("grep -r 'error' /var/log --include='*.log'");
        CommandExpression expr = parser.parse();

        assertTrue(expr instanceof SimpleCommand);
        SimpleCommand grepCmd = (SimpleCommand) expr;
        assertEquals("grep", grepCmd.command());
        assertEquals(4, grepCmd.args().size());
        assertTrue(grepCmd.args().contains("-r"));
        assertTrue(grepCmd.args().contains("error"));
        assertTrue(grepCmd.args().contains("/var/log"));
    }

    @Test
    void testGrepWithExcludeCommand() {
        BashSyntaxParser parser = new BashSyntaxParser("grep 'pattern' file.txt | grep -v 'exclude'");
        CommandExpression expr = parser.parse();

        assertTrue(expr instanceof PipelineExpr);
        PipelineExpr pipeline = (PipelineExpr) expr;
        assertEquals(2, pipeline.commands().size());

        SimpleCommand grep1 = (SimpleCommand) pipeline.commands().get(0);
        assertEquals("grep", grep1.command());

        SimpleCommand grep2 = (SimpleCommand) pipeline.commands().get(1);
        assertEquals("grep", grep2.command());
        assertTrue(grep2.args().contains("-v"));
    }

    @Test
    void testGrepCatPipeCommand() {
        BashSyntaxParser parser = new BashSyntaxParser("cat file.txt | grep 'pattern' | grep -v 'exclude'");
        CommandExpression expr = parser.parse();

        assertTrue(expr instanceof PipelineExpr);
        assertEquals(3, ((PipelineExpr) expr).commands().size());
    }

    @Test
    void testComplexGrepAwkSortCommand() {
        BashSyntaxParser parser = new BashSyntaxParser("cat log.txt | grep ERROR | awk '{print $1}' | sort | uniq");
        CommandExpression expr = parser.parse();

        assertTrue(expr instanceof PipelineExpr);
        PipelineExpr pipeline = (PipelineExpr) expr;
        assertEquals(5, pipeline.commands().size());

        SimpleCommand catCmd = (SimpleCommand) pipeline.commands().get(0);
        assertEquals("cat", catCmd.command());

        SimpleCommand grepCmd = (SimpleCommand) pipeline.commands().get(1);
        assertEquals("grep", grepCmd.command());

        SimpleCommand awkCmd = (SimpleCommand) pipeline.commands().get(2);
        assertEquals("awk", awkCmd.command());

        SimpleCommand sortCmd = (SimpleCommand) pipeline.commands().get(3);
        assertEquals("sort", sortCmd.command());

        SimpleCommand uniqCmd = (SimpleCommand) pipeline.commands().get(4);
        assertEquals("uniq", uniqCmd.command());
    }

    @Test
    void testLogAnalysisCommand() {
        BashSyntaxParser parser = new BashSyntaxParser("cat access.log | grep 'ERROR' | awk '{print $1}' | sort | uniq -c | sort -rn");
        CommandExpression expr = parser.parse();

        assertTrue(expr instanceof PipelineExpr);
        PipelineExpr pipeline = (PipelineExpr) expr;
        assertEquals(6, pipeline.commands().size());

        SimpleCommand sortRnCmd = (SimpleCommand) pipeline.commands().get(5);
        assertEquals("sort", sortRnCmd.command());
        assertTrue(sortRnCmd.args().contains("-rn"));
    }

    @Test
    void testUniqCommand() {
        BashSyntaxParser parser = new BashSyntaxParser("sort names.txt | uniq -c");
        CommandExpression expr = parser.parse();

        assertTrue(expr instanceof PipelineExpr);
        SimpleCommand uniqCmd = (SimpleCommand) ((PipelineExpr) expr).commands().get(1);
        assertEquals("uniq", uniqCmd.command());
        assertEquals(List.of("-c"), uniqCmd.args());
    }

    @Test
    void testFindWithMultipleOptions() {
        BashSyntaxParser parser = new BashSyntaxParser("find /var/log -type f -name '*.log' -mtime +30 -size +100M");
        CommandExpression expr = parser.parse();

        assertTrue(expr instanceof SimpleCommand);
        SimpleCommand findCmd = (SimpleCommand) expr;
        assertEquals("find", findCmd.command());
        assertTrue(findCmd.args().contains("-type"));
        assertTrue(findCmd.args().contains("f"));
        assertTrue(findCmd.args().contains("-mtime"));
        assertTrue(findCmd.args().contains("+30"));
        assertTrue(findCmd.args().contains("-size"));
        assertTrue(findCmd.args().contains("+100M"));
    }

    @Test
    void testPipelineWithSubshell() {
        BashSyntaxParser parser = new BashSyntaxParser("(cd /tmp && ls) | grep pattern");
        CommandExpression expr = parser.parse();

        assertTrue(expr instanceof PipelineExpr);
        PipelineExpr pipeline = (PipelineExpr) expr;
        assertTrue(pipeline.commands().get(0) instanceof SubshellExpr);
        assertTrue(pipeline.commands().get(1) instanceof SimpleCommand);
    }

    @Test
    void testMultipleEnvVarsCommand() {
        BashSyntaxParser parser = new BashSyntaxParser("PATH=/usr/bin:/bin JAVA_HOME=/usr/lib/java find . -name '*.sh'");
        CommandExpression expr = parser.parse();

        assertTrue(expr instanceof SimpleCommand);
        SimpleCommand cmd = (SimpleCommand) expr;
        assertEquals("find", cmd.command());
        assertEquals(2, cmd.envVars().size());
        assertEquals("PATH", cmd.envVars().get(0).name());
        assertEquals("JAVA_HOME", cmd.envVars().get(1).name());
    }
}
