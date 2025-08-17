package com.fingertip.caseaibackend.aiproxies.nodes;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.fingertip.caseaibackend.commons.Consts;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

public class CaseGenerateNode implements NodeAction {
    private final ChatClient chatClient;
    private static final int MAX_RETRY = 3;

    public CaseGenerateNode(ChatClient chatClient) {
        this.chatClient = chatClient;
    }



    @Override
    public Map<String, Object> apply(OverAllState t) {
        // 获取重试次数并检查上限
        int retryCount = (int) t.value(Consts.RETRY_COUNT).orElse(0);

        if (retryCount >= MAX_RETRY) {
            Map<String, Object> updated = new HashMap<>();
            updated.put(Consts.REVIEW_RESULT, "pass");
            return updated;
        }

        //获取上下文信息
        String old_testcase_message = (String) t.value(Consts.OLD_TESTCASE_MESSAGE).orElse("");
        String origin_message = (String) t.value(Consts.ORIGIN_MESSAGE).orElse("");
        String case_reviewer_message = (String) t.value(Consts.CASE_REVIEW_MESSAGE).orElse("");
        String caseInfo = (String) t.value(Consts.CASE_INFO_MESSAGE).orElse("");

        if (!StringUtils.hasText(origin_message)) {
            throw new IllegalArgumentException("没有找到原始消息");
        }

        //构建动态上下文
        StringBuilder contextBuilder = new StringBuilder();
        if (!old_testcase_message.isEmpty()) {
            contextBuilder.append("历史用例参考:\n").append(old_testcase_message).append("\n\n");
        }
        if (!case_reviewer_message.isEmpty()) {
            contextBuilder.append("评审反馈:\n").append(case_reviewer_message).append("\n\n");
        }
        if (!caseInfo.isEmpty() && retryCount > 0) {
            contextBuilder.append("上次生成的用例:\n").append(caseInfo).append("\n\n");
        }


        // 构建提示词
        String prompt = String.format(
                Consts.CASE_WRITER_PROMPT,
                contextBuilder.toString(),
                origin_message
        );

        //调用大模型生成用例
        ChatResponse response = chatClient.prompt(prompt).call().chatResponse();
        String output = null;
        if (response != null) {
            output = response.getResult().getOutput().getText();
        }

        Map<String, Object> updated = new HashMap<>();
        updated.put(Consts.CASE_INFO_MESSAGE, output);
        updated.put(Consts.RETRY_COUNT, retryCount + 1);

        return updated;
    }
}
