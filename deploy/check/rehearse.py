#!/usr/bin/env python3
"""本机旧库升级及进程级发布预演；只访问本次新建隔离库和固定回环端口。"""

import argparse
import hashlib
import http.server
import json
import os
from pathlib import Path
import secrets
import shutil
import socket
import subprocess
import sys
import tarfile
import threading
import time
import urllib.request
from urllib.parse import urlsplit

from local_schema import LocalDatabase, ROOT, isolated_environment
from smoke import NoRedirect, expect_api, get


PORTS = (28080, 28000, 28100, 28081, 28379)
REPLY = '这是部署预演的合成回复，今天的小事已经被认真读到。'


def require_free_ports():
    for port in PORTS:
        with socket.socket() as listener:
            listener.bind(('127.0.0.1', port))


def api(base, path, body, token=None):
    request = urllib.request.Request(base + path, data=json.dumps(body).encode(), method='POST',
                                     headers={'Content-Type': 'application/json'})
    if token:
        request.add_header('token', token)
    with urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirect()).open(request, timeout=10) as response:
        data = json.load(response)
    assert data['code'] == 200, '合成API操作失败'
    return data['data']


def wait_ready(url, processes):
    deadline = time.monotonic() + 40
    while time.monotonic() < deadline:
        assert all(process.poll() is None for process in processes), '预演子进程提前退出，请检查本地日志'
        try:
            if get(url, '')[0] == 200:
                return
        except RuntimeError:
            pass
        time.sleep(0.2)
    raise AssertionError('预演进程就绪超时')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--build-root', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    build = args.build_root.resolve()
    assert build.is_relative_to(Path('/private/tmp')), '产物只允许使用本任务临时目录'
    require_free_ports()
    artifacts = build / 'artifacts'
    artifacts.mkdir(exist_ok=True)
    database = LocalDatabase()
    processes = []
    logs = []
    servers = []
    supplier_calls = []
    report = {'ports': list(PORTS), 'real_model_calls': 0, 'checks': []}

    def start(command, cwd, env, label):
        log = (build / (label + '.log')).open('wb')
        logs.append(log)
        process = subprocess.Popen(command, cwd=cwd, env=env, stdout=log, stderr=subprocess.STDOUT)
        processes.append(process)
        return process

    def stop(process):
        process.terminate()
        try:
            process.wait(timeout=40)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait(timeout=5)
        processes.remove(process)

    try:
        name = database.create('rehearsal')
        old = subprocess.check_output(['git', 'show', '2036e7a:sql/xinyu.sql'], cwd=ROOT, text=True)
        database.initialize(name, old)
        database.execute("""
INSERT INTO `user`(id,username,password) VALUES(900001,'synthetic_legacy','unused-synthetic-password-hash');
INSERT INTO mood_record(id,user_id,mood_type,content,ai_reply,status,create_time,update_time) VALUES
(900001,900001,'平静','历史有效非空','历史回复',1,'2026-01-01 10:00:00','2026-01-01 11:00:00'),
(900002,900001,'平静','历史空白','   ',1,'2026-01-02 10:00:00','2026-01-02 11:00:00'),
(900003,900001,'平静','历史NULL',NULL,1,'2026-01-03 10:00:00','2026-01-03 11:00:00'),
(900004,900001,'平静','历史已删除','历史回复',0,'2026-01-04 10:00:00','2026-01-04 11:00:00');
INSERT INTO community_post(id,user_id,topic_id,mood_type,content,status) VALUES(900001,900001,1,'平静','历史不可见帖子',0);
INSERT INTO community_reply(id,post_id,user_id,content,status) VALUES(900001,900001,900001,'历史不可见回复',0);
""", name)
        original = database.execute('SELECT id,user_id,mood_type,content,ai_reply,status,create_time,update_time FROM mood_record ORDER BY id;', name)
        counts = {table: database.execute(f'SELECT COUNT(*) FROM `{table}`;', name)
                  for table in database.execute('SHOW TABLES;', name).splitlines()}
        dump_command = ['mysqldump', '--no-defaults', '--no-login-paths', '--single-transaction', '--routines', '--triggers', '--events',
                        '--set-gtid-purged=OFF', '--no-tablespaces', '--default-character-set=utf8mb4']
        dump_command += [arg for arg in database.command if arg.startswith(('--host=', '--port=', '--user='))]
        dump = subprocess.run(dump_command + [name], env=database.environment, text=True, capture_output=True)
        assert dump.returncode == 0, '合成库备份失败'
        (artifacts / 'legacy-synthetic.sql').write_text(dump.stdout)
        restore = database.create('restore')
        database.execute(dump.stdout, restore)
        for table, expected in counts.items():
            assert database.execute(f'SELECT COUNT(*) FROM `{table}`;', restore) == expected
        assert database.execute('SELECT id,user_id,mood_type,content,ai_reply,status,create_time,update_time FROM mood_record ORDER BY id;', restore) == original
        report['checks'].append('synthetic_backup_restored_and_counts_equal')

        migrations = sorted((ROOT / 'sql/migrations').glob('*.sql'))
        for index, migration in enumerate(migrations, 1):
            database.execute((ROOT / f'deploy/check/migrations/{index:02d}-before.sql').read_text(), name)
            database.execute(migration.read_text(), name)
            database.execute((ROOT / f'deploy/check/migrations/{index:02d}-after.sql').read_text(), name)
        assert database.execute('SELECT id,user_id,mood_type,content,ai_reply,status,create_time,update_time FROM mood_record ORDER BY id;', name) == original
        assert database.execute('SELECT ai_reply_enabled FROM mood_record ORDER BY id;', name).splitlines() == ['1','0','0','0']
        assert database.execute('SELECT COUNT(*) FROM mood_record WHERE content_version<>1 OR deleted_at IS NOT NULL;', name).strip() == '0'
        assert database.execute('SELECT COUNT(*) FROM ai_job;', name).strip() == '0'
        assert database.execute('SELECT status FROM community_post;', name).strip() == '0'
        assert database.execute('SELECT status FROM community_reply;', name).strip() == '0'
        report['checks'].append('five_migrations_backfill_and_original_fields_preserved')
        # 与初始化路径再次核对，不因带种子数据的 AUTO_INCREMENT 差异误报。
        fresh = database.create('rehearsal_fresh')
        database.initialize(fresh, (ROOT / 'sql/xinyu.sql').read_text())
        assert database.schema(name) == database.schema(fresh)
        report['checks'].append('seeded_migration_schema_matches_fresh')

        jar = build / 'xinyu-backend/target/xinyu-backend-0.0.1-SNAPSHOT.jar'
        assert jar.is_file(), '先打包Java'
        shutil.copy2(jar, artifacts / 'xinyu-backend.jar')
        with tarfile.open(artifacts / 'xinyu-ai.tar.gz', 'w:gz') as archive:
            for item in ['app', 'pyproject.toml', 'uv.lock']:
                archive.add(build / 'xinyu-ai' / item, arcname=item,
                            filter=lambda info: None if '__pycache__' in info.name else info)
        runtime = artifacts / 'python-runtime'
        runtime.mkdir(exist_ok=True)
        with tarfile.open(artifacts / 'xinyu-ai.tar.gz') as archive:
            archive.extractall(runtime, filter='data')
        sync_environment = dict(isolated_environment(), UV_CACHE_DIR=str(build / 'uv-cache'))
        with (build / 'python-runtime-sync.log').open('wb') as sync_log:
            synced = subprocess.run(['uv','sync','--frozen','--no-dev','--python','3.12','--project',str(runtime)],
                                    env=sync_environment, stdout=sync_log, stderr=subprocess.STDOUT)
        assert synced.returncode == 0, 'Python运行时锁定依赖安装失败'
        shutil.copytree(build / 'xinyu-vue/dist', artifacts / 'frontend', dirs_exist_ok=True)
        with tarfile.open(artifacts / 'frontend.tar.gz', 'w:gz') as archive:
            archive.add(artifacts / 'frontend', arcname='dist')
        report['artifact_sha256'] = {filename: hashlib.sha256((artifacts / filename).read_bytes()).hexdigest()
                                     for filename in ('xinyu-backend.jar','xinyu-ai.tar.gz','frontend.tar.gz')}
        report['synthetic_backup_sha256'] = hashlib.sha256((artifacts / 'legacy-synthetic.sql').read_bytes()).hexdigest()

        # Redis 密码、内部令牌及 JWT 均为内存生成的本轮合成值，不写入文件或日志。
        redis_password = secrets.token_urlsafe(32)
        redis_config = artifacts / 'redis-local.conf'
        redis_config.write_text('bind 127.0.0.1\nport 28379\nprotected-mode yes\nmaxmemory 64mb\nmaxmemory-policy allkeys-lru\nsave ""\nappendonly no\ndaemonize no\n')
        redis_process = start(['redis-server', str(redis_config)], build, isolated_environment(), 'redis')
        time.sleep(0.3)
        redis_env = isolated_environment()
        result = subprocess.run(['redis-cli','-h','127.0.0.1','-p','28379','-x','CONFIG','SET','requirepass'],
                                input=redis_password.encode(), capture_output=True, env=redis_env)
        assert result.returncode == 0 and result.stdout.strip() == b'OK', '本机Redis认证配置失败'
        assert redis_process.poll() is None
        test_environment = isolated_environment()
        endpoint = urlsplit(os.environ['AI_TEST_JDBC_URL'][5:])
        test_environment.update(DB_URL=f'jdbc:mysql://127.0.0.1:{endpoint.port or 3306}/{name}?serverTimezone=Asia/Shanghai',
                                DB_USERNAME=os.environ.get('AI_TEST_DB_USER','root'),
                                DB_PASSWORD=os.environ.get('AI_TEST_DB_PASSWORD',''),
                                JWT_SECRET=secrets.token_urlsafe(48), AI_INTERNAL_TOKEN=secrets.token_urlsafe(32),
                                REDIS_URL=f'redis://:{redis_password}@127.0.0.1:28379/0',
                                XINYU_RATELIMIT_KEYPREFIX='xinyu:test:deploy:', XINYU_REVIEW_TIMEZONE='Asia/Shanghai',
                                AI_WORKER_ENABLED='false', AI_WORKER_CONCURRENCY='3',
                                AI_SERVICE_BASE_URL='http://127.0.0.1:28000',
                                SERVER_ADDRESS='127.0.0.1', SERVER_PORT='28080',
                                DEEPSEEK_API_KEY='synthetic-unused-provider-key',
                                DEEPSEEK_BASEURL='http://127.0.0.1:28100/v1',
                                OPENAI_BASE_URL='http://127.0.0.1:28100/v1', OPENAI_API_KEY='synthetic-provider-key',
                                OPENAI_MODEL='synthetic-deploy-model', DEEPSEEK_THINKING_MODE='disabled',
                                AI_SERVICE_CONCURRENCY='3', OPENAI_MAX_TOKENS='4096')
        java_binary = str(Path(os.environ['JAVA_HOME']) / 'bin/java')
        java = start([java_binary, '-jar', str(artifacts / 'xinyu-backend.jar')], build, test_environment, 'java-worker-off')
        base = 'http://127.0.0.1:28080'
        wait_ready(base + '/hello', processes)
        report['checks'].append('redis_then_java_worker_disabled_started')

        class Supplier(http.server.BaseHTTPRequestHandler):
            def log_message(self, *args):
                pass

            def do_POST(self):
                assert self.path == '/v1/chat/completions'
                body = json.loads(self.rfile.read(int(self.headers['Content-Length'])))
                assert body['stream'] is False
                supplier_calls.append(1)
                raw = json.dumps({'model':'synthetic-deploy-model','choices':[{'finish_reason':'stop','message':{'content':REPLY}}]}).encode()
                self.send_response(200)
                self.send_header('Content-Type','application/json')
                self.end_headers()
                self.wfile.write(raw)

        supplier = http.server.ThreadingHTTPServer(('127.0.0.1',28100), Supplier)
        servers.append(supplier)
        threading.Thread(target=supplier.serve_forever,daemon=True).start()
        python = start([str(runtime / '.venv/bin/python'), '-m','uvicorn','app.main:app_from_env','--factory',
                        '--host','127.0.0.1','--port','28000','--workers','1','--no-access-log'],
                       runtime, test_environment, 'python')
        wait_ready('http://127.0.0.1:28000/health', processes)

        class Frontend(http.server.SimpleHTTPRequestHandler):
            # 本机静态/代理替身，仅验证打包产物和路径；真实Nginx在B/D段核验。
            def __init__(self,*args,**kwargs):
                super().__init__(*args,directory=str(artifacts / 'frontend'),**kwargs)

            def log_message(self,*args):
                pass

            def do_GET(self):
                if self.path.startswith(('/internal','/api/internal')):
                    self.send_error(404)
                    return
                if self.path.startswith('/api/'):
                    status, body = get(base, self.path[4:], self.headers.get('token'))
                    self.send_response(status)
                    self.end_headers()
                    self.wfile.write(body)
                    return
                requested = Path(self.translate_path(self.path))
                if not requested.is_file():
                    self.path = '/index.html'
                super().do_GET()

        frontend = http.server.ThreadingHTTPServer(('127.0.0.1',28081), Frontend)
        servers.append(frontend)
        threading.Thread(target=frontend.serve_forever,daemon=True).start()
        report['checks'].append('python_then_frontend_artifact_served')
        api(base,'/user/register',{'username':'synthetic_deploy_member','password':'local-fixture-password'})
        member = api(base,'/user/login',{'username':'synthetic_deploy_member','password':'local-fixture-password'})['token']
        api(base,'/user/register',{'username':'synthetic_deploy_admin','password':'local-fixture-password'})
        database.execute("UPDATE `user` SET role='ADMIN' WHERE username='synthetic_deploy_admin';",name)
        admin = api(base,'/user/login',{'username':'synthetic_deploy_admin','password':'local-fixture-password'})['token']
        disabled = api(base,'/mood/add',{'moodType':'平静','content':'关闭AI的部署合成记录','needAiReply':False},member)
        assert disabled['jobId'] is None
        pending = api(base,'/mood/add',{'moodType':'平静','content':'今天散步后心情平静了些','needAiReply':True},member)
        assert pending['jobId'] is not None
        time.sleep(1.2)
        assert expect_api(base,'/mood/ai-job/'+str(pending['jobId']),member)['status'] == 'queued'
        assert not supplier_calls
        api(base,'/community/post/add',{'topicId':1,'moodType':'平静','content':'关闭AI的部署合成帖子','needAiReply':False},member)
        smoke_env = dict(test_environment, SMOKE_BACKEND_URL=base, SMOKE_PYTHON_URL='http://127.0.0.1:28000',
                         SMOKE_FRONTEND_URL='http://127.0.0.1:28081',SMOKE_USER_TOKEN=member,SMOKE_ADMIN_TOKEN=admin)
        smoke = subprocess.run([sys.executable,str(ROOT/'deploy/check/smoke.py')],env=smoke_env,capture_output=True,text=True)
        assert smoke.returncode == 0, 'worker关闭时只读冒烟失败: '+smoke.stderr.strip()
        report['smoke_before_worker'] = json.loads(smoke.stdout)
        report['checks'].append('worker_off_keeps_queue_and_no_upstream_calls')
        stop(java)
        assert all(p.poll() is None for p in processes)
        # 只保留一个由本脚本管理的 Java；旧进程实际退出后才启用新worker。
        test_environment['AI_WORKER_ENABLED']='true'
        java = start([java_binary,'-jar',str(artifacts/'xinyu-backend.jar')],build,test_environment,'java-worker-on')
        wait_ready(base+'/hello', processes)
        deadline = time.monotonic()+30
        while time.monotonic()<deadline:
            job = expect_api(base,'/mood/ai-job/'+str(pending['jobId']),member)
            if job['status'] in {'succeeded','failed'}:
                break
            time.sleep(0.2)
        assert job['status']=='succeeded', '合成worker任务未成功'
        assert job['replyContent']==REPLY
        assert job['resultKind']=='MODEL'
        assert len(supplier_calls)==1
        assert expect_api(base,'/mood/detail/'+str(pending['id']),member)['aiReply']==REPLY
        assert database.execute('SELECT COUNT(*) FROM ai_reply;',name).strip()=='1'
        redis_env['REDISCLI_AUTH']=redis_password
        keys = subprocess.run(['redis-cli','-p','28379','--scan','--pattern','xinyu:test:deploy:*'],
                              env=redis_env,capture_output=True,text=True)
        assert keys.returncode==0 and keys.stdout.strip(), '限流前缀环境变量未生效'
        report['checks'].append('single_worker_java_python_synthetic_http_persisted_once')
        smoke = subprocess.run([sys.executable,str(ROOT/'deploy/check/smoke.py')],env=smoke_env,capture_output=True,text=True)
        assert smoke.returncode==0, 'worker启用后只读冒烟失败: '+smoke.stderr.strip()
        report['smoke_after_worker']=json.loads(smoke.stdout)
        report['synthetic_supplier_calls']=len(supplier_calls)
        if os.environ.get('SPRING_APPLICATION_JSON') and os.environ.get('JAVA_TOOL_OPTIONS'):
            report['checks'].append('host_spring_and_jvm_overrides_ignored')
        if os.environ.get('HTTP_PROXY') and not os.environ.get('NO_PROXY'):
            report['checks'].append('host_http_proxy_ignored')
        report['nginx_systemd_runtime']='macOS未安装Nginx/systemd；模板待B段按真实环境核验'
    finally:
        for server in reversed(servers):
            server.shutdown()
            server.server_close()
        for process in list(reversed(processes)):
            stop(process)
        for log in logs:
            log.close()
        database.cleanup()
        report['owned_processes_stopped']=not processes
        report['owned_databases_removed']=not database.owned
        args.output.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
        print(json.dumps(report,ensure_ascii=False))


if __name__=='__main__':
    main()
