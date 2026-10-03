package com.drill.crew.dto;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * 送检批次各操作的通用请求体：
 * 建批 / 加样（sampleIds）、交接（operator + transportNote）、退回（reason）、
 * 接收（items 实收清单）、乐观锁（expectedVersion）。
 */
@Getter
@Setter
public class SampleAction {

    /** 操作人（封样人 / 交出人 / 实验室接收人） */
    private String operator;
    /** 备注说明 */
    private String note;
    /** 整批退回原因 */
    private String reason;
    /** 运输说明（车辆 / 押运） */
    private String transportNote;
    /** 批次号（建批时可指定，缺省自动生成） */
    private String batchNo;
    /** 乐观锁：页面上看到的 lockVersion */
    private Integer expectedVersion;
    /** 建批 / 加批的样品 id 列表 */
    private List<Long> sampleIds;
    /** 实验室接收的实收清单 */
    private List<ReceiveItem> items;
}
