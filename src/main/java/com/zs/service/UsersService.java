package com.zs.service;

import com.zs.dto.RegisterDTO;
import com.zs.dto.LoginDTO;
import com.zs.dto.UpdateUserDTO;
import com.zs.dto.ChangePasswordDTO;
import com.zs.dto.UserVO;
import com.zs.vo.ResultVO;

public interface UsersService {

    /**
     * 用户注册
     */
    ResultVO<UserVO> register(RegisterDTO registerDTO);

    /**
     * 用户登录（邮箱登录）
     * @param loginDTO 包含邮箱和密码
     * @return 登录结果和用户信息
     */
    ResultVO<UserVO> login(LoginDTO loginDTO);

    /**
     * 根据用户名查找用户
     */
    UserVO findByUsername(String username);

    /**
     * 根据邮箱查找用户
     */
    UserVO findByEmail(String email);  // 新增方法

    /**
     * 检查用户名是否存在
     */
    boolean existsByUsername(String username);

    /**
     * 检查邮箱是否存在
     */
    boolean existsByEmail(String email);

    /**
     * 发送邮箱验证码
     */
    ResultVO<String> sendVerificationCode(String email);

    /**
     * 使用验证码注册
     */
    ResultVO<UserVO> registerWithCode(RegisterDTO registerDTO);

    /**
     * 更新用户信息
     */
    ResultVO<UserVO> updateUser(String username, UpdateUserDTO updateDTO);

    /**
     * 删除用户（逻辑删除）
     */
    ResultVO<String> deleteUser(String email, String password);  // 参数改为email

    /**
     * 修改密码（邮箱登录）
     */
    ResultVO<String> changePassword(String email, ChangePasswordDTO changePasswordDTO);  // 参数改为email

    /**
     * 激活/禁用用户（管理员功能）
     */
    ResultVO<String> toggleUserStatus(String email, Boolean isActive);  // 参数改为email
}