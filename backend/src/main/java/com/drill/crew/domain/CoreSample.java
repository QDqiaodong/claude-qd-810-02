package com.drill.crew.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 岩芯样品：现场在指定孔段登记样品编号、起止深度、取样人和封签号。
 * <p>
 * 样品编号与封签号全局唯一（uk_sample_no / uk_sample_seal，草稿允许暂缺 NULL）；
 * 同一钻孔内孔段 [fromDepth, toDepth) 不允许重叠（服务层校验）。
 * 关键信息（编号 / 起止深度 / 取样人 / 封签号）不全时只能保存为 DRAFT 草稿。
 */
@Entity
@Table(name = "core_sample")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CoreSample {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "borehole_id", nullable = false)
    private Long boreholeId;

    @Column(name = "sample_no", unique = true, length = 40)
    private String sampleNo;

    @Column(name = "seal_no", unique = true, length = 40)
    private String sealNo;

    @Column(name = "from_depth", precision = 8, scale = 2)
    private BigDecimal fromDepth;

    @Column(name = "to_depth", precision = 8, scale = 2)
    private BigDecimal toDepth;

    @Column(length = 32)
    private String sampler;

    @Column(name = "sampled_at")
    private LocalDate sampledAt;

    @Column(nullable = false, length = 12)
    private String status;

    @Column(name = "batch_id")
    private Long batchId;

    /** 实验室验收说明（异常原因 / 待补原因） */
    @Column(name = "receive_note", length = 255)
    private String receiveNote;

    /** 实验室接收时间（回写） */
    @Column(name = "received_at")
    private LocalDateTime receivedAt;

    @Column(length = 255)
    private String remark;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
