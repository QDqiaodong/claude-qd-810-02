package com.drill.crew.common;

/**
 * 岩芯样品取样送检链路的状态与事件词典。
 * <ul>
 *   <li>样品：DRAFT 草稿（关键信息不全只能暂存）→ READY 待送检 → PACKED 已装批 →
 *       SHIPPED 在途 → RECEIVED 已接收；实验室判定异常为 PENDING_FIX 待补；整批退回为 RETURNED；</li>
 *   <li>批次：PACKING 封样中 → SHIPPED 在途 → RECEIVED 已接收 / PARTIAL 部分接收 / RETURNED 已退回；
 *       PARTIAL 经补接收、待补项清零后转 RECEIVED；</li>
 *   <li>每次封样 / 交接 / 接收 / 部分接收 / 补接收 / 整批退回都落流转记录，历史交接结果可追溯。</li>
 * </ul>
 */
public final class SampleDict {

    private SampleDict() {
    }

    // ---------------- 样品状态 ----------------
    /** 草稿：样品编号 / 起止深度 / 取样人 / 封签号等关键信息不全，只能暂存 */
    public static final String SAMPLE_DRAFT = "DRAFT";
    /** 待送检：登记完成，等待装批 */
    public static final String SAMPLE_READY = "READY";
    /** 已装批：进入封样中的批次 */
    public static final String SAMPLE_PACKED = "PACKED";
    /** 在途：批次已封样交接、运输途中 */
    public static final String SAMPLE_SHIPPED = "SHIPPED";
    /** 实验室已接收 */
    public static final String SAMPLE_RECEIVED = "RECEIVED";
    /** 待补：实验室判定异常（缺失 / 封签不一致 / 破损），已接收样品保留、该件标出待补 */
    public static final String SAMPLE_PENDING = "PENDING_FIX";
    /** 已退回：整批退回现场，可重新装批 */
    public static final String SAMPLE_RETURNED = "RETURNED";

    // ---------------- 批次状态 ----------------
    /** 封样中：可增删样品、可取消批次 */
    public static final String BATCH_PACKING = "PACKING";
    /** 在途：已封样交接，等待实验室接收 */
    public static final String BATCH_SHIPPED = "SHIPPED";
    /** 已接收：数量、封签一致，全部接收 */
    public static final String BATCH_RECEIVED = "RECEIVED";
    /** 部分接收：存在异常件，已接收样品保留、待补项挂起 */
    public static final String BATCH_PARTIAL = "PARTIAL";
    /** 已退回：整批退回现场 */
    public static final String BATCH_RETURNED = "RETURNED";

    // ---------------- 流转事件 ----------------
    public static final String EV_PACK = "PACK";
    public static final String EV_HANDOVER = "HANDOVER";
    public static final String EV_RECEIVE = "RECEIVE";
    public static final String EV_PARTIAL = "PARTIAL";
    public static final String EV_SUPPLEMENT = "SUPPLEMENT";
    public static final String EV_RETURN = "RETURN";

    public static String sampleStatusName(String code) {
        return switch (code) {
            case SAMPLE_DRAFT -> "草稿";
            case SAMPLE_READY -> "待送检";
            case SAMPLE_PACKED -> "已装批";
            case SAMPLE_SHIPPED -> "在途";
            case SAMPLE_RECEIVED -> "已接收";
            case SAMPLE_PENDING -> "待补";
            case SAMPLE_RETURNED -> "已退回";
            default -> code;
        };
    }

    public static String batchStatusName(String code) {
        return switch (code) {
            case BATCH_PACKING -> "封样中";
            case BATCH_SHIPPED -> "在途";
            case BATCH_RECEIVED -> "已接收";
            case BATCH_PARTIAL -> "部分接收";
            case BATCH_RETURNED -> "已退回";
            default -> code;
        };
    }

    public static String eventName(String code) {
        return switch (code) {
            case EV_PACK -> "封样成批";
            case EV_HANDOVER -> "封样交接";
            case EV_RECEIVE -> "实验室接收";
            case EV_PARTIAL -> "部分接收";
            case EV_SUPPLEMENT -> "补接收";
            case EV_RETURN -> "整批退回";
            default -> code;
        };
    }
}
