<template>
  <div class="min-h-screen bg-[var(--bg-primary)]">
    <TicketHeader />

    <main>
      <section class="relative overflow-hidden border-b border-[var(--border-color)]">
        <div class="absolute inset-0 bg-[radial-gradient(circle_at_20%_20%,rgba(139,92,246,.24),transparent_34%),radial-gradient(circle_at_80%_30%,rgba(6,182,212,.16),transparent_30%)]"></div>
        <div class="relative mx-auto grid max-w-7xl gap-10 px-4 py-20 sm:px-6 lg:grid-cols-[1.2fr_.8fr] lg:px-8 lg:py-28">
          <div>
            <div class="mb-6 inline-flex items-center gap-2 rounded-full border border-violet-400/20 bg-violet-400/10 px-4 py-2 text-sm text-violet-300">
              <span class="h-2 w-2 animate-pulse rounded-full bg-emerald-400"></span>
              热门场次正在开售
            </div>
            <h1 class="max-w-3xl font-display text-5xl font-bold leading-tight text-[var(--text-primary)] md:text-7xl">
              把现场留给热爱，<span class="gradient-text">把抢票交给系统</span>
            </h1>
            <p class="mt-6 max-w-2xl text-lg leading-8 text-[var(--text-secondary)]">
              实时余票、原子预扣、异步下单。选择心仪场次，完成一次可追踪的演唱会抢票体验。
            </p>
            <a href="#sessions" class="btn-primary mt-8 inline-flex items-center gap-2 px-7 py-3.5">浏览开售场次 <span>→</span></a>
          </div>
          <div class="grid grid-cols-2 gap-4 self-end">
            <div class="glass-card p-6">
              <div class="text-3xl font-bold text-[var(--text-primary)]">{{ sessions.length }}</div>
              <div class="mt-1 text-sm text-[var(--text-muted)]">可浏览场次</div>
            </div>
            <div class="glass-card p-6">
              <div class="text-3xl font-bold text-emerald-400">{{ onSaleCount }}</div>
              <div class="mt-1 text-sm text-[var(--text-muted)]">正在售票</div>
            </div>
            <div class="glass-card col-span-2 p-6">
              <div class="text-sm uppercase tracking-[0.24em] text-[var(--text-muted)]">Transaction path</div>
              <div class="mt-3 flex flex-wrap items-center gap-2 text-sm text-[var(--text-secondary)]">
                <span class="rounded-lg bg-white/5 px-3 py-2">Redis Lua</span><span>→</span>
                <span class="rounded-lg bg-white/5 px-3 py-2">RabbitMQ</span><span>→</span>
                <span class="rounded-lg bg-white/5 px-3 py-2">MongoDB Order</span>
              </div>
            </div>
          </div>
        </div>
      </section>

      <section id="sessions" class="mx-auto max-w-7xl px-4 py-16 sm:px-6 lg:px-8">
        <div class="mb-9 flex flex-col justify-between gap-4 sm:flex-row sm:items-end">
          <div>
            <p class="text-sm font-semibold uppercase tracking-[0.22em] text-violet-400">On sale</p>
            <h2 class="mt-2 text-3xl font-bold text-[var(--text-primary)]">演出场次</h2>
          </div>
          <button @click="loadSessions" class="btn-secondary px-5 py-2.5 text-sm">刷新余票</button>
        </div>

        <div v-if="loading" class="grid gap-6 md:grid-cols-2 xl:grid-cols-3">
          <div v-for="item in 3" :key="item" class="glass-card h-[430px] animate-pulse bg-white/5"></div>
        </div>
        <div v-else-if="error" class="glass-card p-10 text-center">
          <div class="text-lg font-semibold text-red-400">场次加载失败</div>
          <p class="mt-2 text-[var(--text-muted)]">{{ error }}</p>
          <button @click="loadSessions" class="btn-primary mt-5">重新加载</button>
        </div>
        <div v-else-if="sessions.length === 0" class="glass-card p-12 text-center text-[var(--text-muted)]">暂无可展示的演出场次</div>
        <div v-else class="grid gap-6 md:grid-cols-2 xl:grid-cols-3">
          <article v-for="(session, index) in sessions" :key="session.sessionId" class="glass-card glass-card-hover overflow-hidden">
            <div class="relative h-52 overflow-hidden">
              <img :src="posters[index % posters.length]" :alt="session.name" class="h-full w-full object-cover transition duration-500 hover:scale-105" />
              <div class="absolute inset-0 bg-gradient-to-t from-[#0a0a0f] via-transparent to-transparent"></div>
              <span :class="saleBadge(session)" class="absolute left-4 top-4 rounded-full px-3 py-1.5 text-xs font-semibold">{{ saleText(session) }}</span>
            </div>
            <div class="p-6">
              <h3 class="min-h-14 text-xl font-bold leading-7 text-[var(--text-primary)]">{{ session.name }}</h3>
              <div class="mt-4 space-y-3 text-sm text-[var(--text-secondary)]">
                <div class="flex justify-between gap-4"><span>开售时间</span><span class="text-right text-[var(--text-primary)]">{{ formatDate(session.startTime) }}</span></div>
                <div class="flex justify-between"><span>当前余票</span><span class="font-semibold text-emerald-400">{{ displayStock(session) }} 张</span></div>
              </div>
              <div class="mt-6 flex items-end justify-between border-t border-[var(--border-color)] pt-5">
                <div><span class="text-sm text-[var(--text-muted)]">票价</span><div class="text-2xl font-bold text-[var(--text-primary)]">¥{{ formatPrice(session.price) }}</div></div>
                <router-link :to="`/tickets/${session.sessionId}`" class="btn-primary px-5 py-2.5 text-sm">立即抢票</router-link>
              </div>
            </div>
          </article>
        </div>
      </section>
    </main>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import TicketHeader from '../components/TicketHeader.vue'
import { ticketApi } from '../api/ticket'

const sessions = ref([])
const loading = ref(true)
const error = ref('')
const posters = [
  'https://images.unsplash.com/photo-1501386761578-eac5c94b800a?w=1000&h=620&fit=crop',
  'https://images.unsplash.com/photo-1492684223066-81342ee5ff30?w=1000&h=620&fit=crop',
  'https://images.unsplash.com/photo-1521337581100-8ca9a73a5f79?w=1000&h=620&fit=crop'
]

const isOnSale = session => new Date() >= new Date(session.startTime) && new Date() <= new Date(session.endTime)
const onSaleCount = computed(() => sessions.value.filter(isOnSale).length)
const displayStock = session => session.availableStock ?? session.configuredStock ?? 0
const formatPrice = price => Number(price || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2 })
const formatDate = value => value ? new Intl.DateTimeFormat('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }).format(new Date(value)) : '--'
const saleText = session => isOnSale(session) ? '售票中' : new Date() < new Date(session.startTime) ? '即将开售' : '已结束'
const saleBadge = session => isOnSale(session) ? 'bg-emerald-400/90 text-emerald-950' : 'bg-black/60 text-white backdrop-blur'

async function loadSessions() {
  loading.value = true
  error.value = ''
  try {
    sessions.value = await ticketApi.listSessions()
  } catch (requestError) {
    error.value = requestError.response?.data?.message || '请确认票务服务已在 54870 端口启动'
  } finally {
    loading.value = false
  }
}

onMounted(loadSessions)
</script>
