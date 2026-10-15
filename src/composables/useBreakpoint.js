import { onBeforeUnmount, ref } from 'vue'

/** Breakpoint: mobile < 768px <= tablet < 1200px <= desktop. */
export function useBreakpoint() {
  const mobileQuery = window.matchMedia('(max-width: 767.98px)')
  const tabletQuery = window.matchMedia('(min-width: 768px) and (max-width: 1199.98px)')
  const isMobile = ref(mobileQuery.matches)
  const isTablet = ref(tabletQuery.matches)

  const update = () => {
    isMobile.value = mobileQuery.matches
    isTablet.value = tabletQuery.matches
  }
  mobileQuery.addEventListener('change', update)
  tabletQuery.addEventListener('change', update)
  onBeforeUnmount(() => {
    mobileQuery.removeEventListener('change', update)
    tabletQuery.removeEventListener('change', update)
  })

  return { isMobile, isTablet }
}
