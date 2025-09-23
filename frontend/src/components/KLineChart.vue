<template>
  <div>
    <div style="margin-bottom: 10px;">
      <button
        :class="['mode-btn', { active: currentMode === 'mock' }]"
        @click="switchMode('mock')"
      >Mock数据</button>
      <button
        :class="['mode-btn', { active: currentMode === 'real' }]"
        @click="switchMode('real')"
      >实时推送</button>
      <span style="margin-left: 12px; font-size: 14px; color: #888;">
        模式: {{ currentMode === 'mock' ? 'Mock数据' : 'WebSocket实时' }}
        <span v-if="currentMode === 'real' && wsStatus" :style="{ color: wsStatus === 'connected' ? '#4CAF50' : '#f44336' }">
          ({{ wsStatus === 'connected' ? '已连接' : wsStatus === 'connecting' ? '连接中' : '断开' }})
        </span>
      </span>
    </div>
    <div class="interval-bar">
      <button
        v-for="item in intervals"
        :key="item.value"
        class="interval-btn"
        :class="{ active: interval === item.value }"
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
type KLine = { open: number; close: number; high: number; low: number; volume: number; timestamp: number }
const now = Date.now()
const mockKLineData: KLine[] = []
let lastClose = 100
for (let i = 0; i < 120; i++) {
  const isUp = Math.random() > 0.45
  const change = +(Math.random() * 1.5 + 0.1).toFixed(2)
  const open = lastClose
  const close = +(isUp ? open + change : open - change).toFixed(2)
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
    symbol: { type: String, required: false, default: 'AAPL' },
    data: { type: Array, required: false, default: undefined }
  },
  setup(props) {
    const chartRef = ref<HTMLDivElement | null>(null)
    let chartInstance: echarts.ECharts | null = null

    // 添加模式切换相关状态
    const currentMode = ref('mock')
    const wsStatus = ref('')
    let ws: WebSocket | null = null

    // 优先用 props.data，否则 fallback 到 mockKLineData
    const klineData = ref<any[]>(props.data && props.data.length ? props.data : [...mockKLineData])
    // 监听 props.data 变化，动态更新 klineData
    watch(() => props.data, (val) => {
      if (Array.isArray(val) && val.length) {
        klineData.value = val
        if (chartInstance) {
          try {
            renderChart(aggregateKLine(klineData.value, interval.value))
          } catch (e) {
            console.error('KLineChart renderChart error:', e)
          }
        }
      }
    }, { immediate: true })

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
      if (ws) {
        ws.close()
        ws = null
      }
    })
    onUnmounted(() => {
      chartInstance?.dispose()
      window.removeEventListener('resize', updateSlider)
      if (ws) {
        ws.close()
        ws = null
      }
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
      if (!Array.isArray(data) || !data.length || !chartInstance) return
      try {
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
                  if (Math.abs(value - min) < 1e-6 || Math.abs(value - max) < 1e-6) return ' ';
                  return value.toString();
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
                  if (Math.abs(value - min) < 1e-6 || Math.abs(value - max) < 1e-6) return ' ';
                  return value.toString();
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
      } catch (e) {
        console.error('KLineChart renderChart error:', e)
      }
    }

    // 切换模式函数
    const switchMode = (mode: string) => {
      if (currentMode.value === mode) return

      currentMode.value = mode

      if (mode === 'mock') {
        // 断开 WebSocket
        if (ws) {
          ws.close()
          ws = null
        }
        wsStatus.value = ''
        // 恢复 mock 数据
        klineData.value = [...mockKLineData]
        renderChart(aggregateKLine(klineData.value, interval.value))
      } else if (mode === 'real') {
        // 保留 mock 数据作为历史数据，不清空
        // klineData.value = [] // 删除这行，保留历史数据

        // 如果当前没有数据，则使用 mock 数据作为基础
        if (klineData.value.length === 0) {
          klineData.value = [...mockKLineData]
        }

        // 连接 WebSocket 开始接收实时数据
        connectWebSocket()
      }
    }

    // WebSocket 连接函数
    const connectWebSocket = () => {
      if (ws) ws.close()

      wsStatus.value = 'connecting'
      const wsUrl = `ws://localhost:8080/ws/kline`

      try {
        ws = new WebSocket(wsUrl)

        ws.onopen = () => {
          console.log('WebSocket connected to:', wsUrl)
          wsStatus.value = 'connected'
          // 连接成功后自动订阅当前股票
          subscribeToSymbol(props.symbol)
        }

        ws.onmessage = (event) => {
          try {
            const message = JSON.parse(event.data)
            console.log('Received WebSocket message:', message)

            switch (message.type) {
              case 'connection':
                console.log('Connection confirmed:', message.message)
                break
              case 'subscribe':
                console.log('Subscription confirmed for:', message.symbol)
                break
              case 'unsubscribe':
                console.log('Unsubscription confirmed for:', message.symbol)
                break
              case 'kline':
                handleKlineData(message)
                break
              case 'error':
                console.error('WebSocket error:', message.message)
                break
              case 'pong':
                console.log('Pong received')
                break
            }
          } catch (e) {
            console.error('WebSocket message parse error:', e)
          }
        }

        ws.onclose = () => {
          console.log('WebSocket disconnected')
          wsStatus.value = 'disconnected'
        }

        ws.onerror = (error) => {
          console.error('WebSocket error:', error)
          wsStatus.value = 'error'
        }
      } catch (e) {
        console.error('WebSocket connection failed:', e)
        wsStatus.value = 'error'
      }
    }

    // 订阅股票K线数据
    const subscribeToSymbol = (symbol: string) => {
      if (ws && ws.readyState === WebSocket.OPEN) {
        const message = {
          action: 'subscribe',
          symbol: symbol
        }
        ws.send(JSON.stringify(message))
        console.log('Subscribing to:', symbol)
      }
    }

    // 取消订阅股票K线数据
    const unsubscribeFromSymbol = (symbol: string) => {
      if (ws && ws.readyState === WebSocket.OPEN) {
        const message = {
          action: 'unsubscribe',
          symbol: symbol
        }
        ws.send(JSON.stringify(message))
        console.log('Unsubscribing from:', symbol)
      }
    }

    // 处理K线数据
    const handleKlineData = (message: any) => {
      if (message.symbol === props.symbol) {
        try {
          const newKlineData = typeof message.data === 'string'
            ? JSON.parse(message.data)
            : message.data

          // 确保有timestamp
          if (!newKlineData.timestamp) {
            console.warn('Received kline data without timestamp, skipping')
            return
          }

          updateOrMergeKLineData(newKlineData)
        } catch (e) {
          console.error('Error parsing kline data:', e)
        }
      }
    }

    // 更新或合并K线数据（处理timestamp去重和实时更新）
    const updateOrMergeKLineData = (newKLine: any) => {
      // 转换后端BigDecimal数据为前端数字类型
      const normalizedKLine = {
        open: typeof newKLine.open === 'object' ? parseFloat(newKLine.open) : newKLine.open,
        close: typeof newKLine.close === 'object' ? parseFloat(newKLine.close) : newKLine.close,
        high: typeof newKLine.high === 'object' ? parseFloat(newKLine.high) : newKLine.high,
        low: typeof newKLine.low === 'object' ? parseFloat(newKLine.low) : newKLine.low,
        volume: newKLine.volume,
        timestamp: newKLine.timestamp
      }

      const newTimestamp = normalizedKLine.timestamp

      // 将timestamp标准化到分钟级别（这样同一分钟内的数据会有相同的timestamp）
      const minuteTimestamp = Math.floor(newTimestamp / 60000) * 60000

      // 查找是否已存在相同分钟的K线
      const existingIndex = klineData.value.findIndex(k => {
        const kMinuteTimestamp = Math.floor(k.timestamp / 60000) * 60000
        return kMinuteTimestamp === minuteTimestamp
      })

      if (existingIndex >= 0) {
        // 更新现有K线（实时更新当前分钟的蜡烛图）
        // 保持原有的open不变，更新其他字段
        const existingKLine = klineData.value[existingIndex]
        klineData.value[existingIndex] = {
          ...normalizedKLine,
          timestamp: minuteTimestamp,
          open: existingKLine.open, // 保持分钟开始时的开盘价
          // high和low需要与现有数据比较
          high: Math.max(existingKLine.high, normalizedKLine.high),
          low: Math.min(existingKLine.low, normalizedKLine.low),
          // close使用最新价格
          close: normalizedKLine.close,
          // 累加成交量（如果需要的话）
          volume: existingKLine.volume + normalizedKLine.volume
        }
        console.log('Updated existing kline at minute:', new Date(minuteTimestamp).toLocaleTimeString(),
                   'close:', normalizedKLine.close)
      } else {
        // 添加新的K线
        const newKLineWithMinuteTimestamp = {
          ...normalizedKLine,
          timestamp: minuteTimestamp
        }
        klineData.value.push(newKLineWithMinuteTimestamp)

        // 按timestamp排序
        klineData.value.sort((a, b) => a.timestamp - b.timestamp)

        // 保持最近120根K线
        if (klineData.value.length > 120) {
          klineData.value = klineData.value.slice(-120)
        }

        console.log('Added new kline at minute:', new Date(minuteTimestamp).toLocaleTimeString(),
                   'open:', normalizedKLine.open, 'close:', normalizedKLine.close)
      }

      // 重新渲染图表
      renderChart(aggregateKLine(klineData.value, interval.value))
    }

    // 监听symbol变化，切换订阅
    watch(() => props.symbol, (newSymbol, oldSymbol) => {
      if (currentMode.value === 'real' && ws && ws.readyState === WebSocket.OPEN) {
        if (oldSymbol) {
          unsubscribeFromSymbol(oldSymbol)
        }
        if (newSymbol) {
          subscribeToSymbol(newSymbol)
          // 清空当前数据，准备接收新股票的数据
          klineData.value = []
        }
      }
    })


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
      selectChartType,
      currentMode,
      wsStatus,
      switchMode
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

.mode-btn {
  border: none;
  background: #333;
  color: #fff;
  padding: 8px 16px;
  border-radius: 4px;
  cursor: pointer;
  margin-right: 8px;
  font-size: 14px;
  transition: background 0.3s;
}

.mode-btn:hover {
  background: #555;
}

.mode-btn.active {
  background: #1677ff;
  color: white;
}

/* 让 ECharts 适配深色主题 */
:deep(.echarts) {
  background: var(--kline-card-bg) !important;
  color: var(--kline-text) !important;
}
</style>