<template>
  <div ref="chartRef" style="width: 600px; height: 400px;"></div>
</template>

<script lang="ts">
import { defineComponent, ref, watch, onMounted, onUnmounted } from 'vue'
import * as echarts from 'echarts'
import type { KLineData } from '../types'

export default defineComponent({
  props: {
    symbol: { type: String, required: false, default: 'AAPL' }
  },
  setup(props) {
    const chartRef = ref<HTMLDivElement | null>(null)
    let chartInstance: echarts.ECharts | null = null
    let ws: WebSocket | null = null
    const klineData = ref<KLineData[]>([])

    const convertKLine = (k: any) => ({
      date: k.date || (k.timestamp ? new Date(k.timestamp).toISOString().slice(0, 16).replace('T', ' ') : ''),
      open: k.open,
      close: k.close,
      low: k.low,
      high: k.high
    })

    const renderChart = (data: any[]) => {
      const option: echarts.EChartsOption = {
        title: { text: props.symbol },
        tooltip: { trigger: 'axis' },
        xAxis: { type: 'category', data: data.map(d => d.date) },
        yAxis: { type: 'value' },
        series: [{
          type: 'candlestick',
          data: data.map(d => [d.open, d.close, d.low, d.high])
        }]
      }
      chartInstance?.setOption(option)
    }

    const connectWS = () => {
      if (ws) ws.close()
      ws = new WebSocket('ws://localhost:8080/ws/kline')
      ws.onopen = () => {
        ws?.send(JSON.stringify({ action: 'subscribe', symbol: props.symbol }))
      }
      ws.onmessage = (event) => {
        console.log('WS raw:', event.data); // debug
        try {
          let msg = JSON.parse(event.data)
          if (typeof msg === 'string') {
            msg = JSON.parse(msg)
          }
          if (Array.isArray(msg)) {
            klineData.value = msg.map(convertKLine)
            renderChart(klineData.value)
          } else if (msg && (msg.date || msg.timestamp)) {
            // 增量推送
            const k = convertKLine(msg)
            const last = klineData.value[klineData.value.length - 1]
            if (last && last.date === k.date) {
              klineData.value[klineData.value.length - 1] = k
            } else {
              klineData.value.push(k)
            }
            renderChart(klineData.value)
          }
        } catch (e) { console.error('WS parse error', e) }
      }
      ws.onclose = () => { ws = null }
    }

    watch(() => props.symbol, () => {
      connectWS()
    })
    onMounted(() => {
      if (chartRef.value) chartInstance = echarts.init(chartRef.value)
      connectWS()
    })
    onUnmounted(() => {
      if (ws) ws.close()
    })

    return { chartRef }
  }
})
</script>