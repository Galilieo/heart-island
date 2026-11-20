package com.xinyu.vo;

import com.fasterxml.jackson.annotation.JsonInclude;

/** 仅返回给本次提交者的保存响应。supportText 为命中时的支持文案，否则为 null。 */
public record CommunityWriteVO(@JsonInclude(JsonInclude.Include.ALWAYS) String supportText) {
}
