package io.nop.git.server;

import java.nio.file.Paths;

/**
 * Executable entry: {@code java -jar nop-git-server.jar <port> <repoRoot>
 * [--bind-all] [--token <value>]}. Browser-style GETs return 404 by design —
 * only smart-protocol clients are served (design 04 section 4). Loopback
 * binding is the default; remote exposure requires {@code --bind-all} and
 * authenticated access via {@code --token} is recommended (plan 2582).
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        String portArg = null;
        String rootArg = null;
        String token = null;
        boolean bindAll = false;
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.equals("--bind-all")) {
                bindAll = true;
            } else if (arg.equals("--token")) {
                if (i + 1 >= args.length) {
                    System.err.println("fatal: option --token requires a value");
                    System.exit(128);
                }
                token = args[++i];
            } else if (arg.startsWith("-")) {
                System.err.println("fatal: unknown option '" + arg + "'");
                usage();
            } else if (portArg == null) {
                portArg = arg;
            } else if (rootArg == null) {
                rootArg = arg;
            } else {
                usage();
            }
        }
        if (portArg == null || rootArg == null) {
            usage();
        }
        int port = -1;
        try {
            port = Integer.parseInt(portArg);
            if (port < 0 || port > 65535)
                port = -1;
        } catch (NumberFormatException e) {
            port = -1;
        }
        if (port < 0) {
            // CLI contract: native-style fatal line, no stack trace (plan 2582)
            System.err.println("fatal: invalid port '" + portArg + "'");
            System.exit(128);
        }
        var config = new GitServerConfig(port, Paths.get(rootArg),
                ProtectedBranchPreReceiveHook.DEFAULT_PROTECTED, token, bindAll);
        NopGitHttpServer server = new NopGitHttpServer(config);
        int bound = server.start();
        System.out.println("nop-git-server listening on port " + bound
                + " (root=" + config.root()
                + (bindAll ? ", all-interfaces" : ", loopback")
                + (token != null ? ", token-auth" : "") + ")");
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        Thread.currentThread().join();
    }

    private static void usage() {
        System.err.println("usage: nop-git-server <port> <repoRoot> [--bind-all] [--token <value>]");
        System.exit(128);
    }
}
