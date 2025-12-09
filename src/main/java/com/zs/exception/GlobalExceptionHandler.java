// src/main/java/com/zs/exception/GlobalExceptionHandler.java
package com.zs.exception;

import com.zs.vo.ResultVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.validation.ConstraintViolationException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 参数验证异常处理 - @RequestBody
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResultVO<String> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        log.warn("参数验证失败: {}", message);
        return ResultVO.error(message);
    }

    /**
     * 参数验证异常处理 - @RequestParam
     */
    @ExceptionHandler(BindException.class)
    public ResultVO<String> handleBindException(BindException e) {
        String message = e.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        log.warn("参数验证失败: {}", message);
        return ResultVO.error(message);
    }

    /**
     * 参数验证异常处理 - @RequestParam
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResultVO<String> handleConstraintViolationException(ConstraintViolationException e) {
        String message = e.getConstraintViolations().iterator().next().getMessage();
        log.warn("参数验证失败: {}", message);
        return ResultVO.error(message);
    }

    /**
     * 其他异常处理
     */
    @ExceptionHandler(Exception.class)
    public ResultVO<String> handleException(Exception e) {
        log.error("系统异常: {}", e.getMessage(), e);
        return ResultVO.error("系统繁忙，请稍后重试");
    }
}