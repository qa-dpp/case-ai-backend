package com.fingertip.caseaibackend.service.impl;

import com.fingertip.caseaibackend.entity.MarkdownNode;
import com.fingertip.caseaibackend.entity.MindMap;
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
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AgileTcServiceImpl implements AgileTcService {
    @Autowired
    private RestTemplate restTemplate;

    @Override
    public ApiResult<Boolean> saveToAgileTc(String kmData, String caseName, ApiResult<Boolean> result)    {
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
                    createBody.put("caseContent", kmData);
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

    @Override
    public MindMap convertMarkdownToKityMinder(String markdown) {
        // 1. 解析Markdown为结构化节点树
        MarkdownNode rootNode = parseMarkdown(markdown);

        // 2. 转换为MindMap结构
        MindMap mindMap = new MindMap();
        MindMap.Root root = new MindMap.Root();

        if (rootNode != null && !rootNode.getChildren().isEmpty()) {
            // 根节点使用第一级标题
            MarkdownNode firstChild = rootNode.getChildren().get(0);
            root.setData(createNodeData(firstChild.getText()));

            // 递归转换子节点
            List<MindMap.Node> children = convertChildren(firstChild.getChildren());
            root.setChildren(children);
        } else {
            // 处理空文档情况
            root.setData(createNodeData("Untitled"));
        }

        mindMap.setRoot(root);
        return mindMap;
    }


    private MarkdownNode parseMarkdown(String markdown) {
        // 创建虚拟根节点（层级0）
        MarkdownNode root = new MarkdownNode(0, "");
        List<MarkdownNode> stack = new ArrayList<>();
        stack.add(root);

        // 按行处理Markdown
        String[] lines = markdown.split("\\r?\\n");
        for (String line : lines) {
            if (line.trim().isEmpty()) continue;

            // 解析标题级别
            Matcher headingMatcher = Pattern.compile("^(#+)\\s+(.+)$").matcher(line);
            if (headingMatcher.find()) {
                int level = headingMatcher.group(1).length();
                String text = headingMatcher.group(2).trim();

                // 创建新节点
                MarkdownNode node = new MarkdownNode(level, text);

                // 找到父节点（栈中最后一个层级小于当前层级的节点）
                while (!stack.isEmpty() && stack.get(stack.size() - 1).getLevel() >= level) {
                    stack.remove(stack.size() - 1);
                }

                // 添加到父节点的子节点
                stack.get(stack.size() - 1).getChildren().add(node);
                stack.add(node);
            }
            // 解析列表项
            else if (line.matches("^\\s*[-*]\\s+.+$")) {
                // 计算缩进级别（每2个空格为一级）
                int indent = 0;
                for (char c : line.toCharArray()) {
                    if (c == ' ') indent++;
                    else break;
                }
                int level = (indent / 2) + 1; // 基础层级从1开始

                // 提取文本内容
                String text = line.replaceFirst("^\\s*[-*]\\s+", "").trim();

                // 创建新节点
                MarkdownNode node = new MarkdownNode(level, text);

                // 找到父节点
                while (!stack.isEmpty() && stack.get(stack.size() - 1).getLevel() >= level) {
                    stack.remove(stack.size() - 1);
                }

                // 添加到父节点的子节点
                stack.get(stack.size() - 1).getChildren().add(node);
                stack.add(node);
            }
        }

        return root;
    }


    private List<MindMap.Node> convertChildren(List<MarkdownNode> markdownNodes) {
        List<MindMap.Node> nodes = new ArrayList<>();

        for (MarkdownNode mdNode : markdownNodes) {
            MindMap.Node node = new MindMap.Node();
            node.setData(createNodeData(mdNode.getText()));

            // 递归处理子节点
            if (!mdNode.getChildren().isEmpty()) {
                node.setChildren(convertChildren(mdNode.getChildren()));
            } else {
                node.setChildren(new ArrayList<>());
            }

            nodes.add(node);
        }

        return nodes;
    }

    private MindMap.NodeData createNodeData(String text) {
        MindMap.NodeData nodeData = new MindMap.NodeData();
        nodeData.setId(generateId());
        nodeData.setCreated(System.currentTimeMillis());
        nodeData.setText(text);
        return nodeData;
    }

    private String generateId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 11);
    }

}
