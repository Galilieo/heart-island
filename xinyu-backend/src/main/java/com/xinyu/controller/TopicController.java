package com.xinyu.controller;


import com.xinyu.common.Result;
import com.xinyu.entity.Topic;
import com.xinyu.service.TopicService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/topic")
public class TopicController {

    @Autowired
    private TopicService topicService;

    @GetMapping("list")
    public Result<List<Topic>> list(){
        List<Topic> topics = topicService.listEnabledTopics();
        return Result.success("查询成功",topics);
    }
}
