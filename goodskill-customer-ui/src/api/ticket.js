import axios from 'axios'

const ticketClient = axios.create({
  baseURL: import.meta.env.VITE_TICKET_API_BASE_URL || '/ticket-api',
  timeout: 10000
})

const orderClient = axios.create({
  baseURL: import.meta.env.VITE_ORDER_API_BASE_URL || '/order-api',
  timeout: 10000
})

const getOrCreateUserId = () => {
  let userId = localStorage.getItem('ticket_user_id')
  if (!userId) {
    userId = `web-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
    localStorage.setItem('ticket_user_id', userId)
  }
  return userId
}

export const ticketApi = {
  getUserId: getOrCreateUserId,
  listSessions: () => ticketClient.get('/api/ticket-sessions').then(response => response.data),
  getSession: id => ticketClient.get(`/api/ticket-sessions/${id}`).then(response => response.data),
  reserve: (id, payload) => ticketClient.post(`/api/ticket-sessions/${id}/reservations`, payload, {
    headers: { 'X-User-Id': getOrCreateUserId() }
  }).then(response => response.data),
  getOrder: (reservationId, userId = getOrCreateUserId()) => orderClient
    .get(`/api/orders/by-reservation/${reservationId}`, { headers: { 'X-User-Id': userId } })
    .then(response => response.data)
}

export const reservationHistory = {
  list() {
    try {
      return JSON.parse(localStorage.getItem('ticket_reservations') || '[]')
    } catch {
      return []
    }
  },
  add(item) {
    const current = this.list().filter(entry => entry.reservationId !== item.reservationId)
    localStorage.setItem('ticket_reservations', JSON.stringify([item, ...current].slice(0, 30)))
  }
}
