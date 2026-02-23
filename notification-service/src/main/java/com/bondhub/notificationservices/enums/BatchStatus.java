package com.bondhub.notificationservices.enums;

/**
 * OPEN:    batch đang trong cửa sổ, Redis list đang nhận events
 * FLUSHED: batch đã được flush và gửi đi
 * EXPIRED: cửa sổ đã qua nhưng không tìm thấy data để flush (hiếm)
 */
public enum BatchStatus {
    OPEN, FLUSHED, EXPIRED
}
