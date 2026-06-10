/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-05-29 02:07
 * @description 用户画像控制器，管理Agent偏好设置
 */

package com.suke.agent.controller;

import com.suke.agent.memory.LongTermMemoryStore;
import com.suke.agent.memory.model.UserProfile;
import com.suke.agent.domain.dto.ProfileUpdateDTO;
import com.suke.agent.domain.vo.ProfileVO;
import com.suke.common.Result;
import com.suke.context.UserContext;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/agent/profile")
public class ProfileController {

    private final LongTermMemoryStore memoryStore;

    public ProfileController(LongTermMemoryStore memoryStore) {
        this.memoryStore = memoryStore;
    }

    @GetMapping
    public Result<ProfileVO> getProfile() {
        Long userId = UserContext.getCurrentId();
        if (userId == null) {
            return Result.error("请先登录");
        }
        UserProfile profile = memoryStore.getProfile(userId);
        return Result.success(toVO(profile));
    }

    @PutMapping
    public Result<String> updateProfile(@Valid @RequestBody ProfileUpdateDTO request) {
        Long userId = UserContext.getCurrentId();
        if (userId == null) {
            return Result.error("请先登录");
        }

        UserProfile profile = new UserProfile();
        profile.setIndustry(request.getIndustry());
        profile.setPreferredCharts(request.getPreferredCharts());
        profile.setDetailLevel(request.getDetailLevel());
        profile.setReportStyle(request.getReportStyle());

        memoryStore.updateProfile(userId, profile);
        return Result.success("更新成功");
    }

    private ProfileVO toVO(UserProfile profile) {
        if (profile == null) {
            return null;
        }
        return new ProfileVO()
                .setUserId(profile.getUserId())
                .setIndustry(profile.getIndustry())
                .setPreferredCharts(profile.getPreferredCharts())
                .setDetailLevel(profile.getDetailLevel())
                .setReportStyle(profile.getReportStyle());
    }
}
