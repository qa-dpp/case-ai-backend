package com.fingertip.caseaibackend.dtos;

import lombok.Data;

@Data
public class CaseQueryRequest {
    private Integer page = 1;
    private Integer pageSize = 10;
    private String keyword = "";
}