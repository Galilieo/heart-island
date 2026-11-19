package com.xinyu.dto;

/** 心情与社区成功回应共用的正文、Unicode 与模型调用语义校验。 */
final class ReplyResultValidation {
    // Java → Python 内部契约的正文和模型名称上限，以 UTF-16 代码单元计数。
    private static final int MAX_REPLY_UTF16 = 2000;
    private static final int MAX_MODEL_NAME_UTF16 = 128;

    private ReplyResultValidation() {
    }

    static String validatedReply(String replyContent, String resultKind, Boolean modelCalled, String modelName) {
        String text = stripUnicode(replyContent);
        if (text == null || text.isEmpty() || text.length() > MAX_REPLY_UTF16) {
            throw invalid();
        }
        requireValidUnicode(text);
        if ("MODEL".equals(resultKind)) {
            String name = stripUnicode(modelName);
            if (!Boolean.TRUE.equals(modelCalled) || name == null || name.isEmpty()
                    || modelName.length() > MAX_MODEL_NAME_UTF16) {
                throw invalid();
            }
            requireValidUnicode(modelName);
        } else if ("SUPPORT".equals(resultKind)) {
            if (!Boolean.FALSE.equals(modelCalled) || modelName != null) {
                throw invalid();
            }
        } else {
            throw invalid();
        }
        return text;
    }

    /** 与 Python str.strip 的 Unicode 白字符口径保持一致。 */
    private static String stripUnicode(String value) {
        if (value == null) {
            return null;
        }
        int start = 0;
        int end = value.length();
        while (start < end && isWhitespace(value.charAt(start))) {
            start++;
        }
        while (end > start && isWhitespace(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(start, end);
    }

    private static boolean isWhitespace(char unit) {
        return Character.isWhitespace(unit) || Character.isSpaceChar(unit) || unit == 0x85;
    }

    private static void requireValidUnicode(String text) {
        for (int i = 0; i < text.length(); i++) {
            char unit = text.charAt(i);
            if (Character.isHighSurrogate(unit)) {
                i++;
                if (i >= text.length() || !Character.isLowSurrogate(text.charAt(i))) {
                    throw invalid();
                }
            } else if (Character.isLowSurrogate(unit)) {
                throw invalid();
            }
        }
    }

    static IllegalArgumentException invalid() {
        return new IllegalArgumentException("invalid_model_response");
    }
}
