/** Đọc giá trị thực của một biến CSS (canvas / ECharts không hiểu var()). Gọi lại sau khi đổi giao diện để lấy giá trị mới. */
export const cssVar = (name) => getComputedStyle(document.documentElement).getPropertyValue(name).trim()

/** "#rrggbb" + độ trong suốt -> "rgba(r, g, b, a)"; giá trị khác (rgba, tên màu) trả nguyên. */
export function withAlpha(color, alpha) {
  const m = /^#([0-9a-f]{6})$/i.exec(color)
  if (!m) return color
  const n = parseInt(m[1], 16)
  return `rgba(${(n >> 16) & 255}, ${(n >> 8) & 255}, ${n & 255}, ${alpha})`
}
