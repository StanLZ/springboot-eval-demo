package com.demo.eval.service;

import com.demo.eval.dto.RunView;
import com.demo.eval.model.EvalCase;
import com.demo.eval.model.EvalDataset;
import com.demo.eval.model.EvalResult;
import com.demo.eval.model.EvalRun;
import com.demo.eval.model.RagAnswer;
import com.demo.eval.model.RunStatus;
import com.demo.eval.model.RunSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 评测运行服务：创建运行、异步执行用例、汇总指标。
 */
@Service
public class EvalRunService {

    private static final Logger log = LoggerFactory.getLogger(EvalRunService.class);

    private final DatasetService datasetService;
    private final RagClient ragClient;
    private final MetricService metricService;
    private final Executor executor;

    private final Map<Long, EvalRun> runs = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    public EvalRunService(DatasetService datasetService,
                          RagClient ragClient,
                          MetricService metricService,
                          @Qualifier("evalExecutor") Executor executor) {
        this.datasetService = datasetService;
        this.ragClient = ragClient;
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
                    RagAnswer answer = ragClient.chat(testCase.question());
                    result = metricService.evaluate(testCase, answer, System.currentTimeMillis() - start);
                } catch (Exception e) {
                    log.warn("用例 {} 调用 RAG 失败：{}", testCase.id(), safeMessage(e));
                    result = metricService.targetError(testCase, System.currentTimeMillis() - start, safeMessage(e));
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
        double avgSimilarity = results.stream().mapToDouble(EvalResult::similarity).average().orElse(0);
        double avgLatency = results.stream().mapToLong(EvalResult::latencyMs).average().orElse(0);

        List<EvalResult> hitChecked = results.stream()
                .filter(result -> result.retrievalHit() != null)
                .toList();
        Double hitRate = hitChecked.isEmpty()
                ? null
                : (double) hitChecked.stream().filter(EvalResult::retrievalHit).count() / hitChecked.size();

        RunSummary summary = new RunSummary(
                run.total(),
                completed,
                passed,
                round(passRate),
                hitRate == null ? null : round(hitRate),
                round(avgScore),
                round(avgSimilarity),
                round(avgLatency)
        );

        return new RunView(
                run.id(),
                run.dataset().id(),
                run.dataset().name(),
                run.status(),
                run.total(),
                completed,
                run.startedAt(),
                run.finishedAt(),
                summary
        );
    }

    private static double round(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }

    private static String safeMessage(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }
}
