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
import { defineComponent, ref, onMounted, computed } from 'vue'
import { getStocks } from '../api'
import type { Stock } from '../types'

export default defineComponent({
  props: {
    modelValue: String // 用于高亮当前选中
  },
  emits: ['select'],
  setup(props) {
    const stocks = ref<Stock[]>([])
    const selectedSymbol = computed(() => props.modelValue)
    onMounted(async () => {
      const res = await getStocks()
      stocks.value = res.data
    })
    return { stocks, selectedSymbol }
  }
})
</script>

<style scoped>
.stock-card-list {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 12px;
}
.stock-card {
  min-width: 90px;
  padding: 12px 16px;
  background: #f4f6fa;
  border-radius: 8px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.04);
  cursor: pointer;
  transition: box-shadow 0.2s, background 0.2s;
  display: flex;
  flex-direction: column;
  align-items: center;
  border: 2px solid transparent;
}
.stock-card.selected {
  background: #e6f0ff;
  border-color: #409eff;
  box-shadow: 0 2px 8px rgba(64,158,255,0.12);
}
.stock-symbol {
  font-weight: 700;
  font-size: 16px;
  color: #222;
}
.stock-name {
  font-size: 13px;
  color: #888;
  margin-top: 2px;
}
.stock-price {
  font-size: 14px;
  color: #409eff;
  margin-top: 4px;
}
</style>
