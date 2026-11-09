package com.demo.eval.service;

import com.demo.eval.dto.DatasetDetail;
import com.demo.eval.dto.DatasetView;
import com.demo.eval.model.EvalCase;
import com.demo.eval.model.EvalDataset;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * 数据集服务。
 * 当前使用内存中的 seed 数据，方便 demo 和面试演示；后续可替换为数据库。
 */
@Service
public class DatasetService {

    private final Map<Long, EvalDataset> datasets = new LinkedHashMap<>();

    public DatasetService() {
        List<EvalCase> cases = List.of(
                new EvalCase(1,
                        "员工连续工作满三年但不满五年，每年有多少天年假？",
                        "工作满三年不满五年，每年享受年假十天。",
                        List.of("十天"),
                        "员工手册"),
                new EvalCase(2,
                        "迟到或早退超过多少分钟按旷工半天处理？",
                        "迟到或早退超过十五分钟，按旷工半天处理。",
                        List.of("十五分钟"),
                        "员工手册"),
                new EvalCase(3,
                        "新员工的试用期是多久？",
                        "新员工试用期为三个月。",
                        List.of("三个月"),
                        "员工手册"),
                new EvalCase(4,
                        "公司每月几号发放上月工资？",
                        "公司每月十五日发放上月工资。",
                        List.of("十五日"),
                        "员工手册"),
                new EvalCase(5,
                        "员工主动离职需要提前多少天提交书面申请？",
                        "主动离职须提前三十天提交书面辞职申请。",
                        List.of("三十天"),
                        "员工手册"),
                new EvalCase(6,
                        "公司每年为每位员工提供不少于多少小时的培训？",
                        "每年为每位员工提供不少于二十小时的培训机会。",
                        List.of("二十小时"),
                        "员工手册")
        );

        EvalDataset dataset = new EvalDataset(
                1L,
                "RAG 员工手册基线",
                "覆盖年假、考勤、试用期、薪资、离职、培训 6 个问题，期望命中员工手册。",
                cases
        );
        datasets.put(dataset.id(), dataset);
    }

    public List<DatasetView> list() {
        return datasets.values().stream()
                .map(d -> new DatasetView(d.id(), d.name(), d.description(), d.cases().size()))
                .toList();
    }

    public DatasetDetail detail(long id) {
        EvalDataset dataset = get(id);
        return new DatasetDetail(dataset.id(), dataset.name(), dataset.description(), dataset.cases());
    }

    /** 内部使用：取完整数据集，不存在时抛 404 对应的异常 */
    public EvalDataset get(long id) {
        EvalDataset dataset = datasets.get(id);
        if (dataset == null) {
            throw new NoSuchElementException("数据集不存在: " + id);
        }
        return dataset;
    }
}
