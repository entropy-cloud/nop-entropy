package io.nop.cli.commands;

import io.nop.jq.IJsonQuery;
import io.nop.jq.JqEngine;
import io.nop.core.lang.json.JsonTool;
import picocli.CommandLine;

import java.io.File;
import java.io.PrintStream;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "jq",
        mixinStandardHelpOptions = true,
        description = "Execute jq expression against JSON input."
)
public class CliJqCommand implements Callable<Integer> {

    @CommandLine.Parameters(index = "0", description = "jq expression")
    String expression;

    @CommandLine.Option(names = {"-i", "--input"}, description = "JSON input file (default: stdin)")
    File inputFile;

    @Override
    public Integer call() {
        try {
            String json = inputFile != null
                    ? Files.readString(inputFile.toPath())
                    : new String(System.in.readAllBytes());
            Object root = JsonTool.parseNonStrict(null, json);
            IJsonQuery query = JqEngine.compile(expression);
            List<Object> results = query.apply(root);
            PrintStream out = System.out;
            for (Object result : results) {
                out.println(JsonTool.stringify(result));
            }
            return 0;
        } catch (Exception e) {
            // full trace (class + message + frames), not just getMessage()
            e.printStackTrace();
            return 2;
        }
    }
}
