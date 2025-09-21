export interface Stock {
    symbol: string
    name: string
    price: number
}

export interface KLineData {
    date: string
    open: number
    high: number
    low: number
    close: number
    volume: number
}