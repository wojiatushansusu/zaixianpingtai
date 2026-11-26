package com.exam.common.exception;

import com.exam.common.result.Result;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.time.format.DateTimeParseException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 业务异常
     */
    @ExceptionHandler(BusinessException.class)
    public Result<?> handleBusinessException(BusinessException e) {
        log.warn("业务异常: {}", e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    /**
     * 参数校验异常 - @Valid @RequestBody
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> handleValidException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        log.warn("参数校验失败: {}", message);
        return Result.error(400, message);
    }

    /**
     * 参数校验异常 - @Validated
     */
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> handleConstraintViolation(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining(", "));
        log.warn("约束校验失败: {}", message);
        return Result.error(400, message);
    }

    /**
     * 绑定异常
     */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> handleBindException(BindException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        log.warn("参数绑定失败: {}", message);
        return Result.error(400, message);
    }

    /**
     * Spring Security - 认证失败
     */
    @ExceptionHandler(BadCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Result<?> handleBadCredentials(BadCredentialsException e) {
        // 正常登录失败不是系统故障，用 WARN，别污染 ERROR 日志
        log.warn("登录失败: {}", e.getMessage());
        return Result.error(401, "用户名或密码错误");
    }

    /**
     * 请求体解析失败（字段类型不对、JSON 语法错、时间格式不对）。
     *
     * <p>不单独处理会落到通用分支返回 500「系统繁忙」——把**调用方的参数错误**
     * 报成**服务端故障**。实测踩到的是时间格式：前端提交 ISO
     * （`2026-11-27T00:10:00`），外部调用方若写成 `2026-11-27 00:10:00` 就 500，
     * 排查时会一路怀疑后端。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> handleUnreadable(HttpMessageNotReadableException e) {
        String cause = e.getMostSpecificCause() != null ? e.getMostSpecificCause().getMessage() : "";
        log.warn("请求体解析失败: {}", cause);

        // 按异常类型判断，不按文案匹配。
        // 实际抛的是 DateTimeParseException，message 形如
        // "Text '2026-11-27 20:00:00' could not be parsed at index 10" ——
        // 里面并没有 "LocalDateTime" 字样，靠字符串匹配的分支永远不会命中。
        Throwable root = e.getMostSpecificCause();
        if (root instanceof DateTimeParseException
                || (root != null && root.getMessage() != null
                    && root.getMessage().contains("could not be parsed"))) {
            return Result.error(400, "时间格式不正确，应为 yyyy-MM-ddTHH:mm:ss（例：2026-11-27T09:00:00）");
        }
        return Result.error(400, "请求参数格式不正确");
    }

    /**
     * 路径不存在（含静态资源）。
     *
     * 不单独处理的话会落到通用 Exception 分支：返回 500「系统繁忙」并记一条带堆栈的 ERROR。
     * 后果有两个：客户端无法区分「接口写错了」和「服务挂了」；
     * 扫描器、拼错的路径、旧版客户端残留请求都会在监控里变成「系统故障」。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<?> handleNoResource(NoResourceFoundException e) {
        log.debug("接口不存在: {}", e.getResourcePath());
        return Result.error(404, "接口不存在");
    }

    /**
     * 兜住其余认证异常。
     *
     * 认证失败原本会落到通用 Exception 分支：返回 500「系统繁忙」并记 ERROR，
     * 客户端看到的是"服务器挂了"，运维看到的是一条假故障。
     */
    @ExceptionHandler(AuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Result<?> handleAuthentication(AuthenticationException e) {
        log.warn("认证失败: {}", e.getMessage());
        return Result.error(401, "用户名或密码错误");
    }

    /**
     * Spring Security - 权限不足
     */
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<?> handleAccessDenied(AccessDeniedException e) {
        return Result.error(403, "权限不足");
    }

    /**
     * 文件上传大小超限
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Result<?> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        return Result.error(7003, "文件大小超限");
    }

    /**
     * 数字格式化异常（参数类型错误）
     */
    @ExceptionHandler(NumberFormatException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> handleNumberFormatException(NumberFormatException e) {
        log.warn("数字格式错误: {}", e.getMessage());
        return Result.error(400, "参数格式错误");
    }

    /**
     * 非法参数异常
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("非法参数: {}", e.getMessage());
        return Result.error(400, "请求参数不合法");
    }

    /**
     * 其他异常
     * 注意：不设置 @ResponseStatus，统一以 HTTP 200 + 业务 code 返回，
     * 避免前端 Axios 同时触发 error 回调和业务层错误提示，导致重复弹窗。
     */
    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception e) {
        log.error("系统异常: ", e);
        return Result.error("系统繁忙，请稍后重试");
    }
}
