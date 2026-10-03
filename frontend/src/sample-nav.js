import { reactive } from 'vue'

/**
 * 样品送检页 ↔ 孔资料页的跨页导航状态。
 *
 * 样品送检页点「孔资料」跳到孔位分布页查看该孔；返回后样品页要保留
 * 【当前批次】和【筛选条件】。SampleView 在 App.vue 中以 KeepAlive 缓存，
 * 切页不丢组件状态；本模块级单例负责跨页传递目标孔；localStorage（dc_sample_ui）
 * 负责浏览器刷新后的恢复。
 *
 * navigation = { fromSamples, boreholeId, batchNo, nonce }
 */
export const sampleNav = reactive({
  fromSamples: false,
  boreholeId: null,
  batchNo: '',
  nonce: 0
})

/** 从样品送检页跳到孔资料（孔位分布）页 */
export function gotoHoleFromSamples(boreholeId, batchNo) {
  sampleNav.fromSamples = true
  sampleNav.boreholeId = boreholeId
  sampleNav.batchNo = batchNo || ''
  sampleNav.nonce++
  return 'holes'
}

/** 返回样品送检页：清掉跨页标记（批次与筛选条件由 SampleView 自身状态保留） */
export function exitToSamples() {
  sampleNav.fromSamples = false
  sampleNav.boreholeId = null
  sampleNav.batchNo = ''
}
