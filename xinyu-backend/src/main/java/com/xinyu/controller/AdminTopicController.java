package com.xinyu.controller;

import com.xinyu.common.Result;
import com.xinyu.entity.Topic;
import com.xinyu.service.TopicService;
import com.xinyu.service.UserService;
import com.xinyu.utils.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/topic")
public class AdminTopicController {

    @Autowired
    private TopicService topicService;

    @Autowired
    private UserService userService;

    @Autowired
    private JwtUtil jwtUtil;

    @GetMapping("/list")
    public Result<List<Topic>> list(@RequestHeader(value = "token", required = false) String token,
                                    @RequestParam(required = false) String keyword,
                                    @RequestParam(required = false) Integer status) {
        Long adminId = getAdminId(token);

        if (adminId == null) {
            return Result.error("权限不足");
        }

        List<Topic> topics = topicService.listAdminTopics(keyword, status);
        return Result.success("查询成功", topics);
    }

    @PostMapping
    public Result<Topic> add(@RequestHeader(value = "token", required = false) String token,
                             @RequestBody Topic topic) {
        Long adminId = getAdminId(token);

        if (adminId == null) {
            return Result.error("权限不足");
        }

        if (topic.getName() == null || topic.getName().trim().isEmpty()) {
            return Result.error("话题名称不能为空");
        }

        if(topicService.existsByName(topic.getName(),null)){
            return Result.error("话题已存在");
        }

        Topic savedTopic = topicService.addTopic(topic);
        return Result.success("新增成功", savedTopic);
    }

    @PutMapping("/{id}")
    public Result<Topic> update(@RequestHeader(value = "token", required = false) String token,
                                @PathVariable Long id,
                                @RequestBody Topic topic) {
        Long adminId = getAdminId(token);

        if (adminId == null) {
            return Result.error("权限不足");
        }

        if (id == null) {
            return Result.error("话题id不能为空");
        }

        if (topic.getName() == null || topic.getName().trim().isEmpty()) {
            return Result.error("话题名称不能为空");
        }

        if(topicService.existsByName(topic.getName(),id)){
            return Result.error("话题名称已存在");
        }

        Topic updatedTopic = topicService.updateTopic(id, topic);

        if (updatedTopic == null) {
            return Result.error("话题不存在");
        }

        return Result.success("修改成功", updatedTopic);
    }

    @PutMapping("/{id}/status")
    public Result<Boolean> updateStatus(@RequestHeader(value = "token", required = false) String token,
                                        @PathVariable Long id,
                                        @RequestParam Integer status) {
        Long adminId = getAdminId(token);

        if (adminId == null) {
            return Result.error("权限不足");
        }

        if (id == null) {
            return Result.error("话题id不能为空");
        }

        if (status == null || (status != 0 && status != 1)) {
            return Result.error("话题状态只能是0或1");
        }

        Topic topic = topicService.updateStatus(id, status);

        if (topic == null) {
            return Result.error("话题不存在");
        }

        return Result.success("修改成功", true);
    }

    private Long getAdminId(String token) {
        if (token == null || token.trim().isEmpty()) {
            return null;
        }

        Long userId = jwtUtil.getUserId(token);
        return userService.isAdmin(userId) ? userId : null;
    }
}