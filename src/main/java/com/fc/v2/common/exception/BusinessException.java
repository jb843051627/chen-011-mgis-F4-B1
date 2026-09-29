package com.fc.v2.common.exception;

/**
 * 业务校验异常：参数对不上底档、状态不合规等可预期的拦截，
 * 由全局异常解析为 AjaxResult 把话带给页面，区别于系统异常。
 *
 * @author fuce
 */
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public BusinessException(String message) {
        super(message);
    }
}
