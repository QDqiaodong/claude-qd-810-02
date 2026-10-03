package com.drill.crew.domain;

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
 * 送检批次：现场按批次封样运输，实验室接收后回写接收时间、状态及异常说明。
 * <p>
 * 状态机：PACKING 封样中 → SHIPPED 在途 → RECEIVED 已接收 / PARTIAL 部分接收 / RETURNED 已退回；
 * PARTIAL 经补接收、待补项清零后转 RECEIVED。
 * 交接瞬间把样品数量（sampleCount）与封签清单（sealDigest）固化为快照，
 * 实验室只接收状态正确且数量、封签一致的批次。
 * {@code lockVersion} 为手工乐观锁：交接、接收、退回都必须携带页面上看到的版本，
 * 配合状态机约束保证重复点击不会产生重复记录。
 */
@Entity
@Table(name = "sample_batch")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SampleBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_no", nullable = false, unique = true, length = 40)
    private String batchNo;

    @Column(nullable = false, length = 12)
    private String status;

    /** 交接时样品数量快照（封样中随增删实时更新） */
    @Column(name = "sample_count", nullable = false)
    private Integer sampleCount;

    /** 交接时封签号清单快照（逗号分隔），实验室据此核对封签一致 */
    @Column(name = "seal_digest", length = 255)
    private String sealDigest;

    @Column(name = "packed_by", length = 32)
    private String packedBy;

    @Column(name = "handed_by", length = 32)
    private String handedBy;

    @Column(name = "handed_at")
    private LocalDateTime handedAt;

    @Column(name = "transport_note", length = 255)
    private String transportNote;

    @Column(name = "received_by", length = 32)
    private String receivedBy;

    @Column(name = "received_at")
    private LocalDateTime receivedAt;

    /** 累计实收样品数 */
    @Column(name = "received_count", nullable = false)
    private Integer receivedCount;

    /** 异常说明 / 待补项（部分接收时由系统汇总） */
    @Column(name = "exception_note", length = 255)
    private String exceptionNote;

    /** 整批退回原因 */
    @Column(name = "return_reason", length = 255)
    private String returnReason;

    @Column(name = "lock_version", nullable = false)
    private Integer lockVersion;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
