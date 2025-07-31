package com.fingertip.caseaibackend.dtos;

import lombok.Data;

@Data
public class CaseSaveReq {
    private String caseName;
    private String caseContent;
}
