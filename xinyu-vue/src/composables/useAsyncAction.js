import { ref } from 'vue'
import { useToast } from './useToast'

/**
 * 把"loading + try/catch + toast"三件套包成一个 composable。
 *
 * @param {(...args: any[]) => Promise<any>} fn 实际执行的异步函数。
 *        返回值约定 { ok: boolean, data?, message? }，或抛错。
 * @param {object} options
 * @param {string|((data)=>string)} [options.successMsg]
 * @param {string} [options.errorMsg] 默认错误兜底文案
 * @param {boolean} [options.silent] true 则不弹 toast
 */
export function useAsyncAction(fn, options = {}) {
  const toast = useToast()
  const loading = ref(false)

  async function run(...args) {
    if (loading.value) return { ok: false }
    loading.value = true

    try {
      const result = await fn(...args)

      if (result && result.ok === false) {
        if (!options.silent) {
          toast.error(result.message || options.errorMsg || '操作失败')
        }
        return result
      }

      if (!options.silent && options.successMsg) {
        const msg =
          typeof options.successMsg === 'function'
            ? options.successMsg(result?.data)
            : options.successMsg
        if (msg) toast.success(msg)
      }

      return result ?? { ok: true }
    } catch (err) {
      const msg = err?.userMessage || err?.message || options.errorMsg || '操作失败'
      if (!options.silent) toast.error(msg)
      return { ok: false, message: msg, error: err }
    } finally {
      loading.value = false
    }
  }

  return { run, loading }
}
