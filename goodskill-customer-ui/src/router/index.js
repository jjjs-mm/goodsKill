import {createRouter, createWebHistory} from 'vue-router'
import LoginView from '../views/LoginView.vue'

const routes = [
  {
    path: '/',
    name: 'ticketSessions',
    component: () => import('../views/TicketSessionsView.vue')
  },
  {
    path: '/tickets/:id',
    name: 'ticketReservation',
    component: () => import('../views/TicketReservationView.vue')
  },
  {
    path: '/orders',
    name: 'ticketOrders',
    component: () => import('../views/TicketOrdersView.vue')
  },
  {
    path: '/mall',
    name: 'home',
    component: () => import('../views/HomeView.vue')
  },
  {
    path: '/login',
    name: 'login',
    component: LoginView
  },
  {
    path: '/goods/:id',
    name: 'goodsDetail',
    component: () => import('../views/GoodsDetailView.vue')
  },
  {
    path: '/legacy-orders',
    name: 'orders',
    component: () => import('../views/OrdersView.vue')
  },
  {
    path: '/order/:id',
    name: 'orderDetail',
    component: () => import('../views/OrderDetailView.vue')
  },
  {
    path: '/profile',
    name: 'profile',
    component: () => import('../views/ProfileView.vue')
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫，检查登录状态
router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('access_token')
  const publicPages = ['ticketSessions', 'ticketReservation', 'ticketOrders', 'login']
  if (!publicPages.includes(to.name) && !token) {
    next({ name: 'login' })
  } else {
    next()
  }
})

export default router
