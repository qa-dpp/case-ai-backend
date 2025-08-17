package com.fingertip.caseaibackend.service;

import com.fingertip.caseaibackend.enums.StorageType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;


@Component
public class ThirdPartCaseFactory {

    @Autowired
    private List<ThirdPartCaseService> thirdPartCaseService;

    @Value("${thirdPart.type}")
    private List<String> thirdPartType;


    //根据配置的类型执行
    public void exec(String caseInfo, String caseName) {
        for (ThirdPartCaseService thirdPartCaseService : thirdPartCaseService) {
            for (String thirdPartType : thirdPartType) {
                StorageType storageType = StorageType.valueOf(thirdPartType);
                if (thirdPartCaseService.getStorageType().equals(storageType)) {
                    thirdPartCaseService.saveOrUpdate(caseInfo, caseName);
                }
            }
        }
    }

}
