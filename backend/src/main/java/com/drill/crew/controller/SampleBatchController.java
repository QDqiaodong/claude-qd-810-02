package com.drill.crew.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.drill.crew.common.ApiResponse;
import com.drill.crew.domain.SampleBatch;
import com.drill.crew.dto.SampleAction;
import com.drill.crew.service.SampleBatchService;

/**
 * 送检批次链路：现场封样成批、封样交接（运输）；实验室接收（回写接收时间 / 状态 / 异常说明）、
 * 部分接收保留已接收样品并标出待补项、整批退回；历史交接结果随流转记录可追溯。
 */
@RestController
@RequestMapping("/api/sample-batches")
public class SampleBatchController {

    private final SampleBatchService batchService;

    public SampleBatchController(SampleBatchService batchService) {
        this.batchService = batchService;
    }

    /** 批次列表（可按状态过滤） */
    @GetMapping
    public ApiResponse<List<SampleBatch>> list(@RequestParam(required = false) String status) {
        return ApiResponse.success(batchService.list(status));
    }

    /** 批次详情：批次头 + 样品明细 + 流转记录 */
    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        return ApiResponse.success(batchService.detail(id));
    }

    /** 现场：封样成批（选择待送检 / 已退同样品） */
    @PostMapping
    public ApiResponse<Map<String, Object>> create(@RequestBody SampleAction action) {
        return ApiResponse.success("批次已建立，样品已装批", batchService.create(action));
    }

    /** 现场：向封样中的批次添加样品 */
    @PostMapping("/{id}/samples")
    public ApiResponse<Map<String, Object>> addSamples(@PathVariable Long id, @RequestBody SampleAction action) {
        return ApiResponse.success("样品已加入批次", batchService.addSamples(id, action));
    }

    /** 现场：从封样中的批次移除样品 */
    @DeleteMapping("/{id}/samples/{sampleId}")
    public ApiResponse<Map<String, Object>> removeSample(@PathVariable Long id, @PathVariable Long sampleId,
                                                         @RequestBody(required = false) SampleAction action) {
        return ApiResponse.success("样品已移出批次", batchService.removeSample(id, sampleId, action));
    }

    /** 现场：取消封样中的批次（样品退回待送检） */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> disband(@PathVariable Long id, @RequestBody(required = false) SampleAction action) {
        batchService.disband(id, action);
        return ApiResponse.success("批次已取消，样品退回待送检", null);
    }

    /** 现场：封样交接（固化数量与封签快照，批次转为在途） */
    @PostMapping("/{id}/handover")
    public ApiResponse<Map<String, Object>> handover(@PathVariable Long id, @RequestBody SampleAction action) {
        return ApiResponse.success("封样交接完成，批次在途", batchService.handover(id, action));
    }

    /** 实验室：整批退回（必须填写原因，样品退回现场） */
    @PostMapping("/{id}/return")
    public ApiResponse<Map<String, Object>> returnBatch(@PathVariable Long id, @RequestBody SampleAction action) {
        return ApiResponse.success("批次已整批退回", batchService.returnBatch(id, action));
    }

    /** 实验室：接收（数量、封签一致整批接收；部分异常保留已接收样品并标出待补项） */
    @PostMapping("/{id}/receive")
    public ApiResponse<Map<String, Object>> receive(@PathVariable Long id, @RequestBody SampleAction action) {
        return ApiResponse.success("接收结果已回写", batchService.receive(id, action));
    }
}
