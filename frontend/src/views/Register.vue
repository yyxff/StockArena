<template>
  <div>
    <h2>Register</h2>
    <input v-model="username" placeholder="Username" />
    <input v-model="password" type="password" placeholder="Password" />
    <button @click="handleRegister">Register</button>
    <router-link to="/login">Login</router-link>
  </div>
</template>

<script lang="ts">
import { defineComponent, ref } from 'vue'
import { register } from '../api'
import { useRouter } from 'vue-router'

export default defineComponent({
  setup() {
    const username = ref('')
    const password = ref('')
    const router = useRouter()

    const handleRegister = async () => {
      await register({ username: username.value, password: password.value })
      router.push('/login')
    }

    return { username, password, handleRegister }
  }
})
</script>