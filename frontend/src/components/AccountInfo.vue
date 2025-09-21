<template>
  <div>
    <h3>Account Info</h3>
    <p>Username: {{ user.username }}</p>
    <p>Balance: ${{ user.balance.toFixed(2) }}</p>
    <h4>Holdings:</h4>
    <ul>
      <li v-for="(qty, symbol) in user.holdings" :key="symbol">
        {{ symbol }}: {{ qty }} shares
      </li>
    </ul>
  </div>
</template>

<script lang="ts">
import { defineComponent, onMounted } from 'vue'
import { useUserStore } from '../store/user'
import { getAccountInfo } from '../api'

export default defineComponent({
  setup() {
    const user = useUserStore()

    const fetchAccount = async () => {
      const res = await getAccountInfo()
      user.setUser({ balance: res.data.balance, holdings: res.data.holdings })
    }

    onMounted(fetchAccount)

    return { user }
  }
})
</script>