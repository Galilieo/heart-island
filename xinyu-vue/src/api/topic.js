import { request, unwrap } from './_helpers'

export const topicApi = {
  list() {
    return unwrap(request.get('/topic/list'))
  }
}
