/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-05-29 02:07
 * @description 用户画像MyBatis映射器
 */

package com.suke.agent.memory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.suke.agent.memory.model.UserProfile;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserProfileMapper extends BaseMapper<UserProfile> {
}
