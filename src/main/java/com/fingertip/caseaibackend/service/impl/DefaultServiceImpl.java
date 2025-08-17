package com.fingertip.caseaibackend.service.impl;

import com.fingertip.caseaibackend.enums.StorageType;
import com.fingertip.caseaibackend.service.ThirdPartCaseService;

public class DefaultServiceImpl implements ThirdPartCaseService {
    @Override
    public StorageType getStorageType() {
        return StorageType.DEFAULT;
    }

    @Override
    public void saveOrUpdate(String caseInfo, String caseName) {
    }
}
