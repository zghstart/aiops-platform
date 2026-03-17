package com.aiops.annotation;

import com.aiops.domain.AuditLog;

import java.lang.annotation.*;

/**
 * 审计日志注解
 * 标记需要记录审计日志的方法
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Auditable {

    /**
     * 操作类型
     */
    AuditLog.OperationType operationType();

    /**
     * 资源类型
     */
    String resourceType() default "";

    /**
     * 操作描述
     */
    String description() default "";

    /**
     * 是否为敏感操作
     */
    boolean sensitive() default false;

    /**
     * 是否记录请求参数
     */
    boolean logParams() default true;

    /**
     * 是否记录响应结果
     */
    boolean logResult() default false;
}
