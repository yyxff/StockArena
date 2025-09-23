<template>
  <div class="dashboard-dark-root">
    <div class="sidebar-dark">
      <!-- 侧边栏功能菜单，可后续抽离为 Sidebar 组件 -->
      <div class="sidebar-logo">StockArena</div>
      <ul class="sidebar-menu">
        <li class="active"><i class="iconfont">★</i> Watchlist</li>
        <li><i class="iconfont">📈</i> Orders</li>
        <li><i class="iconfont">👤</i> Account</li>
        <li><i class="iconfont">⚙️</i> Settings</li>
      </ul>
    </div>
    <div class="dashboard-dark-main">
      <div class="dashboard-dark-content">
        <div class="watchlist-card">
          <div class="card-title">Watchlist</div>
          <StockList :modelValue="selectedStock" @select="selectedStock = $event" />
        </div>
        <div class="main-cards">
          <div class="kline-card-dark">
            <div class="card-title">K线图</div>
            <KLineChart :symbol="selectedStock" :data="currentKlineData" />
          </div>
          <div class="orders-card-dark">
            <div class="card-title">下单</div>
            <OrderForm @submit="onOrderSubmit" />
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script lang="ts">
import { defineComponent, ref, watch } from 'vue'
import StockList from '../components/StockList.vue'
import KLineChart from '../components/KLineChart.vue'
import Orders from '../components/Orders.vue'
import OrderForm from '../components/OrderForm.vue'

// mock k线生成函数
function genMockKLine(symbol: string) {
  const now = Date.now()
  const arr = []
  let lastClose = 100 + Math.floor(Math.random() * 100)
  for (let i = 0; i < 120; i++) {
    const isUp = Math.random() > 0.45
    const change = +(Math.random() * 1.5 + 0.1).toFixed(2)
    const open = lastClose
    const close = +(isUp ? open + change : open - change).toFixed(2)
    const high = +(Math.max(open, close) + Math.random() * 0.5).toFixed(2)
    const low = +(Math.min(open, close) - Math.random() * 0.5).toFixed(2)
    const volume = +(Math.random() * 1000 + 1000).toFixed(0)
    arr.push({
      open, close, high, low, volume,
      timestamp: now - (119 - i) * 60 * 1000
    })
    lastClose = close
  }
  return arr
}

export default defineComponent({
  components: { StockList, KLineChart, Orders, OrderForm },
  setup() {
    const selectedStock = ref('AAPL')
    const klineMap = ref<Record<string, any[]>>({})
    // 初始化默认股票
    if (!klineMap.value['AAPL']) {
      klineMap.value['AAPL'] = genMockKLine('AAPL')
    }
    const currentKlineData = ref(klineMap.value[selectedStock.value])
    watch(selectedStock, (symbol) => {
      if (!klineMap.value[symbol]) {
        klineMap.value[symbol] = genMockKLine(symbol)
      }
      currentKlineData.value = klineMap.value[symbol]
    }, { immediate: true })
    const onOrderSubmit = (order: any) => {
      // 这里可以处理下单逻辑，比如调用API
      console.log('下单信息', order)
    }
    return { selectedStock, onOrderSubmit, currentKlineData }
  }
})
</script>

<style scoped>
.dashboard-dark-root {
  min-height: 100vh;
  background: linear-gradient(135deg, #23242b 0%, #181920 100%);
  display: flex;
  font-family: 'Inter', 'PingFang SC', 'Microsoft YaHei', Arial, sans-serif;
}
.sidebar-dark {
  width: 220px;
  background: #1a1b20;
  color: #fff;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 32px 0 0 0;
  box-shadow: 2px 0 16px 0 rgba(0,0,0,0.12);
}
.sidebar-logo {
  font-size: 22px;
  font-weight: bold;
  margin-bottom: 36px;
  letter-spacing: 2px;
  color: #fff;
}
.sidebar-menu {
  list-style: none;
  padding: 0;
  width: 100%;
}
.sidebar-menu li {
  padding: 14px 32px;
  font-size: 16px;
  color: #bfc4d1;
  cursor: pointer;
  display: flex;
  align-items: center;
  transition: background 0.2s, color 0.2s;
}
.sidebar-menu li.active, .sidebar-menu li:hover {
  background: #23242b;
  color: #fff;
}
.sidebar-menu i {
  margin-right: 12px;
}
.dashboard-dark-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.dashboard-dark-content {
  display: flex;
  flex-direction: row;
  gap: 32px;
  padding: 48px 48px 0 48px;
}
.watchlist-card {
  width: 280px;
  background: #23242b;
  border-radius: 16px;
  box-shadow: 0 2px 16px rgba(0,0,0,0.10);
  padding: 24px 18px 18px 18px;
  color: #fff;
  min-height: 480px;
  display: flex;
  flex-direction: column;
}
.main-cards {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 28px;
  min-width: 0;
}
.kline-card-dark, .orders-card-dark {
  background: #23242b;
  border-radius: 16px;
  box-shadow: 0 2px 16px rgba(0,0,0,0.10);
  padding: 24px 24px 18px 24px;
  color: #fff;
}
.kline-card-dark {
  min-height: 340px;
}
.orders-card-dark {
  min-height: 180px;
}
.card-title {
  font-size: 18px;
  font-weight: 600;
  margin-bottom: 18px;
  color: #fff;
  letter-spacing: 1px;
}
</style>
