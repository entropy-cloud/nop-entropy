/**
 * nop-lint tsc bridge server (roadmap item 20, design 06 §5.3 / design 11 §3-§4).
 *
 * Resident Node process speaking newline-delimited JSON over stdin/stdout.
 * The Java side (io.nop.lint.js.tsc.NodeTscBridge) spawns `node tsc-bridge-server.mjs`,
 * reads one startup ready frame, then issues request/response frames. Wire
 * positions are 0-based line and 0-based column (UTF-16 code units), the
 * TypeScript-internal convention, so no off-by-one conversion lives here.
 *
 * Frames:
 *   -> {"type":"ready","node":"v25.3.0","typescript":"5.9.3"}
 *   <- {"id":1,"op":"initProject","tsConfigPath":"/abs/tsconfig.json"}
 *   -> {"id":1,"ok":true,"result":{"cacheKey":"...","rebuilt":true,"fileCount":3}}
 *   <- {"id":2,"op":"getTypeAtLocation","file":"/abs/a.ts","line":0,"col":16}
 *   -> {"id":2,"ok":true,"result":{"type":"number"}}
 *   <- {"id":3,"op":"isTypeAssignableTo","file":"/abs/a.ts","line":0,"col":16,
 *       "expectedType":"number"}                    (string-target form)
 *   <- {"id":4,"op":"isTypeAssignableTo","from":{"file":..,"line":..,"col":..},
 *       "to":{"file":..,"line":..,"col":..}}        (node-reference form)
 *   -> {"id":3,"ok":true,"result":{"assignable":true,"actualType":"number",
 *       "expectedType":"number"}}
 *   -> {"id":9,"ok":false,"error":{"code":"NO_PROJECT","message":"..."}}
 *
 * Program cache (design 11 §4): per tsconfig path; the cache key is a sha1
 * over the tsconfig fields that shape the program (files / include / exclude
 * / references / compilerOptions.paths), so a change to any of them rebuilds
 * the program while an unrelated edit (comment, compilerOptions noise) does
 * not. Source-content invalidation is the item 43 disk-layer scope. A
 * per-program query cache maps file:line:col to the rendered type string.
 *
 * Every failure returns a structured error frame; nothing exits silently.
 */
import { createRequire } from 'node:module';
import { createHash } from 'node:crypto';
import { dirname, resolve as resolveFsPath } from 'node:path';

const require = createRequire(import.meta.url);
let ts;
try {
  ts = require('typescript');
} catch (e) {
  process.stdout.write(JSON.stringify({
    type: 'fatal',
    code: 'TYPESCRIPT_MISSING',
    message: "the 'typescript' package is not resolvable from the helper script: " + e.message,
  }) + '\n');
  process.exit(2);
}

/** program state per absolute tsconfig path (design 11 §4 program cache). */
const projects = new Map();

function writeFrame(obj) {
  process.stdout.write(JSON.stringify(obj) + '\n');
}

function errorFrame(id, code, message) {
  return { id, ok: false, error: { code, message } };
}

/** canonical absolute path in TypeScript's own normalization. */
function canonicalFile(p) {
  return ts.normalizePath(resolveFsPath(p));
}

/**
 * sha1 over the parsed tsconfig fields that shape the program (design 11 §4:
 * include / paths / references / files; exclude participates through the
 * resolved file set). Canonical JSON keeps the hash order-stable.
 */
function programCacheKey(configParseResult, tsconfigPath) {
  const raw = configParseResult.raw || {};
  const relevant = {
    tsconfigPath: canonicalFile(tsconfigPath),
    files: (configParseResult.fileNames || []).slice().sort(),
    include: raw.include || null,
    exclude: raw.exclude || null,
    references: (raw.references || null),
    paths: (raw.compilerOptions && raw.compilerOptions.paths) || null,
    optionsSignature: {
      target: raw.compilerOptions && raw.compilerOptions.target,
      strict: raw.compilerOptions && raw.compilerOptions.strict,
      module: raw.compilerOptions && raw.compilerOptions.module,
    },
  };
  return createHash('sha1').update(JSON.stringify(relevant)).digest('hex');
}

function parseConfig(canonicalTsConfigPath) {
  const configFile = ts.readConfigFile(canonicalTsConfigPath, ts.sys.readFile);
  if (configFile.error) {
    const diag = configFile.error;
    return { error: ts.flattenDiagnosticMessageText(diag.messageText, '\n') };
  }
  const parsed = ts.parseJsonConfigFileContent(
    configFile.config, ts.sys, dirname(canonicalTsConfigPath));
  if (parsed.errors && parsed.errors.length > 0) {
    return { error: parsed.errors.map(d => ts.flattenDiagnosticMessageText(d.messageText, '\n')).join('; ') };
  }
  return { parsed };
}

function ensureProject(id, tsConfigPath) {
  if (!tsConfigPath || typeof tsConfigPath !== 'string') {
    return errorFrame(id, 'BAD_REQUEST', 'initProject needs a tsConfigPath string');
  }
  let resolved;
  try {
    resolved = canonicalFile(tsConfigPath);
  } catch (e) {
    return errorFrame(id, 'TSCONFIG_INVALID', 'cannot resolve tsconfig path: ' + e.message);
  }
  const existing = projects.get(resolved);
  // Re-read + re-hash the config on every initProject: cheap (no program
  // build), and it is what makes hash invalidation observable (design 11 §4).
  const { parsed, error } = parseConfig(resolved);
  if (error) {
    return errorFrame(id, 'TSCONFIG_INVALID', error);
  }
  const cacheKey = programCacheKey(parsed, resolved);
  if (existing && existing.cacheKey === cacheKey) {
    return { id, ok: true, result: { cacheKey, rebuilt: false, fileCount: existing.program.getSourceFiles().length } };
  }
  const program = ts.createProgram({
    rootNames: parsed.fileNames,
    options: parsed.options,
    projectReferences: parsed.projectReferences,
  });
  const project = newProject(program, program.getTypeChecker(), cacheKey);
  project.parsed = parsed;
  projects.set(resolved, project);
  return {
    id,
    ok: true,
    result: { cacheKey, rebuilt: true, fileCount: program.getSourceFiles().length },
  };
}

function projectFor(id, filePath) {
  for (const project of projects.values()) {
    try {
      const source = project.program.getSourceFile(canonicalFile(filePath));
      if (source) {
        return { project, source };
      }
    } catch (e) {
      // fall through to not-found
    }
  }
  return { error: errorFrame(id, 'FILE_NOT_IN_PROJECT',
    'no initialized project contains file ' + filePath + ' (call initProject first)') };
}

function positionOf(source, line0, col0) {
  try {
    return source.getPositionOfLineAndCharacter(line0, col0);
  } catch (e) {
    return null;
  }
}

function opGetTypeAtLocation(id, params) {
  const { file, line, col } = params;
  if (typeof file !== 'string' || !Number.isInteger(line) || !Number.isInteger(col)) {
    return errorFrame(id, 'BAD_REQUEST', 'getTypeAtLocation needs file/line/col (0-based line and column)');
  }
  const found = projectFor(id, file);
  if (found.error) return found.error;
  const position = positionOf(found.source, line, col);
  if (position === null) {
    return errorFrame(id, 'QUERY_FAILED', `position ${line}:${col} is outside file ${file}`);
  }
  const cacheKey = file + ':' + line + ':' + col;
  const cached = found.project.queryCache.get(cacheKey);
  if (cached !== undefined) {
    return { id, ok: true, result: { type: cached } };
  }
  const token = ts.getTokenAtPosition(found.source, position);
  const type = found.project.checker.getTypeAtLocation(token);
  const rendered = found.project.checker.typeToString(type, undefined,
    ts.TypeFormatFlags.NoTruncation | ts.TypeFormatFlags.UseFullyQualifiedType);
  found.project.queryCache.set(cacheKey, rendered);
  return { id, ok: true, result: { type: rendered } };
}

function resolveNodeTypeIn(id, project, ref) {
  if (!ref || typeof ref !== 'object' || typeof ref.file !== 'string'
    || !Number.isInteger(ref.line) || !Number.isInteger(ref.col)) {
    return { error: errorFrame(id, 'BAD_REQUEST',
      'node reference needs file/line/col (0-based line and column)') };
  }
  let source;
  try {
    source = project.program.getSourceFile(canonicalFile(ref.file));
  } catch (e) {
    source = null;
  }
  if (!source) {
    return { error: errorFrame(id, 'FILE_NOT_IN_PROJECT', 'project does not contain file ' + ref.file) };
  }
  const position = positionOf(source, ref.line, ref.col);
  if (position === null) {
    return { error: errorFrame(id, 'QUERY_FAILED', `position ${ref.line}:${ref.col} is outside file ${ref.file}`) };
  }
  const token = ts.getTokenAtPosition(source, position);
  const type = project.checker.getTypeAtLocation(token);
  return { type, checker: project.checker };
}

function resolveNodeType(id, ref) {
  const found = projectFor(id, ref.file);
  if (found.error) return { error: found.error };
  return resolveNodeTypeIn(id, found.project, ref);
}

/** per-initProject state: the main program plus, lazily, per-expectedType
 * "query programs" (design 11 §4: the harness file joins the same root set so
 * both sides of an assignability comparison come from one checker — type
 * identity is per-program, never mixed across programs). */
function newProject(program, checker, cacheKey) {
  return { program, checker, cacheKey, queryCache: new Map(), queryPrograms: new Map() };
}

const QUERY_HARNESS = '__nop_lint_query__.ts';
const QUERY_PROGRAM_LIMIT = 16;

function queryProgramFor(project, parsed, expectedType) {
  let query = project.queryPrograms.get(expectedType);
  if (query) return query;
  const harnessName = ts.normalizePath(resolveFsPath('/', QUERY_HARNESS));
  const harnessContent = `const __q: ${expectedType};\n`;
  const options = parsed.options;
  const host = ts.createCompilerHost(options);
  const baseGetSourceFile = host.getSourceFile.bind(host);
  host.getSourceFile = (fileName, languageVersionOrOptions, onError, shouldCreate) => {
    if (ts.normalizePath(fileName) === harnessName) {
      return ts.createSourceFile(fileName, harnessContent, options.target ?? ts.ScriptTarget.Latest, true);
    }
    return baseGetSourceFile(fileName, languageVersionOrOptions, onError, shouldCreate);
  };
  const program = ts.createProgram({
    rootNames: [...parsed.fileNames, harnessName],
    options,
    projectReferences: parsed.projectReferences,
    host,
  });
  query = { program, checker: program.getTypeChecker(), harnessName };
  if (project.queryPrograms.size >= QUERY_PROGRAM_LIMIT) {
    const oldest = project.queryPrograms.keys().next().value;
    project.queryPrograms.delete(oldest);
  }
  project.queryPrograms.set(expectedType, query);
  return query;
}

function expectedTypeViaQueryProgram(id, project, parsed, fromRef, expectedType) {
  const query = queryProgramFor(project, parsed, expectedType);
  const scratch = query.program.getSourceFile(query.harnessName);
  const stmt = scratch && scratch.statements[0];
  const typeNode = stmt && stmt.declarationList && stmt.declarationList.declarations[0]
    && stmt.declarationList.declarations[0].type;
  if (!typeNode) {
    return { error: errorFrame(id, 'BAD_REQUEST', `expectedType '${expectedType}' is not a parseable type expression`) };
  }
  const expected = query.checker.getTypeFromTypeNode(typeNode);
  if (expected.flags & ts.TypeFlags.Any && expected.intrinsicName === 'error') {
    return { error: errorFrame(id, 'QUERY_FAILED', `expectedType '${expectedType}' does not resolve in the project`) };
  }
  const from = resolveNodeTypeIn(id, query, fromRef);
  if (from.error) return from;
  return { checker: query.checker, fromType: from.type, targetType: expected };
}

function opIsTypeAssignableTo(id, params) {
  if (params.to) {
    const from = resolveNodeType(id, params.from || params);
    if (from.error) return from.error;
    const to = resolveNodeType(id, params.to);
    if (to.error) return to.error;
    const checker = from.checker;
    if (checker !== to.checker) {
      return errorFrame(id, 'QUERY_FAILED', 'from/to node references resolved to different projects');
    }
    const assignable = checker.isTypeAssignableTo(from.type, to.type);
    return assignabilityResult(id, checker, assignable, from.type, to.type);
  }
  if (typeof params.expectedType === 'string') {
    const ref = params.from || params;
    const found = projectFor(id, ref.file);
    if (found.error) return found.error;
    const resolved = expectedTypeViaQueryProgram(id, found.project, found.project.parsed, ref, params.expectedType);
    if (resolved.error) return resolved.error;
    const assignable = resolved.checker.isTypeAssignableTo(resolved.fromType, resolved.targetType);
    return assignabilityResult(id, resolved.checker, assignable, resolved.fromType, resolved.targetType);
  }
  return errorFrame(id, 'BAD_REQUEST', 'isTypeAssignableTo needs either a to node reference or an expectedType string');
}

function assignabilityResult(id, checker, assignable, fromType, targetType) {
  const render = t => checker.typeToString(t, undefined,
    ts.TypeFormatFlags.NoTruncation | ts.TypeFormatFlags.UseFullyQualifiedType);
  return {
    id,
    ok: true,
    result: { assignable, actualType: render(fromType), expectedType: render(targetType) },
  };
}

function dispatch(line) {
  let request;
  try {
    request = JSON.parse(line);
  } catch (e) {
    // A frame id may be unreadable; report on a synthetic id so the caller
    // sees the protocol violation instead of hanging.
    return errorFrame(-1, 'BAD_REQUEST', 'request is not valid JSON: ' + e.message);
  }
  const id = request.id;
  switch (request.op) {
    case 'initProject':
      return ensureProject(id, request.tsConfigPath);
    case 'getTypeAtLocation':
      return opGetTypeAtLocation(id, request);
    case 'isTypeAssignableTo':
      return opIsTypeAssignableTo(id, request);
    case 'shutdown':
      return { id, ok: true, result: { bye: true } };
    default:
      return errorFrame(id, 'UNKNOWN_OP', `unknown op '${request.op}' (supported: initProject, getTypeAtLocation, isTypeAssignableTo, shutdown)`);
  }
}

let buffer = '';
process.stdin.setEncoding('utf8');
process.stdin.on('data', chunk => {
  buffer += chunk;
  let index;
  while ((index = buffer.indexOf('\n')) >= 0) {
    const line = buffer.slice(0, index).trim();
    buffer = buffer.slice(index + 1);
    if (line.length === 0) continue;
    let frame;
    try {
      frame = dispatch(line);
    } catch (e) {
      frame = errorFrame(-1, 'QUERY_FAILED', 'internal helper failure: ' + (e && e.stack || e));
    }
    writeFrame(frame);
  }
});
process.stdin.on('end', () => {
  process.exit(0);
});

writeFrame({
  type: 'ready',
  node: process.version,
  typescript: ts.version,
});
