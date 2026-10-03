package com.drill.crew.controller;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.drill.crew.common.ApiResponse;
import com.drill.crew.common.SampleDict;
import com.drill.crew.domain.CoreSample;
import com.drill.crew.service.SampleService;

/**
 * 岩芯样品取样登记：现场在指定孔段登记样品编号、起止深度、取样人和封签号。
 * 关键信息不全时只能保存为草稿；编号与封签号唯一；同一孔段不允许重叠样品。
 */
@RestController
@RequestMapping("/api/samples")
public class SampleController {

    private final SampleService sampleService;

    public SampleController(SampleService sampleService) {
        this.sampleService = sampleService;
    }

    /** 样品列表：可按钻孔 / 状态过滤 */
    @GetMapping
    public ApiResponse<List<CoreSample>> list(@RequestParam(required = false) Long boreholeId,
                                              @RequestParam(required = false) String status) {
        return ApiResponse.success(sampleService.list(boreholeId, status));
    }

    /** 取样登记（信息不全自动保存为草稿） */
    @PostMapping
    public ApiResponse<CoreSample> create(@RequestBody CoreSample sample) {
        CoreSample saved = sampleService.create(sample);
        return ApiResponse.success(SampleDict.SAMPLE_DRAFT.equals(saved.getStatus())
                ? "关键信息不全，已保存为草稿" : "样品已登记，待送检", saved);
    }

    /** 修改登记（仅草稿 / 待送检 / 已退回） */
    @PutMapping("/{id}")
    public ApiResponse<CoreSample> update(@PathVariable Long id, @RequestBody CoreSample sample) {
        CoreSample saved = sampleService.update(id, sample);
        return ApiResponse.success(SampleDict.SAMPLE_DRAFT.equals(saved.getStatus())
                ? "关键信息不全，已保存为草稿" : "样品已更新", saved);
    }

    /** 删除（仅草稿 / 待送检 / 已退回） */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        sampleService.delete(id);
        return ApiResponse.success("样品已删除", null);
    }
}
