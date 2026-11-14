"""回顾内部协议与确定性输出校验；不接受模型提供的统计数字。"""
import json
import re
from typing import Literal
from pydantic import Field, field_validator, model_validator
from app.contracts import Contract, Correlation, MAX_CONTENT_UTF16, MoodType, utf16_length

TEMPLATE_VERSION = 'mood-review-v1'
# 与 Java MoodReviewService.MAX_TOTAL_CONTENT_UTF16 保持同值。
MAX_TOTAL_CONTENT_UTF16 = 12000
# 2026-11-14 任务书第 3.7 节硬上限，与 Java ReviewAiResponse 同值。
MAX_WEEK_BODY_UTF16 = 500
MAX_RECENT_BODY_UTF16 = 600
MAX_PS_UTF16 = 60
# 与 Java MoodReviewService.TRUNCATION_MARKER 及 evals/run_review_eval.py 同值。
TRUNCATION_MARKER = '〔中间省略〕'
TRUNCATION_MARKER_WORD = '中间省略'
REFERENCE = re.compile(r'\{\{(r[1-9][0-9]*)}}')
# 第6节的输出门禁，不能替代后续文字质量评测。
UNSAFE = re.compile(
    r'舍曲林|氟西汀|帕罗西汀|艾司西酞普兰|文拉法辛|地西泮|劳拉西泮|阿普唑仑|锂盐|抑郁|躁郁|躁狂|强迫症|诊断|确诊|患有|抑郁症|焦虑症|双相|精神疾病|服药|用药|药物|处方|治疗|停药|加药|减药'
    r'|sertraline|fluoxetine|paroxetine|escitalopram|venlafaxine|diazepam|lorazepam|alprazolam|depression|bipolar|disorder|prescri|dosage|diagnos|medication|antidepressant|https?://|www\.|[\w.+-]+@[\w.-]+\.[a-zA-Z]{2,}'
    r'|(?:\+?\d[\d\s-]{5,}\d)|热线|联系方式|拨打|致电|建议就医|建议看医生|建议服用', re.IGNORECASE,
)
# 日常记录里常见的联系方式词：发给小芽的记录原文写过时允许照说，模型自己引入时仍拒绝。
# 2026-11-14 第二轮测评中“留了电话”“等外婆打电话”被整份误杀，见第 3 步任务书第 11 节。
ECHOABLE_CONTACT_WORDS = re.compile(r'微信|电话|QQ', re.IGNORECASE)


class ReviewEntry(Contract):
    ref: str = Field(pattern=r'^r[1-9][0-9]*$')
    date: str = Field(pattern=r'^\d{4}-\d{2}-\d{2}$')
    weekday: str
    mood_type: MoodType
    content: str

    @field_validator('content')
    @classmethod
    def limited_content(cls, value):
        if utf16_length(value) > MAX_CONTENT_UTF16:
            raise ValueError('invalid_content')
        return value


class MoodCount(Contract):
    moodType: str
    count: int = Field(ge=0)


class ReviewDay(Contract):
    date: str
    moods: list[str]


class ReviewStats(Contract):
    rangeStart: str
    rangeEnd: str
    total: int = Field(ge=1)
    daysWithRecords: int = Field(ge=1, le=30)
    days: list[ReviewDay] = Field(max_length=30)
    moodCounts: list[MoodCount] = Field(max_length=30)


class ReviewRequest(Correlation):
    review_type: Literal['WEEK', 'RECENT_30']
    records: list[ReviewEntry] = Field(min_length=1, max_length=60)
    ai_enabled_total: int = Field(ge=1)
    stats: ReviewStats
    budget_ms: int = Field(ge=1, le=40000)

    @model_validator(mode='after')
    def consistent_references(self):
        refs = [record.ref for record in self.records]
        if len(set(refs)) != len(refs) or len(refs) > self.ai_enabled_total or self.ai_enabled_total > self.stats.total:
            raise ValueError('invalid_records')
        total_content_utf16 = sum(utf16_length(record.content) for record in self.records)
        if total_content_utf16 > MAX_TOTAL_CONTENT_UTF16:
            raise ValueError('invalid_total_content')
        return self


class ReviewText(Contract):
    body: str
    ps: str | None


def introduces_contact_word(value: str, record_text: str) -> bool:
    """value 出现记录原文里没有的联系方式词时返回 True；record_text 须已 casefold。"""
    words = {match.group().casefold() for match in ECHOABLE_CONTACT_WORDS.finditer(value)}
    return any(word not in record_text for word in words)


def validate_output(raw: str, request: ReviewRequest) -> ReviewText:
    """校验完整 JSON、引用、长度和禁用内容；不自动重试，不接受多余字段。"""
    text = ReviewText.model_validate(json.loads(raw))
    limit = MAX_WEEK_BODY_UTF16 if request.review_type == 'WEEK' else MAX_RECENT_BODY_UTF16
    if not text.body.strip() or utf16_length(text.body) > limit:
        raise ValueError('invalid_body')
    if text.ps is not None and (not text.ps.strip() or utf16_length(text.ps) > MAX_PS_UTF16):
        raise ValueError('invalid_ps')
    allowed = {record.ref for record in request.records}
    record_text = ''.join(record.content for record in request.records).casefold()
    for value in (text.body, text.ps or ''):
        if TRUNCATION_MARKER in value or TRUNCATION_MARKER_WORD in value:
            raise ValueError('invalid_output')
        if UNSAFE.search(value) or any(ref not in allowed for ref in REFERENCE.findall(value)):
            raise ValueError('invalid_output')
        if introduces_contact_word(value, record_text):
            raise ValueError('invalid_output')
        stripped = REFERENCE.sub('', value)
        if '{{' in stripped or '}}' in stripped:
            raise ValueError('invalid_reference')
    return text


def success_response(request: ReviewRequest, body: str, ps: str | None, model: str | None) -> dict:
    """关联字段与日记回复保持相同类型，模板和内容字段独立。"""
    return {
        **request.model_dump(include={'request_id', 'job_id', 'attempt', 'source_version'}),
        'body': body, 'ps': ps, 'result_kind': 'SUPPORT' if model is None else 'MODEL',
        'model_called': model is not None, 'model_name': model, 'template_version': TEMPLATE_VERSION,
    }
