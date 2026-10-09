package io.nop.cli.commands;

import io.nop.cli.exception.NopExitCodeExceptionMapper;
import picocli.CommandLine;

import java.io.PrintStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class ToolBridge {

    private ToolBridge() {
    }

    public static int runRg(String[] args, PrintStream out, PrintStream err) {
        if (hasDelegateRg(args)) {
            return invokeSystemRg(args, err);
        }
        try {
            Class<?> cls = Class.forName("io.nop.rg.cli.NopRgMain");
            Object instance = cls.getDeclaredConstructor().newInstance();
            // NopRgMain is a picocli @Command object: its @Parameters/@Option fields are only
            // populated by picocli parsing. Calling call() on a bare newInstance() leaves them
            // null (Path.of(null) NPE), so parse through picocli exactly like NopRgMain.main().
            CommandLine commandLine = new CommandLine(instance);
            commandLine.setExitCodeExceptionMapper(new NopExitCodeExceptionMapper());
            return commandLine.execute(args);
        } catch (ClassNotFoundException e) {
            throw new UnsupportedOperationException(
                    "nop-rg-cli is not on classpath. Build with JDK 22+ profile: -Pjava22-modules", e);
        } catch (ReflectiveOperationException e) {
            throw new UnsupportedOperationException("Failed to invoke NopRgMain: " + e.getMessage(), e);
        }
    }

    public static int runLint(String[] args, PrintStream out, PrintStream err) {
        try {
            Class<?> cls = Class.forName("io.nop.lint.core.cli.NopLintCli");
            Method run = cls.getMethod("run", String[].class, PrintStream.class, PrintStream.class);
            Object result = run.invoke(null, args, out, err);
            return result instanceof Integer ? (Integer) result : 2;
        } catch (ClassNotFoundException e) {
            throw new UnsupportedOperationException(
                    "nop-lint-core is not on classpath. Check nop-cli-core dependencies.", e);
        } catch (ReflectiveOperationException e) {
            throw new UnsupportedOperationException("Failed to invoke NopLintCli: " + e.getMessage(), e);
        }
    }

    public static int runRefactor(String[] args, PrintStream out, PrintStream err) {
        try {
            Class<?> cls = Class.forName("io.nop.refactor.core.cli.NopRefactorCli");
            Method run = cls.getMethod("run", String[].class, PrintStream.class, PrintStream.class);
            Object result = run.invoke(null, args, out, err);
            return result instanceof Integer ? (Integer) result : 2;
        } catch (ClassNotFoundException e) {
            throw new UnsupportedOperationException(
                    "nop-refactor-core is not on classpath. Check nop-cli-core dependencies.", e);
        } catch (ReflectiveOperationException e) {
            throw new UnsupportedOperationException("Failed to invoke NopRefactorCli: " + e.getMessage(), e);
        }
    }

    private static boolean hasDelegateRg(String[] args) {
        for (String arg : args) {
            if (arg.equals("--delegate-rg") || arg.startsWith("--delegate-rg=")) {
                return true;
            }
        }
        return false;
    }

    private static int invokeSystemRg(String[] args, PrintStream err) {
        String rgPath = "rg";
        List<String> rgArgs = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("--delegate-rg=")) {
                rgPath = arg.substring("--delegate-rg=".length());
            }
            if (arg.equals("--delegate-rg") || arg.startsWith("--delegate-rg=")) {
                continue;
            }
            rgArgs.add(arg);
        }
        rgArgs.add(0, rgPath);
        try {
            Process process = new ProcessBuilder(rgArgs).inheritIO().start();
            return process.waitFor();
        } catch (Exception e) {
            err.println("nop-cli: failed to launch rg (" + rgArgs.get(0) + ")");
            // full trace (class + message + frames), not just getMessage()
            e.printStackTrace(err);
            return 2;
        }
    }
}
