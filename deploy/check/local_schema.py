#!/usr/bin/env python3
"""在自动分配的本机隔离库中比较初始化与历史增量路径，不读取业务库。"""

import argparse
import difflib
import json
import os
from pathlib import Path
import re
import subprocess
from urllib.parse import urlsplit
import uuid


ROOT = Path(__file__).resolve().parents[2]


def isolated_environment():
    """只保留运行工具所需的系统变量，阻断宿主Spring/JVM/供应商配置注入。"""
    allowed = ('PATH', 'HOME', 'JAVA_HOME', 'LANG', 'LC_ALL', 'TMPDIR')
    return {key: os.environ[key] for key in allowed if key in os.environ}


class LocalDatabase:
    """只连接显式配置的回环测试 MySQL，屏蔽客户端错误中的连接信息。"""

    def __init__(self):
        jdbc = os.environ.get('AI_TEST_JDBC_URL', '')
        if not jdbc.startswith('jdbc:mysql://'):
            raise ValueError('需要本机 AI_TEST_JDBC_URL')
        target = urlsplit(jdbc[5:])
        if (target.hostname != '127.0.0.1' or target.username or target.query or target.fragment
                or not re.fullmatch(r'/xinyu_test_[A-Za-z0-9_]+', target.path)):
            raise ValueError('仅接受回环地址、无附加参数的 xinyu_test_ 配置')
        self.command = ['mysql', '--no-defaults', '--no-login-paths', '--batch', '--raw', '--skip-column-names', '--default-character-set=utf8mb4',
                        '--host=127.0.0.1', f'--port={target.port or 3306}',
                        '--user=' + os.environ.get('AI_TEST_DB_USER', 'root')]
        self.environment = dict(isolated_environment(), MYSQL_PWD=os.environ.get('AI_TEST_DB_PASSWORD', ''))
        self.owned = set()

    def execute(self, sql, database=None):
        """通过 stdin 传 SQL；调用方只保存合成数据和结构，不输出连接配置。"""
        if database is not None and database not in self.owned:
            raise ValueError('拒绝访问本次未创建的数据库')
        command = self.command + ([database] if database else [])
        result = subprocess.run(command, input=sql, text=True, capture_output=True, env=self.environment)
        if result.returncode:
            # 只回报 MySQL 错误码，不回显凭据、连接地址或原 SQL。
            code = re.search(r'ERROR (\d+)', result.stderr)
            raise RuntimeError('本机隔离 SQL 失败，错误码 ' + (code[1] if code else 'unknown'))
        return result.stdout

    def create(self, label):
        name = 'xinyu_test_deploy_' + label + '_' + uuid.uuid4().hex[:10]
        self.execute(f'CREATE DATABASE `{name}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;')
        self.owned.add(name)
        return name

    def initialize(self, name, script):
        """只允许将本仓初始化脚本的库选择改写到本次新建库。"""
        script, creates = re.subn(r'CREATE DATABASE IF NOT EXISTS `xinyu`',
                                  f'CREATE DATABASE IF NOT EXISTS `{name}`', script)
        script, uses = re.subn(r'USE `xinyu`;', f'USE `{name}`;', script)
        if creates != 1 or uses != 1:
            raise ValueError('初始化脚本库选择不符合预期')
        self.execute(script, name)

    def schema(self, name, normalize=True):
        tables = self.execute('SHOW TABLES;', name).splitlines()
        result = {}
        for table in tables:
            ddl = self.execute(f'SHOW CREATE TABLE `{table}`;', name).split('\t', 1)[1].strip()
            if normalize:
                ddl = re.sub(r"\s+COMMENT(?:=)?\s*'(?:[^'\\]|\\.)*'", '', ddl)
                ddl = re.sub(r' AUTO_INCREMENT=\d+', '', ddl)
            result[table] = ddl
        return result

    def cleanup(self):
        for name in list(self.owned):
            self.execute(f'DROP DATABASE `{name}`;')
            self.owned.remove(name)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    database = LocalDatabase()
    try:
        fresh = database.create('fresh')
        legacy = database.create('legacy')
        database.initialize(fresh, (ROOT / 'sql/xinyu.sql').read_text())
        old = subprocess.check_output(['git', 'show', '2036e7a:sql/xinyu.sql'], cwd=ROOT, text=True)
        database.initialize(legacy, old)
        timings = []
        import time
        for migration in sorted((ROOT / 'sql/migrations').glob('*.sql')):
            start = time.monotonic()
            database.execute(migration.read_text(), legacy)
            timings.append({'script': migration.name, 'seconds': round(time.monotonic() - start, 4)})
        left = database.schema(fresh)
        right = database.schema(legacy)
        differences = {}
        for table in sorted(left.keys() | right.keys()):
            if left.get(table) != right.get(table):
                differences[table] = list(difflib.unified_diff(
                    left.get(table, '<missing>').splitlines(), right.get(table, '<missing>').splitlines(),
                    fromfile='fresh', tofile='legacy+migrations', lineterm=''))
        comment_differences = {}
        raw_left = database.schema(fresh, normalize=False)
        raw_right = database.schema(legacy, normalize=False)
        for table in sorted(left.keys() & right.keys()):
            comments = []
            for ddl in (raw_left[table], raw_right[table]):
                fields = {}
                for line in ddl.splitlines():
                    match = re.search(r"COMMENT(?:=)?\s*'(?:[^'\\]|\\.)*'", line)
                    if match:
                        column = re.match(r'\s*`([^`]+)`', line)
                        fields[column[1] if column else '__table__'] = match[0]
                comments.append(fields)
            changed = {field: {'fresh': comments[0].get(field), 'migrated': comments[1].get(field)}
                       for field in sorted(comments[0].keys() | comments[1].keys())
                       if comments[0].get(field) != comments[1].get(field)}
            if changed:
                comment_differences[table] = changed
        report = {'fresh_tables': len(left), 'migrated_tables': len(right),
                  'ignored': ['COMMENT', 'table AUTO_INCREMENT next value'],
                  'differences': differences, 'comment_differences': comment_differences,
                  'migration_timings': timings}
        # 再次运行完整初始化，验证 DROP 的外键依赖顺序。
        if not differences:
            database.initialize(fresh, (ROOT / 'sql/xinyu.sql').read_text())
            assert database.schema(fresh) == right
            report['second_initialization_equal'] = True
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
        print(json.dumps({'fresh_tables': len(left), 'migrated_tables': len(right),
                          'different_tables': sorted(differences)}, ensure_ascii=False))
    finally:
        database.cleanup()
        print('本轮两个隔离库已删除')


if __name__ == '__main__':
    main()
