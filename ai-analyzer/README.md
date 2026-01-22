# AI源码分析器 - AI Analyzer

基于AI的Java源码智能分析系统，采用GraphQL接口和丰富的模型结构，支持语义级别的源码预处理和智能索引。

## 项目结构

```
ai-analyzer/
├── docs/                    # 文档目录
│   ├── workflow.md          # 预处理工作流详细设计
│   ├── graphql-schema.md    # GraphQL数据模型设计
│   └── prompts/             # 提示词设计
│       ├── code-analysis.md
│       ├── semantic-extraction.md
│       └── relationship-extraction.md
├── src/
│   ├── main/java/io/nop/ai/analyzer/
│   │   ├── model/           # 数据模型
│   │   ├── service/         # 业务服务
│   │   ├── graphql/         # GraphQL接口
│   │   ├── ai/              # AI处理引擎
│   │   └── storage/         # 存储层
│   └── test/
├── config/
│   ├── application.yml      # 应用配置
│   ├── graphql-schema.graphqls
│   └── ai-prompt-templates.yml
├── scripts/
│   ├── analyze-project.sh   # 项目分析脚本
│   ├── build-index.sh       # 索引构建脚本
│   └── deploy.sh            # 部署脚本
└── docker/
    ├── Dockerfile
    └── docker-compose.yml
```