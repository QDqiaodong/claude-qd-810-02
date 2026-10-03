package com.drill.crew.domain;

import java.time.LocalDateTime;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.drill.crew.dto.SampleEventItem;

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
 * 送检批次流转记录：封样 / 交接 / 接收 / 部分接收 / 补接收 / 整批退回各落一条。
 * detail 为当时样品明细快照（JSON），历史交接结果据此追溯，不被后续改动覆盖。
 */
@Entity
@Table(name = "sample_batch_event")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SampleBatchEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_id", nullable = false)
    private Long batchId;

    @Column(name = "event_type", nullable = false, length = 16)
    private String eventType;

    @Column(length = 32)
    private String operator;

    @Column(name = "event_at", nullable = false)
    private LocalDateTime eventAt;

    @Column(length = 255)
    private String note;

    /** JSON 列：当时样品明细快照（编号 / 封签 / 孔段 / 接收结论） */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "detail", columnDefinition = "json")
    private List<SampleEventItem> detail;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
