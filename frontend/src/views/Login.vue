<template>
  <div>
    <h2>Login</h2>
    <input v-model="username" placeholder="Username" />
    <input v-model="password" type="password" placeholder="Password" />
    <button @click="handleLogin">Login</button>
    <router-link to="/register">Register</router-link>
  </div>
</template>

<script lang="ts">
import { defineComponent, ref } from 'vue'
import { login } from '../api'
import { useUserStore } from '../store/user'
import { useRouter } from 'vue-router'

export default defineComponent({
  setup() {
    const username = ref('')
    const password = ref('')
    const userStore = useUserStore()
    const router = useRouter()

    const handleLogin = async () => {
      const res = await login({ username: username.value, password: password.value })
      userStore.setUser({ token: res.data.token, username: username.value })
      router.push('/dashboard')
    }

    return { username, password, handleLogin }
  }
})
</script>