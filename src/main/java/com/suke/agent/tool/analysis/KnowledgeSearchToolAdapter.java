package com.suke.agent.tool.analysis;

import com.suke.tool.KnowledgeSearchTool;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeSearchToolAdapter {

    private final KnowledgeSearchTool knowledgeSearchTool;

    public KnowledgeSearchToolAdapter(KnowledgeSearchTool knowledgeSearchTool) {
        this.knowledgeSearchTool = knowledgeSearchTool;
    }

    @Tool(description = "从知识库中检索与用户分析目标相关的行业知识、分析方法和最佳实践")
    public String searchKnowledge(
            @ToolParam(description = "用户的分析目标或查询意图") String goal) {
        return knowledgeSearchTool.searchKnowledge(goal);
    }

    @Tool(description = "按行业分类搜索知识库内容")
    public String searchByIndustry(
            @ToolParam(description = "查询内容") String query,
            @ToolParam(description = "行业类型，如：金融、医疗、科技") String industry) {
        return knowledgeSearchTool.searchByIndustry(query, industry);
    }
}
