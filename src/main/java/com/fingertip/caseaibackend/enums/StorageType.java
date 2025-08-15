package com.fingertip.caseaibackend.enums;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum StorageType {
    AGILETC(1, "agileTc"),
    METERSPHERE(2, "metersphere");
    private Integer value;
    private String desc;

    public static StorageType fromValue(Integer value) {
        if (value == null) {
            return null;
        }
        for (StorageType storageType : StorageType.values()) {
            if (storageType.getValue().equals(value)) {
                return storageType;
            }
        }
        return null;
    }
}
