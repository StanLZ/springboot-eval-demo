package com.demo.eval.service;

import com.demo.eval.dto.RunView;
import com.demo.eval.model.AgentAnswer;
import com.demo.eval.model.EvalCase;
import com.demo.eval.model.EvalDataset;
import com.demo.eval.model.EvalResult;
import com.demo.eval.model.EvalRun;
import com.demo.eval.model.EvalTargetType;
import com.demo.eval.model.JudgeOutcome;
import com.demo.eval.model.JudgeStatus;
import com.demo.eval.model.RagAnswer;
import com.demo.eval.model.RagSource;
import com.demo.eval.model.RunStatus;
import com.demo.eval.model.RunSummary;
import com.demo.eval.model.ToolCall;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 评测运行服务：创建运行、异步执行用例、调用 RAG 或 Agent、汇总指标。
 */
@Service
public class EvalRunService {

    private static final Logger log = LoggerFactory.getLogger(EvalRunService.class);

    private final DatasetService datasetService;
    private final RagClient ragClient;
    private final AgentClient agentClient;
    private final JudgeService judgeService;
    private final MetricService metricService;
    private final Executor executor;

    private final Map<Long, EvalRun> runs = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    public EvalRunService(DatasetService datasetService,
                          RagClient ragClient,
                          AgentClient agentClient,
                          JudgeService judgeService,
                          MetricService metricService,
                          @Qualifier("evalExecutor") Executor executor) {
        this.datasetService = datasetService;
        this.ragClient = ragClient;
        this.agentClient = agentClient;
        this.judgeService = judgeService;
        this.metricService = metricService;
        this.executor = executor;
    }

    public RunView start(long datasetId) {
        EvalDataset dataset = datasetService.get(datasetId);
        EvalRun run = new EvalRun(sequence.incrementAndGet(), dataset);
        runs.put(run.id(), run);
        executor.execute(() -> execute(run));
        return toView(run);
    }

    public RunView get(long id) {
        return toView(getRun(id));
    }

    public List<RunView> list() {
        return runs.values().stream()
                .sorted(Comparator.comparing(EvalRun::startedAt).reversed())
                .map(this::toView)
                .toList();
    }

    public List<EvalResult> results(long id) {
        return getRun(id).results();
    }

    private EvalRun getRun(long id) {
        EvalRun run = runs.get(id);
        if (run == null) {
            throw new NoSuchElementException("评测运行不存在: " + id);
        }
        return run;
    }

    private void execute(EvalRun run) {
        try {
            for (EvalCase testCase : run.dataset().cases()) {
                long start = System.currentTimeMillis();
                EvalResult result;
                try {
                    if (run.dataset().targetType() == EvalTargetType.RAG) {
                        RagAnswer answer = ragClient.chat(testCase.question());
                        long targetLatency = elapsed(start);
                        long judgeStart = System.currentTimeMillis();
                        JudgeOutcome judge = judgeService.judge(
                                testCase, answer.answer(), ragContexts(answer.sources()));
                        result = metricService.evaluateRag(
                                testCase, answer, judge, targetLatency, elapsed(judgeStart));
                    } else {
                        String sessionId = "eval-" + run.id() + "-" + testCase.id();
                        AgentAnswer answer = agentClient.chat(sessionId, testCase.question());
                        long targetLatency = elapsed(start);
                        long judgeStart = System.currentTimeMillis();
                        JudgeOutcome judge = judgeService.judge(
                                testCase, answer.answer(), agentContexts(answer.toolCalls()));
                        result = metricService.evaluateAgent(
                                testCase, answer, judge, targetLatency, elapsed(judgeStart));
                    }
                } catch (Exception e) {
                    log.warn("用例 {} 调用被测系统失败：{}", testCase.id(), safeMessage(e));
                    result = metricService.targetError(
                            testCase, run.dataset().targetType(), elapsed(start), safeMessage(e));
                }
                run.addResult(result);
            }
            run.finish(RunStatus.SUCCESS);
            log.info("评测运行 {} 完成：{}", run.id(), toView(run).summary());
        } catch (Exception e) {
            log.error("评测运行 {} 异常终止", run.id(), e);
            run.finish(RunStatus.FAILED);
        }
    }

    private RunView toView(EvalRun run) {
        List<EvalResult> results = run.results();
        int completed = results.size();
        int passed = (int) results.stream().filter(EvalResult::pass).count();
        double passRate = completed == 0 ? 0 : (double) passed / completed;
        double avgScore = results.stream().mapToInt(EvalResult::score).average().orElse(0);
        double avgRuleScore = results.stream().mapToInt(EvalResult::ruleScore).average().orElse(0);
        double avgLatency = results.stream().mapToLong(EvalResult::latencyMs).average().orElse(0);

        List<Boolean> hitValues = results.stream()
                .map(result -> run.dataset().targetType() == EvalTargetType.RAG
                        ? result.retrievalHit()
                        : result.toolHit())
                .filter(Objects::nonNull)
                .toList();
        Double hitRate = hitValues.isEmpty()
                ? null
                : (double) hitValues.stream().filter(Boolean::booleanValue).count() / hitValues.size();

        List<Integer> judgeScores = results.stream()
                .filter(result -> result.judgeStatus() == JudgeStatus.OK)
                .map(EvalResult::judgeScore)
                .filter(Objects::nonNull)
                .toList();
        Double avgJudgeScore = judgeScores.isEmpty()
                ? null
                : judgeScores.stream().mapToInt(Integer::intValue).average().orElse(0);
        double judgeCoverage = completed == 0 ? 0 : (double) judgeScores.size() / completed;

        RunSummary summary = new RunSummary(
                run.total(),
                completed,
                passed,
                round(passRate),
                hitRate == null ? null : round(hitRate),
                round(avgScore),
                round(avgRuleScore),
                avgJudgeScore == null ? null : round(avgJudgeScore),
                round(judgeCoverage),
                round(avgLatency)
        );

        return new RunView(
                run.id(),
                run.dataset().id(),
                run.dataset().name(),
                run.dataset().targetType(),
                run.status(),
                run.total(),
                completed,
                run.startedAt(),
                run.finishedAt(),
                summary
        );
    }

    private List<String> ragContexts(List<RagSource> sources) {
        if (sources == null) {
            return List.of();
        }
        return sources.stream()
                .limit(8)
                .map(source -> "文档：" + source.documentName() + "（相似度 " + source.score() + "）\n"
                        + source.snippet())
                .toList();
    }

    private List<String> agentContexts(List<ToolCall> toolCalls) {
        if (toolCalls == null) {
            return List.of();
        }
        return toolCalls.stream()
                .limit(5)
                .map(toolCall -> "工具：" + toolCall.name() + "\n参数：" + toolCall.arguments()
                        + "\n结果：" + toolCall.result())
                .toList();
    }

    private static long elapsed(long start) {
        return System.currentTimeMillis() - start;
    }

    private static double round(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }

    private static String safeMessage(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }
}
