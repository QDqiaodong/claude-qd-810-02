package com.drill.crew.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.drill.crew.domain.CoreSample;

public interface CoreSampleRepository extends JpaRepository<CoreSample, Long> {

    List<CoreSample> findByBoreholeIdOrderByFromDepthAscIdAsc(Long boreholeId);

    List<CoreSample> findByBoreholeIdAndStatusOrderByFromDepthAscIdAsc(Long boreholeId, String status);

    List<CoreSample> findByStatusOrderByIdAsc(String status);

    List<CoreSample> findByBatchIdOrderByIdAsc(Long batchId);

    List<CoreSample> findByBatchIdAndStatusOrderByIdAsc(Long batchId, String status);

    Optional<CoreSample> findBySampleNo(String sampleNo);

    Optional<CoreSample> findBySealNo(String sealNo);

    /**
     * 同一钻孔内与 [fromDepth, toDepth) 重叠的样品（区间相交：from &lt; 他止 且 他起 &lt; to）。
     * 草稿缺深度的行不参与比较（NULL 不匹配）。
     */
    @Query("select s from CoreSample s where s.boreholeId = :boreholeId"
            + " and s.fromDepth < :toDepth and s.toDepth > :fromDepth")
    List<CoreSample> findOverlapping(@Param("boreholeId") Long boreholeId,
                                     @Param("fromDepth") BigDecimal fromDepth,
                                     @Param("toDepth") BigDecimal toDepth);
}
