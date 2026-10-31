# 手写伴学题库格式与内置来源

ZIP 根目录包含 bank.json 和相对图片路径；版本1继续兼容，建议使用版本2。

导入器也支持外层文件夹、一个 ZIP 内含多个题库文件夹，以及 ZIP 中再嵌套独立题库 ZIP（最多4层嵌套）。一次选择总包即可导入所有题库；各题库资源路径仍相对各自的 bank.json。整包先校验再安装，任何子题库失败则整批回滚。同一总包中不允许重复题库ID。每个题库仍限100 MiB、10,000文件；整批限128个题库、128个ZIP、120,000条目和4 GiB累计解压数据（含嵌套ZIP本身）。

自定义题目使用普通版本2题库。`referencePolicy: "independent"` 时允许没有参考答案，模型按独立解题/写作标准评价；默认 `reference` 仍必须提供解析文字或图片。自定义题目不填写虚构参考答案。

照片创建的题目沿用 `questionImages` / `answerImages` 保存分离后的题目和参考区域。可选 `initialStudentMarkdown` 仅在该题尚无答题草稿时填入答题区；可选 `studentSourceImages` 保存原手写作答区域供核对，随学习记录导出，但不会作为参考答案，也不会在后续编辑后重复注入作答。

补充题库使用独立题号记录。可选 `source` / `referenceSource` 保存来源文件、原页码、题号和 `pageRects`；`referenceAvailable:false` 表示解析尚未可靠配对，不允许自动批改。`materials` 可列出附加资料，格式为 `{title,type,path,source}`，`type` 支持 `audio` 和 `image`，路径相对题库目录。`unitLabel` 默认为题。附加资料不计作独立试题。

```json
{
  "version": 2,
  "id": "my-bank",
  "title": "我的题集",
  "subject": "数学",
  "knowledgeSystem": {"nodes": [
    {"id": "limits", "name": "极限", "parentId": ""},
    {"id": "equivalent", "name": "等价无穷小", "parentId": "limits"}
  ]},
  "questions": [{
    "id": "q1", "title": "第一题", "chapter": "第一章", "number": 1,
    "topic": "极限", "type": "solution", "knowledgeIds": ["equivalent"],
    "questionMarkdown": "求极限 $$\\lim_{x\\to0}\\frac{\\sin x}{x}$$。",
    "answerMarkdown": "由基本极限得 $$1$$。",
    "questionImages": [], "answerImages": [], "referencePolicy": "reference"
  }]
}
```

knowledgeSystem 保存完整知识结构，节点 ID 唯一、父节点存在、不能循环；题目的 knowledgeIds 引用节点，筛选父节点会包括其所有后代。题库可用 Markdown（支持 LaTeX）、图片或二者组合。questionImage 为旧版单图字段；questionImages 为多图数组（最多16张），answerImages 最多8张。题型包括 choice、multi-choice、fill、solution、reading、cloze、matching、translation、writing、analysis。章内题号与题型共同消除重号；全库序号为数组顺序，从1开始。

没有官方答案时请将 referencePolicy 设为 independent，并在 answerMarkdown 中如实说明独立评价的依据。多材料题可用一条记录保留完整上下文，明确材料组包含的小题数及评分方式。

英语二将原PDF的题面与图表渲染为图片，按年份保留完形、4篇阅读、新题型、翻译、小作文、大作文，共99个学习单元。参考答题表图片保留完整，提示模型只读取对应年份/题号；写作没有范文时不生成虚构的官方答案。

政治源文件19份原样保存在 assets/banks/politics/sources；旧Word通过只读 Apache POI 提取段落、编号与图片，新Word通过OOXML提取，图片锚点归入所属题目。718题包含源文档2018年第16题的缺口；coverage.json 记录逐卷覆盖情况及源文件SHA256。题库细知识点采用关键词初步标注，可人工校订，不能将其视为官方命题分类。

重新验证：`python tools/verify_subject_banks.py`。生成：`tools/WordRead.java` 提取旧Word后运行 `python tools/prepare_subject_banks.py`；数学完整库使用 `python tools/prepare_full_bank.py`，会同步知识元数据。可下载导入的样例为 examples/bank-template.zip，应用设置页也提供模板导出。

模型用量采用实际响应中的 usage 字段；流式数据解析参照 [DeepSeek Chat Completions](https://api-docs.deepseek.com/api/create-chat-completion/)，余额格式参照 [User Balance](https://api-docs.deepseek.com/api/get-user-balance/)。费率由用户配置，费用是本地估算，账户余额必须从提供商查询。
