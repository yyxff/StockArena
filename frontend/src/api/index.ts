import axios from 'axios'

const api = axios.create({
    baseURL: 'http://localhost:8080/api', // 后端地址
    timeout: 5000
})

export const register = (data: { username: string; password: string }) => api.post('/users/register', data)
export const login = (data: { username: string; password: string }) => api.post('/users/login', data)
export const getStocks = () => api.get('/stocks')
export const getKLine = (symbol: string) => api.get(`/stocks/${symbol}/kline`)
export const getOrders = (symbol: string) => api.get(`/stocks/${symbol}/orders`)
export const placeOrder = (symbol: string, type: 'buy'|'sell', quantity: number) =>
    api.post(`/stocks/${symbol}/orders`, { type, quantity })
export const getAccountInfo = () => api.get('/account')