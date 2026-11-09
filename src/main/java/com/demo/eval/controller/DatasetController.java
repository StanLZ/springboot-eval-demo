package com.demo.eval.controller;

import com.demo.eval.dto.DatasetDetail;
import com.demo.eval.dto.DatasetView;
import com.demo.eval.service.DatasetService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 数据集查询接口。
 */
@RestController
@RequestMapping("/api/datasets")
public class DatasetController {

    private final DatasetService datasetService;

    public DatasetController(DatasetService datasetService) {
        this.datasetService = datasetService;
    }

    @GetMapping
    public List<DatasetView> list() {
        return datasetService.list();
    }

    @GetMapping("/{id}")
    public DatasetDetail detail(@PathVariable long id) {
        return datasetService.detail(id);
    }
}
