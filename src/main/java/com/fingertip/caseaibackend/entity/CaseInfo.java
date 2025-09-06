package com.fingertip.caseaibackend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@TableName("case_info")// 对应数据库表名
@AllArgsConstructor
@NoArgsConstructor
public class CaseInfo {
    @TableId(type = IdType.ASSIGN_UUID) // 自动生成UUID作为主键
    private String id;
    private String name;
    private String caseContent;
}
