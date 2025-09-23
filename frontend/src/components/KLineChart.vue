<template>
  <div>
    <div class="interval-bar">
      <button
        v-for="(item, idx) in intervals"
        :key="item.value"
        :class="['interval-btn', { active: interval === item.value }]"
        @click="setInterval(item.value)"
        ref="intervalBtns"
      >
        {{ item.label }}
      </button>
      <div class="chart-type-dropdown" ref="dropdownRef" @click.stop="toggleDropdown">
        <span class="chart-type-selected-icon">
          <span v-if="chartType === 'candle'">🕯️</span>
          <span v-else>⛰️</span>
        </span>
        <span class="dropdown-arrow">▼</span>
        <div v-if="dropdownOpen" class="chart-type-options">
          <div class="chart-type-option" :class="{active: chartType==='candle'}" @click.stop="selectChartType('candle')">
            <span>🕯️</span> <span class="option-label">Candle</span>
          </div>
          <div class="chart-type-option" :class="{active: chartType==='mountain'}" @click.stop="selectChartType('mountain')">
            <span>⛰️</span> <span class="option-label">Mountain</span>
          </div>
        </div>
      </div>
      <div
        class="interval-slider"
        :style="sliderStyle"
      ></div>
    </div>
    <div ref="chartRef" class="kline-chart-area"></div>
  </div>
</template>

<script lang="ts">
import { defineComponent, ref, watch, onMounted, onUnmounted, nextTick, onBeforeUnmount } from 'vue'
import * as echarts from 'echarts'

const intervals = [
  { label: '1m', value: '1m', ms: 60 * 1000 },
  { label: '5m', value: '5m', ms: 5 * 60 * 1000 },
  { label: '15m', value: '15m', ms: 15 * 60 * 1000 },
  { label: '1h', value: '1h', ms: 60 * 60 * 1000 },
  { label: '1d', value: '1d', ms: 24 * 60 * 60 * 1000 }
]

// mock 数据，120 根，open/close 连续，涨跌交替，覆盖2小时
const now = Date.now()
const mockKLineData = []
let lastClose = 100
for (let i = 0; i < 120; i++) {
  // 随机决定涨跌
  const isUp = Math.random() > 0.45
  // 波动幅度
  const change = +(Math.random() * 1.5 + 0.1).toFixed(2)
  const open = lastClose
  const close = +(isUp ? open + change : open - change).toFixed(2)
  // high/low 在 open/close 附近波动
  const high = +(Math.max(open, close) + Math.random() * 0.5).toFixed(2)
  const low = +(Math.min(open, close) - Math.random() * 0.5).toFixed(2)
  const volume = +(Math.random() * 1000 + 1000).toFixed(0)
  mockKLineData.push({
    open,
    close,
    high,
    low,
    volume,
    timestamp: now - (119 - i) * 60 * 1000
  })
  lastClose = close
}

export default defineComponent({
  props: {
    symbol: { type: String, required: false, default: 'AAPL' }
  },
  setup(props) {
    const chartRef = ref<HTMLDivElement | null>(null)
    let chartInstance: echarts.ECharts | null = null
    const klineData = ref<any[]>([...mockKLineData])
    const interval = ref('1m')
    const intervalBtns = ref([])
    const sliderStyle = ref({ left: '0px', width: '0px' })
    const chartType = ref<'candle' | 'mountain'>('candle')
    const dropdownOpen = ref(false)
    const dropdownRef = ref<HTMLElement | null>(null)

    const setInterval = (val: string) => {
      interval.value = val
      renderChart(aggregateKLine(klineData.value, val))
      nextTick(() => updateSlider())
    }

    const toggleDropdown = () => {
      dropdownOpen.value = !dropdownOpen.value
    }

    const selectChartType = (type: 'candle' | 'mountain') => {
      chartType.value = type
      dropdownOpen.value = false
      renderChart(aggregateKLine(klineData.value, interval.value))
    }

    const updateSlider = () => {
      // @ts-ignore
      const btns = intervalBtns.value as HTMLButtonElement[]
      const idx = intervals.findIndex(i => i.value === interval.value)
      if (btns && btns[idx]) {
        const btn = btns[idx]
        const left = btn.offsetLeft
        const width = btn.offsetWidth
        sliderStyle.value = {
          left: left + 'px',
          width: width + 'px'
        }
      }
    }

    const handleClickOutside = (e: MouseEvent) => {
      if (!dropdownRef.value) return
      if (!(e.target instanceof Node)) return
      if (!dropdownRef.value.contains(e.target)) {
        dropdownOpen.value = false
      }
    }

    onMounted(() => {
      chartInstance = echarts.init(chartRef.value as HTMLDivElement)
      renderChart(aggregateKLine(klineData.value, interval.value))
      nextTick(() => updateSlider())
      window.addEventListener('resize', updateSlider)
      document.addEventListener('click', handleClickOutside)
    })
    onBeforeUnmount(() => {
      document.removeEventListener('click', handleClickOutside)
    })
    onUnmounted(() => {
      chartInstance?.dispose()
      window.removeEventListener('resize', updateSlider)
    })
    watch(interval, () => nextTick(() => updateSlider()))
    watch(chartType, () => renderChart(aggregateKLine(klineData.value, interval.value)))

    // 只用 mock 数据，不需要 convertKLine
    function aggregateKLine(data: any[], intervalStr: string) {
      if (intervalStr === '1m') return data
      const intervalObj = intervals.find(i => i.value === intervalStr)
      if (!intervalObj) return data
      const ms = intervalObj.ms
      const result: any[] = []
      let bucket: any = null
      for (const k of data) {
        const t = Math.floor(k.timestamp / ms) * ms
        if (!bucket || bucket.bucketTime !== t) {
          if (bucket) result.push({ ...bucket })
          bucket = {
            bucketTime: t,
            date: new Date(t).toISOString().slice(0, 16).replace('T', ' '),
            open: k.open,
            high: k.high,
            low: k.low,
            close: k.close,
            volume: k.volume,
            timestamp: t
          }
        } else {
          bucket.high = Math.max(bucket.high, k.high)
          bucket.low = Math.min(bucket.low, k.low)
          bucket.close = k.close
          bucket.volume += k.volume
        }
      }
      if (bucket) result.push({ ...bucket })
      return result
    }
    const renderChart = (data: any[]) => {
      if (!data.length) return
      // 调试输出每根K线的涨跌
      data.forEach((d, i) => {
        const dir = d.close > d.open ? 'UP' : (d.close < d.open ? 'DOWN' : 'FLAT')
        // eslint-disable-next-line
        console.log(`Candle #${i}: open=${d.open}, close=${d.close}, ${dir}`)
      })
      // 计算最高价和最低价
      let max = Math.max(...data.map(d => d.high))
      let min = Math.min(...data.map(d => d.low))
      const padding = (max - min) * 0.01
      max += padding
      min -= padding
      const xData = data.map(d => new Date(d.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }))
      let option: echarts.EChartsOption
      if (chartType.value === 'candle') {
        option = {
          title: { text: props.symbol },
          tooltip: { trigger: 'axis' },
          xAxis: { type: 'category', data: xData },
          yAxis: {
            type: 'value',
            min,
            max,
            splitNumber: 5,
            axisLabel: {
              show: true,
              color: '#bfc9d4',
              formatter: function(value: number) {
                if (Math.abs(value - min) < 1e-6 || Math.abs(value - max) < 1e-6) return '';
                return value;
              }
            },
            splitLine: { show: true, lineStyle: { color: '#fff', opacity: 0.18, width: 1, type: 'solid' } },
            axisTick: { show: false },
            axisLine: { show: false }
          },
          series: [{
            type: 'candlestick',
            data: data.map(d => [d.open, d.close, d.low, d.high]),
            barWidth: '99%',
            itemStyle: {
              color: '#3fc371',
              borderColor: '#3fc371',
              color0: '#f36c6c',
              borderColor0: '#f36c6c'
            }
          }]
        }
      } else {
        option = {
          title: { text: props.symbol },
          tooltip: {
            trigger: 'axis',
            formatter: (params: any) => {
              const d = data[params[0].dataIndex]
              return `时间: ${d.date || xData[params[0].dataIndex]}<br />`
                + `Open: ${d.open}<br />High: ${d.high}<br />Low: ${d.low}<br />Close: ${d.close}<br />Volume: ${d.volume}`
            }
          },
          xAxis: { type: 'category', data: xData },
          yAxis: {
            type: 'value',
            min,
            max,
            splitNumber: 5,
            axisLabel: {
              show: true,
              color: '#bfc9d4',
              formatter: function(value: number) {
                if (Math.abs(value - min) < 1e-6 || Math.abs(value - max) < 1e-6) return '';
                return value;
              }
            },
            splitLine: { show: true, lineStyle: { color: '#fff', opacity: 0.18, width: 1, type: 'solid' } },
            axisTick: { show: false },
            axisLine: { show: false }
          },
          series: [{
            type: 'line',
            data: data.map(d => d.close),
            smooth: true,
            symbol: 'none',
            lineStyle: { color: '#3fc371', width: 2 },
            areaStyle: { color: 'rgba(63,195,113,0.18)' }
          }]
        }
      }
      chartInstance?.setOption(option)
    }
    return {
      chartRef,
      intervals,
      interval,
      setInterval,
      intervalBtns,
      sliderStyle,
      chartType,
      dropdownOpen,
      dropdownRef,
      toggleDropdown,
      selectChartType
    }
  }
})
</script>

<style scoped>
:root {
  --kline-bg: #181c24;
  --kline-card-bg: #232a36;
  --kline-btn-bg: #232a36;
  --kline-btn-active: #1677ff;
  --kline-btn-hover: #22304a;
  --kline-text: #fff;
  --kline-btn-text: #bfc9d4;
  --kline-btn-active-text: #fff;
}

.interval-bar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
  background: transparent;
  position: relative;
  align-items: flex-end;
  height: 40px;
}
.interval-btn {
  border: none;
  background: var(--kline-btn-bg);
  color: var(--kline-btn-text);
  padding: 6px 16px;
  border-radius: 6px;
  cursor: pointer;
  font-weight: 500;
  transition: background 0.2s, color 0.2s, box-shadow 0.2s, font-weight 0.2s;
  outline: none;
  position: relative;
  z-index: 1;
}
.interval-btn.active {
  background: linear-gradient(90deg, #2a5cff 60%, #1677ff 100%);
  color: #e6f7ff;
  font-weight: 700;
  box-shadow: 0 2px 16px 0 #1677ff80, 0 0 0 2px #3a8fff;
  border: 1.5px solid #3a8fff;
  outline: none;
  z-index: 2;
}
.interval-btn:hover:not(.active) {
  background: var(--kline-btn-hover);
  color: var(--kline-btn-active-text);
}
.interval-slider {
  position: absolute;
  bottom: 0;
  height: 3px;
  border-radius: 2px;
  background: var(--kline-btn-active);
  transition: left 0.25s cubic-bezier(.4,0,.2,1), width 0.25s cubic-bezier(.4,0,.2,1);
  z-index: 0;
}
.kline-chart-area {
  width: 100%;
  height: 400px;
  background: var(--kline-card-bg);
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.07);
  padding: 0;
}
.chart-type-dropdown {
  position: relative;
  display: inline-block;
  margin-left: 8px;
  min-width: 40px;
  user-select: none;
  cursor: pointer;
  background: var(--kline-btn-bg);
  border-radius: 6px;
  border: 1.5px solid #3a8fff;
  padding: 6px 12px 6px 10px;
  color: var(--kline-btn-text);
  font-size: 18px;
  transition: border 0.2s, box-shadow 0.2s;
}
.chart-type-dropdown:focus-within, .chart-type-dropdown:active {
  border: 1.5px solid #1677ff;
  box-shadow: 0 2px 8px 0 #1677ff40;
}
.chart-type-selected-icon {
  font-size: 20px;
  vertical-align: middle;
}
.dropdown-arrow {
  font-size: 12px;
  margin-left: 4px;
  vertical-align: middle;
}
.chart-type-options {
  position: absolute;
  left: 0;
  top: 110%;
  background: var(--kline-card-bg);
  border-radius: 8px;
  box-shadow: 0 2px 12px 0 #0002;
  min-width: 120px;
  z-index: 10;
  padding: 4px 0;
  border: 1.5px solid #3a8fff;
}
.chart-type-option {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  font-size: 16px;
  color: var(--kline-btn-text);
  cursor: pointer;
  background: transparent;
  transition: background 0.18s, color 0.18s;
}
.chart-type-option.active, .chart-type-option:hover {
  background: #1677ff22;
  color: #1677ff;
}
.option-label {
  font-size: 15px;
}

/* 让 ECharts 适配深色主题 */
:deep(.echarts) {
  background: var(--kline-card-bg) !important;
  color: var(--kline-text) !important;
}
</style>