package com.fingertip.caseaibackend.service;

import com.fingertip.caseaibackend.entity.CaseInfo;
import com.fingertip.caseaibackend.enums.StorageType;
import com.fingertip.caseaibackend.vo.ApiResult;

public interface CaseSaveOrUpdate {
    //存储渠道
    StorageType getStorageType();
    //保存
    ApiResult<Boolean> saveOrUpdate(String caseInfo, String caseName, ApiResult<Boolean> result);
}
