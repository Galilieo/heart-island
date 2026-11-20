package com.xinyu.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xinyu.common.Result;
import com.xinyu.dto.CommunityPostAddDTO;
import com.xinyu.dto.CommunityPostUpdateDTO;
import com.xinyu.entity.CommunityPost;
import com.xinyu.service.CommunityPostService;
import com.xinyu.utils.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 帖子 HTTP 入口，读取观看者身份，保存时把任务状态随写响应返回。 */
@RestController
@RequestMapping("/community/post")
public class CommunityPostController {

    // 公开列表分页契约：默认返回 10 条，最多 50 条以限制单页查询和响应量。
    private static final String DEFAULT_POST_PAGE_SIZE = "10";
    private static final long MAX_POST_PAGE_SIZE = 50L;

    @Autowired
    private CommunityPostService communityPostService;

    @Autowired
    private JwtUtil jwtUtil;

    @GetMapping("/list")
    public Result<Page<CommunityPost>> list(
            @RequestParam(required = false) Long topicId,
            @RequestParam(required = false) Boolean mine,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = DEFAULT_POST_PAGE_SIZE) long pageSize,
            @RequestHeader(value = "token", required = false) String token) {
        Long userId = null;

        if (token != null && !token.trim().isEmpty()) {
            userId = jwtUtil.getUserId(token);
        }

        if (Boolean.TRUE.equals(mine) && userId == null) {
            return Result.error("请先登录");
        }

        if (pageNum < 1) {
            return Result.error("页码不能小于1");
        }
        if (pageSize < 1) {
            return Result.error("每页条数不能小于1");
        }

        Page<CommunityPost> page = new Page<>(pageNum, Math.min(pageSize, MAX_POST_PAGE_SIZE));
        Page<CommunityPost> posts = communityPostService.pagePosts(page, topicId, userId, mine, sort);
        return Result.success(posts);
    }

    @GetMapping("/favorites")
    public Result<List<CommunityPost>> favorites(@RequestHeader("token") String token){
        Long userId = jwtUtil.getUserId(token);
        List<CommunityPost> posts = communityPostService.listFavorites(userId);
        return Result.success(posts);
    }

    @PostMapping("/add")
    public Result<?> add(@RequestBody CommunityPostAddDTO dto,
                         @RequestHeader("token") String token){
        if(dto.getTopicId()==null){
            return Result.error("请选择一个话题");
        }

        if(!communityPostService.isTopicAvailable(dto.getTopicId())){
            return Result.error("话题不存在或已被禁用");
        }

        if (dto.getMoodType() == null || dto.getMoodType().trim().isEmpty()) {
            return Result.error("请选择当前情绪");
        }

        if (dto.getContent() == null || dto.getContent().trim().isEmpty()) {
            return Result.error("请写一点想说的话");
        }

        if (dto.getContent().length() > 800) {
            return Result.error("内容不能超过800个字");
        }

        Long userId = jwtUtil.getUserId(token);

        CommunityPostService.PostSaveResult result = communityPostService.add(dto, userId);

        if (result.saved()) {
            return Result.success("发布成功", result.response());
        }
        return Result.error("发布失败");
    }

    @PostMapping("/like")
    public Result<?> like(Long postId, @RequestHeader("token") String token) {
        if (postId == null) {
            return Result.error("请选择要共情的帖子");
        }

        Long userId = jwtUtil.getUserId(token);
        Boolean success = communityPostService.like(postId, userId);

        if (success) {
            return Result.success("共情成功");
        }

        return Result.error("共情失败");
    }

    @PostMapping("/unlike")
    public Result<?> unlike(Long postId, @RequestHeader("token") String token) {
        if (postId == null) {
            return Result.error("请选择要取消共情的帖子");
        }

        Long userId = jwtUtil.getUserId(token);
        Boolean success = communityPostService.unlike(postId, userId);

        if (success) {
            return Result.success("已取消共情");
        }

        return Result.error("取消共情失败");
    }

    @PostMapping("/delete")
    public Result<?> delete(Long id, @RequestHeader("token") String token) {
        if (id == null) {
            return Result.error("请选择要删除的帖子");
        }

        Long userId = jwtUtil.getUserId(token);
        Boolean success = communityPostService.delete(id, userId);

        if (success) {
            return Result.success("删除成功");
        }

        return Result.error("删除失败");
    }

    @PostMapping("/update")
    public Result<?> update(@RequestBody CommunityPostUpdateDTO dto,
                            @RequestHeader("token") String token) {
        if (dto.getId() == null) {
            return Result.error("请选择要修改的帖子");
        }

        if(!communityPostService.isTopicAvailable(dto.getTopicId())){
            return Result.error("话题不存在或已被禁用");
        }

        if (dto.getTopicId() == null) {
            return Result.error("请选择一个话题");
        }

        if (dto.getMoodType() == null || dto.getMoodType().trim().isEmpty()) {
            return Result.error("请选择当前情绪");
        }

        if (dto.getContent() == null || dto.getContent().trim().isEmpty()) {
            return Result.error("请写一点想说的话");
        }

        if (dto.getContent().length() > 800) {
            return Result.error("内容不能超过800个字");
        }

        Long userId = jwtUtil.getUserId(token);
        CommunityPostService.PostSaveResult result = communityPostService.update(dto, userId);

        if (result.saved()) {
            return Result.success("修改成功", result.response());
        }

        return Result.error("修改失败");
    }

    @PostMapping("/favorite")
    public Result<?> favorite(Long postId, @RequestHeader("token") String token) {
        if (postId == null) {
            return Result.error("请选择要收藏的帖子");
        }

        Long userId = jwtUtil.getUserId(token);
        Boolean success = communityPostService.favorite(postId, userId);

        if (success) {
            return Result.success("收藏成功");
        }

        return Result.error("收藏失败");
    }

    @PostMapping("/unfavorite")
    public Result<?> unfavorite(Long postId, @RequestHeader("token") String token) {
        if (postId == null) {
            return Result.error("请选择要取消收藏的帖子");
        }

        Long userId = jwtUtil.getUserId(token);
        Boolean success = communityPostService.unfavorite(postId, userId);

        if (success) {
            return Result.success("已取消收藏");
        }

        return Result.error("取消收藏失败");
    }

    @GetMapping("/detail")
    public Result<CommunityPost> detail(Long id,
                                        @RequestHeader(value = "token",required = false) String token){
        if(id == null){
            return Result.error("请选择查看的帖子");
        }

        Long userId = null;

        if(token !=null && !token.trim().isEmpty()){
            userId=jwtUtil.getUserId(token);
        }

        CommunityPost post = communityPostService.detail(id,userId);

        if(post == null){
            return Result.error("帖子不存在或者已隐藏");
        }

        return Result.success(post);
    }
}
