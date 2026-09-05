/**
 * Generic `*Env` secret resolver — zero-dependency (NFR-1/NFR-2).
 *
 * Mission config files externalise secrets via convention: instead of a
 * plaintext value, a field `<key>Env` holds the *name* of the environment
 * variable that contains the real secret, e.g.:
 *
 *   { "database": { "passwordEnv": "DB_PASSWORD" } }
 *
 * This resolver walks a config object and, for every `<key>Env` field, reads
 * `process.env[<value>]` and fills the sibling `<key>` field with the resolved
 * secret (e.g. `database.password`). The original `<key>Env` field is kept so
 * the provenance remains traceable.
 *
 * Config loaders call this single shared function — one resolution point,
 * no drift.
 *
 * A missing environment variable is a HARD error: throwing early surfaces a
 * misconfigured environment immediately rather than silently running with an
 * empty password .
 */

/**
 * Resolve every `<key>Env` field in a config object by reading the named
 * environment variable and writing the value into the matching `<key>` field.
 *
 * Mutates and returns the input object (so callers can chain). Recurses into
 * nested plain objects.
 *
 * @param {object} config  the parsed config object (mutated in place)
 * @returns {object} the same object with resolved secrets populated
 * @throws {Error} when a referenced environment variable is not set
 */
export function resolveEnvSecrets(config) {
  if (!config || typeof config !== "object") return config;

  for (const key of Object.keys(config)) {
    const val = config[key];

    if (key.endsWith("Env") && typeof val === "string" && val.length > 0) {
      const baseKey = key.slice(0, -3); // strip "Env" suffix
      const secret = process.env[val];
      if (secret === undefined) {
        throw new Error(
          `Secret environment variable "${val}" (referenced by field "${key}") is not set. ` +
            `Define it in your .env file or shell environment before running.`,
        );
      }
      config[baseKey] = secret;
    } else if (val && typeof val === "object" && !Array.isArray(val)) {
      resolveEnvSecrets(val);
    }
  }
  return config;
}
