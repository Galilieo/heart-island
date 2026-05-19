// 话题列表接口。只有一个查询，没有 CRUD（话题由后端运营维护）。
import { request, unwrap } from './_helpers'

export const topicApi = {
  list() {
    return unwrap(request.get('/topic/list'))
  }
}
