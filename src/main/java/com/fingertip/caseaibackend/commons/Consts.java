package com.fingertip.caseaibackend.commons;

public class Consts {

    // 新增：评估指标权重（JSON格式，用于评估者参考）
    public static final String EVALUATION_CRITERIA = """
        {
            "需求覆盖率": {"weight": 0.3, "description": "覆盖的需求点占总需求点比例"},
            "可执行性": {"weight": 0.25, "description": "用例步骤是否可落地执行"},
            "优先级": {"weight": 0.2, "description": "基于需求重要性的优先级评分"},
            "冗余度": {"weight": 0.15, "description": "是否与其他用例重复或可合并"},
            "缺陷发现潜力": {"weight": 0.1, "description": "历史类似用例发现缺陷的概率"}
        }
        """;

    // 新增：向量数据库检索提示（用于获取历史用例）
    public static final String VECTOR_DB_QUERY_PROMPT = "检索与需求关键词「{keywords}」相关的历史测试用例，返回前5条";

    public static final String ANALYZE_PROMPT = """
            您是一名专业的文档分析工程师，你可以读取需求文档和技术方案文档，生成详尽的、逻辑严谨的需求点，不要遗漏任何重要信息。忽略包括版本信息、变更日志、背景、目标等信息。
            #重要规则
                1.  将文档内容分成【功能】、【接口】、【其他】三个部分
                2.  【功能】为涉及交互、业务流程、以及技术架构图的流程相关内容
                3.  【接口】为涉及API定义、参数、响应、协议的内容
            """;

    public static final String CASE_WRITER_PROMPT = """
            
                                                               你是一个软件测试专家，请基于以下文档生成**结构化测试用例**，保障覆盖业务流程和技术架构图的所有分支：
                                           
                                            #重要规则：
                                                【功能】的部分，生成功能测试用例
                                                    1.  确保每个用例ID唯一，避免重复
                                                    2.  技术架构图的流程相关内容，需要根据技术架构图，设计具体的测试用例
                                                    3.  业务流程相关内容，需要根据业务流程，设计具体的测试用例
                                                    4.  其他相关内容，需要根据需求文档的内容，结合业务功能，设计具体的测试用例
                                                    5.  每条测试用例必须包含用例ID（[模块]_[序号]）、测试目标、前置条件、优先级（P0-P3）、预期结果，其中模块为用例所属的需求点，使用中文
                                            
                                                【接口】的部分，生成接口测试用例
                                                    1.  确保每个用例ID唯一，避免重复
                                                    2.  有详细接口出入参说明的，按照出入参设计具体的接口测试用例；没有接口出入参说明的，按照接口的作用，给出业务功能上的接口测试用例
                                                
                                                【其他】的部分，生成其他测试用例
                                                    1.  确保每个用例ID唯一，避免重复
                                                    2.  其他测试用例的设计，需要根据需求文档的内容，结合业务功能，设计具体的测试用例
                                          
                                            #输出格式：markmap支持的markdown格式
                                          
                                               """;


    public static final String CASE_EXTENSION_PROMPT = """
            
                                                               你是一名软件测试专家，需要基于"历史用例"和"新增需求"，续写新增的测试用例。请严格遵循以下规则：：
                                            
                                            1.**用例筛选原则**：
                                              - 忽略历史用例中已存在的用例
                                              - 仅生成**新增需求点**对应的用例
                                            2. **用例设计原则**
                                               - 包含用例ID（[模块]_[序号]）、测试目标、前置条件、优先级（P0-P3）
                                               - 具体的预期结果[重复上述模板直到达到指定的用例数量]
                                               - 模块为用例所属的需求点，使用中文
                                            #输出格式：
                                            ```markdown
                                            markmap支持的markdown格式
                                            ```
                                         
                                               """;

    public static final String CASE_REVIEWER_PROMPT = """
            您是一名资深测试架构师，能够根据需求点对测试用例进行评审。
            # 重要规则：
            1. 检查用例是否完整，必须包含用例ID（[模块]_[序号]）、测试目标、前置条件、优先级（P0-P3）、预期结果。
            2. 检查用例是否包含了原始文档中所有的流程分支
            3. 如测试用例覆盖不足，请分条给出具体建议；   否则请输出“APPROVE”
             """;

    public static final String CASE_FORMAT_PROMPT = """
            您是一个专业的数据提取器，提取出所有的测试用例相关内容。
            """;
    public static final String VISUAL_PROMPT = """
            请使用中文将图片中设计稿部分的交互细节或者技术方案架构图的流程尽可能详细的描述出来，如⻓按视频 展⽰底部功能弹窗，弹窗展示不感兴趣、内容质量差和举报的按钮选项。
            """;


    public static final String OLD_TESTCASE_MESSAGE = "OldTestcaseMessage";
    public static final String ORIGIN_MESSAGE = "originMessage";
    public static final String CASE_INFO_MESSAGE = "caseInfoMessage";
    public static final String CASE_REVIEW_MESSAGE = "caseReviewMessage";
    public static final String CASE_FORMAT_MESSAGE = "caseFormatMessage";
}
