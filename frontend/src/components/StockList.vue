<template>
  <div>
    <h3>Stocks</h3>
    <div class="stock-card-list">
      <div
        v-for="stock in stocks"
        :key="stock.symbol"
        class="stock-card"
        @click="$emit('select', stock.symbol)"
        :class="{ selected: stock.symbol === selectedSymbol }"
      >
        <div class="stock-symbol">{{ stock.symbol }}</div>
        <div class="stock-name">{{ stock.name }}</div>
        <div class="stock-price">${{ stock.price }}</div>
      </div>
    </div>
  </div>
</template>

<script lang="ts">
import { defineComponent, ref, computed } from 'vue'
export default defineComponent({
  props: {
    modelValue: String // 用于高亮当前选中
  },
  emits: ['select'],
  setup(props) {
    // mock 更多股票数据
    const stocks = ref([
      { symbol: 'AAPL', name: 'Apple', price: 175.12 },
      { symbol: 'TSLA', name: 'Tesla', price: 245.67 },
      { symbol: 'GOOG', name: 'Google', price: 2820.15 },
      { symbol: 'MSFT', name: 'Microsoft', price: 330.21 },
      { symbol: 'AMZN', name: 'Amazon', price: 3400.55 },
      { symbol: 'NVDA', name: 'Nvidia', price: 900.12 },
      { symbol: 'META', name: 'Meta', price: 320.88 },
      { symbol: 'BABA', name: 'Alibaba', price: 85.23 },
      { symbol: 'NFLX', name: 'Netflix', price: 410.45 },
      { symbol: 'ORCL', name: 'Oracle', price: 120.33 },
      { symbol: 'INTC', name: 'Intel', price: 38.12 },
      { symbol: 'AMD', name: 'AMD', price: 110.56 },
      { symbol: 'IBM', name: 'IBM', price: 140.22 },
      { symbol: 'ADBE', name: 'Adobe', price: 570.11 },
      { symbol: 'CRM', name: 'Salesforce', price: 250.77 },
      { symbol: 'PYPL', name: 'PayPal', price: 65.44 },
      { symbol: 'UBER', name: 'Uber', price: 45.67 },
      { symbol: 'BIDU', name: 'Baidu', price: 120.88 },
      { symbol: 'PDD', name: 'Pinduoduo', price: 140.99 },
      { symbol: 'SHOP', name: 'Shopify', price: 70.12 },
      { symbol: 'TCEHY', name: 'Tencent', price: 38.88 },
      { symbol: 'JD', name: 'JD.com', price: 28.77 },
      { symbol: 'QCOM', name: 'Qualcomm', price: 130.55 },
      { symbol: 'CSCO', name: 'Cisco', price: 52.33 },
      { symbol: 'AVGO', name: 'Broadcom', price: 1200.12 },
      { symbol: 'TXN', name: 'Texas Instruments', price: 170.22 },
      { symbol: 'SAP', name: 'SAP', price: 160.44 },
      { symbol: 'SONY', name: 'Sony', price: 90.11 },
      { symbol: 'ZM', name: 'Zoom', price: 62.88 },
      { symbol: 'COIN', name: 'Coinbase', price: 220.55 }
    ])
    const selectedSymbol = computed(() => props.modelValue)
    return { stocks, selectedSymbol }
  }
})
</script>

<style scoped>
.stock-card-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 12px;
  max-height: 420px;
  overflow-y: auto;
  scrollbar-width: thin;
  scrollbar-color: #3a8fff #232a36;
}
.stock-card-list::-webkit-scrollbar {
  width: 7px;
  background: #232a36;
}
.stock-card-list::-webkit-scrollbar-thumb {
  background: #3a8fff;
  border-radius: 6px;
}
.stock-card {
  min-width: 90px;
  padding: 12px 16px;
  background: #232a36;
  border-radius: 8px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.10);
  cursor: pointer;
  transition: box-shadow 0.2s, background 0.2s, border 0.2s;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  border: 2px solid transparent;
  color: #bfc9d4;
}
.stock-card.selected {
  background: linear-gradient(90deg, #2a5cff 60%, #1677ff 100%);
  border-color: #3a8fff;
  color: #e6f7ff;
  box-shadow: 0 2px 8px rgba(64,158,255,0.18);
}
.stock-card:hover:not(.selected) {
  background: #22304a;
  color: #fff;
}
.stock-symbol {
  font-weight: 700;
  font-size: 16px;
  color: inherit;
}
.stock-name {
  font-size: 13px;
  color: #8ca0b3;
  margin-top: 2px;
}
.stock-price {
  font-size: 14px;
  color: #3fc371;
  margin-top: 4px;
}
h3 {
  color: #bfc9d4;
  margin-bottom: 8px;
}
</style>
