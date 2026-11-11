/** 保存已被确认后，刷新失败只作为附加结果返回，不能把已完成的保存改报失败。 */
export async function refreshAfterSave(...refreshers) {
  const results = await Promise.allSettled(refreshers.map(refresh => refresh()))
  return results.some(result => result.status === 'rejected' || !result.value?.ok)
}
