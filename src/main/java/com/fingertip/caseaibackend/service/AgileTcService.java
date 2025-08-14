package com.fingertip.caseaibackend.service;

import com.fingertip.caseaibackend.entity.MindMap;
import com.fingertip.caseaibackend.vo.ApiResult;

public interface AgileTcService {
    public ApiResult<Boolean> saveToAgileTc(String kmData, String caseName, ApiResult<Boolean> result);
    public MindMap convertMarkdownToKityMinder(String markdown);
}
