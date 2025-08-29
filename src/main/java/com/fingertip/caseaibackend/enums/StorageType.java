package com.fingertip.caseaibackend.enums;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import opennlp.tools.util.StringUtil;

@Getter
@AllArgsConstructor
public enum StorageType {
    AGILETC(1, "agileTc"),
    DEFAULT(2, "default");
    private Integer value;
    private String desc;

    public static StorageType getValueByDesc(String desc) {
        if (StringUtil.isEmpty(desc)) {
            return null;
        }
        for (StorageType storageType : StorageType.values()) {
            if (storageType.getDesc().equals(desc)) {
                return storageType;
            }
        }
        return null;
    }
}
