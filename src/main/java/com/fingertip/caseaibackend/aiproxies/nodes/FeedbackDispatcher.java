package com.fingertip.caseaibackend.aiproxies.nodes;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.EdgeAction;
import com.fingertip.caseaibackend.commons.Consts;

import java.util.HashMap;
import java.util.Map;

public class FeedbackDispatcher implements EdgeAction {
    @Override
    public String apply(OverAllState t) {

        String reviewResult = (String) t.value(Consts.REVIEW_RESULT).orElse("error");

        // 传递详细反馈给生成节点
        if ("fail".equals(reviewResult)) {
            Map<String, Object> updated = new HashMap<>();
            updated.put(Consts.CASE_REVIEW_MESSAGE,
                    t.value(Consts.CASE_REVIEW_MESSAGE).orElse(""));
        }

        return reviewResult;
    }
}
