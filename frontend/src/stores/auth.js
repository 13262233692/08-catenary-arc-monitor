import { defineStore } from 'pinia'
import { login as loginApi, getUserInfo } from '@/api/auth'
import { ElMessage } from 'element-plus'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    username: '',
    realName: '',
    role: '',
    bureauId: '',
    stationId: '',
    workAreaId: ''
  }),

  getters: {
    isLoggedIn: (state) => !!state.token
  },

  actions: {
    async login(loginForm) {
      const res = await loginApi(loginForm)
      const { token, username, role, bureauId, stationId, workAreaId } = res.data
      this.token = token
      this.username = username
      this.role = role
      this.bureauId = bureauId || ''
      this.stationId = stationId || ''
      this.workAreaId = workAreaId || ''
      localStorage.setItem('token', token)
      return res
    },

    async fetchUserInfo() {
      try {
        const res = await getUserInfo()
        const { username, realName, role, bureauId, stationId, workAreaId } = res.data
        this.username = username
        this.realName = realName
        this.role = role
        this.bureauId = bureauId || ''
        this.stationId = stationId || ''
        this.workAreaId = workAreaId || ''
      } catch {
        this.logout()
      }
    },

    logout() {
      this.token = ''
      this.username = ''
      this.realName = ''
      this.role = ''
      this.bureauId = ''
      this.stationId = ''
      this.workAreaId = ''
      localStorage.removeItem('token')
    }
  }
})
