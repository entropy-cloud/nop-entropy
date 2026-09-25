#!/usr/bin/env node
// nop-code GraphQL 查询助手（零依赖，Node 18+）
// nop-deepwiki skill 的 Phase 2 工具。用法见 references/nop-code-api.md §2。
//
//   node nop-code-graphql.mjs --login nop nop-test          # 登录取 JWT，打印并提示导出
//   echo 'query { NopCodeIndex__findList { id name } }' | node nop-code-graphql.mjs
//   node nop-code-graphql.mjs --query-file q.graphql --vars '{"indexId":"main"}'
//   node nop-code-graphql.mjs --rest /r/NopCodeIndex__findList --data '{}'
//
// 环境变量：NOP_CODE_ENDPOINT（默认 http://localhost:8081/graphql）、NOP_CODE_TOKEN（Bearer JWT）

const args = process.argv.slice(2);

function opt(name) {
  const i = args.indexOf(name);
  return i >= 0 ? args[i + 1] : undefined;
}

function readStdin() {
  return new Promise((resolve) => {
    let s = '';
    if (process.stdin.isTTY) return resolve('');
    process.stdin.setEncoding('utf8');
    process.stdin.on('data', (d) => (s += d));
    process.stdin.on('end', () => resolve(s));
  });
}

async function postJson(url, body, token) {
  const headers = { 'Content-Type': 'application/json' };
  if (token) headers.Authorization = `Bearer ${token}`;
  const resp = await fetch(url, {
    method: 'POST',
    headers,
    body: JSON.stringify(body),
  });
  const text = await resp.text();
  let json;
  try {
    json = JSON.parse(text);
  } catch {
    throw new Error(`非 JSON 响应（HTTP ${resp.status}）：${text.slice(0, 300)}`);
  }
  if (!resp.ok) throw new Error(`HTTP ${resp.status}：${text.slice(0, 300)}`);
  return json;
}

async function main() {
  const endpointBase = process.env.NOP_CODE_ENDPOINT || 'http://localhost:8081';
  const graphqlUrl = endpointBase.replace(/\/$/, '').endsWith('/graphql')
    ? endpointBase
    : `${endpointBase.replace(/\/$/, '')}/graphql`;
  const token = process.env.NOP_CODE_TOKEN;

  // --login <user> <pass>
  if (args[0] === '--login') {
    const [user, pass] = [args[1] || 'nop', args[2] || 'nop-test'];
    const result = await postJson(
      `${graphqlUrl.replace(/\/graphql$/, '')}/r/LoginApi__login`,
      { principalId: user, principalSecret: pass, loginType: 1 }
    );
    if (!result.accessToken) {
      console.error('登录失败：', JSON.stringify(result).slice(0, 300));
      process.exit(1);
    }
    console.log(`# 登录成功，expiresIn=${result.expiresIn}s。后续调用前设置：`);
    console.log(`export NOP_CODE_TOKEN='${result.accessToken}'`);
    return;
  }

  // --rest /r/xxx --data '{}'
  if (args[0] === '--rest') {
    const path = args[1];
    if (!path || !path.startsWith('/r/')) {
      console.error('用法：--rest /r/<Query或Mutation名> [--data JSON]');
      process.exit(2);
    }
    const dataStr = opt('--data') || (await readStdin()) || '{}';
    const url = `${graphqlUrl.replace(/\/graphql$/, '')}${path}`;
    const json = await postJson(url, JSON.parse(dataStr), token);
    console.log(JSON.stringify(json, null, 2));
    return;
  }

  // GraphQL 模式：--query / --query-file / stdin
  let query = opt('--query');
  const queryFile = opt('--query-file');
  if (queryFile) query = (await import('node:fs/promises')).readFile(queryFile, 'utf8');
  if (!query) query = (await readStdin()).trim();
  if (!query) {
    console.error('用法：--query "..." | --query-file f.graphql | stdin 传 GraphQL query');
    process.exit(2);
  }

  let vars = {};
  const varsStr = opt('--vars');
  if (varsStr) {
    try {
      vars = JSON.parse(varsStr);
    } catch (e) {
      console.error(`--vars 不是合法 JSON：${e.message}`);
      process.exit(2);
    }
  }

  const json = await postJson(graphqlUrl, { query, variables: vars }, token);
  if (json.errors && json.errors.length) {
    console.log(JSON.stringify(json.data ?? {}, null, 2));
    console.error('GraphQL errors：', JSON.stringify(json.errors, null, 2));
    process.exit(1);
  }
  console.log(JSON.stringify(json.data, null, 2));
}

main().catch((e) => {
  console.error(`请求失败：${e.message}`);
  console.error('排查：服务是否已启动（默认 http://localhost:8081）？是否需要先 --login？');
  process.exit(1);
});
