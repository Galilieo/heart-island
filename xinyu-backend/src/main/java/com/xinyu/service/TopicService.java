package com.xinyu.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.xinyu.entity.Topic;
import com.xinyu.mapper.TopicMapper;
import com.xinyu.entity.CommunityPost;
import com.xinyu.mapper.CommunityPostMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TopicService {

    @Autowired
    private TopicMapper topicMapper;

    @Autowired
    private CommunityPostMapper communityPostMapper;

    public List<Topic> listEnabledTopics(){
        QueryWrapper<Topic> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("status",1);
        queryWrapper.orderByAsc("sort");
        queryWrapper.orderByAsc("id");

        return topicMapper.selectList(queryWrapper);
    }

    public List<Topic> listAdminTopics(String keyword,Integer status){
        QueryWrapper<Topic> queryWrapper = new QueryWrapper<>();

        if(keyword !=null && !keyword.trim().isEmpty()){
            queryWrapper.like("name",keyword.trim());
        }

        if(status != null){
            queryWrapper.eq("status",status);
        }

        queryWrapper.orderByAsc("sort");
        queryWrapper.orderByAsc("id");

        List<Topic> topics = topicMapper.selectList(queryWrapper);

        for(Topic topic : topics){
            QueryWrapper<CommunityPost> postQuery = new QueryWrapper<>();
            postQuery.eq("topic_id",topic.getId());
            topic.setPostCount(communityPostMapper.selectCount(postQuery).intValue());
        }
        return topics;
    }

    public Boolean existsByName(String name, Long excludeId){
        if(name == null || name.trim().isEmpty()){
            return false;
        }

        QueryWrapper<Topic> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("name",name.trim());

        if(excludeId != null){
            queryWrapper.ne("id",excludeId);
        }

        return topicMapper.selectCount(queryWrapper)>0;
    }

    public Topic addTopic(Topic topic){
        topic.setName(topic.getName().trim());

        if(topic.getDescription() !=null){
            topic.setDescription(topic.getDescription().trim());
        }

        if(topic.getSort() == null){
            topic.setSort(0);
        }

        if(topic.getStatus() == null){
            topic.setStatus(1);
        }

        topicMapper.insert(topic);
        return topic;
    }

    public Topic updateTopic(Long id,Topic topic){
        Topic dbTopic = topicMapper.selectById(id);

        if(dbTopic == null){
            return null;
        }

        dbTopic.setName(topic.getName().trim());

        if(topic.getDescription() !=null){
            dbTopic.setDescription(topic.getDescription().trim());
        }else{
            dbTopic.setDescription(null);
        }

        if(topic.getSort() != null){
            dbTopic.setSort(topic.getSort());
        }

        topicMapper.updateById(dbTopic);
        return dbTopic;
    }

    public Topic updateStatus(Long id, Integer status) {
        Topic topic = topicMapper.selectById(id);

        if (topic == null) {
            return null;
        }

        topic.setStatus(status);
        topicMapper.updateById(topic);

        return topic;
    }
}
