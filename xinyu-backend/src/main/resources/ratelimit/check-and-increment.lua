-- 付费 AI 调用的多范围固定窗口限流（阶段 1b 契约 2.2）：
-- 一个脚本完成"全部检查 → 全部通过才一起加一并设置过期"，任一范围超额则一个都不加。
-- 不能用先 GET 再 INCR 或分三次 INCR 后回滚代替：检查与递增必须原子。
--
-- KEYS[1..N]      各范围的计数键，与 ARGV 中的范围一一对应
-- ARGV[1..N]      各范围的窗口额度
-- ARGV[N+1..2N]   各范围计数键的过期秒数（窗口长度 + 60 秒余量）
-- ARGV[2N+1..3N]  各范围当前窗口的结束时刻（epoch 秒）
-- ARGV[3N+1]      当前时刻（epoch 秒），仅用于换算剩余等待
--
-- 返回：允许时 {0}；
--       拒绝时 {1, 再试秒数（向上取整、至少 1）, 触发范围序号}，序号从 1 开始对应 KEYS 顺序。
--       再试秒数与触发范围取"所有超额范围里最晚重置的那一个"：
--       必须等全部超额范围都重置，请求才可能通过，提示更早的时间只会让用户再吃一次 429。
-- 键里只存计数：不写入任何用户内容（契约 2.2 键设计）。

local n = #KEYS
local now = tonumber(ARGV[3 * n + 1])
local latestWait = nil
local latestIndex = 0

for i = 1, n do
    local current = redis.call('GET', KEYS[i])
    if current and tonumber(current) >= tonumber(ARGV[i]) then
        local wait = tonumber(ARGV[2 * n + i]) - now
        if latestWait == nil or wait > latestWait then
            latestWait = wait
            latestIndex = i
        end
    end
end

if latestWait == nil then
    for i = 1, n do
        local changed = redis.call('INCRBY', KEYS[i], 1)
        -- 键首次创建时设置过期；对历史遗留的无 TTL 键补设，保证计数键一定会消失。
        if changed == 1 or redis.call('TTL', KEYS[i]) == -1 then
            redis.call('EXPIRE', KEYS[i], tonumber(ARGV[n + i]))
        end
    end
    return {0}
end

local retryAfter = math.max(1, math.ceil(latestWait))
return {1, retryAfter, latestIndex}
