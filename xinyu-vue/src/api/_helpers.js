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

export { request }
