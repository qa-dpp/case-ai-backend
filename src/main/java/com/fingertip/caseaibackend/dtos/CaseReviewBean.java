package com.fingertip.caseaibackend.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CaseReviewBean {
    private String score;
    private String result;
    private String feedback;
    private String improvements;
}
