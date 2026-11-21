package io.nop.git.server;

import io.nop.git.api.NopGitException;
import jakarta.servlet.http.HttpServletRequest;
import org.eclipse.jgit.http.server.GitServlet;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryCache;
import org.eclipse.jgit.transport.ReceivePack;
import org.eclipse.jgit.transport.resolver.RepositoryResolver;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.ee11.servlet.ServletHolder;
import org.eclipse.jetty.ee11.servlet.ServletContextHandler;

import java.io.File;
import java.io.IOException;

/**
 * Embedded HTTP Git server (design 04 section 4): GitServlet smart protocol
 * endpoint over an embedded Jetty, exposing every repository under the
 * registry root as {@code <name>.git}; receive-pack is explicitly enabled
 * (GitServlet serves fetch only by default) with the protected-branch
 * PreReceiveHook installed.
 */
public class NopGitHttpServer {

    private final GitServerConfig config;
    private Server server;

    public NopGitHttpServer(GitServerConfig config) {
        this.config = config;
    }

    /**
     * Start the embedded server.
     *
     * @return the actual bound port (useful when configured with port 0)
     */
    public int start() {
        if (server != null)
            throw new NopGitException("HTTP git server already started");
        GitServlet gitServlet = new GitServlet();
        gitServlet.setRepositoryResolver(new RegistryResolver());
        gitServlet.setReceivePackFactory((req, repository) -> {
            ReceivePack receivePack = new ReceivePack(repository);
            receivePack.setPreReceiveHook(
                    new ProtectedBranchPreReceiveHook(config.protectedBranches()));
            return receivePack;
        });

        ServletContextHandler context = new ServletContextHandler();
        context.setContextPath("/");
        context.addServlet(new ServletHolder(gitServlet), "/*");
        // plan 2582: bearer-token access control when a token is configured
        if (config.token() != null)
            context.addFilter(new org.eclipse.jetty.ee11.servlet.FilterHolder(
                    new BearerTokenFilter(config.token())), "/*",
                    java.util.EnumSet.of(jakarta.servlet.DispatcherType.REQUEST));

        // plan 2582: loopback binding by default; remote exposure is an
        // explicit opt-in through bindAllInterfaces
        server = new Server();
        org.eclipse.jetty.server.ServerConnector connector =
                new org.eclipse.jetty.server.ServerConnector(server);
        connector.setPort(config.port());
        connector.setHost(config.bindAllInterfaces() ? null : "127.0.0.1");
        server.addConnector(connector);
        server.setHandler(context);
        try {
            server.start();
        } catch (Exception e) {
            throw new NopGitException("cannot start HTTP git server: " + e.getMessage(), e);
        }
        return server.getURI().getPort();
    }

    /**
     * Access control (plan 2582): when a token is configured, every request
     * must carry {@code Authorization: Bearer <token>} (git clients pass it
     * via {@code -c http.extraHeader=...}); anything else gets 401 before the
     * smart protocol endpoint is reached.
     */
    static class BearerTokenFilter implements jakarta.servlet.Filter {

        private final String token;

        BearerTokenFilter(String token) {
            this.token = token;
        }

        @Override
        public void doFilter(jakarta.servlet.ServletRequest request,
                             jakarta.servlet.ServletResponse response,
                             jakarta.servlet.FilterChain chain)
                throws java.io.IOException, jakarta.servlet.ServletException {
            String header = ((jakarta.servlet.http.HttpServletRequest) request)
                    .getHeader("Authorization");
            if (header == null || !header.equals("Bearer " + token)) {
                ((jakarta.servlet.http.HttpServletResponse) response)
                        .sendError(jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED,
                                "authentication required");
                return;
            }
            chain.doFilter(request, response);
        }
    }

    public void stop() {
        if (server == null)
            return;
        try {
            server.stop();
        } catch (Exception e) {
            throw new NopGitException("cannot stop HTTP git server: " + e.getMessage(), e);
        } finally {
            server = null;
        }
    }

    public int getPort() {
        if (server == null)
            throw new NopGitException("HTTP git server not started");
        return server.getURI().getPort();
    }

    /**
     * Resolve {@code <name>.git} (or {@code <name>}) against the registry
     * root; {@code incrementOpen} keeps the repository open for the request
     * lifetime (design 04 section 4).
     */
    private class RegistryResolver implements RepositoryResolver<HttpServletRequest> {
        @Override
        public Repository open(HttpServletRequest request, String name)
                throws org.eclipse.jgit.errors.RepositoryNotFoundException,
                ServiceNotEnabledException, org.eclipse.jgit.transport.ServiceMayNotContinueException {
            // only plain names are accepted; no traversal out of the root
            if (name.contains("..") || name.contains("/"))
                throw new ServiceNotEnabledException("invalid repository name: " + name);
            String base = name.endsWith(".git") ? name.substring(0, name.length() - 4) : name;
            File dir = config.root().resolve(base).toFile();
            File gitDir = new File(dir, ".git");
            File repoDir = gitDir.isDirectory() ? gitDir : dir;
            if (!new File(repoDir, "HEAD").exists())
                throw new ServiceNotEnabledException("repository not found: " + name);
            try {
                return RepositoryCache.FileKey.lenient(repoDir,
                        org.eclipse.jgit.util.FS.DETECTED).open(true);
            } catch (IOException e) {
                throw new org.eclipse.jgit.errors.RepositoryNotFoundException(name, e);
            }
        }
    }
}
