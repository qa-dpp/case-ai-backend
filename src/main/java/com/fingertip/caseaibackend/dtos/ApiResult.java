package com.fingertip.caseaibackend.dtos;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ApiResult<T> {
    private T data;
    private String message;
    private  int code;
}
