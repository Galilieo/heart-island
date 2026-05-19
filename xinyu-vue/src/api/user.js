import { request, unwrap } from './_helpers'

export const userApi = {
  login(payload) {
    return unwrap(request.post('/user/login', payload))
  },

  register(payload) {
    return unwrap(request.post('/user/register', payload))
  }
}
