import assert from 'node:assert/strict'
import { beforeEach, test } from 'node:test'
import { fileURLToPath } from 'node:url'
import { createServer } from 'vite'
import { AxiosError } from 'axios'

// 通过 Vite 加载真实请求层，适配 import.meta.env；只替换 HTTP 传输和浏览器对象。
const server = await createServer({
  root: fileURLToPath(new URL('..', import.meta.url)),
  configFile: false,
  optimizeDeps: { noDiscovery: true, include: [] },
  server: { middlewareMode: true, hmr: false, ws: false },
})
let request
try {
  request = (await server.ssrLoadModule('/src/utils/request.js')).default
} finally {
  await server.close()
}

let storage
beforeEach(() => {
  storage = new Map([
    ['token', 'fixture-token'],
    ['loginUser', '{"id":"11"}'],
  ])
  globalThis.localStorage = {
    getItem: (key) => storage.get(key) ?? null,
    removeItem: (key) => storage.delete(key),
  }
  globalThis.window = { location: { pathname: '/home', href: '/home' } }
})

function failWith(status, message) {
  return (config) =>
    Promise.reject(
      new AxiosError('HTTP fixture', 'ERR_BAD_RESPONSE', config, null, {
        status,
        statusText: '',
        data: { code: status, message, data: null },
        headers: {},
        config,
      }),
    )
}

test('HTTP 401 clears the saved session and returns to login regardless of message wording', async () => {
  await assert.rejects(
    request.get('/mood/list', { adapter: failWith(401, 'Session expired') }),
    (error) => {
      assert.equal(error.userMessage, 'Session expired')
      return true
    },
  )
  assert.equal(storage.has('token'), false)
  assert.equal(storage.has('loginUser'), false)
  assert.equal(window.location.href, '/')
})

test('HTTP 403 keeps a valid session and exposes the permission message', async () => {
  await assert.rejects(
    request.get('/admin/user/page', { adapter: failWith(403, '权限不足') }),
    (error) => {
      assert.equal(error.userMessage, '权限不足')
      return true
    },
  )
  assert.equal(storage.get('token'), 'fixture-token')
  assert.equal(window.location.href, '/home')
})

test('legacy HTTP 200 login errors still clear the session during a rolling deployment', async () => {
  await request.get('/mood/list', {
    adapter: async (config) => ({
      status: 200,
      data: { code: 500, message: '请先登录', data: null },
      headers: {},
      config,
    }),
  })
  assert.equal(storage.has('token'), false)
  assert.equal(window.location.href, '/')
})

test('HTTP 401 on the login page clears stale credentials without navigating again', async () => {
  window.location = { pathname: '/', href: '/?notice=expired' }
  await assert.rejects(request.get('/mood/list', { adapter: failWith(401, '请先登录') }))
  assert.equal(storage.has('token'), false)
  assert.equal(window.location.href, '/?notice=expired')
})

test('server errors keep the session and expose the existing error message', async () => {
  await assert.rejects(
    request.get('/mood/list', { adapter: failWith(500, '服务异常') }),
    (error) => {
      assert.equal(error.userMessage, '服务异常')
      return true
    },
  )
  assert.equal(storage.get('token'), 'fixture-token')
})
