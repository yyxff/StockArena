<template>
  <div>
    <h3>Stocks</h3>
    <ul>
      <li v-for="stock in stocks" :key="stock.symbol" @click="$emit('select', stock.symbol)">
        {{ stock.symbol }} - {{ stock.name }} - ${{ stock.price }}
      </li>
    </ul>
  </div>
</template>

<script lang="ts">
import { defineComponent, ref, onMounted } from 'vue'
import { getStocks } from '../api'
import type { Stock } from '../types'

export default defineComponent({
  setup() {
    const stocks = ref<Stock[]>([])
    onMounted(async () => {
      const res = await getStocks()
      stocks.value = res.data
    })
    return { stocks }
  }
})
</script>