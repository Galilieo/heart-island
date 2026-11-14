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
    '{"body":"' + '文' * 501 + '","ps":null}',
    '{"body":"散步","ps":"' + '文' * 61 + '"}',
    '{"body":"散步","ps":null,"total":999}',
    '{"body":"这里写了〔中间省略〕","ps":null}',
    '{"body":"这里提到中间省略了","ps":null}',
    '{"body":"散步","ps":"附注含〔中间省略〕"}',
    '{"body":"散步","ps":"附注含中间省略"}',
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


def contact_review(content: str, body: str, ps: str | None = None):
    """构造只有一条记录的回顾请求，以及模型返回的信。"""
    import json
    data = review_payload()
    data['records'][0]['content'] = content
    return data, StubProvider(json.dumps({'body': body, 'ps': ps}, ensure_ascii=False))


@pytest.mark.parametrize('content,body', [
    ('下班遇到走丢的橘猫，给保安大叔留了电话', '给保安大叔留了电话{{r1}}。'),
    ('保安大叔发微信说猫被抱回去了', '保安大叔发微信说猫回家了{{r1}}。'),
    ('晚上和老同学开QQ语音聊到很晚', '和老同学开qq语音聊到很晚{{r1}}。'),
])
async def test_contact_word_echoed_from_records_is_allowed(content, body):
    data, provider = contact_review(content, body)
    async with client_for(create_app(make_settings(), provider)) as client:
        response = await client.post('/internal/v1/mood-reviews', json=data, headers=AUTH)
    assert response.status_code == 200
    assert response.json()['body'] == body


@pytest.mark.parametrize('body,ps', [
    ('散步之后可以给朋友打个电话{{r1}}。', None),
    ('散步了{{r1}}。', '加个微信和朋友聊聊。'),
    ('散步了{{r1}}，回来记得上QQ。', None),
])
async def test_contact_word_introduced_by_model_is_rejected(body, ps):
    data, provider = contact_review('散步', body, ps)
    async with client_for(create_app(make_settings(), provider)) as client:
        response = await client.post('/internal/v1/mood-reviews', json=data, headers=AUTH)
    assert response.status_code == 502
    assert response.json()['code'] == 'invalid_model_response'


async def test_contact_word_spliced_across_records_is_rejected():
    import json
    data = review_payload(ai_enabled_total=2, records=[
        {"ref": "r1", "date": "2026-11-03", "weekday": "TUESDAY", "mood_type": "平静", "content": "晚上楼下停电"},
        {"ref": "r2", "date": "2026-11-04", "weekday": "WEDNESDAY", "mood_type": "开心", "content": "话剧票抢到了"},
    ])
    data['stats']['total'] = 2
    provider = StubProvider(json.dumps({'body': '停电时给朋友打了电话{{r1}}。', 'ps': None}, ensure_ascii=False))
    async with client_for(create_app(make_settings(), provider)) as client:
        response = await client.post('/internal/v1/mood-reviews', json=data, headers=AUTH)
    assert response.status_code == 502
    assert response.json()['code'] == 'invalid_model_response'


@pytest.mark.parametrize('body', ['看到了那条热线{{r1}}。', '记下了 400-123-4567{{r1}}。', '可以拨打这个号码{{r1}}。'])
async def test_hotline_and_numbers_are_rejected_even_when_in_records(body):
    data, provider = contact_review('地铁里看到求助热线 400-123-4567，可以拨打这个号码', body)
    async with client_for(create_app(make_settings(), provider)) as client:
        response = await client.post('/internal/v1/mood-reviews', json=data, headers=AUTH)
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


def test_messages_use_review_prompt_for_reviews_and_preserve_mood_reply_prompt():
    import json
    from pathlib import Path
    from app.contracts import MoodReplyRequest
    from app.provider import SYSTEM_PROMPT, messages
    from app.review_prompt import REVIEW_SYSTEM_PROMPT
    from app.reviews import ReviewRequest

    prompt_file = Path(__file__).resolve().parents[1] / 'evals/prompts/mood-review-v1a.txt'
    assert REVIEW_SYSTEM_PROMPT == prompt_file.read_text(encoding='utf-8').strip()

    review_req = ReviewRequest.model_validate(review_payload())
    review_msgs = messages(review_req)
    assert [m['role'] for m in review_msgs] == ['system', 'user']
    assert review_msgs[0]['content'] == REVIEW_SYSTEM_PROMPT
    assert set(json.loads(review_msgs[1]['content']).keys()) == {'review_type', 'records', 'ai_enabled_total', 'stats'}

    reply_req = MoodReplyRequest(
        request_id='reply-test', job_id='1', attempt=1, source_version='1',
        mood_type='平静', content='散步', budget_ms=40000,
    )
    reply_msgs = messages(reply_req)
    assert [m['role'] for m in reply_msgs] == ['system', 'user']
    assert reply_msgs[0]['content'] == SYSTEM_PROMPT
    assert json.loads(reply_msgs[1]['content']) == {'mood_type': '平静', 'content': '散步'}


@pytest.mark.parametrize('review_type,ok_len,bad_len', [
    ('WEEK', 500, 501),
    ('RECENT_30', 600, 601),
])
async def test_review_body_hard_limit_boundaries(review_type, ok_len, bad_len):
    ok_provider = StubProvider('{"body":"' + '文' * ok_len + '","ps":null}')
    bad_provider = StubProvider('{"body":"' + '文' * bad_len + '","ps":null}')
    async with client_for(create_app(make_settings(), ok_provider)) as client:
        ok_resp = await client.post('/internal/v1/mood-reviews', json=review_payload(review_type=review_type), headers=AUTH)
    async with client_for(create_app(make_settings(), bad_provider)) as client:
        bad_resp = await client.post('/internal/v1/mood-reviews', json=review_payload(review_type=review_type), headers=AUTH)
    assert ok_resp.status_code == 200
    assert bad_resp.status_code == 502
    assert bad_resp.json()['code'] == 'invalid_model_response'


async def test_review_accepts_full_12000_utf16_payload_with_escapable_chars():
    import json
    records = [
        {
            'ref': f'r{i + 1}',
            'date': f'2026-10-{(i % 28) + 1:02d}',
            'weekday': '周一',
            'mood_type': '平静',
            'content': '\x01' * 199 + '记',
        }
        for i in range(60)
    ]
    data = review_payload(review_type='RECENT_30', ai_enabled_total=60, records=records)
    data['stats']['total'] = 60
    raw = json.dumps(data, ensure_ascii=True).encode('utf-8')
    assert 16384 < len(raw) < 131072
    provider = StubProvider('{"body":"满载测试通过{{r1}}", "ps":null}')
    async with client_for(create_app(make_settings(), provider)) as client:
        response = await client.post(
            '/internal/v1/mood-reviews',
            content=raw,
            headers={**AUTH, 'Content-Type': 'application/json'},
        )
    assert response.status_code == 200
    assert len(provider.calls) == 1


async def test_review_rejects_total_content_above_12000_utf16():
    records = [
        {
            'ref': f'r{i + 1}',
            'date': f'2026-10-{(i % 28) + 1:02d}',
            'weekday': '周一',
            'mood_type': '平静',
            'content': 'a' * (300 if i < 40 else 1),
        }
        for i in range(41)
    ]
    data = review_payload(review_type='RECENT_30', ai_enabled_total=41, records=records)
    data['stats']['total'] = 41
    provider = StubProvider('{"body":"散步了{{r1}}", "ps":null}')
    async with client_for(create_app(make_settings(), provider)) as client:
        response = await client.post('/internal/v1/mood-reviews', json=data, headers=AUTH)
    assert response.status_code == 400
    assert response.json()['code'] == 'invalid_request'
    assert provider.calls == []


async def test_review_allows_128_kib_limit_while_mood_reply_keeps_16_kib():
    import json
    from tests.stub_provider import payload
    provider = StubProvider('{"body":"散步了{{r1}}", "ps":null}')
    async with client_for(create_app(make_settings(), provider)) as client:
        oversized_review = b' ' * (131072 - len(b'{}')) + b'{' + b'}'  # 131072 bytes; +1 below
        too_big_review = b' ' * 131073
        r_big = await client.post('/internal/v1/mood-reviews', content=too_big_review, headers=AUTH)
        assert r_big.status_code == 400
        assert r_big.json()['code'] == 'invalid_request'

        padded_valid_review = json.dumps(review_payload()).encode('utf-8')
        padded_valid_review = b' ' * (131072 - len(padded_valid_review)) + padded_valid_review
        r_ok = await client.post(
            '/internal/v1/mood-reviews',
            content=padded_valid_review,
            headers={**AUTH, 'Content-Type': 'application/json'},
        )
        assert r_ok.status_code == 200

        reply_ok_payload = json.dumps(payload()).encode('utf-8')
        reply_over = b' ' * (16385 - len(reply_ok_payload)) + reply_ok_payload
        r_reply_over = await client.post(
            '/internal/v1/mood-replies',
            content=reply_over,
            headers={**AUTH, 'Content-Type': 'application/json'},
        )
        assert r_reply_over.status_code == 400
        assert r_reply_over.json()['code'] == 'invalid_request'
