import { defineStore } from 'pinia'
import { getAllSections, getSectionDetail } from '@/api/section'

export const useSectionsStore = defineStore('sections', {
  state: () => ({
    sections: [],
    currentSection: null,
    loading: false,
    stats: {
      total: 0,
      normal: 0,
      warning: 0,
      alarm: 0
    }
  }),

  actions: {
    async fetchSections() {
      this.loading = true
      try {
        const res = await getAllSections()
        this.sections = res.data || []
        this.calculateStats()
      } finally {
        this.loading = false
      }
    },

    async fetchSectionDetail(sectionId) {
      this.loading = true
      try {
        const res = await getSectionDetail(sectionId)
        this.currentSection = res.data
        return res.data
      } finally {
        this.loading = false
      }
    },

    calculateStats() {
      this.stats.total = this.sections.length
      this.stats.normal = this.sections.filter(s => s.status === 'NORMAL').length
      this.stats.warning = this.sections.filter(s => s.status === 'WARNING').length
      this.stats.alarm = this.sections.filter(s => s.status === 'ALARM').length
    },

    updateSectionStatus(sectionId, status) {
      const section = this.sections.find(s => s.sectionId === sectionId)
      if (section) {
        section.status = status
        this.calculateStats()
      }
    }
  }
})
