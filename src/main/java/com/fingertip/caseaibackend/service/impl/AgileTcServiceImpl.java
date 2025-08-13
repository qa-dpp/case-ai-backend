package com.fingertip.caseaibackend.service.impl;

import com.fingertip.caseaibackend.service.AgileTcService;
import com.fingertip.caseaibackend.vo.ApiResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AgileTcServiceImpl implements AgileTcService {
    @Autowired
    private RestTemplate restTemplate;

    @Override
    public ApiResult<Boolean> saveToAgileTc(String kmData, String caseName, ApiResult<Boolean> result) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        try {
            // 1. 调用GET接口检查用例是否存在
            String encodedCaseName = URLEncoder.encode(caseName, StandardCharsets.UTF_8.name());
            String getUrl = String.format(
                    "https://agile.leoao-inc.com/api/case/list?pageSize=10&pageNum=1&productLineId=1&caseType=0&title=%s&creator=&channel=1&requirementId=&bizId=root",
                    encodedCaseName  // 使用编码后的名称进行替换
            );
            ResponseEntity<Map> getResponse = restTemplate.getForEntity(getUrl, Map.class);

            if (getResponse.getStatusCode().is2xxSuccessful() && getResponse.getBody() != null) {
                Map<String, Object> responseBody = getResponse.getBody();
                List<Map<String, Object>> dataSources = (List<Map<String, Object>>) responseBody.get("dataSources");

                if (dataSources == null || dataSources.isEmpty()) {
                    // 2. 用例不存在，调用创建接口
                    Map<String, Object> createBody = new HashMap<>();
                    createBody.put("productLineId", 1);
                    createBody.put("creator", "admin");
                    createBody.put("caseType", 0);
                    // 正则表达式匹配 ```json 和 ``` 之间的内容（包含换行符）
                    Pattern pattern = Pattern.compile(
                            "([`']){3}\\s*json\\s*([\\s\\S]*?)\\s*\\1",  // 捕获组2：JSON内容
                            Pattern.CASE_INSENSITIVE
                    );
                    Matcher matcher = pattern.matcher(kmData);
                    String cleanedJson = kmData;  // 初始化为原始数据，保留所有字符

                    if (matcher.find()) {
                        // 提取JSON内容并去除首尾空白（保留转义字符）
                        cleanedJson = matcher.group(2).trim();
                    } else {
                        // 未找到JSON代码块，记录警告日志
                        System.out.println("警告：未从kmData中提取到JSON代码块，使用原始内容");
                    }
                    createBody.put("caseContent", cleanedJson);
                    createBody.put("title", caseName);  // 使用实际用例名称作为标题
                    createBody.put("channel", 1);
                    createBody.put("bizId", "-1");
                    createBody.put("id", "");
                    createBody.put("description", "");

                    HttpEntity<Map<String, Object>> createRequest = new HttpEntity<>(createBody, headers);
                    ResponseEntity<String> createResponse = restTemplate.postForEntity(
                            "https://agile.leoao-inc.com/api/case/create",
                            createRequest,
                            String.class
                    );
                    if (createResponse.getStatusCode().is2xxSuccessful()) {
                        result.setMessage("用例保存成功并同步创建到agileTC");
                    } else {
                        result.setMessage("用例保存成功，但agileTC创建失败: " + createResponse.getStatusCode());
                    }
                } else {
                    // 3. 用例已存在，调用更新接口
                    Map<String, Object> existingCase = dataSources.get(0);  // 获取第一个匹配用例
                    String caseId = (String) existingCase.get("id");  // 获取现有用例ID

                    Map<String, Object> updateBody = new HashMap<>();
                    updateBody.put("id", caseId);
                    updateBody.put("title", "更新内容，实际不会保存title");  // 按需求固定标题
                    updateBody.put("modifier", "admin");
                    updateBody.put("caseContent", kmData);  // 更新用例内容

                    HttpEntity<Map<String, Object>> updateRequest = new HttpEntity<>(updateBody, headers);
                    ResponseEntity<String> updateResponse = restTemplate.postForEntity(
                            "https://agile.leoao-inc.com/api/case/update",
                            updateRequest,
                            String.class
                    );
                    if (updateResponse.getStatusCode().is2xxSuccessful()) {
                        result.setMessage("用例保存成功并同步更新到agileTC");
                    } else {
                        result.setMessage("用例保存成功，但agileTC更新失败: " + updateResponse.getStatusCode());
                    }
                }
            } else {
                result.setMessage("用例保存成功，但agileTC查询接口调用失败");
            }
        } catch (Exception e) {
            result.setMessage("用例保存成功，但同步agileTC时发生错误: " + e.getMessage());
        }
        return result;

    }
}
