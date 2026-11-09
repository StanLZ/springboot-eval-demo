package com.demo.eval.controller;

import com.demo.eval.dto.CreateRunRequest;
import com.demo.eval.dto.RunView;
import com.demo.eval.model.EvalResult;
import com.demo.eval.service.EvalRunService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 评测运行接口：
 *  - POST /api/runs                     创建并异步启动评测
 *  - GET  /api/runs                     历史运行
 *  - GET  /api/runs/{id}                状态 + 进度 + 汇总
 *  - GET  /api/runs/{id}/results        用例结果
 */
@RestController
@RequestMapping("/api/runs")
public class RunController {

    private final EvalRunService evalRunService;

    public RunController(EvalRunService evalRunService) {
        this.evalRunService = evalRunService;
    }

    @PostMapping
    public RunView start(@RequestBody CreateRunRequest request) {
        if (request == null || request.datasetId() == null) {
            throw new IllegalArgumentException("datasetId 不能为空");
        }
        return evalRunService.start(request.datasetId());
    }

    @GetMapping
    public List<RunView> list() {
        return evalRunService.list();
    }

    @GetMapping("/{id}")
    public RunView get(@PathVariable long id) {
        return evalRunService.get(id);
    }

    @GetMapping("/{id}/results")
    public List<EvalResult> results(@PathVariable long id) {
        return evalRunService.results(id);
    }
}
