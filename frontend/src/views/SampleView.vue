<template>
  <div class="dc-page">
    <div class="dc-toolbar">
      <el-radio-group v-model="role" size="small">
        <el-radio-button label="field">现场班组</el-radio-button>
        <el-radio-button label="lab">实验室</el-radio-button>
      </el-radio-group>

      <span class="dc-label">钻孔</span>
      <el-select v-model="holeId" size="small" style="width: 130px" @change="loadSamples">
        <el-option v-for="h in holes" :key="h.id" :label="h.holeNo" :value="h.id" />
      </el-select>

      <span class="dc-label">样品状态</span>
      <el-select v-model="sampleStatus" size="small" style="width: 108px" clearable placeholder="全部" @change="loadSamples">
        <el-option v-for="s in SAMPLE_STATUS" :key="s.value" :label="s.label" :value="s.value" />
      </el-select>

      <span class="dc-label">批次状态</span>
      <el-select v-model="batchStatus" size="small" style="width: 108px" clearable placeholder="全部" @change="loadBatches">
        <el-option v-for="s in BATCH_STATUS" :key="s.value" :label="s.label" :value="s.value" />
      </el-select>

      <el-input
        v-model="operator"
        size="small"
        style="width: 130px"
        :placeholder="role === 'field' ? '班组操作人' : '实验室接收人'"
      />
      <el-button size="small" @click="reloadAll">刷新</el-button>
      <el-button size="small" text type="primary" @click="gotoHole">孔资料 →</el-button>
      <span style="color: #8a7761; font-size: 11px">
        {{ role === 'field' ? '取样登记 → 装批 → 封样交接运输' : '核对数量与封签：接收 / 部分接收标待补 / 整批退回' }}
      </span>
    </div>

    <div class="dc-split" style="align-items: flex-start">
      <!-- 左：样品登记 -->
      <div class="dc-panel" style="width: 400px; flex: none">
        <div class="dc-panel-title">
          样品登记（{{ samples.length }}）
          <el-button v-if="role === 'field'" size="small" type="primary" style="float: right" @click="openSampleCreate">
            取样登记
          </el-button>
        </div>

        <div
          v-for="s in samples"
          :key="s.id"
          class="dc-round-row"
          :class="{ on: s.id === selectedSampleId }"
          @click="selectSample(s)"
        >
          <span style="width: 96px; font-weight: 700">{{ s.sampleNo || '（草稿）' }}</span>
          <span style="width: 108px">{{ depthText(s) }}</span>
          <el-tag size="small" :type="sampleTag(s.status)">{{ sampleStatusName(s.status) }}</el-tag>
          <span style="color: #8a7761; font-size: 11px">{{ s.sealNo || '无封签' }}</span>
          <span style="margin-left: auto; display: flex; gap: 2px">
            <template v-if="role === 'field'">
              <el-button v-if="canPack(s) && packingActive" size="small" text type="success" @click.stop="addToBatch(s)">
                入批
              </el-button>
              <el-button v-if="canEdit(s)" size="small" text type="primary" @click.stop="openSampleEdit(s)">编辑</el-button>
              <el-button v-if="canEdit(s)" size="small" text type="danger" @click.stop="removeSample(s)">删除</el-button>
            </template>
          </span>
        </div>
        <div v-if="!samples.length" style="font-size: 12px; color: #8a7761">该孔暂无样品记录</div>
      </div>

      <!-- 中：送检批次 -->
      <div class="dc-panel" style="width: 310px; flex: none">
        <div class="dc-panel-title">
          送检批次（{{ batches.length }}）
          <el-button v-if="role === 'field'" size="small" type="primary" plain style="float: right" @click="openBatchCreate">
            新建批次
          </el-button>
        </div>

        <div
          v-for="b in batches"
          :key="b.id"
          class="dc-round-row"
          :class="{ on: b.id === selectedBatchId }"
          @click="openBatch(b.id)"
        >
          <span style="width: 128px; font-weight: 700">{{ b.batchNo }}</span>
          <el-tag size="small" :type="batchTag(b.status)">{{ batchStatusName(b.status) }}</el-tag>
          <span style="color: #8a7761; font-size: 11px">
            {{ b.sampleCount }} 件<template v-if="b.receivedCount"> · 实收 {{ b.receivedCount }}</template>
          </span>
        </div>
        <div v-if="!batches.length" style="font-size: 12px; color: #8a7761">暂无送检批次</div>
      </div>

      <!-- 右：批次详情 -->
      <div style="flex: 1; min-width: 430px; display: flex; flex-direction: column; gap: 12px">
        <template v-if="batchDetail">
          <div class="dc-panel">
            <div class="dc-panel-title">
              批次 {{ batch.batchNo }}
              <el-tag size="small" style="margin-left: 8px" :type="batchTag(batch.status)">
                {{ batchStatusName(batch.status) }}
              </el-tag>
              <span style="float: right; font-size: 11px; color: #8a7761; font-weight: 400">
                锁版本 #{{ batch.lockVersion }}
              </span>
            </div>

            <div class="dc-round-row">
              <span style="width: 150px">样品数量（交接快照）</span>
              <span style="font-weight: 700">{{ batch.sampleCount }} 件</span>
              <span style="color: #8a7761">累计实收 {{ batch.receivedCount }} 件</span>
            </div>
            <div class="dc-round-row">
              <span style="width: 150px">封签清单（交接快照）</span>
              <span>{{ batch.sealDigest || '—（封样交接时生成）' }}</span>
            </div>
            <div class="dc-round-row">
              <span style="width: 150px">封样 / 交接</span>
              <span>{{ batch.packedBy || '—' }} → {{ batch.handedBy || '—' }} · {{ fmt(batch.handedAt) }}</span>
            </div>
            <div class="dc-round-row">
              <span style="width: 150px">运输说明</span><span>{{ batch.transportNote || '—' }}</span>
            </div>
            <div class="dc-round-row">
              <span style="width: 150px">实验室接收</span>
              <span>{{ batch.receivedBy || '—' }} · {{ fmt(batch.receivedAt) }}</span>
            </div>
            <div v-if="batch.exceptionNote" class="dc-reject-box">
              <b>异常 / 待补项：</b>{{ batch.exceptionNote }}
            </div>
            <div v-if="batch.returnReason" class="dc-reject-box">
              <b>整批退回原因：</b>{{ batch.returnReason }}
            </div>

            <!-- 样品明细 -->
            <div class="dc-panel-title" style="margin-top: 10px">样品明细（{{ detailSamples.length }}）</div>
            <div v-for="s in detailSamples" :key="s.id" class="dc-round-row">
              <span style="width: 92px; font-weight: 700">{{ s.sampleNo }}</span>
              <span style="width: 64px">{{ holeNo(s.boreholeId) }}</span>
              <span style="width: 116px">{{ num(s.fromDepth) }} ~ {{ num(s.toDepth) }} m</span>
              <span style="width: 96px">封签 {{ s.sealNo }}</span>
              <el-tag size="small" :type="sampleTag(s.status)">{{ sampleStatusName(s.status) }}</el-tag>
              <el-button
                v-if="batch.status === 'PACKING' && role === 'field'"
                size="small" text type="danger"
                @click="removeFromBatch(s)"
              >移出</el-button>
              <span v-if="s.receiveNote" style="color: #c05621; font-size: 11px">{{ s.receiveNote }}</span>
            </div>

            <!-- 操作按钮 -->
            <div style="margin-top: 10px; display: flex; gap: 8px; flex-wrap: wrap; align-items: center">
              <template v-if="role === 'field' && batch.status === 'PACKING'">
                <el-button size="small" type="primary" :loading="submitting" @click="openHandover">封样交接</el-button>
                <el-button size="small" type="danger" plain :disabled="submitting" @click="disband">取消批次</el-button>
                <span style="font-size: 11px; color: #8a7761">在左侧样品列表点「入批」可继续添加样品</span>
              </template>
              <template v-if="role === 'lab' && batch.status === 'SHIPPED'">
                <el-button size="small" type="success" :loading="submitting" @click="openReceive">实验室接收</el-button>
                <el-button size="small" type="danger" :disabled="submitting" @click="openReturn">整批退回</el-button>
              </template>
              <template v-if="role === 'lab' && batch.status === 'PARTIAL'">
                <el-button size="small" type="warning" :loading="submitting" @click="openReceive">
                  补接收（待补 {{ pendingCount }} 件）
                </el-button>
                <span style="font-size: 11px; color: #c05621">已接收样品保留，待补件到齐后再次接收</span>
              </template>
              <el-button size="small" @click="refreshBatchDetail">刷新明细</el-button>
            </div>
          </div>

          <!-- 流转记录 -->
          <div class="dc-panel">
            <div class="dc-panel-title">
              流转记录（{{ events.length }}）
              <span style="float: right; font-size: 11px; color: #8a7761; font-weight: 400">
                历史交接结果随样品明细快照留痕，可追溯
              </span>
            </div>
            <div v-for="e in events" :key="e.id" class="dc-version-card">
              <div>
                <b>{{ eventName(e.eventType) }}</b>
                <span style="font-size: 12px; color: #8a7761; margin-left: 8px">
                  {{ e.operator || '—' }} · {{ fmt(e.eventAt) }}
                </span>
              </div>
              <div v-if="e.note" style="font-size: 12px; color: #5d4037; margin: 4px 0">{{ e.note }}</div>
              <div v-for="d in e.detail || []" :key="d.sampleId" style="font-size: 11px; color: #8a7761">
                {{ d.sampleNo }} · {{ num(d.fromDepth) }}~{{ num(d.toDepth) }} m · 封签 {{ d.sealNo }}
                <span v-if="d.result" :style="{ color: d.result === 'RECEIVED' ? '#5b8c5a' : '#c05621' }">
                  → {{ d.result === 'RECEIVED' ? '已接收' : '待补' }}{{ d.note ? '：' + d.note : '' }}
                </span>
              </div>
            </div>
            <div v-if="!events.length" style="font-size: 12px; color: #8a7761">暂无流转记录</div>
          </div>
        </template>

        <div v-else class="dc-panel" style="color: #8a7761; font-size: 12px">
          请在中间列表选择送检批次，查看样品明细与交接记录；现场班组可新建批次装样。
        </div>
      </div>
    </div>

    <!-- 取样登记 / 修改 -->
    <el-dialog v-model="sampleDialog" :title="sampleForm.id ? '修改样品登记' : '取样登记'" width="480px">
      <el-alert
        type="info" :closable="false" style="margin-bottom: 12px"
        title="样品编号、起止深度、取样人、封签号为关键信息；不全时只能保存为草稿，补齐后自动转为待送检。同一孔段不能重叠，编号与封签号全局唯一。"
      />
      <el-form :model="sampleForm" label-width="96px" size="small">
        <el-form-item label="样品编号">
          <el-input v-model="sampleForm.sampleNo" placeholder="如 ZK2401-H03（全局唯一）" />
        </el-form-item>
        <el-form-item label="封签号">
          <el-input v-model="sampleForm.sealNo" placeholder="如 FQ-2024-010（全局唯一）" />
        </el-form-item>
        <el-form-item label="起始深度 m"><el-input-number v-model="sampleForm.fromDepth" :precision="2" :min="0" /></el-form-item>
        <el-form-item label="终止深度 m"><el-input-number v-model="sampleForm.toDepth" :precision="2" :min="0" /></el-form-item>
        <el-form-item label="取样人"><el-input v-model="sampleForm.sampler" /></el-form-item>
        <el-form-item label="取样日期">
          <el-date-picker v-model="sampleForm.sampledAt" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="sampleForm.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="sampleDialog = false">取消</el-button>
        <el-button size="small" type="primary" :loading="submitting" @click="submitSample">保存</el-button>
      </template>
    </el-dialog>

    <!-- 新建批次 -->
    <el-dialog v-model="batchDialog" title="新建送检批次" width="560px">
      <el-alert
        type="info" :closable="false" style="margin-bottom: 12px"
        title="从「待送检 / 已退回」样品中选择装批；批次号留空则自动生成（SJ-日期-序号）。"
      />
      <el-form label-width="96px" size="small">
        <el-form-item label="批次号">
          <el-input v-model="batchForm.batchNo" placeholder="留空自动生成" />
        </el-form-item>
        <el-form-item label="封样人"><el-input v-model="operator" placeholder="班组操作人" /></el-form-item>
        <el-form-item label="运输说明">
          <el-input v-model="batchForm.transportNote" placeholder="车辆 / 押运，如 专车押运 甘F-62183" />
        </el-form-item>
        <el-form-item label="装批样品">
          <el-select v-model="batchForm.sampleIds" multiple style="width: 100%" placeholder="选择待送检 / 已退同样品">
            <el-option
              v-for="s in packableSamples" :key="s.id" :value="s.id"
              :label="`${s.sampleNo} · ${holeNo(s.boreholeId)} · ${num(s.fromDepth)}~${num(s.toDepth)} m · 封签 ${s.sealNo}`"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="batchDialog = false">取消</el-button>
        <el-button size="small" type="primary" :loading="submitting" @click="submitBatchCreate">封样成批</el-button>
      </template>
    </el-dialog>

    <!-- 封样交接 -->
    <el-dialog v-model="handoverDialog" title="封样交接" width="560px">
      <el-alert
        type="warning" :closable="false" style="margin-bottom: 12px"
        :title="`交接后批次转为「在途」，样品数量（${detailSamples.length} 件）与封签清单将固化为快照，实验室据此核对。`"
      />
      <div class="dc-panel" style="margin-bottom: 12px">
        <div class="dc-panel-title">交接样品明细（{{ detailSamples.length }} 件）</div>
        <div v-for="s in detailSamples" :key="s.id" class="dc-round-row">
          <span style="width: 100px; font-weight: 700">{{ s.sampleNo }}</span>
          <span style="width: 130px">{{ num(s.fromDepth) }} ~ {{ num(s.toDepth) }} m</span>
          <span>封签 {{ s.sealNo }}</span>
        </div>
      </div>
      <el-form label-width="96px" size="small">
        <el-form-item label="交出人"><el-input v-model="operator" placeholder="班组交出人" /></el-form-item>
        <el-form-item label="运输说明">
          <el-input v-model="handoverNote" placeholder="车辆 / 押运，如 专车押运 甘F-62183" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="handoverDialog = false">取消</el-button>
        <el-button size="small" type="primary" :loading="submitting" @click="doHandover">确认交接</el-button>
      </template>
    </el-dialog>

    <!-- 整批退回 -->
    <el-dialog v-model="returnDialog" title="整批退回" width="460px">
      <el-alert
        type="error" :closable="false" style="margin-bottom: 12px"
        title="整批退回后，批次内全部样品退回现场（状态「已退回」），可修正后重新装批送检。"
      />
      <el-form label-width="96px" size="small">
        <el-form-item label="接收人"><el-input v-model="operator" placeholder="实验室接收人" /></el-form-item>
        <el-form-item label="退回原因">
          <el-input v-model="returnReason" type="textarea" :rows="3" placeholder="必须填写，如 运输途中样品箱破损" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="returnDialog = false">取消</el-button>
        <el-button size="small" type="danger" :loading="submitting" @click="doReturn">确认退回</el-button>
      </template>
    </el-dialog>

    <!-- 实验室接收 / 补接收 -->
    <el-dialog v-model="receiveDialog" :title="batch && batch.status === 'PARTIAL' ? '补接收（待补样品）' : '实验室接收'" width="760px">
      <el-alert
        type="info" :closable="false" style="margin-bottom: 12px"
        title="逐件核对：实收打勾并确认封签号；数量、封签一致才整批接收。未打勾的样品按「缺失」记为待补；封签不一致或填写异常说明的样品标为待补，已接收样品保留。"
      />
      <div class="dc-panel" style="margin-bottom: 12px">
        <div class="dc-panel-title">应收 {{ receiveRows.length }} 件 / 实收 {{ receivedCount }} 件</div>
        <div v-for="r in receiveRows" :key="r.sampleId" class="dc-round-row" style="gap: 8px">
          <el-checkbox v-model="r.present" style="width: 62px">实收</el-checkbox>
          <span style="width: 96px; font-weight: 700">{{ r.sampleNo }}</span>
          <span style="width: 108px">{{ num(r.fromDepth) }} ~ {{ num(r.toDepth) }} m</span>
          <span style="width: 104px; color: #8a7761">登记 {{ r.sealNo }}</span>
          <el-input v-model="r.actualSealNo" size="small" style="width: 118px" :disabled="!r.present" placeholder="实收封签" />
          <el-input
            v-model="r.note" size="small" style="flex: 1" :disabled="!r.present"
            placeholder="异常说明（封签破损 / 玷污等，留空为正常）"
          />
        </div>
      </div>
      <el-form label-width="96px" size="small">
        <el-form-item label="接收人"><el-input v-model="operator" placeholder="实验室接收人" /></el-form-item>
        <el-form-item label="接收说明">
          <el-input v-model="receiveNote" placeholder="整批接收时的说明（可选）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="receiveDialog = false">取消</el-button>
        <el-button size="small" type="success" :loading="submitting" @click="doReceive">确认接收</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onActivated, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../api'
import { setDetail } from '../detail'
import { gotoHoleFromSamples } from '../sample-nav'

const SAMPLE_STATUS = [
  { value: 'DRAFT', label: '草稿' },
  { value: 'READY', label: '待送检' },
  { value: 'PACKED', label: '已装批' },
  { value: 'SHIPPED', label: '在途' },
  { value: 'RECEIVED', label: '已接收' },
  { value: 'PENDING_FIX', label: '待补' },
  { value: 'RETURNED', label: '已退回' }
]
const BATCH_STATUS = [
  { value: 'PACKING', label: '封样中' },
  { value: 'SHIPPED', label: '在途' },
  { value: 'RECEIVED', label: '已接收' },
  { value: 'PARTIAL', label: '部分接收' },
  { value: 'RETURNED', label: '已退回' }
]
const SAMPLE_NAMES = Object.fromEntries(SAMPLE_STATUS.map((s) => [s.value, s.label]))
const BATCH_NAMES = Object.fromEntries(BATCH_STATUS.map((s) => [s.value, s.label]))
const EVENT_NAMES = {
  PACK: '封样成批', HANDOVER: '封样交接', RECEIVE: '实验室接收',
  PARTIAL: '部分接收', SUPPLEMENT: '补接收', RETURN: '整批退回'
}

/** 页面状态持久化：刷新浏览器 / 从孔资料返回后保留当前批次与筛选条件 */
const UI_KEY = 'dc_sample_ui'

const role = ref('field')
const operator = ref(localStorage.getItem('dc_operator') || '')
const holes = ref([])
const holeId = ref(null)
const sampleStatus = ref(null)
const batchStatus = ref(null)
const samples = ref([])
const batches = ref([])
const selectedSampleId = ref(null)
const selectedBatchId = ref(null)
const batchDetail = ref(null)
const submitting = ref(false)

const sampleDialog = ref(false)
const sampleForm = ref({})
const batchDialog = ref(false)
const batchForm = ref({})
const packableSamples = ref([])
const handoverDialog = ref(false)
const handoverNote = ref('')
const returnDialog = ref(false)
const returnReason = ref('')
const receiveDialog = ref(false)
const receiveRows = ref([])
const receiveNote = ref('')

const batch = computed(() => batchDetail.value?.batch || {})
const detailSamples = computed(() => batchDetail.value?.samples || [])
const events = computed(() => batchDetail.value?.events || [])
const packingActive = computed(() => batch.value?.status === 'PACKING')
const pendingCount = computed(() => detailSamples.value.filter((s) => s.status === 'PENDING_FIX').length)
const receivedCount = computed(() => receiveRows.value.filter((r) => r.present).length)

function num(v) {
  return v == null ? '—' : Number(v)
}

function fmt(t) {
  return t ? String(t).replace('T', ' ').slice(0, 16) : '—'
}

function depthText(s) {
  return s.fromDepth == null || s.toDepth == null ? '孔段未定' : `${num(s.fromDepth)} ~ ${num(s.toDepth)} m`
}

function holeNo(id) {
  const h = holes.value.find((x) => x.id === id)
  return h ? h.holeNo : '#' + id
}

function sampleStatusName(code) {
  return SAMPLE_NAMES[code] || code
}

function batchStatusName(code) {
  return BATCH_NAMES[code] || code
}

function eventName(code) {
  return EVENT_NAMES[code] || code
}

function sampleTag(code) {
  return {
    DRAFT: 'info', READY: 'warning', PACKED: 'primary', SHIPPED: 'primary',
    RECEIVED: 'success', PENDING_FIX: 'danger', RETURNED: 'info'
  }[code] || ''
}

function batchTag(code) {
  return { PACKING: 'warning', SHIPPED: 'primary', RECEIVED: 'success', PARTIAL: 'danger', RETURNED: 'info' }[code] || ''
}

function canEdit(s) {
  return ['DRAFT', 'READY', 'RETURNED'].includes(s.status)
}

function canPack(s) {
  return ['READY', 'RETURNED'].includes(s.status)
}

// ---------------- 数据加载 ----------------

async function loadSamples() {
  if (!holeId.value) { samples.value = []; return }
  const params = { boreholeId: holeId.value }
  if (sampleStatus.value) params.status = sampleStatus.value
  samples.value = await api.samples(params)
}

async function loadBatches() {
  batches.value = await api.sampleBatches(batchStatus.value || undefined)
}

async function openBatch(id) {
  selectedBatchId.value = id
  batchDetail.value = await api.sampleBatchDetail(id)
}

async function refreshBatchDetail() {
  if (!selectedBatchId.value) { batchDetail.value = null; return }
  try {
    batchDetail.value = await api.sampleBatchDetail(selectedBatchId.value)
  } catch (e) {
    selectedBatchId.value = null
    batchDetail.value = null
  }
}

async function reloadAll() {
  await Promise.all([loadSamples(), loadBatches()])
  await refreshBatchDetail()
}

// ---------------- 样品登记 ----------------

function selectSample(s) {
  selectedSampleId.value = s.id
  setDetail({
    title: s.sampleNo || '（未编号草稿）',
    subtitle: holeNo(s.boreholeId) + ' · ' + depthText(s),
    tag: '样品 · ' + sampleStatusName(s.status),
    fields: [
      { label: '样品编号', value: s.sampleNo || '—（草稿暂缺）' },
      { label: '封签号', value: s.sealNo || '—（草稿暂缺）' },
      { label: '取样孔段 (m)', value: depthText(s) },
      { label: '取样人', value: s.sampler || '—' },
      { label: '取样日期', value: s.sampledAt || '—' },
      { label: '状态', value: sampleStatusName(s.status) },
      { label: '所属批次', value: s.batchId ? '#' + s.batchId : '—' },
      { label: '实验室接收时间', value: fmt(s.receivedAt) },
      { label: '验收说明', value: s.receiveNote || '—' },
      { label: '备注', value: s.remark || '—' }
    ]
  })
}

function openSampleCreate() {
  sampleForm.value = {
    id: null, boreholeId: holeId.value, sampleNo: '', sealNo: '',
    fromDepth: null, toDepth: null, sampler: operator.value, sampledAt: null, remark: ''
  }
  sampleDialog.value = true
}

function openSampleEdit(s) {
  sampleForm.value = {
    id: s.id, boreholeId: s.boreholeId, sampleNo: s.sampleNo || '', sealNo: s.sealNo || '',
    fromDepth: s.fromDepth == null ? null : Number(s.fromDepth),
    toDepth: s.toDepth == null ? null : Number(s.toDepth),
    sampler: s.sampler || '', sampledAt: s.sampledAt || null, remark: s.remark || ''
  }
  sampleDialog.value = true
}

async function submitSample() {
  saveOperator()
  submitting.value = true
  try {
    const body = { ...sampleForm.value }
    const saved = body.id
      ? await api.updateSample(body.id, body)
      : await api.createSample(body)
    ElMessage.success(saved.status === 'DRAFT' ? '关键信息不全，已保存为草稿' : '样品已登记，待送检')
    sampleDialog.value = false
    await loadSamples()
  } catch (e) { /* 已提示 */ } finally { submitting.value = false }
}

async function removeSample(s) {
  try {
    await ElMessageBox.confirm(`确认删除样品【${s.sampleNo || '草稿#' + s.id}】？`, '删除样品', {
      type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消'
    })
  } catch (e) { return }
  try {
    await api.deleteSample(s.id)
    ElMessage.success('样品已删除')
    if (selectedSampleId.value === s.id) selectedSampleId.value = null
    await loadSamples()
  } catch (e) { /* 已提示 */ }
}

// ---------------- 批次：建批 / 装样 / 取消 ----------------

async function openBatchCreate() {
  const [ready, returned] = await Promise.all([
    api.samples({ status: 'READY' }),
    api.samples({ status: 'RETURNED' })
  ])
  packableSamples.value = [...ready, ...returned]
  if (!packableSamples.value.length) {
    ElMessage.warning('当前没有待送检或已退回的样品，请先完成取样登记')
    return
  }
  batchForm.value = { batchNo: '', transportNote: '', sampleIds: [] }
  batchDialog.value = true
}

async function submitBatchCreate() {
  if (!batchForm.value.sampleIds.length) {
    ElMessage.warning('请至少选择 1 件装批样品')
    return
  }
  saveOperator()
  submitting.value = true
  try {
    const data = await api.createSampleBatch({
      batchNo: batchForm.value.batchNo,
      operator: operator.value,
      transportNote: batchForm.value.transportNote,
      sampleIds: batchForm.value.sampleIds
    })
    ElMessage.success('批次已建立：' + data.batch.batchNo)
    batchDialog.value = false
    selectedBatchId.value = data.batch.id
    batchDetail.value = data
    await Promise.all([loadSamples(), loadBatches()])
  } catch (e) { /* 已提示 */ } finally { submitting.value = false }
}

async function addToBatch(s) {
  if (!packingActive.value) {
    ElMessage.warning('请先在中间列表选中一个「封样中」的批次')
    return
  }
  submitting.value = true
  try {
    batchDetail.value = await api.batchAddSamples(batch.value.id, {
      sampleIds: [s.id],
      expectedVersion: batch.value.lockVersion
    })
    ElMessage.success(`样品 ${s.sampleNo} 已加入批次 ${batch.value.batchNo}`)
    await Promise.all([loadSamples(), loadBatches()])
  } catch (e) { /* 已提示 */ } finally { submitting.value = false }
}

async function removeFromBatch(s) {
  submitting.value = true
  try {
    batchDetail.value = await api.batchRemoveSample(batch.value.id, s.id, {
      expectedVersion: batch.value.lockVersion
    })
    ElMessage.success(`样品 ${s.sampleNo} 已移出批次`)
    await Promise.all([loadSamples(), loadBatches()])
  } catch (e) { /* 已提示 */ } finally { submitting.value = false }
}

async function disband() {
  try {
    await ElMessageBox.confirm(
      `确认取消批次 ${batch.value.batchNo}？批次内 ${detailSamples.value.length} 件样品将退回待送检。`,
      '取消批次', { type: 'warning', confirmButtonText: '取消批次', cancelButtonText: '再想想' }
    )
  } catch (e) { return }
  submitting.value = true
  try {
    await api.disbandBatch(batch.value.id, { expectedVersion: batch.value.lockVersion })
    ElMessage.success('批次已取消，样品退回待送检')
    selectedBatchId.value = null
    batchDetail.value = null
    await Promise.all([loadSamples(), loadBatches()])
  } catch (e) { /* 已提示 */ } finally { submitting.value = false }
}

// ---------------- 批次：封样交接 / 退回 / 接收 ----------------

function openHandover() {
  if (!detailSamples.value.length) {
    ElMessage.warning('批次内没有样品，不能交接')
    return
  }
  handoverNote.value = batch.value.transportNote || ''
  handoverDialog.value = true
}

async function doHandover() {
  if (!operator.value.trim()) {
    ElMessage.warning('封样交接必须填写交出人')
    return
  }
  saveOperator()
  submitting.value = true
  try {
    batchDetail.value = await api.handoverBatch(batch.value.id, {
      operator: operator.value,
      transportNote: handoverNote.value,
      expectedVersion: batch.value.lockVersion
    })
    ElMessage.success('封样交接完成，批次在途')
    handoverDialog.value = false
    await Promise.all([loadSamples(), loadBatches()])
  } catch (e) { /* 已提示 */ } finally { submitting.value = false }
}

function openReturn() {
  returnReason.value = ''
  returnDialog.value = true
}

async function doReturn() {
  if (!returnReason.value.trim()) {
    ElMessage.warning('整批退回必须填写退回原因')
    return
  }
  saveOperator()
  submitting.value = true
  try {
    batchDetail.value = await api.returnBatch(batch.value.id, {
      operator: operator.value,
      reason: returnReason.value,
      expectedVersion: batch.value.lockVersion
    })
    ElMessage.success('批次已整批退回')
    returnDialog.value = false
    await Promise.all([loadSamples(), loadBatches()])
  } catch (e) { /* 已提示 */ } finally { submitting.value = false }
}

function openReceive() {
  const checkStatus = batch.value.status === 'PARTIAL' ? 'PENDING_FIX' : 'SHIPPED'
  receiveRows.value = detailSamples.value
    .filter((s) => s.status === checkStatus)
    .map((s) => ({
      sampleId: s.id, sampleNo: s.sampleNo, fromDepth: s.fromDepth, toDepth: s.toDepth,
      sealNo: s.sealNo, present: true, actualSealNo: s.sealNo, note: ''
    }))
  receiveNote.value = ''
  receiveDialog.value = true
}

async function doReceive() {
  if (!operator.value.trim()) {
    ElMessage.warning('实验室接收必须填写接收人')
    return
  }
  const items = receiveRows.value
    .filter((r) => r.present)
    .map((r) => ({ sampleId: r.sampleId, actualSealNo: r.actualSealNo, note: r.note }))
  if (!items.length) {
    ElMessage.warning('实收样品不能为空；若整批无法接收请使用「整批退回」')
    return
  }
  saveOperator()
  submitting.value = true
  try {
    const data = await api.receiveBatch(batch.value.id, {
      operator: operator.value,
      note: receiveNote.value,
      items,
      expectedVersion: batch.value.lockVersion
    })
    batchDetail.value = data
    ElMessage.success(data.batch.status === 'RECEIVED'
      ? '数量、封签一致，批次已接收'
      : '部分接收：已保留实收样品并标出待补项')
    receiveDialog.value = false
    await Promise.all([loadSamples(), loadBatches()])
  } catch (e) { /* 已提示 */ } finally { submitting.value = false }
}

// ---------------- 孔资料跳转 ----------------

function gotoHole() {
  if (!holeId.value) return
  const tab = gotoHoleFromSamples(holeId.value, batch.value?.batchNo || '')
  window.dispatchEvent(new CustomEvent('dc:navigate', { detail: { tab } }))
}

// ---------------- 状态持久化（刷新 / 切页返回后保留批次与筛选条件） ----------------

watch([role, holeId, sampleStatus, batchStatus, selectedBatchId], () => {
  localStorage.setItem(UI_KEY, JSON.stringify({
    role: role.value,
    holeId: holeId.value,
    sampleStatus: sampleStatus.value,
    batchStatus: batchStatus.value,
    batchId: selectedBatchId.value
  }))
})

function saveOperator() {
  if (operator.value) localStorage.setItem('dc_operator', operator.value)
}

let activatedOnce = false
onActivated(async () => {
  // KeepAlive 首次激活时 onMounted 已完成加载；之后每次从其他页返回都刷新数据、保留筛选与当前批次
  if (!activatedOnce) { activatedOnce = true; return }
  await reloadAll()
})

onMounted(async () => {
  let saved = {}
  try { saved = JSON.parse(localStorage.getItem(UI_KEY) || '{}') } catch (e) { saved = {} }
  role.value = saved.role === 'lab' ? 'lab' : 'field'
  sampleStatus.value = saved.sampleStatus || null
  batchStatus.value = saved.batchStatus || null
  holes.value = await api.boreholes()
  holeId.value = saved.holeId && holes.value.some((h) => h.id === saved.holeId)
    ? saved.holeId
    : (holes.value[0]?.id ?? null)
  await Promise.all([loadSamples(), loadBatches()])
  if (saved.batchId) {
    try { await openBatch(saved.batchId) } catch (e) { /* 批次可能已取消 */ }
  }
})
</script>
