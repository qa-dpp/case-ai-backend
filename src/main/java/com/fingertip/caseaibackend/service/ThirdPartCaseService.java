package com.fingertip.caseaibackend.service;

import com.fingertip.caseaibackend.enums.StorageType;


public interface ThirdPartCaseService {
    //存储渠道
    StorageType getStorageType();
    //保存
    void saveOrUpdate( String caseName,String caseInfo);
}
