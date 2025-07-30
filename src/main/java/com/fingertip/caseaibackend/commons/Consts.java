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
            您是一名专业的文档分析工程师，你可以读取需求文档和技术方案文档，生成详尽的、逻辑严谨的需求点，不要遗漏任何重要信息。忽略包括版本信息、变更日志、背景、目标等信息。""";

    public static final String CASE_WRITER_PROMPT = """
            
                                                               你是一个软件测试专家，请基于以下需求生成**结构化测试用例**，请根据需求文档按照以下规范编写专业测试用例：
                                           
                                            #重要规则：
                                            1. 确保每个用例ID唯一，避免重复
                                            2. 测试用例必须包含所有关键路径
                                            3. 测试用例必须包含所有边界条件
                                            4. 测试用例必须包含所有异常情况
                                            5. 使用等价类划分、边界值分析、归因法等测试技术，确保用例的需求覆盖率达到100%
                                            
                                            #测试策略：
                                            1. **用例设计原则**
                                               - 包含用例ID（[模块]_[序号]）、测试目标、前置条件、优先级（P0-P3）
                                               - 具体的预期结果[重复上述模板直到达到指定的用例数量]
                                               - 模块为用例所属的需求点，使用中文
                                           2. **输出格式**
                                               - 以表格形式输出，包含用例ID、测试目标、前置条件、优先级、具体的预期结果
                                         
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
            1. 请用简体中文输出内容
            2. 如测试用例覆盖不足，请分条给出具体建议；   否则请输出“APPROVE”
             """;

    public static final String CASE_FORMAT_PROMPT = """
            您是一个专业的数据提取器，提取出所有的用例内容，并转换成markmap支持的markdown格式。
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
