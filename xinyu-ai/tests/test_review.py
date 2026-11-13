"""回顾端点使用离线供应商替身，校验请求、危机零调用及结构失败。"""
import pytest

from app.main import create_app
from tests.stub_provider import AUTH, StubProvider, client_for, make_settings


def review_payload(**changes):
    return {
        "request_id": "review-test", "job_id": "1", "source_version": "1", "attempt": 1,
        "review_type": "WEEK", "budget_ms": 40000, "ai_enabled_total": 1,
        "records": [{"ref": "r1", "date": "2026-11-03", "weekday": "TUESDAY", "mood_type": "平静", "content": "散步"}],
        "stats": {"rangeStart": "2026-11-02", "rangeEnd": "2026-11-09", "total": 1,
                  "daysWithRecords": 1, "days": [], "moodCounts": [{"moodType": "平静", "count": 1}]},
        **changes,
    }


async def test_review_success_validates_references_and_returns_separate_template():
    provider = StubProvider('{"body":"散步了{{r1}}", "ps":null}')
    async with client_for(create_app(make_settings(), provider)) as client:
        response = await client.post('/internal/v1/mood-reviews', json=review_payload(), headers=AUTH)
    assert response.status_code == 200
    assert response.json()['body'] == '散步了{{r1}}'
    assert response.json()['template_version'] == 'mood-review-v1'
    assert len(provider.calls) == 1


async def test_crisis_never_calls_provider():
    provider = StubProvider()
    data = review_payload()
    data['records'][0]['content'] = '我想自杀'
    async with client_for(create_app(make_settings(), provider)) as client:
        response = await client.post('/internal/v1/mood-reviews', json=data, headers=AUTH)
    assert response.status_code == 200
    assert response.json()['result_kind'] == 'SUPPORT'
    assert response.json()['model_called'] is False
    assert provider.calls == []


@pytest.mark.parametrize('text', [
    'not json', '{"body":"引用{{r2}}","ps":null}', '{"body":"坏标记{{12}}","ps":null}',
    '{"body":"你患有抑郁症","ps":null}', '{"body":"联系13800138000","ps":null}',
    '{"body":"' + '文' * 221 + '","ps":null}',
    '{"body":"散步","ps":"' + '文' * 61 + '"}',
    '{"body":"散步","ps":null,"total":999}',
])
async def test_invalid_review_is_classified_without_retry(text):
    provider = StubProvider(text)
    async with client_for(create_app(make_settings(), provider)) as client:
        response = await client.post('/internal/v1/mood-reviews', json=review_payload(), headers=AUTH)
    assert response.status_code == 502
    assert response.json()['code'] == 'invalid_model_response'
    assert len(provider.calls) == 1


@pytest.mark.parametrize('body', ['你需要每天吃两片舍曲林。', 'You have depression. Take sertraline daily.'])
async def test_medical_advice_and_diagnosis_are_rejected(body):
    import json
    provider = StubProvider(json.dumps({'body': body, 'ps': None}, ensure_ascii=False))
    async with client_for(create_app(make_settings(), provider)) as client:
        response = await client.post('/internal/v1/mood-reviews', json=review_payload(), headers=AUTH)
    assert response.status_code == 502
    assert response.json()['code'] == 'invalid_model_response'


def test_shared_safety_corpus_matches_java_contract():
    import json
    from pathlib import Path
    from app.safety import build_support_reply, support_text
    corpus = json.loads((Path(__file__).parents[2] / 'test-contracts/review-safety-cases.json').read_text())
    assert support_text() == corpus['supportText']
    for case in corpus['cases']:
        assert (build_support_reply(case['content']) is not None) == case['support'], case['content']
