package com.xinyu.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.xinyu.entity.Topic;
import com.xinyu.mapper.TopicMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TopicService {

    @Autowired
    private TopicMapper topicMapper;

    public List<Topic> listEnabledTopics(){
        QueryWrapper<Topic> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("status",1);
        queryWrapper.orderByAsc("sort");
        queryWrapper.orderByAsc("id");

        return topicMapper.selectList(queryWrapper);
    }
}
