package com.drill.crew.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.drill.crew.domain.SampleBatch;

import jakarta.persistence.LockModeType;

public interface SampleBatchRepository extends JpaRepository<SampleBatch, Long> {

    List<SampleBatch> findAllByOrderByIdDesc();

    List<SampleBatch> findByStatusOrderByIdDesc(String status);

    boolean existsByBatchNo(String batchNo);

    /**
     * 变更操作专用：SELECT ... FOR UPDATE 行锁。
     * 交接 / 接收 / 退回在此串行 —— 重复点击或两人同时操作时，后到的请求读到新状态，
     * 被状态机或乐观锁拦下，不会产生重复记录。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from SampleBatch b where b.id = :id")
    Optional<SampleBatch> findForUpdate(@Param("id") Long id);
}
