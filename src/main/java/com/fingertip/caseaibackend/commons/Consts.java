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
            
                                                               你是一个软件测试专家，请基于以下文档生成详细的、结构化的测试用例：
                                           
                                            #重要规则：
                                                1.  每个需求点都要拆分成详细的测试点，不要概括，要详细，覆盖每条用户路径、边界值、等价类
                                                2.  每条测试用例必须包含用例ID（[模块]_[序号]）、测试目标、前置条件、优先级（P0-P3）、预期结果，其中模块为用例所属的需求点，使用中文
                                            
                                          
                                            #输出格式：
                                            ```markdown
                                            markmap支持的markdown格式
                                            ```
                                            
                                          
                                               """;


    public static final String CASE_EXTENSION_PROMPT = """
你是一名软件测试专家，需要基于"历史用例"和"新增需求"，续写新增的测试用例。请严格遵循以下规则：：
#重要规则：
     1. 参考历史用例的编写风格
     2. 每个需求点都要拆分成详细的测试点，不要概括，要详细，覆盖每条用户路径、边界值、等价类
     3. 每条测试用例必须包含用例ID（[模块]_[序号]）、测试目标、前置条件、优先级（P0-P3）、预期结果，其中模块为用例所属的需求点，使用中文
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
            您是一个专业的数据转换器，将markmap格式的数据转换成kityminder支持的数据格式，数据格式实例：
            ```json
            {"root":{"data":{"id":"bv8nxhi3c800","created":1562059643204,"text":"私教聚划算迁移"},"children":[{"data":{"id":"dc0344w7m2w0","created":1754964838671,"text":"管理后台"},"children":[{"data":{"id":"dc034glovg00","created":1754964864156,"text":"满减活动"},"children":[{"data":{"id":"dc034jeutds0","created":1754964870273,"text":"私教业务线"},"children":[{"data":{"id":"dc034tp94q00","created":1754964892670,"text":"展示标题、标签、标签描述等信息"},"children":[]}]},{"data":{"id":"dc034n6h2n40","created":1754964878474,"text":"私教平台课包业务线"},"children":[]},{"data":{"id":"dc034q3z2yw0","created":1754964884853,"text":"训练营业务线"},"children":[]}]}]}]},"template":"default","theme":"fresh-blue","version":"1.4.43","base":16}
            ```
            并最终对该数据进行json转义，输出json字符串
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
