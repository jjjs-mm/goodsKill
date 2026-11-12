<template>
  <div class="min-h-screen bg-[var(--bg-primary)]">
    <TicketHeader />
    <main class="mx-auto max-w-6xl px-4 py-12 sm:px-6 lg:px-8">
      <router-link to="/" class="mb-8 inline-flex items-center gap-2 text-sm text-[var(--text-muted)] hover:text-[var(--text-primary)]">← 返回场次列表</router-link>

      <div v-if="loading" class="glass-card h-[520px] animate-pulse bg-white/5"></div>
      <div v-else-if="error" class="glass-card p-12 text-center text-red-400">{{ error }}</div>
      <div v-else class="grid gap-8 lg:grid-cols-[1.15fr_.85fr]">
        <section class="overflow-hidden rounded-[2rem] border border-[var(--border-color)] bg-[var(--bg-secondary)]">
          <div class="relative h-72 overflow-hidden">
            <img src="https://images.unsplash.com/photo-1501386761578-eac5c94b800a?w=1200&h=700&fit=crop" :alt="session.name" class="h-full w-full object-cover" />
            <div class="absolute inset-0 bg-gradient-to-t from-[#12121a] via-transparent to-transparent"></div>
            <div class="absolute bottom-6 left-6 right-6">
              <span class="rounded-full bg-emerald-400 px-3 py-1 text-xs font-bold text-emerald-950">限量开售</span>
              <h1 class="mt-3 text-3xl font-bold text-white">{{ session.name }}</h1>
            </div>
          </div>
          <div class="grid gap-5 p-7 sm:grid-cols-2">
            <InfoItem label="演出场次" :value="`#${session.sessionId}`" />
            <InfoItem label="开售时间" :value="formatDate(session.startTime)" />
            <InfoItem label="截止时间" :value="formatDate(session.endTime)" />
            <InfoItem label="实时余票" :value="`${displayStock} 张`" highlight />
          </div>
          <div class="mx-7 mb-7 rounded-2xl border border-violet-400/20 bg-violet-400/10 p-5 text-sm leading-6 text-[var(--text-secondary)]">
            提交后将通过 Redis Lua 原子预扣库存，订单由 RabbitMQ 异步创建。请在支付时间内完成后续操作，超时后库存自动释放。
          </div>
        </section>

        <aside class="glass-card h-fit p-7 lg:sticky lg:top-24">
          <p class="text-sm font-semibold uppercase tracking-[0.2em] text-violet-400">Reservation</p>
          <h2 class="mt-2 text-2xl font-bold text-[var(--text-primary)]">确认抢票信息</h2>
          <div class="mt-7 space-y-5">
            <label class="block">
              <span class="mb-2 block text-sm text-[var(--text-secondary)]">购票手机号</span>
              <input v-model.trim="phone" maxlength="11" class="input-dark w-full" placeholder="请输入11位手机号" />
            </label>
            <div>
              <span class="mb-2 block text-sm text-[var(--text-secondary)]">购票数量</span>
              <div class="flex items-center justify-between rounded-2xl border border-[var(--border-color)] bg-white/5 p-2">
                <button @click="quantity = Math.max(1, quantity - 1)" class="h-10 w-10 rounded-xl text-xl text-[var(--text-primary)] hover:bg-white/10">−</button>
                <span class="text-lg font-bold text-[var(--text-primary)]">{{ quantity }}</span>
                <button @click="quantity = Math.min(4, quantity + 1)" class="h-10 w-10 rounded-xl text-xl text-[var(--text-primary)] hover:bg-white/10">＋</button>
              </div>
              <p class="mt-2 text-xs text-[var(--text-muted)]">单个账号每场限购4张</p>
            </div>
          </div>
          <div class="my-7 border-t border-dashed border-[var(--border-color)]"></div>
          <div class="flex items-end justify-between">
            <span class="text-sm text-[var(--text-muted)]">应付金额</span>
            <div class="text-right"><span class="text-sm text-[var(--text-muted)]">¥</span><span class="text-3xl font-bold text-[var(--text-primary)]">{{ totalPrice }}</span></div>
          </div>
          <button :disabled="submitting" @click="reserve" class="btn-primary mt-7 w-full py-4 disabled:cursor-not-allowed disabled:opacity-50">
            {{ submitting ? '正在锁定余票…' : '确认抢票' }}
          </button>
          <p v-if="submitError" class="mt-4 rounded-xl bg-red-400/10 p-3 text-sm text-red-400">{{ submitError }}</p>
          <div v-if="success" class="mt-4 rounded-2xl border border-emerald-400/20 bg-emerald-400/10 p-4">
            <div class="font-semibold text-emerald-400">库存预扣成功，订单创建中</div>
            <div class="mt-1 break-all font-mono text-xs text-[var(--text-muted)]">{{ success.reservationId }}</div>
            <router-link to="/orders" class="mt-3 inline-block text-sm font-semibold text-emerald-300">查看订单状态 →</router-link>
          </div>
        </aside>
      </div>
    </main>
  </div>
</template>

<script setup>
import { computed, defineComponent, h, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import TicketHeader from '../components/TicketHeader.vue'
import { reservationHistory, ticketApi } from '../api/ticket'

const InfoItem = defineComponent({
  props: { label: String, value: String, highlight: Boolean },
  setup: props => () => h('div', { class: 'rounded-2xl bg-white/5 p-4' }, [
    h('div', { class: 'text-xs text-[var(--text-muted)]' }, props.label),
    h('div', { class: ['mt-2 font-semibold', props.highlight ? 'text-emerald-400' : 'text-[var(--text-primary)]'] }, props.value)
  ])
})

const route = useRoute()
const session = ref({})
const loading = ref(true)
const error = ref('')
const phone = ref('13800138000')
const quantity = ref(1)
const submitting = ref(false)
const submitError = ref('')
const success = ref(null)
const displayStock = computed(() => session.value.availableStock ?? session.value.configuredStock ?? 0)
const totalPrice = computed(() => (Number(session.value.price || 0) * quantity.value).toLocaleString('zh-CN', { minimumFractionDigits: 2 }))
const formatDate = value => value ? new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value)) : '--'

async function reserve() {
  if (!/^1\d{10}$/.test(phone.value)) {
    submitError.value = '请输入正确的11位手机号'
    return
  }
  submitting.value = true
  submitError.value = ''
  success.value = null
  try {
    const result = await ticketApi.reserve(route.params.id, { userPhone: phone.value, quantity: quantity.value })
    success.value = result
    reservationHistory.add({
      reservationId: result.reservationId,
      userId: ticketApi.getUserId(),
      sessionId: result.sessionId,
      sessionName: session.value.name,
      quantity: quantity.value,
      expiresAt: result.expiresAt,
      createdAt: new Date().toISOString()
    })
    session.value.availableStock = result.remainingStock
  } catch (requestError) {
    submitError.value = requestError.response?.data?.message || requestError.response?.data?.detail || '抢票失败，请稍后重试'
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  try {
    session.value = await ticketApi.getSession(route.params.id)
  } catch (requestError) {
    error.value = requestError.response?.data?.message || '场次不存在或服务暂不可用'
  } finally {
    loading.value = false
  }
})
</script>
