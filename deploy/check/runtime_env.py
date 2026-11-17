#!/usr/bin/env python3
"""B 段经 SSH stdin 执行，只输出运行中后端 DB 密码检查的布尔结果。"""

import json
from pathlib import Path
import re
import sys


if len(sys.argv) != 2 or not re.fullmatch(r'[1-9][0-9]*', sys.argv[1]):
    raise SystemExit('需要后端 PID；不要传入任何凭据')
try:
    raw = Path('/proc') / sys.argv[1] / 'environ'
    values = dict(entry.split(b'=', 1) for entry in raw.read_bytes().split(b'\0') if b'=' in entry)
    password = values.get(b'DB_PASSWORD')
    # application.yml 中的公开开发默认值，仅用于比较，不输出其内容。
    is_default = password == bytes([49, 50, 51, 52, 53, 54]) if password else None
    print(json.dumps({'db_password_configured': bool(password), 'db_password_is_default': is_default}))
except (OSError, ValueError):
    raise SystemExit('无法读取进程环境；不得据此认定已配置或非默认') from None
