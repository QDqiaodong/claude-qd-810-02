package com.drill.crew.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.drill.crew.common.BizException;
import com.drill.crew.common.SampleDict;
import com.drill.crew.domain.Borehole;
import com.drill.crew.domain.CoreSample;
import com.drill.crew.repository.BoreholeRepository;
import com.drill.crew.repository.CoreSampleRepository;

/**
 * 岩芯样品取样登记。
 * <p>
 * 关键规则：
 * <ul>
 *   <li>样品编号、封签号全局唯一（服务层预检 + 数据库唯一约束兜底）；</li>
 *   <li>同一钻孔内孔段 [from, to) 不允许重叠；</li>
 *   <li>关键信息（编号 / 起止深度 / 取样人 / 封签号）不全时只能保存为 DRAFT 草稿，
 *       补齐后自动转为 READY 待送检；</li>
 *   <li>已装批 / 在途 / 已接收 / 待补的样品由批次链路管理，此处不允许改删。</li>
 * </ul>
 */
@Service
public class SampleService {

    private final CoreSampleRepository sampleRepository;
    private final BoreholeRepository boreholeRepository;

    public SampleService(CoreSampleRepository sampleRepository, BoreholeRepository boreholeRepository) {
        this.sampleRepository = sampleRepository;
        this.boreholeRepository = boreholeRepository;
    }

    // ---------------- 查询 ----------------

    @Transactional(readOnly = true)
    public List<CoreSample> list(Long boreholeId, String status) {
        if (boreholeId != null && status != null && !status.isBlank()) {
            return sampleRepository.findByBoreholeIdAndStatusOrderByFromDepthAscIdAsc(boreholeId, status);
        }
        if (boreholeId != null) {
            return sampleRepository.findByBoreholeIdOrderByFromDepthAscIdAsc(boreholeId);
        }
        if (status != null && !status.isBlank()) {
            return sampleRepository.findByStatusOrderByIdAsc(status);
        }
        return sampleRepository.findAll();
    }

    @Transactional(readOnly = true)
    public CoreSample get(Long id) {
        return sampleRepository.findById(id).orElseThrow(() -> new BizException("样品不存在：id=" + id));
    }

    // ---------------- 取样登记 ----------------

    @Transactional
    public CoreSample create(CoreSample payload) {
        CoreSample sample = CoreSample.builder()
                .boreholeId(payload.getBoreholeId())
                .sampleNo(trim(payload.getSampleNo()))
                .sealNo(trim(payload.getSealNo()))
                .fromDepth(payload.getFromDepth())
                .toDepth(payload.getToDepth())
                .sampler(trim(payload.getSampler()))
                .sampledAt(payload.getSampledAt())
                .remark(trim(payload.getRemark()))
                .build();
        validate(sample, null);
        sample.setStatus(isComplete(sample) ? SampleDict.SAMPLE_READY : SampleDict.SAMPLE_DRAFT);
        sample.setCreatedAt(LocalDateTime.now());
        sample.setUpdatedAt(sample.getCreatedAt());
        return saveGuarded(sample);
    }

    @Transactional
    public CoreSample update(Long id, CoreSample payload) {
        CoreSample sample = get(id);
        requireFieldEditable(sample);
        sample.setSampleNo(trim(payload.getSampleNo()));
        sample.setSealNo(trim(payload.getSealNo()));
        sample.setFromDepth(payload.getFromDepth());
        sample.setToDepth(payload.getToDepth());
        sample.setSampler(trim(payload.getSampler()));
        sample.setSampledAt(payload.getSampledAt());
        sample.setRemark(trim(payload.getRemark()));
        validate(sample, id);
        // 关键信息不全只能保存草稿；补齐后（含退回件修改）回到待送检
        sample.setStatus(isComplete(sample) ? SampleDict.SAMPLE_READY : SampleDict.SAMPLE_DRAFT);
        sample.setUpdatedAt(LocalDateTime.now());
        return saveGuarded(sample);
    }

    @Transactional
    public void delete(Long id) {
        CoreSample sample = get(id);
        requireFieldEditable(sample);
        sampleRepository.delete(sample);
    }

    // ---------------- 业务规则 ----------------

    /** 只有未进入送检链路的样品（草稿 / 待送检 / 已退回）才能在登记口改删 */
    private void requireFieldEditable(CoreSample sample) {
        String s = sample.getStatus();
        if (!SampleDict.SAMPLE_DRAFT.equals(s) && !SampleDict.SAMPLE_READY.equals(s)
                && !SampleDict.SAMPLE_RETURNED.equals(s)) {
            throw new BizException("样品【" + sample.getSampleNo() + "】当前为「"
                    + SampleDict.sampleStatusName(s) + "」，已进入送检链路，不能在登记口修改或删除");
        }
    }

    /** 关键信息：样品编号 / 起止深度 / 取样人 / 封签号 */
    static boolean isComplete(CoreSample s) {
        return s.getSampleNo() != null && s.getSealNo() != null
                && s.getFromDepth() != null && s.getToDepth() != null
                && s.getSampler() != null;
    }

    private void validate(CoreSample sample, Long excludeId) {
        if (sample.getBoreholeId() == null) {
            throw new BizException("样品必须挂在某个钻孔下");
        }
        Borehole hole = boreholeRepository.findById(sample.getBoreholeId())
                .orElseThrow(() -> new BizException("钻孔不存在：id=" + sample.getBoreholeId()));
        if ("封孔".equals(hole.getStatus())) {
            throw new BizException("钻孔【" + hole.getHoleNo() + "】已封孔，不允许再登记样品");
        }

        // 唯一性预检（数据库 uk_sample_no / uk_sample_seal 兜底）
        if (sample.getSampleNo() != null) {
            sampleRepository.findBySampleNo(sample.getSampleNo())
                    .filter(x -> !x.getId().equals(excludeId))
                    .ifPresent(x -> {
                        throw new BizException("样品编号【" + sample.getSampleNo() + "】已存在，不能重复登记");
                    });
        }
        if (sample.getSealNo() != null) {
            sampleRepository.findBySealNo(sample.getSealNo())
                    .filter(x -> !x.getId().equals(excludeId))
                    .ifPresent(x -> {
                        throw new BizException("封签号【" + sample.getSealNo() + "】已被样品【"
                                + (x.getSampleNo() == null ? "草稿#" + x.getId() : x.getSampleNo()) + "】使用，不能重复");
                    });
        }

        // 深度校验：只填一端不允许；两端齐全时校验倒挂、孔深与孔段重叠
        BigDecimal from = sample.getFromDepth();
        BigDecimal to = sample.getToDepth();
        if ((from == null) != (to == null)) {
            throw new BizException("取样起止深度必须同时填写");
        }
        if (from != null) {
            if (from.compareTo(BigDecimal.ZERO) < 0) {
                throw new BizException("取样起始深度不能为负数");
            }
            if (to.compareTo(from) <= 0) {
                throw new BizException("取样起止深度倒挂：起始 " + from + " m 不得大于或等于终止 " + to + " m");
            }
            if (to.compareTo(hole.getActualDepth()) > 0) {
                throw new BizException("取样终止深度 " + to + " m 超过当前孔深 " + hole.getActualDepth() + " m");
            }
            List<CoreSample> overlapping = sampleRepository.findOverlapping(sample.getBoreholeId(), from, to);
            overlapping.removeIf(x -> x.getId().equals(excludeId));
            if (!overlapping.isEmpty()) {
                CoreSample first = overlapping.get(0);
                throw new BizException("同一孔段不能生成重叠样品：与样品【"
                        + (first.getSampleNo() == null ? "草稿#" + first.getId() : first.getSampleNo()) + "】的孔段 "
                        + first.getFromDepth() + "~" + first.getToDepth() + " m 重叠");
            }
        }
    }

    /** 唯一约束兜底：并发登记相同编号 / 封签时给出业务提示而不是 500 */
    private CoreSample saveGuarded(CoreSample sample) {
        try {
            return sampleRepository.saveAndFlush(sample);
        } catch (DataIntegrityViolationException e) {
            throw new BizException("样品编号或封签号与已有记录重复，请更换后重新保存");
        }
    }

    static String trim(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
