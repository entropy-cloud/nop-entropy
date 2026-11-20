package io.nop.git.cli;

import io.nop.git.api.GitConfigScope;
import io.nop.git.api.NopGitException;

import java.util.List;

import static io.nop.git.cli.GitCompatCli.CliContext;
import static io.nop.git.cli.GitCompatCli.EXIT_FAILURE;
import static io.nop.git.cli.GitCompatCli.EXIT_OK;
import static io.nop.git.cli.GitCompatCli.EXIT_USAGE;

/**
 * {@code git config} subset: {@code --get <key>}, {@code <key> <value>}
 * (write, repository scope), {@code --global} scope modifier for both.
 * {@code --get} on an unset key exits 1 with no output (native behavior).
 */
final class ConfigCommand {

    private ConfigCommand() {
    }

    static int run(CliContext ctx, List<String> args) {
        boolean global = false;
        boolean get = false;
        List<String> positional = new java.util.ArrayList<>();
        for (int i = 0; i < args.size(); i++) {
            String arg = args.get(i);
            if (arg.startsWith("--nop-git-internal"))
                continue;
            switch (arg) {
                case "--global" -> global = true;
                case "--local" -> { /* repository scope is the default */ }
                case "--get" -> get = true;
                default -> {
                    if (arg.startsWith("-")) {
                        ctx.err().println("error: unknown option `"
                                + (arg.startsWith("--") ? arg.substring(2) : arg.substring(1)) + "'");
                        return EXIT_USAGE;
                    }
                    positional.add(arg);
                }
            }
        }

        try (var svc = ctx.openRepo()) {
            GitConfigScope scope = global ? GitConfigScope.GLOBAL : GitConfigScope.REPOSITORY;

            if (get) {
                if (positional.size() != 1) {
                    ctx.err().println("usage: git config --get <key>");
                    return EXIT_USAGE;
                }
                String value = svc.configGet(positional.get(0), scope);
                if (value == null)
                    return EXIT_FAILURE; // native: exit 1, no output
                ctx.out().println(value);
                return EXIT_OK;
            }

            if (positional.isEmpty()) {
                ctx.err().println("usage: git config [--global] [--get] <key> [<value>]");
                return EXIT_USAGE;
            }
            String key = positional.get(0);
            if (positional.size() >= 2) {
                svc.configSet(key, positional.get(1), scope);
                return EXIT_OK;
            }
            // bare read: git config user.name
            String value = svc.configGet(key, scope);
            if (value == null)
                return EXIT_FAILURE;
            ctx.out().println(value);
            return EXIT_OK;
        } catch (NopGitException e) {
            return GitCompatCli.nativeFatal(ctx, e);
        }
    }
}
