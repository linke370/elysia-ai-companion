package com.zs.service.impl;

import com.zs.dto.*;
import com.zs.entity.Users;
import com.zs.mapper.UsersMapper;
import com.zs.service.EmailService;
import com.zs.service.UsersService;
import com.zs.util.PasswordEncoder;
import com.zs.vo.ResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class UsersServiceImpl implements UsersService {

    private final UsersMapper usersMapper;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    @Override
    @Transactional
    public ResultVO<UserVO> register(RegisterDTO registerDTO) {
        try {
            // 检查用户名是否已存在
            if (existsByUsername(registerDTO.getUsername())) {
                return ResultVO.error("用户名已存在");
            }

            // 检查邮箱是否已存在
            if (existsByEmail(registerDTO.getEmail())) {
                return ResultVO.error("邮箱已被注册");
            }

            // 创建新用户
            Users user = new Users();
            BeanUtils.copyProperties(registerDTO, user);
            user.setPasswordHash(passwordEncoder.encode(registerDTO.getPassword()));
            user.setIsActive(true);
            user.setLastLoginAt(LocalDateTime.now());

            // 设置默认性格类型
            if (user.getPersonalityType() == null) {
                user.setPersonalityType("balanced");
            }

            // 保存用户
            int result = usersMapper.insert(user);
            if (result > 0) {
                log.info("用户注册成功: {} (邮箱: {})", registerDTO.getUsername(), registerDTO.getEmail());

                // 转换为VO返回
                UserVO userVO = convertToUserVO(user);
                return ResultVO.success("注册成功", userVO);
            } else {
                return ResultVO.error("注册失败，请稍后重试");
            }
        } catch (Exception e) {
            log.error("用户注册异常: {}", e.getMessage(), e);
            return ResultVO.error("系统异常，注册失败");
        }
    }

    @Override
    public ResultVO<UserVO> login(LoginDTO loginDTO) {
        try {
            // 根据邮箱查找用户
            Users user = usersMapper.findByEmail(loginDTO.getEmail());  // 修改：改为根据邮箱查找
            if (user == null) {
                return ResultVO.error("邮箱或密码错误");
            }

            // 检查用户是否激活
            if (!user.getIsActive()) {
                return ResultVO.error("账户已被禁用，请联系管理员");
            }

            // 验证密码
            if (!passwordEncoder.matches(loginDTO.getPassword(), user.getPasswordHash())) {
                return ResultVO.error("邮箱或密码错误");
            }

            // 更新最后登录时间
            user.setLastLoginAt(LocalDateTime.now());
            usersMapper.updateById(user);

            log.info("用户登录成功 (邮箱): {}", loginDTO.getEmail());

            // 返回完整的用户信息
            UserVO userVO = convertToUserVO(user);
            return ResultVO.success("登录成功", userVO);

        } catch (Exception e) {
            log.error("用户登录异常: {}", e.getMessage(), e);
            return ResultVO.error("系统异常，登录失败");
        }
    }

    @Override
    public UserVO findByUsername(String username) {
        Users user = usersMapper.findByUsername(username);
        return user != null ? convertToUserVO(user) : null;
    }

    @Override
    public UserVO findByEmail(String email) {  // 新增方法
        Users user = usersMapper.findByEmail(email);
        return user != null ? convertToUserVO(user) : null;
    }

    @Override
    public boolean existsByUsername(String username) {
        return usersMapper.countByUsername(username) > 0;
    }

    @Override
    public boolean existsByEmail(String email) {
        return usersMapper.countByEmail(email) > 0;
    }

    @Override
    public ResultVO<String> sendVerificationCode(String email) {
        try {
            // 检查邮箱是否已被注册
            if (existsByEmail(email)) {
                return ResultVO.error("该邮箱已被注册");
            }

            // 生成验证码
            String code = emailService.generateVerificationCode();

            // 发送验证码
            emailService.sendVerificationCode(email, code);

            return ResultVO.success("验证码发送成功");

        } catch (Exception e) {
            log.error("发送验证码失败: {}", e.getMessage(), e);
            return ResultVO.error("验证码发送失败，请稍后重试");
        }
    }

    @Override
    @Transactional
    public ResultVO<UserVO> registerWithCode(RegisterDTO registerDTO) {
        try {
            // 验证验证码
            if (!emailService.verifyCode(registerDTO.getEmail(),
                    registerDTO.getVerificationCode(),
                    "REGISTER")) {
                return ResultVO.error("验证码错误或已过期");
            }

            // 检查用户名是否已存在
            if (existsByUsername(registerDTO.getUsername())) {
                return ResultVO.error("用户名已存在");
            }

            // 创建新用户（重用原有注册逻辑）
            RegisterDTO basicRegisterDTO = new RegisterDTO();
            BeanUtils.copyProperties(registerDTO, basicRegisterDTO);

            return register(basicRegisterDTO);

        } catch (Exception e) {
            log.error("验证码注册异常: {}", e.getMessage(), e);
            return ResultVO.error("系统异常，注册失败");
        }
    }

    @Override
    @Transactional
    public ResultVO<UserVO> updateUser(String username, UpdateUserDTO updateDTO) {
        try {
            // 查找用户（仍根据用户名）
            Users user = usersMapper.findByUsername(username);
            if (user == null) {
                return ResultVO.error("用户不存在");
            }

            // 检查新邮箱是否已被其他用户使用
            if (updateDTO.getNewEmail() != null &&
                    !updateDTO.getNewEmail().equals(user.getEmail())) {
                if (existsByEmail(updateDTO.getNewEmail())) {
                    return ResultVO.error("邮箱已被其他用户使用");
                }
                user.setEmail(updateDTO.getNewEmail());
            }

            // 更新其他字段
            if (updateDTO.getStudentId() != null) {
                user.setStudentId(updateDTO.getStudentId());
            }
            if (updateDTO.getUniversity() != null) {
                user.setUniversity(updateDTO.getUniversity());
            }
            if (updateDTO.getMajor() != null) {
                user.setMajor(updateDTO.getMajor());
            }
            if (updateDTO.getGrade() != null) {
                user.setGrade(updateDTO.getGrade());
            }
            if (updateDTO.getPersonalityType() != null) {
                user.setPersonalityType(updateDTO.getPersonalityType());
            }
            if (updateDTO.getEmotionalTendency() != null) {
                user.setEmotionalTendency(updateDTO.getEmotionalTendency());
            }

            // 更新时间戳
            user.setUpdatedAt(LocalDateTime.now());

            // 保存更新
            int result = usersMapper.updateById(user);
            if (result > 0) {
                log.info("用户信息更新成功: {} (邮箱: {})", username, user.getEmail());
                UserVO userVO = convertToUserVO(user);
                return ResultVO.success("用户信息更新成功", userVO);
            } else {
                return ResultVO.error("用户信息更新失败");
            }

        } catch (Exception e) {
            log.error("更新用户信息异常: {}", e.getMessage(), e);
            return ResultVO.error("系统异常，更新失败");
        }
    }

    @Override
    @Transactional
    public ResultVO<String> deleteUser(String email, String password) {
        try {
            // 根据邮箱查找用户
            Users user = usersMapper.findByEmail(email);
            if (user == null) {
                return ResultVO.error("邮箱或密码错误");
            }

            // 验证密码
            if (!passwordEncoder.matches(password, user.getPasswordHash())) {
                return ResultVO.error("邮箱或密码错误，无法删除账户");
            }

            // 逻辑删除：将 isActive 设为 false
            user.setIsActive(false);
            user.setUpdatedAt(LocalDateTime.now());

            int result = usersMapper.updateById(user);
            if (result > 0) {
                log.info("用户删除成功（逻辑删除）: {} (邮箱: {})", user.getUsername(), email);
                return ResultVO.success("账户已成功删除");
            } else {
                return ResultVO.error("账户删除失败");
            }

        } catch (Exception e) {
            log.error("删除用户异常: {}", e.getMessage(), e);
            return ResultVO.error("系统异常，删除失败");
        }
    }

    @Override
    @Transactional
    public ResultVO<String> changePassword(String email, ChangePasswordDTO changePasswordDTO) {
        try {
            // 验证两次输入的新密码是否一致
            if (!changePasswordDTO.getNewPassword().equals(changePasswordDTO.getConfirmPassword())) {
                return ResultVO.error("两次输入的新密码不一致");
            }

            // 根据邮箱查找用户
            Users user = usersMapper.findByEmail(email);
            if (user == null) {
                return ResultVO.error("邮箱或密码错误");
            }

            // 验证旧密码
            if (!passwordEncoder.matches(changePasswordDTO.getOldPassword(), user.getPasswordHash())) {
                return ResultVO.error("旧密码错误");
            }

            // 更新密码
            user.setPasswordHash(passwordEncoder.encode(changePasswordDTO.getNewPassword()));
            user.setUpdatedAt(LocalDateTime.now());

            int result = usersMapper.updateById(user);
            if (result > 0) {
                log.info("密码修改成功: {} (邮箱: {})", user.getUsername(), email);
                return ResultVO.success("密码修改成功");
            } else {
                return ResultVO.error("密码修改失败");
            }

        } catch (Exception e) {
            log.error("修改密码异常: {}", e.getMessage(), e);
            return ResultVO.error("系统异常，密码修改失败");
        }
    }

    @Override
    @Transactional
    public ResultVO<String> toggleUserStatus(String email, Boolean isActive) {
        try {
            // 根据邮箱查找用户
            Users user = usersMapper.findByEmail(email);
            if (user == null) {
                return ResultVO.error("用户不存在");
            }

            // 更新激活状态
            user.setIsActive(isActive);
            user.setUpdatedAt(LocalDateTime.now());

            int result = usersMapper.updateById(user);
            if (result > 0) {
                String action = isActive ? "激活" : "禁用";
                log.info("用户状态更新成功: {} (邮箱: {}) -> {}", user.getUsername(), email, action);
                return ResultVO.success("用户已成功" + action);
            } else {
                return ResultVO.error("用户状态更新失败");
            }

        } catch (Exception e) {
            log.error("更新用户状态异常: {}", e.getMessage(), e);
            return ResultVO.error("系统异常，状态更新失败");
        }
    }

    /**
     * 将 Users 实体转换为 UserVO
     */
    private UserVO convertToUserVO(Users user) {
        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(user, userVO);
        return userVO;
    }
}