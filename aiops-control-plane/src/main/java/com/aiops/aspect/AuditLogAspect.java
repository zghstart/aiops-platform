package com.aiops.aspect;

import com.aiops.annotation.Auditable;
import com.aiops.domain.AuditLog;
import com.aiops.repository.AuditLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 审计日志切面
 * 自动拦截标记了@Auditable注解的方法并记录审计日志
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditLogAspect {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    @Around("@annotation(com.aiops.annotation.Auditable)")
    public Object recordAuditLog(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();

        // 获取注解
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Auditable auditable = method.getAnnotation(Auditable.class);

        // 获取请求信息
        HttpServletRequest request = getHttpRequest();

        // 构建审计日志
        AuditLog.AuditLogBuilder builder = AuditLog.builder()
            .operationType(auditable.operationType())
            .resourceType(auditable.resourceType())
            .operationDesc(auditable.description())
            .isSensitive(auditable.sensitive())
            .createdAt(LocalDateTime.now());

        // 设置用户信息
        setUserInfo(builder, request);

        // 设置请求信息
        if (request != null) {
            builder.httpMethod(request.getMethod())
                .requestPath(request.getRequestURI())
                .clientIp(getClientIp(request))
                .userAgent(request.getHeader("User-Agent"));

            // 记录请求参数
            if (auditable.logParams()) {
                try {
                    String params = buildParams(joinPoint, signature);
                    builder.requestParams(params);
                } catch (Exception e) {
                    log.warn("Failed to serialize request params: {}", e.getMessage());
                }
            }
        }

        // 执行目标方法
        Object result = null;
        Throwable exception = null;
        try {
            result = joinPoint.proceed();
            builder.isSuccess(true);
        } catch (Throwable t) {
            exception = t;
            builder.isSuccess(false)
                .errorMessage(t.getMessage());
            throw t;
        } finally {
            // 计算响应时间
            long responseTime = System.currentTimeMillis() - startTime;
            builder.responseTimeMs(responseTime);

            // 设置响应状态
            if (request != null) {
                HttpServletResponse response = getHttpResponse();
                if (response != null) {
                    builder.responseStatus(response.getStatus());
                }
            }

            // 保存审计日志
            try {
                AuditLog auditLog = builder.build();
                auditLogRepository.save(auditLog);

                // 敏感操作额外输出日志
                if (auditable.sensitive()) {
                    log.info("[SENSITIVE OPERATION] User: {}, Operation: {}, Resource: {}, Time: {}ms",
                        auditLog.getUserId(),
                        auditLog.getOperationType(),
                        auditLog.getResourceType() + ":" + auditLog.getResourceId(),
                        responseTime);
                }
            } catch (Exception e) {
                log.error("Failed to save audit log: {}", e.getMessage(), e);
            }
        }

        return result;
    }

    /**
     * 获取HTTP请求
     */
    private HttpServletRequest getHttpRequest() {
        ServletRequestAttributes attributes =
            (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }

    /**
     * 获取HTTP响应
     */
    private HttpServletResponse getHttpResponse() {
        ServletRequestAttributes attributes =
            (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getResponse() : null;
    }

    /**
     * 设置用户信息
     */
    private void setUserInfo(AuditLog.AuditLogBuilder builder, HttpServletRequest request) {
        // 从Spring Security获取用户信息
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            builder.userId(authentication.getName());

            // 如果有用户详细信息，设置用户名
            Object principal = authentication.getPrincipal();
            if (principal instanceof org.springframework.security.core.userdetails.User) {
                builder.userName(((org.springframework.security.core.userdetails.User) principal).getUsername());
            }

            // 从认证信息中获取租户ID（假设存储在details中）
            if (authentication.getDetails() instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> details = (Map<String, Object>) authentication.getDetails();
                if (details.containsKey("tenantId")) {
                    builder.tenantId((String) details.get("tenantId"));
                }
            }
        } else if (request != null) {
            // 从请求头获取租户ID（用于API Key认证）
            String tenantId = request.getHeader("X-Tenant-Id");
            if (tenantId != null) {
                builder.tenantId(tenantId);
            }
        }

        // 默认租户
        if (builder.build().getTenantId() == null) {
            builder.tenantId("default");
        }
    }

    /**
     * 构建请求参数
     */
    private String buildParams(ProceedingJoinPoint joinPoint, MethodSignature signature) {
        try {
            String[] paramNames = signature.getParameterNames();
            Object[] args = joinPoint.getArgs();

            if (paramNames == null || args == null || args.length == 0) {
                return null;
            }

            Map<String, Object> params = new HashMap<>();
            for (int i = 0; i < paramNames.length && i < args.length; i++) {
                Object arg = args[i];

                // 跳过不需要记录的参数类型
                if (arg instanceof HttpServletRequest ||
                    arg instanceof HttpServletResponse ||
                    arg instanceof MultipartFile) {
                    continue;
                }

                params.put(paramNames[i], arg);
            }

            if (params.isEmpty()) {
                return null;
            }

            // 序列化为JSON（限制长度）
            String json = objectMapper.writeValueAsString(params);
            if (json.length() > 5000) {
                return json.substring(0, 5000) + "...(truncated)";
            }
            return json;
        } catch (Exception e) {
            log.warn("Failed to build params: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 获取客户端IP
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 多个代理时取第一个IP
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
