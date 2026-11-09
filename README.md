# springboot-eval-demo

一个用于面试演示的 **RAG 评测平台**：数据集 → 异步评测 → 指标计算 → 报告与失败归因。

项目刻意保持轻量：单机 Spring Boot + 原生 HTML/JS 前端 + 内存存储，不接数据库、不引入 Python 评测框架，
重点是把评测闭环和指标设计讲清楚。

## 1. 评测流程

```text
浏览器选择数据集
  → POST /api/runs 创建运行（异步）
  → 逐条调用被测 RAG：POST {rag-base-url}/api/chat
  → 计算指标：检索命中 / 关键词覆盖 / 答案相似度 / 耗时
  → 汇总 + 失败归因
  → 前端轮询进度并展示报告
```

被测系统是独立的 `springboot-rag-demo`，默认地址 `http://localhost:8081`；
本评测项目不直接持有大模型 Key，Key 由 RAG 服务自己读取。

## 2. 指标

| 指标 | 说明 |
|---|---|
| Retrieval Hit | 期望来源文档名是否出现在 RAG 返回的 `sources` 中；未配置期望文档时为 `null` |
| Keyword Coverage | 期望关键词在回答中的覆盖率，0~1 |
| Answer Similarity | 回答与参考答案的字符 bigram Dice 相似度，0~1 |
| Latency | 单条用例调用耗时 |
| Score | `keywordCoverage * 0.6 + similarity * 0.4`，换算为 0~100 |
| Pass | 检索命中（或未配置期望文档）且关键词覆盖率 ≥ `eval.pass-threshold` |

失败类型：

- `RETRIEVAL_MISS`：期望文档没有被召回
- `ANSWER_INCOMPLETE`：召回了，但答案缺失关键信息
- `TARGET_ERROR`：RAG 服务超时/连接失败/业务错误
- `PASS`：通过

这样报告不仅能给总分，还能回答“是检索的问题，还是生成的问题”。

## 3. 页面

单页前端，启动后访问 <http://localhost:8082/>：

- 选择数据集，点击「开始评测」
- 实时进度条、运行状态
- 汇总卡片：通过率、平均分、检索命中率、平均耗时
- 用例结果表：每条用例的通过状态、分数、检索命中、耗时、失败类型
- 点击用例查看明细：问题、模型回答、参考答案、期望关键词、来源引用、错误信息
- 历史运行列表，可回看历史报告

## 4. 接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/datasets` | 数据集列表 |
| GET | `/api/datasets/{id}` | 数据集详情（含用例） |
| POST | `/api/runs` | 创建并异步启动评测，body：`{"datasetId": 1}` |
| GET | `/api/runs` | 历史运行 |
| GET | `/api/runs/{id}` | 状态、进度、汇总 |
| GET | `/api/runs/{id}/results` | 逐条用例结果 |

## 5. 配置

```yaml
server:
  port: 8082

eval:
  rag-base-url: ${RAG_BASE_URL:http://localhost:8081}
  connect-timeout: 3s
  read-timeout: 90s   # RAG /api/chat 内部会调用大模型
  pass-threshold: 0.6
```

如 RAG 不在 8081，用环境变量覆盖：

```bash
export RAG_BASE_URL=http://localhost:8081
```

## 6. 运行

前置条件：

- JDK 17、Maven 3.x
- 已启动 `springboot-rag-demo`（8081），并配置好其 `DEEPSEEK_API_KEY` / `EMBEDDING_API_KEY`
- RAG 知识库中已有员工手册类文档（seed 用例基于该文档）

```bash
# 终端 1：启动 RAG
cd springboot-rag-demo
mvn spring-boot:run

# 终端 2：启动评测平台
cd springboot-eval-demo
mvn spring-boot:run
```

打开 <http://localhost:8082/>，选择数据集，点击「开始评测」。

## 7. 当前 seed 数据集

`RAG 员工手册基线`，6 条用例，覆盖：

1. 年假天数
2. 迟到/早退处理
3. 试用期
4. 发薪日
5. 离职提前通知
6. 年度培训时长

期望来源均为「员工手册」，参考答案和关键词来自 `springboot-rag-demo/data/员工手册.txt`。

实际运行结果（本地 RAG 可用时）：

```text
6 / 6 通过
通过率：100%
平均分：84.2
检索命中率：100%
平均耗时：约 1312 ms
```

## 8. 项目结构

```text
src/main/java/com/demo/eval/
├── EvalApplication.java
├── config/
│   ├── EvalProperties.java      # eval.* 配置
│   ├── EvalConfig.java          # RestClient
│   └── AsyncConfig.java         # 评测线程池
├── controller/
│   ├── DatasetController.java
│   └── RunController.java
├── dto/                         # 请求/视图 DTO
├── model/                       # 数据集、用例、运行、结果、指标
├── service/
│   ├── DatasetService.java      # seed 数据集
│   ├── RagClient.java           # 调用 RAG /api/chat
│   ├── MetricService.java       # 指标计算与失败归因
│   └── EvalRunService.java      # 异步执行与汇总
└── web/GlobalExceptionHandler.java
src/main/resources/
├── application.yml
└── static/index.html            # 单页前端
src/test/java/com/demo/eval/
├── EvalApplicationTests.java
└── service/
    ├── MetricServiceTest.java
    └── RagClientTest.java
```

## 9. 测试

```bash
mvn test
```

当前 8 个测试：

- 指标计算 4 个：通过、检索未命中、答案不完整、目标异常
- RAG 客户端 3 个：成功、业务错误、HTTP 错误
- Spring 上下文 1 个

## 10. 面试讲解要点

1. **为什么不能只看最终答案**：RAG 有检索和生成两段，报告用 `Retrieval Hit` 与 `Keyword Coverage` 做失败归因。
2. **指标怎么设计**：检索看来源命中，答案看关键词覆盖和相似度，耗时看体验；总分是加权，不替代逐项指标。
3. **为什么异步**：RAG 单条调用包含检索 + 大模型，6 条用例串行会阻塞 HTTP 请求；用线程池执行，前端轮询进度。
4. **为什么内存存储**：demo 聚焦评测闭环；生产替换为 MySQL/PostgreSQL 即可，接口不变。
5. **为什么用 Spring Boot**：评测核心是编排、指标和报告，不是模型训练；Java 与现有 RAG/Agent 项目一致，便于讲清工程实现。

## 11. 已知限制与后续增强

当前限制：

- 数据集固定为 6 条 seed 用例，暂不支持页面上传/编辑
- 内存存储，重启后历史运行丢失
- 指标为确定性指标，未接 LLM-as-judge
- 仅评测 RAG，不评测 Agent 轨迹
- 单机单线程顺序执行，未做并发和限流
- 没有统计显著性、置信区间、数据污染检测

后续可加：

- 数据集导入/编辑（JSON/CSV）
- LLM-as-judge（DeepSeek JSON 评分 + 人工复核）
- Agent 适配器：任务成功率、工具调用正确率、轨迹步数
- 持久化（MySQL/PostgreSQL）与多数据集版本
- CI 回归门禁、版本 A/B、趋势图
- 并发执行、缓存、失败重试
