package domain;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class Result<T> {

    /**
     * 响应码
     */
    private int code;
    /**
     * 响应消息
     */
    private String msg;
    /**
     * 响应数据
     */
    private T data;

    /**
     * 成功响应
     */
    public static <T> Result<T> success(T data) {
        return setResult(EnumCode.SUCCESS.getCode(), EnumCode.SUCCESS.getMsg(), data);
    }

    public static <T> Result<T> success() {
        return setResult(EnumCode.SUCCESS.getCode(), EnumCode.SUCCESS.getMsg(), null);
    }

    // ------------------------------

    /**
     * 失败响应
     */
    public static <T> Result<T> fail(String msg) {
        return setResult(EnumCode.ERROR.getCode(), msg, null);
    }

    public static <T> Result<T> fail() {
        return setResult(EnumCode.ERROR.getCode(), EnumCode.ERROR.getMsg(), null);
    }

    public static <T> Result<T> fail(int code, String msg) {
        return setResult(code, msg, null);
    }

    /**
     * 提取公共方法，设置响应结果
     */
    private static <T> Result<T> setResult(Integer code, String msg, T data) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMsg(msg);
        result.setData(data);
        return result;
    }
}
