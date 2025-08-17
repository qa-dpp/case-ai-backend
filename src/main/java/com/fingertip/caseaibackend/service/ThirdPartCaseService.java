package com.fingertip.caseaibackend.service;

import com.fingertip.caseaibackend.enums.StorageType;
import com.fingertip.caseaibackend.vo.ApiResult;

public interface ThirdPartCaseService {
    //存储渠道
    StorageType getStorageType();
    //保存
    void saveOrUpdate(String caseInfo, String caseName);
}
