package com.fingertip.caseaibackend.aiproxies.nodes;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;
import com.fingertip.caseaibackend.commons.Consts;
import opennlp.tools.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.mysql.cj.conf.PropertyKey.logger;

public class CaseReviewerNode implements NodeAction {
    private static final Logger logger = LoggerFactory.getLogger(CaseReviewerNode.class);
    private static final String DEFAULT_FEEDBACK = "未提供具体反馈";
    private final ChatClient chatClient;
    private static final int MAX_RETRY = 3;

    public CaseReviewerNode(ChatClient chatClient) {
        this.chatClient = chatClient;
    }


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
        updated.put(Consts.CASE_REVIEW_MESSAGE, reviewResult.get("feedback"));
        updated.put(Consts.REVIEW_SCORE, reviewResult.get("score"));
        updated.put(Consts.RAW_OUTPUT, output);
        return updated;
    }


    /**
     * 解析评审结果
     *
     * @param rawOutput
     * @return
     */
    private Map<String, Object> parseReviewResult(String rawOutput) {
        Map<String, Object> result = new HashMap<>();

        // 步骤1: 提取可能的JSON内容
        String jsonContent = extractJson(rawOutput);
        result.put("raw_json_attempt", jsonContent);

        // 步骤2: 解析分数
        int score = extractScore(jsonContent);
        result.put("score", score);

        // 步骤3: 解析反馈
        String feedback = extractFeedback(jsonContent);
        result.put("feedback", feedback);

        // 步骤4: 保留原始输出用于调试
        result.put("raw_output", rawOutput);

        return result;
    }

    private String extractJson(String rawOutput) {
        if (StringUtil.isEmpty(rawOutput)) {
            return rawOutput;
        }


        // 1. 尝试直接解析整个输出
        if (isValidJson(rawOutput)) {
            return rawOutput;
        }

        // 2. 尝试提取可能的JSON对象
        int start = rawOutput.indexOf('{');
        int end = rawOutput.lastIndexOf('}');

        if (start >= 0 && end > start && (end - start) > 10) {
            String candidate = rawOutput.substring(start, end + 1);
            if (isValidJson(candidate)) {
                return candidate;
            }
        }

        // 3. 尝试提取JSON数组
        start = rawOutput.indexOf('[');
        end = rawOutput.lastIndexOf(']');

        if (start >= 0 && end > start && (end - start) > 10) {
            String candidate = rawOutput.substring(start, end + 1);
            if (isValidJson(candidate)) {
                return candidate;
            }
        }

        // 4. 尝试提取键值对格式
        Pattern kvPattern = Pattern.compile("\\{\\s*\"?score\"?\\s*:\\s*\\d+.*?\\}");
        Matcher matcher = kvPattern.matcher(rawOutput);
        if (matcher.find()) {
            String candidate = matcher.group(0);
            if (isValidJson(candidate)) {
                return candidate;
            }
        }

        // 5. 返回原始内容作为最后手段
        return rawOutput;
    }

    /**
     * 检查字符串是否为有效JSON
     */
    private boolean isValidJson(String str) {
        if (StringUtil.isEmpty(str)) {
            return false;
        }

        try {
            Object obj = com.alibaba.fastjson.JSON.parse(str);
            return obj != null;
        } catch (JSONException e) {
            return false;
        }
    }


    private int extractScore(String json) {
        // 1. 尝试解析为JSONObject
        try {
            JSONObject jsonObj = JSON.parseObject(json);
            return parseScoreFromJson(jsonObj);
        } catch (JSONException e) {
            logger.debug("JSON解析失败，尝试其他方式: {}", e.getMessage());
        }

        // 2. 尝试解析为JSONArray
        try {
            JSONArray jsonArray = JSON.parseArray(json);
            if (jsonArray != null && !jsonArray.isEmpty()) {
                // 取第一个元素作为JSON对象
                Object first = jsonArray.get(0);
                if (first instanceof JSONObject) {
                    return parseScoreFromJson((JSONObject) first);
                }
            }
        } catch (JSONException e) {
            logger.debug("JSON数组解析失败: {}", e.getMessage());
        }

        // 3. 尝试正则匹配
        Pattern pattern = Pattern.compile("\"score\"\\s*:\\s*(\\d+)");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                logger.debug("正则匹配分数解析失败: {}", e.getMessage());
            }
        }

        // 4. 尝试其他可能的键名
        String[] possibleKeys = {"评分", "rating", "score_value", "分值"};
        for (String key : possibleKeys) {
            Pattern keyPattern = Pattern.compile("\"" + key + "\"\\s*:\\s*(\\d+)");
            Matcher keyMatcher = keyPattern.matcher(json);
            if (keyMatcher.find()) {
                try {
                    return Integer.parseInt(keyMatcher.group(1));
                } catch (NumberFormatException e) {
                    logger.debug("键名[{}]分数解析失败: {}", key, e.getMessage());
                }
            }
        }

        // 默认0分
        logger.warn("无法从内容中提取分数，返回默认值0");
        return 0;
    }

    private int parseScoreFromJson(JSONObject jsonObj) {
        if (jsonObj == null) {
            return 0;
        }

        // 尝试不同键名
        String[] possibleKeys = {"score", "评分", "rating"};

        for (String key : possibleKeys) {
            if (jsonObj.containsKey(key)) {
                Object value = jsonObj.get(key);
                if (value instanceof Number) {
                    return ((Number) value).intValue();
                } else if (value instanceof String) {
                    try {
                        return Integer.parseInt((String) value);
                    } catch (NumberFormatException e) {
                        logger.debug("键名[{}]的字符串值解析失败: {}", key, value);
                    }
                }
            }
        }

        return 0;
    }

    private String extractFeedback(String json) {
        /// 1. 尝试解析为JSONObject
        try {
            JSONObject jsonObj = JSON.parseObject(json);
            return parseFeedbackFromJson(jsonObj);
        } catch (JSONException e) {
            logger.debug("JSON解析失败，尝试其他方式: {}", e.getMessage());
        }
        // 2. 尝试解析为JSONArray
        try {
            JSONArray jsonArray = JSON.parseArray(json);
            if (jsonArray != null && !jsonArray.isEmpty()) {
                // 取第一个元素作为JSON对象
                Object first = jsonArray.get(0);
                if (first instanceof JSONObject) {
                    return parseFeedbackFromJson((JSONObject) first);
                }
            }
        } catch (JSONException e) {
            logger.debug("JSON数组解析失败: {}", e.getMessage());
        }

        // 3. 尝试正则匹配
        Pattern pattern = Pattern.compile("\"feedback\"\\s*:\\s*\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }

        // 4. 尝试其他可能的键名
        String[] possibleKeys = {"反馈", "review", "comment", "建议"};
        for (String key : possibleKeys) {
            Pattern keyPattern = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]+)\"");
            Matcher keyMatcher = keyPattern.matcher(json);
            if (keyMatcher.find()) {
                return keyMatcher.group(1);
            }
        }
        // 5. 尝试提取improvements数组
        Pattern improvementsPattern = Pattern.compile("\"improvements\"\\s*:\\s*\\[(.*?)\\]");
        Matcher improvementsMatcher = improvementsPattern.matcher(json);
        if (improvementsMatcher.find()) {
            String improvementsStr = improvementsMatcher.group(1);
            return "改进建议: " + improvementsStr.replace("\"", "");
        }

        // 6. 返回默认反馈
        logger.warn("无法从内容中提取反馈，返回默认值");
        return DEFAULT_FEEDBACK;
    }

    /**
     * 从JSON对象中解析反馈内容
     */
    private String parseFeedbackFromJson(JSONObject jsonObj) {
        if (jsonObj == null) {
            return DEFAULT_FEEDBACK;
        }

        // 尝试不同键名
        String[] feedbackKeys = {"feedback", "反馈", "review", "comment"};

        for (String key : feedbackKeys) {
            if (jsonObj.containsKey(key)) {
                Object value = jsonObj.get(key);
                if (value instanceof String) {
                    return (String) value;
                }
            }
        }

        // 尝试从improvements数组构建反馈
        if (jsonObj.containsKey("improvements")) {
            Object improvements = jsonObj.get("improvements");
            if (improvements instanceof JSONArray) {
                JSONArray array = (JSONArray) improvements;
                StringBuilder sb = new StringBuilder("改进建议:\n");
                for (int i = 0; i < array.size(); i++) {
                    String item = array.getString(i);
                    if (item != null) {
                        sb.append("- ").append(item).append("\n");
                    }
                }
                return sb.toString();
            } else if (improvements instanceof String) {
                return "改进建议: " + improvements;
            }
        }

        // 尝试从issues数组构建反馈
        if (jsonObj.containsKey("issues")) {
            Object issues = jsonObj.get("issues");
            if (issues instanceof JSONArray) {
                JSONArray array = (JSONArray) issues;
                StringBuilder sb = new StringBuilder("问题列表:\n");
                for (int i = 0; i < array.size(); i++) {
                    String item = array.getString(i);
                    if (item != null) {
                        sb.append("- ").append(item).append("\n");
                    }
                }
                return sb.toString();
            }
        }

        return DEFAULT_FEEDBACK;
    }



    /**
     * 构建结构化评审提示词
     *
     * @param originMessage
     * @param caseInfo
     * @return
     */
    private String buildStructuredReviewPrompt(String originMessage, String caseInfo) {
        return String.format(Consts.CASE_REVIEWER_PROMPT, originMessage, caseInfo);
    }


}
