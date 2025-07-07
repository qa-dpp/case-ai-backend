package com.fingertip.caseaibackend.aiproxies.nodes;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.fingertip.caseaibackend.commons.Consts;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;

import java.util.HashMap;
import java.util.Map;

public class CaseFormatNode implements NodeAction {
    private final ChatClient chatClient;

    public CaseFormatNode(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public Map<String, Object> apply(OverAllState t) {
        String caseInfo = (String) t.value(Consts.CASE_INFO_MESSAGE).orElse("");

        String content = Consts.CASE_FORMAT_PROMPT +"\n\n"+ caseInfo;
        ChatResponse response = chatClient.prompt(content).call().chatResponse();
        String output = null;
        if (response != null) {
            output = response.getResult().getOutput().getText();
        }

        Map<String, Object> updated = new HashMap<>();
        updated.put(Consts.CASE_FORMAT_MESSAGE, output);

        return updated;
    }
}
