package com.demo.eval.model;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 一次评测运行的内存模型。
 * results 使用 CopyOnWriteArrayList，异步执行时读取不会抛并发修改异常。
 */
public class EvalRun {

    private final long id;
    private final EvalDataset dataset;
    private final Instant startedAt;
    private final List<EvalResult> results = new CopyOnWriteArrayList<>();

    private volatile RunStatus status = RunStatus.RUNNING;
    private volatile Instant finishedAt;

    public EvalRun(long id, EvalDataset dataset) {
        this.id = id;
        this.dataset = dataset;
        this.startedAt = Instant.now();
    }

    public long id() {
        return id;
    }

    public EvalDataset dataset() {
        return dataset;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Instant finishedAt() {
        return finishedAt;
    }

    public RunStatus status() {
        return status;
    }

    public int total() {
        return dataset.cases().size();
    }

    public int completed() {
        return results.size();
    }

    public List<EvalResult> results() {
        return List.copyOf(results);
    }

    public void addResult(EvalResult result) {
        results.add(result);
    }

    public void finish(RunStatus finalStatus) {
        this.status = finalStatus;
        this.finishedAt = Instant.now();
    }
}
