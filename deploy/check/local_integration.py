#!/usr/bin/env python3
"""在每类独立空库和专属 Redis 中运行功能集成回归，不执行测评/性能基准。"""

import argparse
import json
import os
from pathlib import Path
import secrets
import subprocess
import time
import xml.etree.ElementTree as ET

from local_schema import LocalDatabase, isolated_environment


CLASSES = ['AiJobPersistenceIT', 'AiWorkerDatabaseIT', 'AiServiceClientContractIT',
           'AiReplyContractIT', 'MoodReviewPersistenceIT', 'CommunityAiPersistenceIT',
           'CommunityReportPersistenceIT', 'UserProfilePersistenceIT', 'RateLimitRedisIT']


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--build-root', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    build = args.build_root.resolve()
    assert build.is_relative_to(Path('/private/tmp'))
    database = LocalDatabase()
    report = {}
    redis_log = (build / 'redis-integration.log').open('wb')
    redis = subprocess.Popen(['redis-server','--bind','127.0.0.1','--port','28379','--save','','--appendonly','no',
                              '--maxmemory','64mb','--maxmemory-policy','allkeys-lru'],
                             stdout=redis_log,stderr=subprocess.STDOUT,env=isolated_environment())
    try:
        time.sleep(0.3)
        assert redis.poll() is None, '专属Redis端口不可用'
        password = secrets.token_urlsafe(32)
        configured = subprocess.run(['redis-cli','-p','28379','-x','CONFIG','SET','requirepass'],
                                    input=password.encode(),capture_output=True,env=isolated_environment())
        assert configured.returncode == 0 and configured.stdout.strip() == b'OK'
        for test_class in CLASSES:
            name = database.create('it')
            environment = isolated_environment()
            url = next(part for part in database.command if part.startswith('--port=')).split('=',1)[1]
            environment.update(AI_TEST_JDBC_URL=f'jdbc:mysql://127.0.0.1:{url}/{name}',
                               AI_TEST_DB_USER=os.environ.get('AI_TEST_DB_USER','root'),
                               AI_TEST_DB_PASSWORD=os.environ.get('AI_TEST_DB_PASSWORD',''),
                               DB_URL=f'jdbc:mysql://127.0.0.1:{url}/{name}',
                               DB_USERNAME=os.environ.get('AI_TEST_DB_USER','root'),
                               DB_PASSWORD=os.environ.get('AI_TEST_DB_PASSWORD',''),
                               AI_TEST_REDIS_URL=f'redis://:{password}@127.0.0.1:28379/0')
            agent = os.environ['LOCAL_MOCKITO_AGENT']
            with (build / (test_class + '.log')).open('wb') as log:
                result = subprocess.run(['mvn','-q',f'-DargLine=-javaagent:{agent}',f'-Dtest={test_class}','test'],
                                        cwd=build/'xinyu-backend',env=environment,stdout=log,stderr=subprocess.STDOUT)
            xml = list((build/'xinyu-backend/target/surefire-reports').glob('TEST-*.'+test_class+'.xml'))
            assert len(xml)==1, '测试报告缺失'
            suite=ET.parse(xml[0]).getroot()
            report[test_class]={key:int(suite.get(key,0)) for key in ['tests','failures','errors','skipped']}
            args.output.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
            print(test_class, report[test_class],flush=True)
            assert result.returncode==0, '集成测试失败，请检查本机临时日志'
    finally:
        try:
            redis.terminate()
            try:
                redis.wait(timeout=10)
            except subprocess.TimeoutExpired:
                redis.kill()
                redis.wait(timeout=5)
        finally:
            redis_log.close()
            database.cleanup()
        print('本轮功能回归的专属Redis和隔离库已清理',flush=True)


if __name__=='__main__':
    main()
