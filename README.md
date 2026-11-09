# springboot-eval-demo

RAG 与 Agent 评测平台：数据集 → 异步评测 → 规则指标 + LLM-as-judge → 报告与失败归因。

项目刻意保持轻量：Spring Boot 3.3.5 + Java 17 + 原生 HTML/JS 前端 + 内存存储，不接数据库，不引入 Python 评测框架。
重点是把评测闭环、双目标接入和 judge 降级讲清楚。

## 1. 架构

```text
浏览器
  → 选择数据集（RAG / Agent）
  → POST /api/runs 创建评测（异步）
  → 根据 dataset.targetType 选择被测系统
       RAG   → POST {RAG_BASE_URL}/api/chat
       Agent → POST {AGENT_BASE_URL}/api/agent/chat
  → 规则指标（检索/工具/关键词/相似度）
  → 可选 LLM-as-judge（智谱 GLM-4.7）
  → 汇总 + 失败归因
  → 前端轮询进度并展示报告
```

评测平台只调用两个现有项目的 HTTP 接口，不修改它们的代码。

## 2. 评测目标

| 类型 | 被测接口 | 返回 |
|---|---|---|
| RAG | `POST /api/chat` | `{code, message, data:{answer, sources}}` |
| Agent | `POST /api/agent/chat` | `{sessionId, answer, status, toolCalls}` |

每个 Agent 用例使用独立 `sessionId`（`eval-<runId>-<caseId>`），避免会话记忆互相污染。

## 3. 指标

### RAG

| 指标 | 说明 |
|---|---|
| Retrieval Hit | 期望来源文档是否出现在 `sources` 中 |
| Keyword Coverage | 期望关键词在回答中的覆盖率 |
| Answer Similarity | 与参考答案的字符 bigram Dice |
| Latency | RAG 调用耗时 |

### Agent

| 指标 | 说明 |
|---|---|
| Agent Status | `SUCCESS` / `MAX_ITERATIONS` / `LLM_ERROR` |
| Tool Hit | 期望工具是否出现在 `toolCalls` 中 |
| Tool Call Count | 本次调用的工具数量 |
| Keyword Coverage | 最终回答的关键词覆盖率 |
| Answer Similarity | 与参考答案的字符 bigram Dice |
| Latency | Agent 调用耗时 |

### LLM-as-judge

Judge 输入：问题、参考答案、上下文（RAG 的 sources 或 Agent 的 toolCalls 结果）、模型回答。

Rubric：

| 维度 | 分值 | 说明 |
|---|---|---|
| correctness | 0–40 | 与参考答案事实一致 |
| faithfulness | 0–30 | 回答被上下文支持，没有编造 |
| completeness | 0–30 | 覆盖参考答案的关键点 |
| score | 0–100 | 总分 |
| reason | 文本 | 评分理由 |

Judge 结果有三种状态：

- `OK`：拿到合法分数，参与 HYBRID 判定
- `SKIPPED`：未配置 Key / 显式关闭，不影响评测
- `ERROR`：超时、HTTP 错误、JSON 解析失败，回退规则指标

### 通过规则

`eval.pass-mode`：

| 模式 | 规则 |
|---|---|
| `DETERMINISTIC` | 只看规则指标；judge 仅记录，不影响通过 |
| `HYBRID`（默认） | 规则指标做 guardrail；judge 可用时再要求 `judgeScore ≥ eval.judge.pass-score` |

规则分：

```text
ruleScore = (keywordCoverage * 0.6 + similarity * 0.4) * 100
```

有效分：

- judge `OK` 且 HYBRID：取 judgeScore
- 其他情况：取 ruleScore

失败类型：

- `RETRIEVAL_MISS`：RAG 检索没召回期望文档
- `TOOL_MISS`：Agent 没调用期望工具
- `ANSWER_INCOMPLETE`：答案缺关键信息
- `AGENT_ERROR`：Agent 状态非 SUCCESS
- `TARGET_ERROR`：被测系统超时/连接失败/业务错误
- `JUDGE_FAIL`：规则通过，但 judge 分数低于阈值
- `PASS`：通过

## 4. Judge 模型

默认使用智谱 **GLM-4.7**：

- 200K 上下文，足够容纳问题 + 参考答案 + 检索片段 + 回答
- 支持关闭 thinking，降低 judge 延迟和输出 token
- 支持 JSON 结构化输出
- 与 RAG 的 DeepSeek 生成模型不同厂商，降低 self-preference bias

接口：

```text
POST https://open.bigmodel.cn/api/paas/v4/chat/completions
Authorization: Bearer ${ZHIPU_API_KEY}
```

配置：

```yaml
eval:
  judge:
    enabled: ${EVAL_JUDGE_ENABLED:true}
    base-url: ${ZHIPU_BASE_URL:https://open.bigmodel.cn/api/paas/v4}
    api-key: ${ZHIPU_API_KEY:}
    model: ${ZHIPU_JUDGE_MODEL:glm-4.7}
    timeout: 60s
    temperature: 0.0
    max-tokens: 800
    thinking: ${ZHIPU_JUDGE_THINKING:disabled}
    pass-score: 70
```

说明：

- `api-key` 只从环境变量读取，代码和 README 不保存真实密钥。
- 没有 `ZHIPU_API_KEY` 时 judge 自动 `SKIPPED`，评测继续使用规则指标。
- 切到 `glm-5.3` 时，服务端自动改为 `thinking.type=enabled` 并使用 `reasoning_effort=low`，因为 GLM-5.3 不支持关闭思考。
- 低成本场景可切到 `glm-4.7-flash`；旗舰质量校准可切到 `glm-5.3`。

## 5. 数据集

内置两个数据集：

### RAG 员工手册基线

6 条用例，覆盖年假、考勤、试用期、薪资、离职、培训，期望命中员工手册。

### Agent 工具调用基线

4 条用例：

| 问题 | 期望工具 |
|---|---|
| 现在几点？ | `get_current_time` |
| 计算 (12.5+7.5)*2 等于多少 | `calculator` |
| 统计「手机卡吧」有多少字符 | `text_stats` |
| 根据知识库回答：员工连续工作满三年但不满五年，每年有多少天年假？ | `rag_query` |

> 计算题可能被模型直接口算而不调用 `calculator`。如果报告出现 `TOOL_MISS`，说明 Agent 没有按工具契约执行，这是评测需要暴露的问题，不是平台误判。

### 数据集设计原则

- **条件型政策必须带上条件**：年假按工龄分 5/10/15 天，问题必须写明“满三年不满五年”，否则单一参考答案不成立。
- **一个问题只对应一个标准答案**：不要把多个档位压成一个答案，否则会误判正确回答。
- **不要按模型输出反推关键词**：关键词用于快速回归，语义正确性应交给 judge 和人工复核。
- **参考答案用业务原文/等价表述**：便于 judge 对齐，也便于人工检查。

## 6. 接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/datasets` | 数据集列表 |
| GET | `/api/datasets/{id}` | 数据集详情（含用例） |
| POST | `/api/runs` | 创建并异步启动评测，body：`{"datasetId": 1}` |
| GET | `/api/runs` | 历史运行 |
| GET | `/api/runs/{id}` | 状态、进度、汇总 |
| GET | `/api/runs/{id}/results` | 逐条用例结果 |

## 7. 页面

启动后访问 <http://localhost:8082/>：

- 数据集下拉显示 `[RAG]` / `[Agent]`
- 汇总卡片：通过率、平均分、检索/工具命中率、Judge 均分、平均耗时
- 结果表：有效分、规则分、Judge、命中、耗时、失败类型
- 明细：模型回答、参考答案、来源引用或工具调用、Judge 理由
- 历史运行可回看

## 8. 运行

前置条件：

- JDK 17、Maven 3.x
- RAG 服务（默认 8081）已启动
- Agent 服务（默认 8080）已启动
- 如需 judge：配置 `ZHIPU_API_KEY`

```bash
# 终端 1：RAG
cd springboot-rag-demo
mvn spring-boot:run

# 终端 2：Agent
cd springboot-react-agent-demo
mvn spring-boot:run

# 终端 3：评测平台
export ZHIPU_API_KEY=<your-key>   # 可选；不配则 judge 跳过
cd springboot-eval-demo
mvn spring-boot:run
```

打开 <http://localhost:8082/>，选择 RAG 或 Agent 数据集，点击「开始评测」。

地址覆盖：

```bash
export RAG_BASE_URL=http://localhost:8081
export AGENT_BASE_URL=http://localhost:8080
export EVAL_PASS_MODE=HYBRID
export EVAL_JUDGE_ENABLED=true
export ZHIPU_JUDGE_MODEL=glm-4.7
```

## 9. 测试

```bash
mvn test
```

覆盖：

- 指标：RAG 命中/未命中/答案不完整、Agent 工具命中/未命中/Agent 异常、HYBRID judge 未通过、judge 跳过回退
- RAG 客户端：成功、业务错误、HTTP 错误
- Agent 客户端：成功、HTTP 错误
- Judge：合法 JSON、非法 JSON、HTTP 错误、无 Key 跳过
- Spring 上下文冒烟

## 10. 项目结构

```text
src/main/java/com/demo/eval/
├── EvalApplication.java
├── config/
│   ├── EvalProperties.java       # RAG/Agent 地址、超时、通过模式
│   ├── JudgeProperties.java      # 智谱 judge 配置
│   ├── EvalConfig.java           # 三个 RestClient
│   └── AsyncConfig.java          # 评测线程池
├── controller/
│   ├── DatasetController.java
│   └── RunController.java
├── dto/                          # 请求/视图 DTO
├── model/                        # 数据集、用例、结果、judge、工具调用
├── service/
│   ├── DatasetService.java       # 两个 seed 数据集
│   ├── RagClient.java
│   ├── AgentClient.java
│   ├── JudgeService.java         # 智谱 GLM judge
│   ├── MetricService.java        # 规则指标 + judge 合并 + 归因
│   └── EvalRunService.java       # 异步执行与汇总
└── web/GlobalExceptionHandler.java
src/main/resources/
├── application.yml
└── static/index.html
```

## 11. 已知限制

- 内存存储，重启后运行记录丢失
- 数据集固定为 seed 数据，暂不支持页面上传/编辑
- Judge 对上下文只取前 8 条 RAG sources 或 5 条 Agent toolCalls，且做字符截断
- Agent 只评单轮任务和现有 `toolCalls`，不包含完整 Thought/迭代轨迹
- 未做并发限流、缓存、统计显著性和数据污染检测
- Judge 存在模型偏差，建议固定模型版本和 prompt，并抽样人工校准

## 12. 后续扩展

- 数据集导入/编辑（JSON/CSV）
- Judge 子分数展示与人工复核
- Agent 轨迹评分（Thought 步数、工具参数正确性）
- 持久化（MySQL/PostgreSQL）
- CI 回归门禁、版本 A/B、趋势图
- 多 judge 投票与一致性分析
