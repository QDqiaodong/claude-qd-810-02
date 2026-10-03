package com.drill.crew.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.drill.crew.common.BizException;
import com.drill.crew.common.ConflictException;
import com.drill.crew.common.SampleDict;
import com.drill.crew.domain.CoreSample;
import com.drill.crew.domain.SampleBatch;
import com.drill.crew.domain.SampleBatchEvent;
import com.drill.crew.dto.ReceiveItem;
import com.drill.crew.dto.SampleAction;
import com.drill.crew.dto.SampleEventItem;
import com.drill.crew.repository.CoreSampleRepository;
import com.drill.crew.repository.SampleBatchEventRepository;
import com.drill.crew.repository.SampleBatchRepository;

/**
 * 送检批次链路：封样成批 → 封样交接（运输）→ 实验室接收 / 部分接收 / 整批退回 → 补接收。
 * <p>
 * 关键规则：
 * <ul>
 *   <li>批次交接时固化样品数量与封签清单快照，交接对话框与流转记录都展示样品明细；</li>
 *   <li>实验室只接收「在途 / 部分接收」状态且数量、封签一致的批次；
 *       部分异常时保留已接收样品，异常件标为待补并汇总到批次异常说明；</li>
 *   <li>行锁 + 乐观锁 + 状态机：重复点击交接 / 接收不会产生重复记录与重复流转记录；</li>
 *   <li>每次状态推进都落 sample_batch_event（含当时样品明细快照），历史交接结果可追溯。</li>
 * </ul>
 */
@Service
public class SampleBatchService {

    private final SampleBatchRepository batchRepository;
    private final CoreSampleRepository sampleRepository;
    private final SampleBatchEventRepository eventRepository;

    public SampleBatchService(SampleBatchRepository batchRepository, CoreSampleRepository sampleRepository,
                              SampleBatchEventRepository eventRepository) {
        this.batchRepository = batchRepository;
        this.sampleRepository = sampleRepository;
        this.eventRepository = eventRepository;
    }

    // ---------------- 查询 ----------------

    @Transactional(readOnly = true)
    public List<SampleBatch> list(String status) {
        if (status != null && !status.isBlank()) {
            return batchRepository.findByStatusOrderByIdDesc(status);
        }
        return batchRepository.findAllByOrderByIdDesc();
    }

    /** 批次详情：批次头 + 样品明细 + 流转记录（历史交接结果） */
    @Transactional(readOnly = true)
    public Map<String, Object> detail(Long id) {
        SampleBatch batch = getBatch(id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("batch", batch);
        result.put("samples", sampleRepository.findByBatchIdOrderByIdAsc(id));
        result.put("events", eventRepository.findByBatchIdOrderByIdAsc(id));
        return result;
    }

    // ---------------- 现场：封样成批 ----------------

    @Transactional
    public Map<String, Object> create(SampleAction action) {
        List<CoreSample> samples = requirePackableSamples(action.getSampleIds());
        String operator = SampleService.trim(action.getOperator());

        SampleBatch batch = SampleBatch.builder()
                .batchNo(nextBatchNo(action.getBatchNo()))
                .status(SampleDict.BATCH_PACKING)
                .sampleCount(samples.size())
                .packedBy(operator)
                .transportNote(SampleService.trim(action.getTransportNote()))
                .receivedCount(0)
                .lockVersion(0)
                .createdAt(LocalDateTime.now())
                .build();
        batch = saveBatchGuarded(batch);

        for (CoreSample sample : samples) {
            sample.setStatus(SampleDict.SAMPLE_PACKED);
            sample.setBatchId(batch.getId());
            sampleRepository.save(sample);
        }
        saveEvent(batch, SampleDict.EV_PACK, operator, "封样 " + samples.size() + " 件", eventDetail(samples));
        return detail(batch.getId());
    }

    /** 向封样中的批次添加样品 */
    @Transactional
    public Map<String, Object> addSamples(Long id, SampleAction action) {
        SampleBatch batch = lockBatch(id, action.getExpectedVersion());
        requireStatus(batch, SampleDict.BATCH_PACKING, "添加样品");
        List<CoreSample> samples = requirePackableSamples(action.getSampleIds());
        for (CoreSample sample : samples) {
            sample.setStatus(SampleDict.SAMPLE_PACKED);
            sample.setBatchId(batch.getId());
            sampleRepository.save(sample);
        }
        refreshCount(batch);
        batch.setLockVersion(batch.getLockVersion() + 1);
        batchRepository.save(batch);
        return detail(id);
    }

    /** 从封样中的批次移除单个样品（退回待送检） */
    @Transactional
    public Map<String, Object> removeSample(Long id, Long sampleId, SampleAction action) {
        SampleBatch batch = lockBatch(id, action == null ? null : action.getExpectedVersion());
        requireStatus(batch, SampleDict.BATCH_PACKING, "移除样品");
        CoreSample sample = getSample(sampleId);
        if (!batch.getId().equals(sample.getBatchId())) {
            throw new BizException("样品【" + sample.getSampleNo() + "】不在本批次中");
        }
        sample.setStatus(SampleDict.SAMPLE_READY);
        sample.setBatchId(null);
        sampleRepository.save(sample);
        refreshCount(batch);
        batch.setLockVersion(batch.getLockVersion() + 1);
        batchRepository.save(batch);
        return detail(id);
    }

    /** 取消封样中的批次：样品退回待送检，批次及其封样记录删除（尚未交接，无历史结果需保留） */
    @Transactional
    public void disband(Long id, SampleAction action) {
        SampleBatch batch = lockBatch(id, action == null ? null : action.getExpectedVersion());
        requireStatus(batch, SampleDict.BATCH_PACKING, "取消批次");
        for (CoreSample sample : sampleRepository.findByBatchIdOrderByIdAsc(id)) {
            sample.setStatus(SampleDict.SAMPLE_READY);
            sample.setBatchId(null);
            sampleRepository.save(sample);
        }
        eventRepository.deleteByBatchId(id);
        batchRepository.delete(batch);
    }

    // ---------------- 现场：封样交接（运输） ----------------

    @Transactional
    public Map<String, Object> handover(Long id, SampleAction action) {
        SampleBatch batch = lockBatch(id, action.getExpectedVersion());
        requireStatus(batch, SampleDict.BATCH_PACKING, "封样交接");
        String operator = SampleService.trim(action.getOperator());
        if (operator == null) {
            throw new BizException("封样交接必须填写交出人");
        }
        List<CoreSample> samples = sampleRepository.findByBatchIdOrderByIdAsc(id);
        if (samples.isEmpty()) {
            throw new BizException("批次内没有样品，不能交接");
        }
        for (CoreSample s : samples) {
            if (!SampleService.isComplete(s)) {
                throw new BizException("样品【" + (s.getSampleNo() == null ? "草稿#" + s.getId() : s.getSampleNo())
                        + "】关键信息不全，不能交接");
            }
        }

        LocalDateTime now = LocalDateTime.now();
        for (CoreSample s : samples) {
            s.setStatus(SampleDict.SAMPLE_SHIPPED);
            sampleRepository.save(s);
        }
        // 交接瞬间固化数量与封签清单快照，实验室据此核对
        batch.setStatus(SampleDict.BATCH_SHIPPED);
        batch.setSampleCount(samples.size());
        batch.setSealDigest(samples.stream().map(CoreSample::getSealNo).collect(Collectors.joining(",")));
        batch.setHandedBy(operator);
        batch.setHandedAt(now);
        String transportNote = SampleService.trim(action.getTransportNote());
        if (transportNote != null) {
            batch.setTransportNote(transportNote);
        }
        batch.setLockVersion(batch.getLockVersion() + 1);
        batchRepository.save(batch);

        saveEvent(batch, SampleDict.EV_HANDOVER, operator,
                transportNote == null ? "封样交接，共 " + samples.size() + " 件" : transportNote, eventDetail(samples));
        return detail(id);
    }

    // ---------------- 实验室：整批退回 ----------------

    @Transactional
    public Map<String, Object> returnBatch(Long id, SampleAction action) {
        SampleBatch batch = lockBatch(id, action.getExpectedVersion());
        requireStatus(batch, SampleDict.BATCH_SHIPPED, "整批退回");
        String reason = SampleService.trim(action.getReason());
        if (reason == null) {
            throw new BizException("整批退回必须填写退回原因");
        }
        String operator = SampleService.trim(action.getOperator());
        List<CoreSample> samples = sampleRepository.findByBatchIdOrderByIdAsc(id);
        for (CoreSample s : samples) {
            s.setStatus(SampleDict.SAMPLE_RETURNED);
            sampleRepository.save(s);
        }
        batch.setStatus(SampleDict.BATCH_RETURNED);
        batch.setReturnReason(reason);
        batch.setLockVersion(batch.getLockVersion() + 1);
        batchRepository.save(batch);

        saveEvent(batch, SampleDict.EV_RETURN, operator, reason, eventDetail(samples));
        return detail(id);
    }

    // ---------------- 实验室：接收（含部分异常与补接收） ----------------

    @Transactional
    public Map<String, Object> receive(Long id, SampleAction action) {
        SampleBatch batch = lockBatch(id, action.getExpectedVersion());
        boolean supplement = SampleDict.BATCH_PARTIAL.equals(batch.getStatus());
        if (!SampleDict.BATCH_SHIPPED.equals(batch.getStatus()) && !supplement) {
            throw new BizException("只有「在途」或「部分接收」的批次才能实验室接收，当前为「"
                    + SampleDict.batchStatusName(batch.getStatus()) + "」");
        }
        String operator = SampleService.trim(action.getOperator());
        if (operator == null) {
            throw new BizException("实验室接收必须填写接收人");
        }

        // 本次待核对清单：在途批次的全部在途样品；部分接收批次的待补样品
        String checkStatus = supplement ? SampleDict.SAMPLE_PENDING : SampleDict.SAMPLE_SHIPPED;
        List<CoreSample> checkSet = sampleRepository.findByBatchIdAndStatusOrderByIdAsc(id, checkStatus);
        if (checkSet.isEmpty()) {
            throw new BizException("本批次没有待接收的样品");
        }

        List<ReceiveItem> items = action.getItems() == null ? List.of() : action.getItems();
        if (items.isEmpty()) {
            throw new BizException("实收样品清单不能为空；若整批无法接收请使用「整批退回」");
        }
        Map<Long, ReceiveItem> itemBySample = new LinkedHashMap<>();
        for (ReceiveItem item : items) {
            if (item.getSampleId() == null) {
                throw new BizException("实收清单中存在缺少样品的行");
            }
            if (itemBySample.put(item.getSampleId(), item) != null) {
                throw new BizException("实收清单中样品 #" + item.getSampleId() + " 重复，数量核对不一致");
            }
        }
        Set<Long> checkIds = checkSet.stream().map(CoreSample::getId).collect(Collectors.toSet());
        for (Long sampleId : itemBySample.keySet()) {
            if (!checkIds.contains(sampleId)) {
                throw new BizException("样品 #" + sampleId + " 不属于本批次待接收清单，数量核对不一致");
            }
        }

        LocalDateTime now = LocalDateTime.now();
        for (CoreSample sample : checkSet) {
            ReceiveItem item = itemBySample.get(sample.getId());
            if (item == null) {
                markPending(sample, "样品缺失，未随批到达");
                continue;
            }
            String actualSeal = SampleService.trim(item.getActualSealNo());
            String note = SampleService.trim(item.getNote());
            if (actualSeal == null || !actualSeal.equals(sample.getSealNo())) {
                markPending(sample, "封签不一致：登记 " + sample.getSealNo() + " / 实收 "
                        + (actualSeal == null ? "未填" : actualSeal));
            } else if (note != null) {
                markPending(sample, note);
            } else {
                sample.setStatus(SampleDict.SAMPLE_RECEIVED);
                sample.setReceivedAt(now);
                sample.setReceiveNote(null);
                sampleRepository.save(sample);
            }
        }
        // 待补项汇总（按样品编号排序，便于对照）：已接收样品保留，异常件标出待补
        List<String> pendingNotes = checkSet.stream()
                .filter(s -> SampleDict.SAMPLE_PENDING.equals(s.getStatus()))
                .map(s -> s.getSampleNo() + "（" + s.getReceiveNote() + "）")
                .collect(Collectors.toList());

        int totalReceived = sampleRepository.findByBatchIdAndStatusOrderByIdAsc(id, SampleDict.SAMPLE_RECEIVED).size();
        batch.setReceivedCount(totalReceived);
        batch.setReceivedBy(operator);
        batch.setReceivedAt(now);
        batch.setLockVersion(batch.getLockVersion() + 1);

        String eventType;
        if (pendingNotes.isEmpty()) {
            // 数量、封签一致：整批接收
            batch.setStatus(SampleDict.BATCH_RECEIVED);
            batch.setExceptionNote(SampleService.trim(action.getNote()));
            eventType = supplement ? SampleDict.EV_SUPPLEMENT : SampleDict.EV_RECEIVE;
        } else {
            // 部分异常：保留已接收样品，批次挂起待补项
            batch.setStatus(SampleDict.BATCH_PARTIAL);
            batch.setExceptionNote(limit("待补 " + pendingNotes.size() + " 件：" + String.join("；", pendingNotes)));
            eventType = supplement ? SampleDict.EV_SUPPLEMENT : SampleDict.EV_PARTIAL;
        }
        batchRepository.save(batch);

        saveEvent(batch, eventType, operator, batch.getExceptionNote(), eventDetail(checkSet));
        return detail(id);
    }

    // ---------------- 内部规则 ----------------

    private SampleBatch getBatch(Long id) {
        return batchRepository.findById(id).orElseThrow(() -> new BizException("送检批次不存在：id=" + id));
    }

    private CoreSample getSample(Long id) {
        return sampleRepository.findById(id).orElseThrow(() -> new BizException("样品不存在：id=" + id));
    }

    /** 变更操作取批次：行锁 + 乐观锁一并在此完成，重复点击 / 并发推进时后者收到 409 */
    private SampleBatch lockBatch(Long id, Integer expectedVersion) {
        SampleBatch batch = batchRepository.findForUpdate(id)
                .orElseThrow(() -> new BizException("送检批次不存在：id=" + id));
        if (expectedVersion != null && !expectedVersion.equals(batch.getLockVersion())) {
            throw new ConflictException("批次数据已被他人更新（当前版本号 " + batch.getLockVersion()
                    + "，你看到的是 " + expectedVersion + "），请刷新后重新核对");
        }
        return batch;
    }

    private void requireStatus(SampleBatch batch, String expected, String action) {
        if (!expected.equals(batch.getStatus())) {
            throw new ConflictException("批次当前为「" + SampleDict.batchStatusName(batch.getStatus())
                    + "」，不能" + action + "（可能已被他人处理），请刷新后重新核对");
        }
    }

    /** 建批 / 加批的样品：必须存在、关键信息齐全、且处于待送检或已退回（未在其他批次链路中） */
    private List<CoreSample> requirePackableSamples(List<Long> sampleIds) {
        if (sampleIds == null || sampleIds.isEmpty()) {
            throw new BizException("请至少选择 1 件待送检样品");
        }
        Set<Long> distinct = new LinkedHashSet<>(sampleIds);
        List<CoreSample> samples = new ArrayList<>();
        for (Long sampleId : distinct) {
            CoreSample sample = getSample(sampleId);
            String s = sample.getStatus();
            if (!SampleDict.SAMPLE_READY.equals(s) && !SampleDict.SAMPLE_RETURNED.equals(s)) {
                throw new BizException("样品【" + (sample.getSampleNo() == null ? "草稿#" + sample.getId()
                        : sample.getSampleNo()) + "】当前为「" + SampleDict.sampleStatusName(s)
                        + "」，只有待送检或已退回的样品才能装批");
            }
            if (!SampleService.isComplete(sample)) {
                throw new BizException("样品【" + sample.getSampleNo() + "】关键信息不全，不能装批");
            }
            samples.add(sample);
        }
        return samples;
    }

    private void markPending(CoreSample sample, String note) {
        sample.setStatus(SampleDict.SAMPLE_PENDING);
        sample.setReceiveNote(note);
        sampleRepository.save(sample);
    }

    private void refreshCount(SampleBatch batch) {
        batch.setSampleCount(sampleRepository.findByBatchIdOrderByIdAsc(batch.getId()).size());
    }

    /** 汇总说明写入 VARCHAR(255) 列前的长度保护 */
    private static String limit(String s) {
        return s != null && s.length() > 250 ? s.substring(0, 250) : s;
    }

    /** 批次号：可指定（查重）；缺省按 SJ-日期-序号 生成（先占位取主键再定号，唯一约束兜底） */
    private String nextBatchNo(String requested) {
        String no = SampleService.trim(requested);
        if (no != null) {
            if (batchRepository.existsByBatchNo(no)) {
                throw new BizException("批次号【" + no + "】已存在，请更换");
            }
            return no;
        }
        return "TMP-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private SampleBatch saveBatchGuarded(SampleBatch batch) {
        try {
            SampleBatch saved = batchRepository.saveAndFlush(batch);
            if (saved.getBatchNo().startsWith("TMP-")) {
                saved.setBatchNo("SJ-" + LocalDateTime.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                        + "-" + String.format("%03d", saved.getId()));
                saved = batchRepository.saveAndFlush(saved);
            }
            return saved;
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("批次号冲突，本次操作未生效，请刷新后重试");
        }
    }

    private void saveEvent(SampleBatch batch, String type, String operator, String note, List<SampleEventItem> detail) {
        eventRepository.save(SampleBatchEvent.builder()
                .batchId(batch.getId())
                .eventType(type)
                .operator(operator)
                .eventAt(LocalDateTime.now())
                .note(note)
                .detail(detail)
                .createdAt(LocalDateTime.now())
                .build());
    }

    /** 当时样品明细快照：编号 / 封签 / 孔段 / 接收结论，随流转记录留痕 */
    private List<SampleEventItem> eventDetail(List<CoreSample> samples) {
        return samples.stream().map(s -> SampleEventItem.builder()
                .sampleId(s.getId())
                .sampleNo(s.getSampleNo())
                .sealNo(s.getSealNo())
                .fromDepth(s.getFromDepth())
                .toDepth(s.getToDepth())
                .result(SampleDict.SAMPLE_RECEIVED.equals(s.getStatus()) || SampleDict.SAMPLE_PENDING.equals(s.getStatus())
                        ? s.getStatus() : null)
                .note(s.getReceiveNote())
                .build())
                .collect(Collectors.toList());
    }
}
