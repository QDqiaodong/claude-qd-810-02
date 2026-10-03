package com.drill.crew.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 实验室接收清单中的一行：实收样品 + 实际看到的封签号 + 异常说明。
 * 未出现在清单里的批次样品按「缺失」处理（部分异常）。
 */
@Getter
@Setter
public class ReceiveItem {

    private Long sampleId;
    /** 实验室实际核对的封签号，与登记封签不一致则该件转待补 */
    private String actualSealNo;
    /** 异常说明（封签破损、样品玷污等），非空则该件转待补 */
    private String note;
}
