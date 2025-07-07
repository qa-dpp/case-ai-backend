package com.fingertip.caseaibackend.aiproxies.nodes;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.fingertip.caseaibackend.commons.Consts;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

public class CaseReviewerNode implements NodeAction {
    private final ChatClient chatClient;

    public CaseReviewerNode(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public Map<String, Object> apply(OverAllState t) {
        String origin_message = (String) t.value(Consts.ORIGIN_MESSAGE).orElse("");
        String caseInfo = (String) t.value(Consts.CASE_INFO_MESSAGE).orElse("");
        if (!StringUtils.hasText(caseInfo)) {
            throw new IllegalArgumentException("用例信息为空，请检查！");
        }
        ChatResponse response = chatClient.prompt("%s\n# 原始需求:\n%s\n\n# 需求用例:\n%s".formatted(Consts.CASE_REVIEWER_PROMPT,origin_message, caseInfo)).call().chatResponse();
        String output = null;
        if (response != null) {
            output = response.getResult().getOutput().getText();
        }


        Map<String, Object> updated = new HashMap<>();
        updated.put(Consts.CASE_REVIEW_MESSAGE, output);

        return updated;
    }
}
