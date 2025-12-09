// src/main/java/com/zs/vo/ResultVO.java
package com.zs.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import java.io.Serializable;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResultVO<T> implements Serializable {
    private Boolean success;
    private String message;
    private T data;
    private Integer code;

    // 成功静态方法
    public static <T> ResultVO<T> success() {
        return success("操作成功");
    }

    public static <T> ResultVO<T> success(String message) {
        ResultVO<T> result = new ResultVO<>();
        result.setSuccess(true);
        result.setMessage(message);
        result.setCode(200);
        return result;
    }

    public static <T> ResultVO<T> success(String message, T data) {
        ResultVO<T> result = new ResultVO<>();
        result.setSuccess(true);
        result.setMessage(message);
        result.setData(data);
        result.setCode(200);
        return result;
    }

    public static <T> ResultVO<T> success(T data) {
        return success("操作成功", data);
    }

    // 失败静态方法
    public static <T> ResultVO<T> error(String message) {
        ResultVO<T> result = new ResultVO<>();
        result.setSuccess(false);
        result.setMessage(message);
        result.setCode(400);
        return result;
    }

    public static <T> ResultVO<T> error(String message, Integer code) {
        ResultVO<T> result = new ResultVO<>();
        result.setSuccess(false);
        result.setMessage(message);
        result.setCode(code);
        return result;
    }

    // 新增：带数据的错误方法
    public static <T> ResultVO<T> error(String message, T data) {
        ResultVO<T> result = new ResultVO<>();
        result.setSuccess(false);
        result.setMessage(message);
        result.setData(data);
        result.setCode(400);
        return result;
    }

    public static <T> ResultVO<T> error(String message, T data, Integer code) {
        ResultVO<T> result = new ResultVO<>();
        result.setSuccess(false);
        result.setMessage(message);
        result.setData(data);
        result.setCode(code);
        return result;
    }
}