// Direct, non-interactive in-process execution test.
//
// Goal: Skip HTTP server / ACP / TUI. Call core execution logic directly.
//
// This script:
// - bootstraps opencode runtime (project instance, config, etc.)
// - creates a session
// - runs SessionPrompt.prompt() with a user text
// - forces permissions to non-interactive (no "ask") by denying everything that would ask
//
// Note: This repo is primarily Bun-based. Run with Bun.

import "bun";
import path from "path";
import fs from "fs/promises";
import { bootstrap } from "../packages/opencode/src/cli/bootstrap.ts";
import { Session } from "../packages/opencode/src/session/index.ts";
import { SessionPrompt } from "../packages/opencode/src/session/prompt.ts";
import { Provider } from "../packages/opencode/src/provider/provider.ts";
import { Instance } from "../packages/opencode/src/project/instance.ts";
import { Plugin } from "../packages/opencode/src/plugin/index.ts";
import { Share } from "../packages/opencode/src/share/share.ts";
import { ShareNext } from "../packages/opencode/src/share/share-next.ts";
import { Format } from "../packages/opencode/src/format/index.ts";
import { FileWatcher } from "../packages/opencode/src/file/watcher.ts";
import { File } from "../packages/opencode/src/file/index.ts";
import { Vcs } from "../packages/opencode/src/project/vcs.ts";

console.log("[direct-exec] module loaded");

process.on("unhandledRejection", (err) => {
  console.error("[direct-exec] unhandledRejection:", err);
  process.exitCode = 1;
});

process.on("uncaughtException", (err) => {
  console.error("[direct-exec] uncaughtException:", err);
  process.exitCode = 1;
});

async function lightweightInit() {
  // Intentionally skip LSP.init() because it can hang on some Windows setups,
  // and it's not required for a minimal, fully-automated prompt execution.
  await Plugin.init();
  Share.init();
  ShareNext.init();
  Format.init();
  FileWatcher.init();
  File.init();
  Vcs.init();
}

async function main() {
  // Force non-git behavior to avoid running `git` via Bun `$` during Project.fromDirectory on Windows.
  // This keeps the script purely local and non-interactive.
  process.env.OPENCODE_FAKE_VCS = process.env.OPENCODE_FAKE_VCS || "git";

  const args = process.argv.slice(2);
  const projectPath = args[0];
  const promptArg = args[1];
  const sessionArg = args[2];

  if (!projectPath || !promptArg) {
    console.error(
      "Usage: bun scripts/direct_exec_test.mjs <projectPath> <prompt> [sessionId]",
    );
    process.exit(1);
  }

  const cwd = path.resolve(projectPath);
  process.chdir(cwd);

  console.log("[direct-exec] calling Instance.provide...");

  const watchdog = setTimeout(() => {
    console.error("[direct-exec] watchdog: still running after 30s (likely hanging in project discovery/init)");
  }, 30_000);

  const res = await Instance.provide({
    directory: cwd,
    init: lightweightInit,
    fn: async () => {
    try {
      console.log("[direct-exec] bootstrapped, cwd=", cwd);

      const session = await (async () => {
        const sessionFile = path.join(cwd, ".opencode-session-id");

        // 1) CLI arg overrides everything
        if (sessionArg) {
          const existing = await Session.get(sessionArg).catch(() => undefined);
          if (!existing) {
            throw new Error(`Session not found: ${sessionArg}`);
          }
          await fs.writeFile(sessionFile, sessionArg, "utf8").catch(() => {});
          console.log("[direct-exec] continue session (arg)=", sessionArg);
          return existing;
        }

        // 2) Try loading from file in project directory
        const fileId = await fs.readFile(sessionFile, "utf8").then((s) => s.trim()).catch(() => "");
        if (fileId) {
          const existing = await Session.get(fileId).catch(() => undefined);
          if (existing) {
            console.log("[direct-exec] continue session (file)=", fileId);
            return existing;
          }
          console.warn("[direct-exec] session id in file not found, creating new", fileId);
        }

        // 3) Create new session and persist
        const created = await Session.create({ title: "direct-exec-test" });
        await fs.writeFile(sessionFile, created.id, "utf8").catch(() => {});
        console.log("[direct-exec] new session=", created.id);
        console.log(created.id);
        return created;
      })();

    // Disable any interactive permission flow by explicitly denying everything by default.
    // If your workload needs specific tools, flip them to allow.
    await Session.update(session.id, (draft) => {
      draft.permission = [
        { permission: "*", pattern: "*", action: "deny" },
        // allow minimal safe reads if desired
        { permission: "read", pattern: "*", action: "allow" },
        { permission: "glob", pattern: "*", action: "allow" },
        { permission: "grep", pattern: "*", action: "allow" },
        { permission: "list", pattern: "*", action: "allow" },
        // allow file creation / modification via built-in tools
        { permission: "write", pattern: "*", action: "allow" },
        { permission: "edit", pattern: "*", action: "allow" },
        // allow bash so the agent can run common commands (including git)
        { permission: "bash", pattern: "*", action: "allow" },
        // disable question tool (interactive)
        { permission: "question", pattern: "*", action: "deny" },
      ];
    });

  const promptText = promptArg || process.env.PROMPT || "Summarize what this repository does in 3 bullet points.";

  // Force an explicit model to avoid falling back to a configured default that might require keys.
  // You can override by setting:
  //   OPENCODE_MODEL=opencode/<modelID>
  // If unset, we'll use Provider.defaultModel() which will pick free models when no key is present.
  const modelEnv = process.env.OPENCODE_MODEL;
  const model = modelEnv ? Provider.parseModel(modelEnv) : await Provider.defaultModel();
  console.log("[direct-exec] model=", `${model.providerID}/${model.modelID}`);

  const agent = process.env.OPENCODE_AGENT ?? process.env.AGENT ?? "build";

      // Trigger an agent run (matches `opencode run prompt` behavior):
      // 1) enqueue a user message
      // 2) drive the session loop until it naturally exits (idle/finished)
      await SessionPrompt.prompt({
        sessionID: session.id,
        agent,
        model,
        noReply: true,
        parts: [{ type: "text", text: promptText }],
      });

      const result = await SessionPrompt.loop(session.id);

      console.log("[direct-exec] Assistant message:");
      console.log(JSON.stringify(result, null, 2));
      return result;
    } catch (err) {
      console.error("[direct-exec] inside bootstrap failed:", err);
      throw err;
    } finally {
      await Instance.dispose();
    }
  },
  });

  clearTimeout(watchdog);
  console.log("[direct-exec] Instance.provide returned");

  // Keep a reference so the process doesn't exit before stdout flushes in some environments.
  if (res) {
    console.log("[direct-exec] done");
  }
}

main().catch((err) => {
  console.error("direct exec test failed:", err);
  process.exitCode = 1;
});
