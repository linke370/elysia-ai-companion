package com.zs.controller;

import com.zs.dto.EmailCodeDTO;
import com.zs.dto.RegisterDTO;
import com.zs.dto.LoginDTO;
import com.zs.dto.UpdateUserDTO;
import com.zs.dto.ChangePasswordDTO;
import com.zs.service.UsersService;
import com.zs.vo.ResultVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class UserController {

    private final UsersService usersService;

    /**
     * 用户注册
     */
    @PostMapping("/register")
    public ResultVO<?> register(@Valid @RequestBody RegisterDTO registerDTO) {
        return usersService.register(registerDTO);
    }

    /**
     * 用户登录
     */
    @PostMapping("/login")
    public ResultVO<?> login(@Valid @RequestBody LoginDTO loginDTO) {
        return usersService.login(loginDTO);
    }

    /**
     * 检查用户名是否存在
     */
    @GetMapping("/check-username")
    public ResultVO<Boolean> checkUsername(@RequestParam String username) {
        boolean exists = usersService.existsByUsername(username);
        return ResultVO.success(exists ? "用户名已存在" : "用户名可用", exists);
    }

    /**
     * 检查邮箱是否存在
     */
    @GetMapping("/check-email")
    public ResultVO<Boolean> checkEmail(@RequestParam String email) {
        boolean exists = usersService.existsByEmail(email);
        return ResultVO.success(exists ? "邮箱已被注册" : "邮箱可用", exists);
    }

    /**
     * 根据用户名获取用户信息
     */
    @GetMapping("/user/{username}")
    public ResultVO<?> getUserInfo(@PathVariable String username) {
        var userVO = usersService.findByUsername(username);
        if (userVO != null) {
            return ResultVO.success(userVO);
        } else {
            return ResultVO.error("用户不存在");
        }
    }

    /**
     * 发送邮箱验证码
     */
    @PostMapping("/send-code")
    public ResultVO<String> sendVerificationCode(@Valid @RequestBody EmailCodeDTO emailCodeDTO) {
        return usersService.sendVerificationCode(emailCodeDTO.getEmail());
    }

    /**
     * 使用验证码注册
     */
    @PostMapping("/register-with-code")
    public ResultVO<?> registerWithCode(@Valid @RequestBody RegisterDTO registerDTO) {
        return usersService.registerWithCode(registerDTO);
    }

    /**
     * 更新用户信息
     */
    @PutMapping("/user/{username}")
    public ResultVO<?> updateUser(
            @PathVariable String username,
            @Valid @RequestBody UpdateUserDTO updateDTO) {
        return usersService.updateUser(username, updateDTO);
    }

    /**
     * 删除用户（逻辑删除）
     */
    @DeleteMapping("/user/{username}")
    public ResultVO<String> deleteUser(
            @PathVariable String username,
            @RequestParam String password) {
        return usersService.deleteUser(username, password);
    }

    /**
     * 修改密码
     */
    @PostMapping("/user/{username}/change-password")
    public ResultVO<String> changePassword(
            @PathVariable String username,
            @Valid @RequestBody ChangePasswordDTO changePasswordDTO) {
        return usersService.changePassword(username, changePasswordDTO);
    }

    /**
     * 激活/禁用用户（管理员功能）
     */
    @PutMapping("/user/{username}/status")
    public ResultVO<String> toggleUserStatus(
            @PathVariable String username,
            @RequestParam Boolean isActive) {
        return usersService.toggleUserStatus(username, isActive);
    }
}