package com.fingertip.caseaibackend.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fingertip.caseaibackend.entity.CaseInfo;
import com.fingertip.caseaibackend.mapper.CaseInfoMapper;
import com.fingertip.caseaibackend.service.CaseInfoService;
import org.springframework.stereotype.Service;

@Service
public class CaseInfoServiceImpl extends ServiceImpl<CaseInfoMapper, CaseInfo> implements CaseInfoService {
// 继承ServiceImpl后自动获得基本CRUD能力

}
