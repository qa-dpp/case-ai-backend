package com.fingertip.caseaibackend.service.impl;

import com.alibaba.fastjson.JSON;
import com.fingertip.caseaibackend.dtos.MarkdownNode;
import com.fingertip.caseaibackend.entity.MindMap;
import com.fingertip.caseaibackend.enums.StorageType;
import com.fingertip.caseaibackend.service.ThirdPartCaseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AgileTcServiceImpl implements ThirdPartCaseService {

    @Value("${agileTc.url}")
    private String agileTcUrl = "";

    @Autowired
    private RestTemplate restTemplate;


    @Override
    public StorageType getStorageType() {
        return StorageType.AGILETC;
    }

    @Override
    public void saveOrUpdate(String caseInfoOfMarkDown, String caseName)    {
        //格式转换
        String kmData = JSON.toJSONString(convertMarkdownToKityMinder(caseInfoOfMarkDown));
        //调用agileTC的接口进行存储或更新
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        try {
            // 1. 调用GET接口检查用例是否存在
            String encodedCaseName = URLEncoder.encode(caseName, StandardCharsets.UTF_8.name());
            String getUrl = String.format(agileTcUrl +
                            "/api/case/list?pageSize=10&pageNum=1&productLineId=1&caseType=0&title=%s&creator=&channel=1&requirementId=&bizId=root",
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
                            agileTcUrl + "/api/case/create",
                            createRequest,
                            String.class
                    );
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
                            agileTcUrl + "/api/case/update",
                            updateRequest,
                            String.class
                    );

                }
            }
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }

    }




    private MindMap convertMarkdownToKityMinder(String markdown) {
        // 解析Markdown为节点列表
        List<MarkdownNode> nodes = parseMarkdownLines(markdown);

        // 构建节点树
        MarkdownNode rootNode = buildNodeTree(nodes);

        // 转换为MindMap结构
        return convertToMindMap(rootNode);
    }


    private List<MarkdownNode> parseMarkdownLines(String markdown) {
        List<MarkdownNode> nodes = new ArrayList<>();
        String[] lines = markdown.split("\\r?\\n");

        int currentDepth = 0; // 当前层级深度
        int lastIndent = -1;  // 上一个列表项的缩进量
        int lastDepth = 0;    // 上一个节点的深度
        boolean lastWasHeading = false;

        for (String line : lines) {
            if (line.trim().isEmpty()) continue;

            // 解析标题
            Matcher headingMatcher = Pattern.compile("^(#+)\\s+(.+)$").matcher(line);
            if (headingMatcher.find()) {
                int level = headingMatcher.group(1).length();
                String text = headingMatcher.group(2).trim();

                // 标题的深度就是其级别
                currentDepth = level;
                nodes.add(new MarkdownNode(currentDepth, text, false));

                lastDepth = currentDepth;
                lastWasHeading = true;
                lastIndent = -1;
                continue;
            }

            // 解析列表项
            Matcher listMatcher = Pattern.compile("^(\\s*)[-*+]\\s+(.+)$").matcher(line);
            if (listMatcher.find()) {
                int indent = listMatcher.group(1).length();
                String text = listMatcher.group(2).trim();

                // 计算当前列表项的深度
                if (lastWasHeading) {
                    // 标题后的第一个列表项，深度+1
                    currentDepth = lastDepth + 1;
                } else if (lastIndent >= 0) {
                    // 连续列表项，根据缩进变化计算深度
                    if (indent > lastIndent) {
                        // 缩进增加，深度+1
                        currentDepth = lastDepth + 1;
                    } else if (indent < lastIndent) {
                        // 缩进减少，计算深度减少值
                        int indentDiff = lastIndent - indent;
                        int depthDiff = (indentDiff + 1) / 2; // 每2个空格为一级
                        currentDepth = Math.max(lastDepth - depthDiff, 1);
                    } else {
                        // 缩进不变，深度不变
                        currentDepth = lastDepth;
                    }
                } else {
                    // 文档开头的列表项
                    currentDepth = 1;
                }

                nodes.add(new MarkdownNode(currentDepth, text, true));

                lastDepth = currentDepth;
                lastWasHeading = false;
                lastIndent = indent;
                continue;
            }

            // 处理普通文本行（附加到上一个节点）
            if (!nodes.isEmpty()) {
                MarkdownNode lastNode = nodes.get(nodes.size() - 1);
                lastNode.setText(lastNode.getText() + "\n" + line.trim());
            }
        }

        return nodes;
    }

    private MarkdownNode buildNodeTree(List<MarkdownNode> nodes) {
        // 创建虚拟根节点（深度0）
        MarkdownNode root = new MarkdownNode(0, "", false);
        Stack<MarkdownNode> stack = new Stack<>();
        stack.push(root);

        for (MarkdownNode node : nodes) {
            // 弹出层级大于等于当前节点的所有节点
            while (stack.size() > 1 && stack.peek().getDepth() >= node.getDepth()) {
                stack.pop();
            }

            // 当前节点添加到栈顶节点的子节点
            MarkdownNode parent = stack.peek();
            parent.getChildren().add(node);
            stack.push(node);
        }

        return root;
    }

    private MindMap convertToMindMap(MarkdownNode root) {
        MindMap mindMap = new MindMap();

        // 处理空文档情况
        if (root.getChildren().isEmpty()) {
            MindMap.Root rootNode = new MindMap.Root();
            rootNode.setData(createNodeData("Untitled"));
            mindMap.setRoot(rootNode);
            return mindMap;
        }

        // 创建MindMap根节点
        MindMap.Root rootNode = new MindMap.Root();
        rootNode.setData(createNodeData("Document Root"));

        // 转换所有一级节点
        rootNode.setChildren(convertChildren(root.getChildren()));

        mindMap.setRoot(rootNode);
        return mindMap;
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
