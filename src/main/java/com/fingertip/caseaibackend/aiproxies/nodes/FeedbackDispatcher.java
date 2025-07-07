package com.fingertip.caseaibackend.aiproxies.nodes;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.EdgeAction;
import com.fingertip.caseaibackend.commons.Consts;

public class FeedbackDispatcher implements EdgeAction {
    @Override
    public String apply(OverAllState t) {

        String output = (String) t.value(Consts.CASE_REVIEW_MESSAGE).orElse("");

        return output.toLowerCase().contains("approve") ? "positive" : "negative";
    }
}
