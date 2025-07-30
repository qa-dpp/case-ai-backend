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

    public CaseGenerateNode(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public Map<String, Object> apply(OverAllState t) {
        String old_testcase_message = (String) t.value(Consts.OLD_TESTCASE_MESSAGE).orElse("");
        String origin_message = (String) t.value(Consts.ORIGIN_MESSAGE).orElse("");
        String case_reviewer_message = (String) t.value(Consts.CASE_REVIEW_MESSAGE).orElse("");
        String caseInfo = (String) t.value(Consts.CASE_INFO_MESSAGE).orElse("");


        if (!StringUtils.hasText(origin_message)) {
            throw new IllegalArgumentException("没有找到原始消息");
        }

        String content = null;
        if (StringUtils.hasText(old_testcase_message)) {
            content = Consts.CASE_EXTENSION_PROMPT+"\n\n历史用例\n"+old_testcase_message + "\n\n 新增需求\n" + origin_message;
        } else {
            content = Consts.CASE_WRITER_PROMPT + "\n\n" + origin_message;
        }
        //如果是用例打回，则凭借历史用例信息和建议
        if (StringUtils.hasText(case_reviewer_message) && StringUtils.hasText(caseInfo)) {
            content = "%s\n# 原始需求:\n%s\n\n# 上个版本需求用例:\n%s \n# 专家意见:%s\n".formatted(Consts.CASE_WRITER_PROMPT, origin_message, caseInfo, case_reviewer_message);
        }

        ChatResponse response = chatClient.prompt(content).call().chatResponse();
        String output = null;
        if (response != null) {
            output = response.getResult().getOutput().getText();
        }

        Map<String, Object> updated = new HashMap<>();
        updated.put(Consts.CASE_INFO_MESSAGE, output);

        return updated;
    }
}
