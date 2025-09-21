<template>
  <div>
    <h2>Dashboard</h2>
    <AccountInfo />
    <div style="display: flex;">
      <StockList @select="selectedStock = $event" />
      <div>
        <KLineChart :symbol="selectedStock" />
        <Orders :symbol="selectedStock" />
      </div>
    </div>
  </div>
</template>

<script lang="ts">
import { defineComponent, ref } from 'vue'
import StockList from '../components/StockList.vue'
import KLineChart from '../components/KLineChart.vue'
import Orders from '../components/Orders.vue'
import AccountInfo from '../components/AccountInfo.vue'

export default defineComponent({
  components: { StockList, KLineChart, Orders, AccountInfo },
  setup() {
    const selectedStock = ref('AAPL') // 默认股票
    return { selectedStock }
  }
})
</script>