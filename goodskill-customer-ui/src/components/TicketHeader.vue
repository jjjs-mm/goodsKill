<template>
  <header class="sticky top-0 z-40 border-b border-[var(--border-color)] bg-[var(--bg-primary)]/90 backdrop-blur-xl">
    <div class="mx-auto flex h-16 max-w-7xl items-center justify-between px-4 sm:px-6 lg:px-8">
      <router-link to="/" class="flex items-center gap-3">
        <div class="flex h-10 w-10 items-center justify-center rounded-2xl bg-gradient-to-br from-violet-500 to-cyan-400 text-lg font-bold text-white shadow-lg shadow-violet-500/20">T</div>
        <div>
          <div class="font-display text-lg font-bold text-[var(--text-primary)]">LIVE TICKET</div>
          <div class="text-[10px] tracking-[0.22em] text-[var(--text-muted)]">CONCERT ACCESS</div>
        </div>
      </router-link>
      <nav class="flex items-center gap-2 text-sm">
        <router-link to="/" class="rounded-xl px-4 py-2 text-[var(--text-secondary)] transition hover:bg-white/5 hover:text-[var(--text-primary)]">演出场次</router-link>
        <router-link to="/orders" class="rounded-xl px-4 py-2 text-[var(--text-secondary)] transition hover:bg-white/5 hover:text-[var(--text-primary)]">我的订单</router-link>
        <span class="hidden rounded-full border border-[var(--border-color)] px-3 py-1.5 font-mono text-xs text-[var(--text-muted)] sm:block">{{ shortUserId }}</span>
      </nav>
    </div>
  </header>
</template>

<script setup>
import { computed } from 'vue'
import { ticketApi } from '../api/ticket'

const shortUserId = computed(() => {
  const value = ticketApi.getUserId()
  return value.length > 15 ? `${value.slice(0, 12)}…` : value
})
</script>
