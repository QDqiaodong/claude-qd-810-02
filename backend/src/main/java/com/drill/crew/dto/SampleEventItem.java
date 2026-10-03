package com.drill.crew.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 批次流转记录里的单条样品快照：交接 / 接收瞬间定稿，历史可追溯。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SampleEventItem {

    private Long sampleId;
    private String sampleNo;
    private String sealNo;
    private BigDecimal fromDepth;
    private BigDecimal toDepth;
    /** 接收结论：RECEIVED 已接收 / PENDING_FIX 待补（封样、交接事件为空） */
    private String result;
    /** 异常说明（待补原因） */
    private String note;
}
