/**
 * Lỗi nghiệp vụ của "backend chạy trên trình duyệt". Giữ đúng dạng lỗi API cũ
 * ({ status, code, message, fieldErrors }) để các màn hình không phải sửa cách hiển thị lỗi.
 */
export class AppError extends Error {
  constructor(code, message, { status = 400, errors = [] } = {}) {
    super(message)
    this.name = 'AppError'
    this.code = code
    this.status = status
    this.errors = errors
  }

  /** { field: message } để gắn lỗi validate vào từng ô của form. */
  get fieldErrors() {
    return Object.fromEntries(this.errors.map((e) => [e.field, e.message]))
  }
}

export const notFound = (what, id) => new AppError('RESOURCE_NOT_FOUND', `Không tìm thấy ${what}${id != null ? ` #${id}` : ''}`, { status: 404 })
export const invalid = (message, field) => new AppError('VALIDATION_ERROR', message, {
  status: 400, errors: field ? [{ field, message }] : [],
})
export const conflict = (code, message) => new AppError(code, message, { status: 409 })
