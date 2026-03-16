package com.bondhub.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
        // System errors (9xxx)
        SYS_UNCATEGORIZED(HttpStatus.INTERNAL_SERVER_ERROR, 9999, "error.sys.uncategorized"),

        // Authentication errors (1xxx)
        AUTH_UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, 1001, "error.auth.unauthenticated"),
        AUTH_UNAUTHORIZED(HttpStatus.FORBIDDEN, 1002, "error.auth.unauthorized"),
        JWT_INVALID_TOKEN(HttpStatus.UNAUTHORIZED, 1003, "error.jwt.invalid.token"),
        JWT_EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, 1004, "error.jwt.expired.token"),
        JWT_SIGNATURE_INVALID(HttpStatus.UNAUTHORIZED, 1005, "error.jwt.signature.invalid"),
        AUTH_INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, 1006, "error.auth.invalid.credentials"),
        AUTH_DEVICE_ID_REQUIRED(HttpStatus.BAD_REQUEST, 1007, "error.auth.device.id.required"),
        AUTH_DEVICE_MISMATCH(HttpStatus.FORBIDDEN, 1008, "error.auth.device.mismatch"),
        AUTH_SESSION_KICKED(HttpStatus.UNAUTHORIZED, 1009, "error.auth.session.kicked"),
        QR_SESSION_EXPIRED(HttpStatus.BAD_REQUEST, 1010, "error.qr.session.expired"),
        QR_SESSION_INVALID_STATE(HttpStatus.CONFLICT, 1011, "error.qr.session.invalid.state"),
        QR_SESSION_UNAUTHORIZED(HttpStatus.FORBIDDEN, 1012, "error.qr.session.unauthorized"),
        AUTH_INVALID_OLD_PASSWORD(HttpStatus.BAD_REQUEST, 1013, "error.auth.invalid.old.password"),
        AUTH_NEW_PASSWORD_SAME_AS_OLD(HttpStatus.BAD_REQUEST, 1014, "error.auth.new.password.same.as.old"),
        AUTH_ACCOUNT_BANNED(HttpStatus.FORBIDDEN, 1013, "error.auth.account.banned"),

        // User account errors (2xxx)
        ACC_PHONE_NUMBER_ALREADY_USED(HttpStatus.CONFLICT, 2001, "error.acc.phone.number.already.used"),
        ACC_EMAIL_ALREADY_USED(HttpStatus.CONFLICT, 2002, "error.acc.email.already.used"),
        ACC_ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, 2003, "error.acc.account.not.found"),
        USER_NOT_FOUND(HttpStatus.NOT_FOUND, 2004, "error.user.not.found"),
        INVALID_OTP(HttpStatus.BAD_REQUEST, 2005, "error.invalid.otp"),
        ACC_WRONG_PASSWORD(HttpStatus.CONFLICT, 2006, "error.acc.wrong.password"),
        ACC_IS_OAUTH(HttpStatus.CONFLICT, 2007, "error.acc.is.oauth"),
        CIC_IS_EXIST(HttpStatus.CONFLICT, 2008, "error.cic.is.exist"),

        // OTP errors (20xx)
        OTP_COOLDOWN_ACTIVE(HttpStatus.TOO_MANY_REQUESTS, 2009, "error.otp.cooldown.active"),
        OTP_MAX_ATTEMPTS_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, 2010, "error.otp.max.attempts.exceeded"),
        OTP_EXPIRED(HttpStatus.BAD_REQUEST, 2011, "error.otp.expired"),
        OTP_INVALID(HttpStatus.BAD_REQUEST, 2012, "error.otp.invalid"),
        OTP_PURPOSE_MISMATCH(HttpStatus.BAD_REQUEST, 2013, "error.otp.purpose.mismatch"),
        OTP_NOT_FOUND(HttpStatus.NOT_FOUND, 2014, "error.otp.not.found"),

        // Role and permission errors (21xx)
        ROLE_NOT_FOUND(HttpStatus.NOT_FOUND, 2101, "error.role.not.found"),
        PERM_NOT_FOUND(HttpStatus.NOT_FOUND, 2102, "error.perm.not.found"),
        PERMISSION_IN_USE(HttpStatus.CONFLICT, 2103, "error.permission.in.use"),

        // Device errors (22xx)
        DEV_DEVICE_NOT_FOUND(HttpStatus.NOT_FOUND, 2211, "error.dev.device.not.found"),
        DEV_SESSION_ID_ALREADY_USED(HttpStatus.CONFLICT, 2212, "error.dev.session.id.already.used"),

        // VALIDATION (23xx)
        VALIDATION_ERROR(HttpStatus.BAD_REQUEST, 2300, "error.validation.error"),
        PROMOTION_CODE_REQUIRED(HttpStatus.BAD_REQUEST, 2301, "error.promotion.code.required"),
        INVALID_STATUS(HttpStatus.BAD_REQUEST, 2302, "error.invalid.status"),
        INVALID_DATE_ATTRIBUTE_PAIR(HttpStatus.BAD_REQUEST, 2303, "error.invalid.date.attribute.pair"),
        INVALID_YEAR_ATTRIBUTE_PAIR(HttpStatus.BAD_REQUEST, 2304, "error.invalid.year.attribute.pair"),
        INVALID_OPERATION(HttpStatus.BAD_REQUEST, 2305, "error.invalid.operation"),
        INVALID_PROMOTION_CONDITION(HttpStatus.BAD_REQUEST, 2306, "error.invalid.promotion.condition"),
        ACC_PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, 2307, "error.acc.password.mismatch"),

        // Friendship errors (3xxx)
        FRIEND_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, 3001, "error.friend.request.not.found"),
        ALREADY_FRIENDS(HttpStatus.CONFLICT, 3002, "error.friend.already.friends"),
        FRIEND_REQUEST_ALREADY_SENT(HttpStatus.CONFLICT, 3003, "error.friend.request.already.sent"),
        CANNOT_FRIEND_YOURSELF(HttpStatus.BAD_REQUEST, 3004, "error.friend.cannot.friend.yourself"),
        NOT_FRIENDS(HttpStatus.BAD_REQUEST, 3005, "error.friend.not.friends"),
        NOT_AUTHORIZED_TO_ACCEPT(HttpStatus.FORBIDDEN, 3006, "error.friend.not.authorized.to.accept"),
        NOT_AUTHORIZED_TO_DECLINE(HttpStatus.FORBIDDEN, 3007, "error.friend.not.authorized.to.decline"),
        NOT_AUTHORIZED_TO_CANCEL(HttpStatus.FORBIDDEN, 3008, "error.friend.not.authorized.to.cancel"),
        FRIEND_REQUEST_NOT_PENDING(HttpStatus.BAD_REQUEST, 3009, "error.friend.request.not.pending"),

        // Admin / ban errors
        CANNOT_BAN_YOURSELF(HttpStatus.BAD_REQUEST, 2208, "error.cannot.ban.yourself"),
        CANNOT_BAN_ADMIN(HttpStatus.FORBIDDEN, 2209, "error.cannot.ban.admin"),

        // Elasticsearch errors (23xx)
        EL_INDEX_NOT_FOUND(HttpStatus.NOT_FOUND, 2301, "error.el.index.not.found"),
        EL_CLUSTER_UNHEALTHY(HttpStatus.INTERNAL_SERVER_ERROR, 2302, "error.el.cluster.unhealthy"),
        EL_DOCUMENT_NOT_FOUND(HttpStatus.NOT_FOUND, 2303, "error.el.document.not.found"),
        EL_REINDEX_IN_PROGRESS(HttpStatus.CONFLICT, 2304, "error.el.reindex.in.progress"),

        // DLQ errors (24xx)
        DLQ_EVENT_NOT_FOUND(HttpStatus.NOT_FOUND, 2401, "error.dlq.event.not.found"),
        DLQ_RETRY_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, 2402, "error.dlq.retry.failed"),

        //  NOTIFICATION_TEMPLATE (23xx)
        NOTIFICATION_TEMPLATE_NOT_FOUND(HttpStatus.NOT_FOUND, 2300, "error.notification.template.not.found"),
        NOTIFICATION_STRATEGY_NOT_FOUND(HttpStatus.INTERNAL_SERVER_ERROR, 2301, "error.notification.strategy.not.found"),

        // Block List errors (23xx)
        CANNOT_BLOCK_YOURSELF(HttpStatus.BAD_REQUEST, 2301, "error.block.cannot.block.yourself"),
        USER_ALREADY_BLOCKED(HttpStatus.CONFLICT, 2302, "error.block.user.already.blocked"),
        BLOCK_NOT_FOUND(HttpStatus.NOT_FOUND, 2303, "error.block.not.found"),
        MESSAGE_BLOCKED(HttpStatus.FORBIDDEN, 2304, "error.block.message.blocked"),
        CALL_BLOCKED(HttpStatus.FORBIDDEN, 2305, "error.block.call.blocked"),
        STORY_BLOCKED(HttpStatus.FORBIDDEN, 2306, "error.block.story.blocked"),
        COMMUNICATION_BLOCKED(HttpStatus.FORBIDDEN, 2307, "error.block.communication.blocked")

        ;

        private final HttpStatus httpStatus;
        private final int code;
        private final String messageKey;

        ErrorCode(HttpStatus httpStatus, int code, String messageKey) {
                this.httpStatus = httpStatus;
                this.code = code;
                this.messageKey = messageKey;
        }

        public static ErrorCode fromCode(int code) {
                for (ErrorCode errorCode : ErrorCode.values()) {
                        if (errorCode.getCode() == code) {
                                return errorCode;
                        }
                }
                return SYS_UNCATEGORIZED;
        }
}
