// api 层共用助手。所有具体接口都通过 unwrap() 把后端 {code,message,data}
// 拆成 {ok,data,message}，view/store 里就不用每次判断 code === 200。
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

/**
 * 在 unwrap 基础上把带 HTTP 响应体（如 400 / 404 / 409 / 429）的业务错误转成 { ok: false }，
 * 无响应的网络或超时异常仍保持抛出。
 */
export async function unwrapHttp(promise) {
  try {
    return await unwrap(promise)
  } catch (error) {
    if (error?.response) {
      return {
        ok: false,
        status: error.response.status,
        message: error.userMessage || error.response.data?.message || '请求失败',
        data: error.response.data?.data ?? null
      }
    }
    throw error
  }
}

export { request }
