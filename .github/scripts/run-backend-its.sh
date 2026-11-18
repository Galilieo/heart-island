#!/usr/bin/env bash
# 逐类运行后端集成测试（*IT）。
#
# 每个 IT 类在开始时断言自己连的是空的 xinyu_test_ 库、结束时不清理，
# 所以这里为每个类新建一个 xinyu_test_ci_<类名> 空库，跑完即删。
#
# 需要：
#   - 127.0.0.1 上的 MySQL；AI_TEST_DB_USER（默认 root）、AI_TEST_DB_PASSWORD
#   - xinyu-ai/.venv 已执行 uv sync（契约与回顾 IT 会拉起合成 Python 服务）
#   - 可选 AI_TEST_REDIS_URL：设置后运行 Redis 限流 IT；CI 必须设置
# 可选：AI_TEST_DB_PORT（默认 3306）、KEEP_IT_DBS=1 保留各库便于排查、MVN（默认 sh mvnw）。
#
# 任一类失败、或某类全部被跳过（环境变量没传到）都以非零退出，避免“没跑也算过”。
set -euo pipefail

cd "$(dirname "$0")/../../xinyu-backend"

db_user="${AI_TEST_DB_USER:-root}"
db_host="127.0.0.1"
db_port="${AI_TEST_DB_PORT:-3306}"
mvn_cmd="${MVN:-sh mvnw}"

# 超时评估是一次性测评，不作为回归门禁。
skipped_classes=" MoodReviewTimeoutBenchmarkIT "

mysql_exec() {
  MYSQL_PWD="${AI_TEST_DB_PASSWORD:-}" mysql --no-defaults \
    -h"$db_host" -P"$db_port" -u"$db_user" -e "$1"
}

# 从 surefire 报告读出 tests / skipped / failures / errors，判断该类是否真正执行。
report_summary() {
  local class="$1"
  local report
  report=$(ls target/surefire-reports/TEST-*."$class".xml 2>/dev/null | head -1 || true)
  if [[ -z "$report" ]]; then
    echo "missing 0"
    return
  fi
  python3 - "$report" <<'PY'
import sys
import xml.etree.ElementTree as ET

suite = ET.parse(sys.argv[1]).getroot()
tests = int(suite.get("tests", 0))
skipped = int(suite.get("skipped", 0))
print(f"{tests} {skipped}")
PY
}

$mvn_cmd -B -q test-compile

# 不用 mapfile，兼容 macOS 自带的 bash 3.2，便于本机预演。
classes=()
while IFS= read -r class; do
  classes+=("$class")
done < <(grep -rlE 'class [A-Za-z0-9]+IT\b' src/test/java | xargs -n1 basename | sed 's/\.java$//' | sort)

failed=""
summary=()

for class in "${classes[@]}"; do
  if [[ "$skipped_classes" == *" $class "* ]]; then
    summary+=("$class: 不在 CI 范围")
    continue
  fi

  db="xinyu_test_ci_$(echo "$class" | tr '[:upper:]' '[:lower:]')"
  mysql_exec "DROP DATABASE IF EXISTS \`$db\`;
              CREATE DATABASE \`$db\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

  echo "::group::$class"
  status=0
  AI_TEST_JDBC_URL="jdbc:mysql://$db_host:$db_port/$db" \
    $mvn_cmd -B surefire:test -Dtest="$class" -Dsurefire.failIfNoSpecifiedTests=false || status=$?
  echo "::endgroup::"

  if [[ "${KEEP_IT_DBS:-}" != "1" ]]; then
    mysql_exec "DROP DATABASE IF EXISTS \`$db\`;"
  fi

  read -r tests skipped <<<"$(report_summary "$class")"
  if [[ "$status" -ne 0 ]]; then
    failed="$failed $class"
    summary+=("$class: 失败")
  elif [[ "$tests" == "missing" || "$tests" -eq 0 || "$skipped" -ge "$tests" ]]; then
    failed="$failed $class"
    summary+=("$class: 未实际执行（tests=$tests skipped=$skipped），检查环境变量")
  else
    summary+=("$class: $tests 项通过（跳过 $skipped）")
  fi
done

printf '%s\n' "${summary[@]}"

if [[ -n "$failed" ]]; then
  echo "集成测试未通过：$failed" >&2
  exit 1
fi
