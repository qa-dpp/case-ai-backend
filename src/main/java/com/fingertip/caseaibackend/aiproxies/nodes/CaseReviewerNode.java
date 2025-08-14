package com.fingertip.caseaibackend.aiproxies.nodes;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.fingertip.caseaibackend.commons.Consts;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CaseReviewerNode implements NodeAction {
    //正则匹配评审的分数
    private static final Pattern SCORE_PATTERN = Pattern.compile("\\{\"score\":(\\d+),");
    private final ChatClient chatClient;
    public CaseReviewerNode(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    // 评审维度权重配置
    private static final Map<String, Integer> REVIEW_DIMENSION_WEIGHTS = Map.of(
            "coverage", 30,    // 需求覆盖率
            "clarity", 25,     // 步骤清晰度
            "data", 20,        // 测试数据完整性
            "edge_cases", 15,  // 边界场景覆盖
            "format", 10       // 格式规范性
    );




    @Override
    public Map<String, Object> apply(OverAllState t) {
        //获取上下文信息
        String originMessage = (String) t.value(Consts.ORIGIN_MESSAGE).orElse("");
        String caseInfo = (String) t.value(Consts.CASE_INFO_MESSAGE).orElse("");
        if (!StringUtils.hasText(caseInfo)) {
            throw new IllegalArgumentException("用例信息为空，请检查！");
        }

        // 构建结构化评审提示词
        String prompt = buildStructuredReviewPrompt(originMessage, caseInfo);


        //调用大模型审核用例
        ChatResponse response = chatClient.prompt(prompt).call().chatResponse();
        String output = null;
        if (response != null) {
            output = response.getResult().getOutput().getText();
        }
        
        //解析评审结果
        Map<String, Object> reviewResult = parseReviewResult(output);

        Map<String, Object> updated = new HashMap<>();
        updated.put(Consts.CASE_REVIEW_MESSAGE, output);
        updated.put(Consts.REVIEW_SCORE, reviewResult.get("score"));
        updated.put(Consts.REVIEW_RESULT, reviewResult.get("result"));

        return updated;
    }


    /**
     * 解析评审结果
     * @param rawOutput
     * @return
     */
    private Map<String, Object> parseReviewResult(String rawOutput) {
        Map<String, Object> result = new HashMap<>();

        try {
            // 尝试提取JSON部分
            String jsonPart = extractJson(rawOutput);

            // 简化版解析（实际项目应使用JSON库）
            int score = extractScore(jsonPart);
            String feedback = extractFeedback(jsonPart);

            result.put("score", score);
            result.put("feedback", feedback);
            result.put("result", score >= 80 ? "pass" : "fail");

        } catch (Exception e) {
            // 解析失败时的降级处理
            result.put("score", 0);
            result.put("feedback", "评审结果解析失败: " + e.getMessage() + "\n原始输出:\n" + rawOutput);
            result.put("result", "fail");
        }

        return result;
    }

    private String extractJson(String rawOutput) {
        // 简单实现：提取第一个{...}之间的内容
        int start = rawOutput.indexOf('{');
        int end = rawOutput.lastIndexOf('}');

        if (start >= 0 && end > start) {
            return rawOutput.substring(start, end + 1);
        }
        return rawOutput;
    }


    private int extractScore(String json) {
        Matcher matcher = SCORE_PATTERN.matcher(json);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return 0; // 默认0分
    }
    private String extractFeedback(String json) {
        // 简化实现（实际项目应使用JSON解析）
        if (json.contains("\"feedback\":")) {
            int start = json.indexOf("\"feedback\":") + 11;
            int end = json.indexOf("\"", start);
            return json.substring(start, end);
        }
        return "未找到反馈内容";
    }


    /**
     * 构建结构化评审提示词
     * @param originMessage
     * @param caseInfo
     * @return
     */
    private String buildStructuredReviewPrompt(String originMessage, String caseInfo) {
        return String.format(Consts.CASE_REVIEWER_PROMPT, originMessage, caseInfo);
    }
    
    
}
