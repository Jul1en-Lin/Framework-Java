package domain.exception;

import domain.EnumCode;
import lombok.Getter;
import lombok.Setter;

/**
 * 自定义业务服务异常。
 */
@Getter
@Setter
public class ServiceException extends RuntimeException {

    /**
     * 异常响应码。
     */
    private int code;

    /**
     * 异常消息。
     */
    private String msg;

    /**
     * 使用统一响应对象构造异常
     */
    public ServiceException(EnumCode resultCode) {
        this.code = resultCode.getCode();
        this.msg = resultCode.getMsg();
    }

    /**
     * 默认系统错误码构造异常
     */
    public ServiceException(String message) {
        this.msg = message;
        this.code = EnumCode.ERROR.getCode();
    }

    /**
     * 自定义消息和响应码构造异常
     */
    public ServiceException(String message, int code) {
        this.msg = message;
        this.code = code;
    }

}
