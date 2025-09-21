<template>
  <div ref="chartRef" style="width: 600px; height: 400px;"></div>
</template>

<script lang="ts">
import { defineComponent, ref, watch, onMounted } from 'vue'
import * as echarts from 'echarts'
import { getKLine } from '../api'
import type { KLineData } from '../types'

export default defineComponent({
  props: {
    symbol: { type: String, required: true }
  },
  setup(props) {
    const chartRef = ref<HTMLDivElement | null>(null)
    let chartInstance: echarts.ECharts | null = null

    const renderChart = (data: KLineData[]) => {
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

    const fetchData = async () => {
      const res = await getKLine(props.symbol)
      renderChart(res.data)
    }

    watch(() => props.symbol, fetchData)
    onMounted(() => {
      if (chartRef.value) chartInstance = echarts.init(chartRef.value)
      fetchData()
    })

    return { chartRef }
  }
})
</script>