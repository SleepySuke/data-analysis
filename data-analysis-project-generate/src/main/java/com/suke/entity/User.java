package com.suke.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import java.time.LocalDateTime;
import java.io.Serializable;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * <p>
 * 用户
 * </p>
 *
 * @author author
 * @since 2025-10-16
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("user")
@ApiModel(name="User对象", description="用户")
public class User implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(name = "id")
    @TableId(name = "id", type = IdType.AUTO)
    private Long id;

    @Schema(name = "账号")
    private String userAccount;

    @Schema(name = "密码")
    private String userPassword;

    @Schema(name = "用户昵称")
    private String userName;

    @Schema(name = "用户头像")
    private String userAvatar;

    @Schema(name = "用户角色：user/admin")
    private String userRole;

    @Schema(name = "创建时间")
    private LocalDateTime createTime;

    @Schema(name = "更新时间")
    private LocalDateTime updateTime;

    @Schema(name = "是否删除")
    private Integer isDelete;


}
