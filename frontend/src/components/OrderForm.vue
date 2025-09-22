<template>
  <div class="order-form-card-flex">
    <div
        class="order-side-card buy"
        :class="{ expanded: expanded === 'buy' }"
        @click="expand('buy')"
    >
      <div class="side-title">Buy</div>
      <template v-if="expanded === 'buy'">
        <div class="order-input-group">
          <label>Quantity</label>
          <input type="number" v-model.number="quantity" min="1" placeholder="Enter quantity" />
        </div>
        <div class="order-input-group">
          <label>Price</label>
          <input type="number" v-model.number="price" min="0" step="0.01" placeholder="Enter price" />
        </div>
        <button class="submit-btn buy" @click.stop="submitOrder('buy')">Place Buy Order</button>
      </template>
    </div>
    <div
        class="order-side-card sell"
        :class="{ expanded: expanded === 'sell' }"
        @click="expand('sell')"
    >
      <div class="side-title">Sell</div>
      <template v-if="expanded === 'sell'">
        <div class="order-input-group">
          <label>Quantity</label>
          <input type="number" v-model.number="quantity" min="1" placeholder="Enter quantity" />
        </div>
        <div class="order-input-group">
          <label>Price</label>
          <input type="number" v-model.number="price" min="0" step="0.01" placeholder="Enter price" />
        </div>
        <button class="submit-btn sell" @click.stop="submitOrder('sell')">Place Sell Order</button>
      </template>
    </div>
  </div>
</template>

<script lang="ts">
import { defineComponent, ref } from 'vue'

export default defineComponent({
  emits: ['submit'],
  setup(_, { emit }) {
    const expanded = ref<'buy' | 'sell' | null>(null)
    const quantity = ref<number>(1)
    const price = ref<number>(0)
    const expand = (side: 'buy' | 'sell') => {
      expanded.value = expanded.value === side ? null : side
    }
    const submitOrder = (type: 'buy' | 'sell') => {
      if (quantity.value > 0 && price.value > 0) {
        emit('submit', { type, quantity: quantity.value, price: price.value })
      }
    }
    return { expanded, quantity, price, expand, submitOrder }
  }
})
</script>

<style scoped>
.order-form-card-flex {
  display: flex;
  gap: 24px;
  width: 100%;
  justify-content: center;
  align-items: stretch;
}
.order-side-card {
  flex: 1;
  background: #23242b;
  border-radius: 16px;
  box-shadow: 0 2px 16px rgba(0,0,0,0.10);
  color: #fff;
  padding: 32px 0 32px 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  cursor: pointer;
  transition: flex 0.3s, background 0.2s, box-shadow 0.2s;
  min-width: 0;
  position: relative;
}
.order-side-card.buy {
  background: linear-gradient(135deg, #1e2e2b 0%, #1a3e2a 100%);
  border: 2px solid #2ecc71;
}
.order-side-card.sell {
  background: linear-gradient(135deg, #2e1e2b 0%, #3e1a2a 100%);
  border: 2px solid #e74c3c;
}
.order-side-card .side-title {
  font-size: 22px;
  font-weight: 700;
  margin-bottom: 12px;
  letter-spacing: 1px;
}
.order-side-card.expanded {
  flex: 2;
  box-shadow: 0 4px 32px rgba(64,158,255,0.18);
  z-index: 2;
}
.order-side-card:not(.expanded) {
  opacity: 0.7;
}
.order-side-card.expanded {
  cursor: default;
}
.order-input-group {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  margin-bottom: 16px;
  width: 80%;
  max-width: 220px;
}
.order-input-group label {
  font-size: 14px;
  color: #bfc4d1;
  margin-bottom: 6px;
}
.order-input-group input {
  width: 100%;
  padding: 10px 12px;
  border-radius: 8px;
  border: 1px solid #31344b;
  background: #181920;
  color: #fff;
  font-size: 16px;
  outline: none;
  transition: border 0.2s;
}
.order-input-group input:focus {
  border: 1.5px solid #409eff;
}
.submit-btn {
  width: 80%;
  max-width: 220px;
  padding: 14px 0;
  font-size: 18px;
  font-weight: 600;
  border: none;
  border-radius: 10px;
  margin-top: 8px;
  cursor: pointer;
  box-shadow: 0 2px 8px rgba(64,158,255,0.10);
  transition: background 0.2s;
}
.submit-btn.buy {
  background: linear-gradient(90deg, #2ecc71 0%, #27ae60 100%);
  color: #fff;
}
.submit-btn.buy:hover {
  background: linear-gradient(90deg, #27ae60 0%, #2ecc71 100%);
}
.submit-btn.sell {
  background: linear-gradient(90deg, #e74c3c 0%, #c0392b 100%);
  color: #fff;
}
.submit-btn.sell:hover {
  background: linear-gradient(90deg, #c0392b 0%, #e74c3c 100%);
}
</style>
