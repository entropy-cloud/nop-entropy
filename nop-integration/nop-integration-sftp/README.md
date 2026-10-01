# nop-integration-sftp

基于 JSch 的 SFTP 客户端集成模块（`SftpClient` / `SftpConfig`）。

## 连接配置

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `host` / `port` / `username` / `password` | — | 连接目标与凭证（凭证处理遵循平台 fail-closed 约定） |
| `strictHostKeyChecking` | `false` | 主机公钥校验开关。`false` 跳过 known_hosts 校验（**存在中间人风险**，每次建连输出 WARN）；`true` 强制校验，未知主机连接失败（fail-closed） |
| `knownHostsPath` | — | `strictHostKeyChecking=true` 时装载的 known_hosts 文件路径；未配置则使用 JSch 默认位置 |

> 风险声明：`strictHostKeyChecking=false`（默认，兼容现网行为）不校验服务器主机公钥，生产环境建议显式开启并配置 `knownHostsPath`。
