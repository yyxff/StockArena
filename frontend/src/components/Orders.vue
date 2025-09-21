<template>
  <div>
    <h3>Orders for {{ symbol }}</h3>
    <input v-model.number="quantity" type="number" placeholder="Quantity" />
    <button @click="placeBuy">Buy</button>
    <button @click="placeSell">Sell</button>

    <h4>Current Orders:</h4>
    <ul>
      <li v-for="(order, index) in orders" :key="index">
        {{ order.type }} - {{ order.quantity }} shares - ${{ order.price }}
      </li>
    </ul>
  </div>
</template>

<script lang="ts">
import { defineComponent, ref, watch, onMounted } from 'vue'
import { getOrders, placeOrder } from '../api'

interface Order {
  type: 'buy' | 'sell'
  quantity: number
  price: number
}

export default defineComponent({
  props: {
    symbol: { type: String, required: true }
  },
  setup(props) {
    const orders = ref<Order[]>([])
    const quantity = ref(0)

    const fetchOrders = async () => {
      const res = await getOrders(props.symbol)
      orders.value = res.data
    }

    const placeBuy = async () => {
      if (quantity.value <= 0) return
      await placeOrder(props.symbol, 'buy', quantity.value)
      fetchOrders()
    }

    const placeSell = async () => {
      if (quantity.value <= 0) return
      await placeOrder(props.symbol, 'sell', quantity.value)
      fetchOrders()
    }

    watch(() => props.symbol, fetchOrders)
    onMounted(fetchOrders)

    return { orders, quantity, placeBuy, placeSell }
  }
})
</script>