package com.bondhub.friendservice.aspect;

import com.bondhub.common.exception.AppException;
import com.bondhub.common.exception.ErrorCode;
import com.bondhub.common.utils.SecurityUtil;
import com.bondhub.friendservice.annotation.CheckBlockStatus;
import com.bondhub.friendservice.service.blocklist.BlockCheckService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

/**
 * AOP Aspect to intercept methods annotated with @CheckBlockStatus
 * and verify that communication is not blocked before execution
 */
@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class BlockCheckAspect {

    private final BlockCheckService blockCheckService;
    private final SecurityUtil securityUtil;

    /**
     * Before advice that executes before any method annotated with @CheckBlockStatus
     */
    @Before("@annotation(com.bondhub.friendservice.annotation.CheckBlockStatus)")
    public void checkBlockStatus(JoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        CheckBlockStatus annotation = method.getAnnotation(CheckBlockStatus.class);

        if (annotation == null) {
            return;
        }

        log.debug("Checking block status for method: {}", method.getName());

        // Get current user ID directly from security context
        String senderId = securityUtil.getCurrentUserId();

        // Extract target user ID from method parameters
        String targetUserId = extractTargetUserId(joinPoint, annotation.targetUserIdParam());

        if (targetUserId == null || targetUserId.isEmpty()) {
            log.warn("Could not extract target user ID from parameter: {}", annotation.targetUserIdParam());
            return;
        }

        // Check blocking status
        if (annotation.bidirectional()) {
            blockCheckService.checkBidirectionalBlock(senderId, targetUserId, annotation.blockType());
        } else {
            blockCheckService.checkAndThrowIfBlocked(senderId, targetUserId, annotation.blockType());
        }

        log.debug("Block check passed for {} -> {} (type: {})",
                  senderId, targetUserId, annotation.blockType());
    }

    /**
     * Extract target user ID from method parameters
     */
    private String extractTargetUserId(JoinPoint joinPoint, String paramName) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Parameter[] parameters = method.getParameters();
        Object[] args = joinPoint.getArgs();

        for (int i = 0; i < parameters.length; i++) {
            if (parameters[i].getName().equals(paramName)) {
                Object value = args[i];
                if (value instanceof String) {
                    return (String) value;
                }
                return value != null ? value.toString() : null;
            }
        }

        log.warn("Parameter '{}' not found in method {}", paramName, method.getName());
        return null;
    }
}
