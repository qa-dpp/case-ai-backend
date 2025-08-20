package com.fingertip.caseaibackend.aiproxies.nodes;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.EdgeAction;
import com.fingertip.caseaibackend.commons.Consts;

import java.util.HashMap;
import java.util.Map;

public class FeedbackDispatcher implements EdgeAction {
    @Override
    public String apply(OverAllState t) {

        int retryCount = (int) t.value(Consts.RETRY_COUNT).orElse(0);
        int currentRetruCount = retryCount + 1;

        int score = (int) t.value(Consts.REVIEW_SCORE).orElse(0);
        String reviewResult = score > 85 ? "pass" : "fail";

        Map<String, Object> updated = new HashMap<>();

        if ("fail".equals(reviewResult)) {
            if (currentRetruCount >= 3) {
                reviewResult = "pass";
            }
        }
        updated.put(Consts.RETRY_COUNT, currentRetruCount);
        t.updateState(updated);
        return reviewResult;
    }
}
