// src/test/java/com/zs/JsonAuthTest.java
package com.zs;

import com.zs.dto.RegisterDTO;
import com.zs.dto.LoginDTO;
import com.zs.service.UsersService;
import com.zs.vo.ResultVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class JsonAuthTest {

    @Autowired
    private UsersService usersService;

    @Test
    public void testJsonRegister() {
        System.out.println("=== 测试JSON注册 ===");

        RegisterDTO registerDTO = new RegisterDTO();
        registerDTO.setUsername("json_user_" + System.currentTimeMillis());
        registerDTO.setPassword("jsonpass123");
        registerDTO.setEmail("json_" + System.currentTimeMillis() + "@test.com");
        registerDTO.setUniversity("清华大学");
        registerDTO.setMajor("计算机科学");
        registerDTO.setGrade("大二");

        ResultVO<?> result = usersService.register(registerDTO);
        System.out.println("JSON注册结果: " + result.getSuccess());
        System.out.println("JSON注册消息: " + result.getMessage());
    }



}