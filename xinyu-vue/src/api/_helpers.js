import request from '../utils/request'

/**
 * 统一拆包后端响应：
 *   { code, message, data } → { ok, data, message }
 * 网络层错误抛出（保留 userMessage 由拦截器附加）。
 */
export async function unwrap(promise) {
  const res = await promise
  const body = res?.data
  if (body && body.code === 200) {
    return { ok: true, data: body.data, message: body.message }
  }
  return { ok: false, message: body?.message || '请求失败', data: body?.data }
}

export { request }
