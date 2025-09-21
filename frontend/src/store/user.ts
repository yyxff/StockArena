import { defineStore } from 'pinia'

interface UserState {
    token: string
    username: string
    balance: number
    holdings: Record<string, number>
}

export const useUserStore = defineStore('user', {
    state: (): UserState => ({
        token: '',
        username: '',
        balance: 0,
        holdings: {}
    }),
    actions: {
        setUser(data: Partial<UserState>) {
            Object.assign(this, data)
        },
        logout() {
            this.token = ''
            this.username = ''
            this.balance = 0
            this.holdings = {}
        }
    }
})