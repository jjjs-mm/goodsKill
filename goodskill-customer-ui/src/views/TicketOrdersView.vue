<template>
  <div class="min-h-screen bg-[var(--bg-primary)]">
    <TicketHeader />
    <main class="mx-auto max-w-6xl px-4 py-14 sm:px-6 lg:px-8">
      <div class="mb-9 flex flex-col justify-between gap-4 sm:flex-row sm:items-end">
        <div>
          <p class="text-sm font-semibold uppercase tracking-[0.22em] text-violet-400">Order tracking</p>
          <h1 class="mt-2 text-3xl font-bold text-[var(--text-primary)]">我的票务订单</h1>
          <p class="mt-2 text-[var(--text-muted)]">自动追踪异步下单与超时关单状态</p>
        </div>
        <button :disabled="refreshing" @click="loadOrders" class="btn-secondary px-5 py-2.5 text-sm disabled:opacity-50">{{ refreshing ? '刷新中…' : '刷新状态' }}</button>
      </div>

      <div v-if="history.length === 0" class="glass-card px-6 py-20 text-center">
        <div class="mx-auto flex h-20 w-20 items-center justify-center rounded-full bg-violet-400/10 text-3xl">🎫</div>
        <h2 class="mt-6 text-xl font-bold text-[var(--text-primary)]">还没有抢票记录</h2>
        <p class="mt-2 text-[var(--text-muted)]">选择一个演唱会场次，完成首次抢票体验。</p>
        <router-link to="/" class="btn-primary mt-6 inline-block">浏览演出场次</router-link>
      </div>

      <div v-else class="space-y-5">
        <article v-for="item in orders" :key="item.reservationId" class="glass-card p-6">
          <div class="flex flex-col gap-6 lg:flex-row lg:items-center lg:justify-between">
            <div class="flex min-w-0 items-start gap-4">
              <div class="flex h-14 w-14 flex-none items-center justify-center rounded-2xl bg-gradient-to-br from-violet-500/20 to-cyan-400/20 text-2xl">♪</div>
              <div class="min-w-0">
                <h2 class="truncate text-lg font-bold text-[var(--text-primary)]">{{ item.sessionName || `演出场次 #${item.sessionId}` }}</h2>
                <p class="mt-1 text-sm text-[var(--text-muted)]">{{ item.quantity || item.order?.quantity || 1 }} 张 · {{ formatDate(item.createdAt) }}</p>
                <p class="mt-2 truncate font-mono text-xs text-[var(--text-muted)]">预约号 {{ item.reservationId }}</p>
              </div>
            </div>
            <div class="flex flex-col gap-3 sm:flex-row sm:items-center lg:justify-end">
              <div v-if="item.order" class="text-sm text-[var(--text-secondary)] sm:text-right">
                <div>订单号 {{ item.order.id }}</div>
                <div v-if="item.order.expiresAt" class="mt-1 text-xs text-[var(--text-muted)]">支付截止 {{ formatDate(item.order.expiresAt) }}</div>
              </div>
              <span :class="statusClass(item)" class="inline-flex w-fit items-center gap-2 rounded-full px-4 py-2 text-sm font-semibold">
                <span class="h-2 w-2 rounded-full bg-current"></span>{{ statusText(item) }}
              </span>
            </div>
          </div>
          <div v-if="item.error && item.error !== 'CREATING'" class="mt-4 rounded-xl bg-red-400/10 p-3 text-sm text-red-400">{{ item.error }}</div>
          <div v-else-if="item.order?.cancellationReason" class="mt-4 rounded-xl bg-white/5 p-3 text-sm text-[var(--text-muted)]">取消原因：{{ item.order.cancellationReason }}</div>
        </article>
      </div>

      <div v-if="history.length" class="mt-8 rounded-2xl border border-[var(--border-color)] bg-white/[.03] p-4 text-center text-xs text-[var(--text-muted)]">
        页面每3秒自动查询一次订单状态；刷新页面后仍会保留最近30条预约记录。
      </div>
    </main>
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import TicketHeader from '../components/TicketHeader.vue'
import { reservationHistory, ticketApi } from '../api/ticket'

const history = ref(reservationHistory.list())
const orders = ref(history.value.map(item => ({ ...item, error: 'CREATING' })))
const refreshing = ref(false)
let timer

const formatDate = value => value ? new Intl.DateTimeFormat('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit' }).format(new Date(value)) : '--'
const statusText = item => {
  if (!item.order) return item.error === 'CREATING' ? '订单创建中' : '查询失败'
  return ({ 1: '待支付', 2: '已支付', 3: '已取消' })[item.order.status] || item.order.stateDesc || '处理中'
}
const statusClass = item => {
  if (!item.order) return item.error === 'CREATING' ? 'bg-cyan-400/10 text-cyan-400' : 'bg-red-400/10 text-red-400'
  return ({ 1: 'bg-amber-400/10 text-amber-400', 2: 'bg-emerald-400/10 text-emerald-400', 3: 'bg-slate-400/10 text-slate-400' })[item.order.status] || 'bg-cyan-400/10 text-cyan-400'
}

async function loadOrders() {
  refreshing.value = true
  orders.value = await Promise.all(history.value.map(async item => {
    try {
      return { ...item, order: await ticketApi.getOrder(item.reservationId, item.userId), error: '' }
    } catch (requestError) {
      const status = requestError.response?.status
      return { ...item, order: null, error: status === 404 ? 'CREATING' : '订单状态查询失败' }
    }
  }))
  refreshing.value = false
}

onMounted(() => {
  loadOrders()
  timer = window.setInterval(loadOrders, 3000)
})
onBeforeUnmount(() => window.clearInterval(timer))
</script>
