package io.nop.git.server;

import java.nio.file.Paths;

/**
 * Executable entry: {@code java -jar nop-git-server.jar <port> <repoRoot>}.
 * Browser-style GETs return 404 by design — only smart-protocol clients are
 * served (design 04 section 4).
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("usage: nop-git-server <port> <repoRoot>");
            System.exit(128);
        }
        int port = Integer.parseInt(args[0]);
        var config = GitServerConfig.of(port, Paths.get(args[1]));
        NopGitHttpServer server = new NopGitHttpServer(config);
        int bound = server.start();
        System.out.println("nop-git-server listening on port " + bound
                + " (root=" + config.root() + ")");
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        Thread.currentThread().join();
    }
}
