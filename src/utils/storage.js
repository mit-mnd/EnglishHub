/** localStorage an toàn: không lỗi khi trình duyệt chặn storage (chế độ ẩn danh, iframe...). */
export const storage = {
  get(key) {
    try {
      return localStorage.getItem(key)
    } catch {
      return null
    }
  },
  set(key, value) {
    try {
      if (value == null) localStorage.removeItem(key)
      else localStorage.setItem(key, value)
    } catch {
      /* bỏ qua */
    }
  },
}
