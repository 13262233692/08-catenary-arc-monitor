import { computed } from 'vue'
import { storeToRefs } from 'pinia'
import { useAuthStore } from '@/stores/auth'

export function useAuth() {
  const authStore = useAuthStore()
  const { token, username, role, bureauId, stationId, workAreaId, isLoggedIn, userRole } = storeToRefs(authStore)

  const hasRole = (r) => authStore.role === r

  const isBureauUser = computed(() => authStore.role === 'bureau')
  const isStationUser = computed(() => authStore.role === 'station')
  const isWorkAreaUser = computed(() => authStore.role === 'work_area')

  return {
    token,
    username,
    role,
    bureauId,
    stationId,
    workAreaId,
    isLoggedIn,
    userRole,
    login: authStore.login,
    logout: authStore.logout,
    loadFromStorage: authStore.loadFromStorage,
    hasRole,
    isBureauUser,
    isStationUser,
    isWorkAreaUser
  }
}
