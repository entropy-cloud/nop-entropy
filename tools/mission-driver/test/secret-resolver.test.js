import { describe, it, beforeEach, afterEach } from "node:test";
import assert from "node:assert/strict";
import { writeFileSync, mkdirSync, rmSync } from "node:fs";
import { resolve } from "node:path";
import { tmpdir } from "node:os";

import { resolveEnvSecrets } from "../src/secret-resolver.js";
import { loadDotenv } from "../src/env-loader.js";

describe("resolveEnvSecrets — *Env secret resolution", () => {
  const stash = {};

  beforeEach(() => {
    // stash any pre-existing test vars so we control them precisely
    for (const k of ["DB_PASSWORD", "GUI_TEST_PASSWORD", "TEST_VAR_X", "TEST_TOKEN"]) {
      stash[k] = process.env[k];
      delete process.env[k];
    }
  });

  afterEach(() => {
    for (const [k, v] of Object.entries(stash)) {
      if (v === undefined) delete process.env[k];
      else process.env[k] = v;
    }
  });

  it("resolves passwordEnv → password from process.env (Plan jve-1 Exit Criteria 4)", () => {
    process.env.TEST_VAR_X = "secret";
    const cfg = { database: { passwordEnv: "TEST_VAR_X" } };
    resolveEnvSecrets(cfg);
    assert.equal(cfg.database.password, "secret");
  });

  it("keeps the original *Env field for provenance traceability", () => {
    process.env.TEST_VAR_X = "secret";
    const cfg = { database: { passwordEnv: "TEST_VAR_X" } };
    resolveEnvSecrets(cfg);
    assert.equal(cfg.database.passwordEnv, "TEST_VAR_X");
  });

  it("throws a clear error when the referenced env var is missing (no silent empty password)", () => {
    const cfg = { database: { passwordEnv: "DB_PASSWORD" } };
    assert.throws(
      () => resolveEnvSecrets(cfg),
      /DB_PASSWORD.*not set/,
    );
    assert.ok(!("password" in cfg.database), "must not populate a fallback password");
  });

  it("resolves nested auth.passwordEnv at arbitrary depth", () => {
    process.env.TEST_TOKEN = "tok123";
    const cfg = {
      backends: { gui: { baseUrl: "http://x" } },
      auth: { user: "fj", passwordEnv: "TEST_TOKEN" },
    };
    resolveEnvSecrets(cfg);
    assert.equal(cfg.auth.password, "tok123");
  });

  it("is a no-op when there are no *Env fields", () => {
    const cfg = { database: { host: "h", port: 5432, password: "plain" } };
    resolveEnvSecrets(cfg);
    assert.equal(cfg.database.password, "plain");
  });

  it("resolves multiple *Env fields in one pass", () => {
    process.env.DB_PASSWORD = "dbpw";
    process.env.GUI_TEST_PASSWORD = "guipw";
    const cfg = {
      database: { passwordEnv: "DB_PASSWORD" },
      auth: { passwordEnv: "GUI_TEST_PASSWORD" },
    };
    resolveEnvSecrets(cfg);
    assert.equal(cfg.database.password, "dbpw");
    assert.equal(cfg.auth.password, "guipw");
  });

  it("ignores non-string Env fields and keys not ending in Env", () => {
    const cfg = { data: { count: 3, nameEnv: 123, regular: "x" } };
    resolveEnvSecrets(cfg); // should not throw
    assert.equal(cfg.data.count, 3);
    assert.equal(cfg.data.regular, "x");
  });

  it("handles null/array gracefully", () => {
    const cfg = { a: null, b: [1, 2], c: { nested: null } };
    resolveEnvSecrets(cfg);
    assert.equal(cfg.a, null);
    assert.deepEqual(cfg.b, [1, 2]);
  });
});

describe("loadDotenv — zero-dependency .env parser", () => {
  let tmpDir;

  beforeEach(() => {
    tmpDir = resolve(tmpdir(), `jve-env-${Date.now()}-${Math.random().toString(36).slice(2)}`);
    mkdirSync(tmpDir, { recursive: true });
  });

  afterEach(() => {
    rmSync(tmpDir, { recursive: true, force: true });
  });

  it("returns 0 and is a silent no-op when .env is missing", () => {
    const n = loadDotenv(tmpDir);
    assert.equal(n, 0);
  });

  it("parses KEY=VALUE and writes into process.env", () => {
    writeFileSync(resolve(tmpDir, ".env"), "JVE_FOO=bar\n");
    const n = loadDotenv(tmpDir);
    assert.equal(n, 1);
    assert.equal(process.env.JVE_FOO, "bar");
    delete process.env.JVE_FOO;
  });

  it("skips blank lines and # comments", () => {
    writeFileSync(resolve(tmpDir, ".env"), "# a comment\n\nJVE_BAZ=qux\n  # indented comment\n");
    const n = loadDotenv(tmpDir);
    assert.equal(n, 1);
    assert.equal(process.env.JVE_BAZ, "qux");
    delete process.env.JVE_BAZ;
  });

  it("strips surrounding quotes (double and single)", () => {
    writeFileSync(resolve(tmpDir, ".env"), 'JVE_DQ="hello world"\nJVE_SQ=\'it works\'\n');
    loadDotenv(tmpDir);
    assert.equal(process.env.JVE_DQ, "hello world");
    assert.equal(process.env.JVE_SQ, "it works");
    delete process.env.JVE_DQ;
    delete process.env.JVE_SQ;
  });

  it("strips trailing inline comment from unquoted values", () => {
    writeFileSync(resolve(tmpDir, ".env"), "JVE_INC=value  # inline\n");
    loadDotenv(tmpDir);
    assert.equal(process.env.JVE_INC, "value");
    delete process.env.JVE_INC;
  });

  it("never overrides an already-set env var (shell/IDE precedence)", () => {
    process.env.JVE_PRE = "from-shell";
    writeFileSync(resolve(tmpDir, ".env"), "JVE_PRE=from-file\n");
    const n = loadDotenv(tmpDir);
    assert.equal(n, 0);
    assert.equal(process.env.JVE_PRE, "from-shell");
    delete process.env.JVE_PRE;
  });

  it("skips invalid key names", () => {
    writeFileSync(resolve(tmpDir, ".env"), "123BAD=value\n-valid=no\nJVE_OK=yes\n");
    const n = loadDotenv(tmpDir);
    assert.equal(n, 1);
    assert.equal(process.env.JVE_OK, "yes");
    delete process.env.JVE_OK;
  });
});
