百炼key
https://bailian.console.aliyun.com/?tab=model#/api-key


https://java2ai.com/docs/quick-start

### 极简Agent+Skill示例
核心逻辑：Skill注册在Agent → 大模型选Skill → Agent执行Skill
```java
import java.util.HashMap;
import java.util.Map;

/**
 * 极简Agent+Skill示例
 * 核心逻辑：Skill注册在Agent → 大模型选Skill → Agent执行Skill
 */
public class AgentSkillDemo {

    // ====================== 1. 定义Skill接口（所有工具都实现这个接口） ======================
    interface Skill {
        // 执行工具的方法
        String execute(Map<String, String> params);
        // 获取工具名称
        String getName();
        // 获取工具描述（给大模型看）
        String getDesc();
    }

    // ====================== 2. 实现具体的Skill（比如计算器、查订单） ======================
    // 计算器Skill
    static class CalculatorSkill implements Skill {
        @Override
        public String execute(Map<String, String> params) {
            String expression = params.get("expression");
            // 简单实现：支持加减乘除
            try {
                // 这里简化计算逻辑，实际可替换为真实计算库
                double result = (double) new javax.script.ScriptEngineManager()
                        .getEngineByName("JavaScript")
                        .eval(expression);
                return "计算结果：" + result;
            } catch (Exception e) {
                return "计算失败：" + e.getMessage();
            }
        }

        @Override
        public String getName() {
            return "calculator";
        }

        @Override
        public String getDesc() {
            return "用于计算数学表达式，参数：expression（比如1+1、2*3）";
        }
    }

    // 查订单Skill
    static class OrderQuerySkill implements Skill {
        @Override
        public String execute(Map<String, String> params) {
            String userId = params.get("userId");
            String orderId = params.get("orderId");
            // 模拟查数据库：实际可替换为真实DB查询
            return "用户" + userId + "的订单" + orderId + "信息：状态=已发货，金额=99元";
        }

        @Override
        public String getName() {
            return "query_order";
        }

        @Override
        public String getDesc() {
            return "查询用户订单信息，参数：userId（用户ID）、orderId（订单ID）";
        }
    }

    // ====================== 3. 实现Agent（调度器，管理Skill） ======================
    static class Agent {
        // Skill注册表：key=Skill名称，value=Skill实例（核心！Skill存在这里）
        private Map<String, Skill> skillRegistry = new HashMap<>();

        // 注册Skill到Agent
        public void registerSkill(Skill skill) {
            skillRegistry.put(skill.getName(), skill);
            System.out.println("✅ Skill注册成功：" + skill.getName() + " - " + skill.getDesc());
        }

        // 获取所有Skill的清单（给大模型看的描述）
        public String getSkillListPrompt() {
            StringBuilder sb = new StringBuilder("可用工具列表：\n");
            for (Skill skill : skillRegistry.values()) {
                sb.append("- 名称：").append(skill.getName())
                  .append("，描述：").append(skill.getDesc()).append("\n");
            }
            return sb.toString();
        }

        // 执行指定的Skill
        public String executeSkill(String skillName, Map<String, String> params) {
            Skill skill = skillRegistry.get(skillName);
            if (skill == null) {
                return "❌ 未找到Skill：" + skillName;
            }
            System.out.println("🔧 执行Skill：" + skillName);
            return skill.execute(params);
        }
    }

    // ====================== 4. 模拟大模型（选Skill） ======================
    static class MockLLM {
        // 大模型根据用户问题+Skill清单，选择要调用的Skill（返回JSON格式）
        public String chooseSkill(String userQuestion, String skillList) {
            // 模拟大模型思考过程：实际是通过Prompt让大模型输出JSON
            String prompt = "用户问题：" + userQuestion + "\n" +
                           "可用工具列表：\n" + skillList + "\n" +
                           "请选择要调用的工具，输出JSON格式：{\"skill\":\"工具名称\",\"params\":{\"参数名\":\"参数值\"}}";

            System.out.println("\n===== 大模型Prompt =====\n" + prompt + "\n");

            // 模拟大模型返回结果（实际是调用大模型API）
            if (userQuestion.contains("计算") || userQuestion.contains("等于")) {
                return "{\"skill\":\"calculator\",\"params\":{\"expression\":\"1+1*2\"}}";
            } else if (userQuestion.contains("订单")) {
                return "{\"skill\":\"query_order\",\"params\":{\"userId\":\"1001\",\"orderId\":\"OD20260318\"}}";
            } else {
                return "{\"skill\":\"unknown\",\"params\":{}}";
            }
        }
    }

    // ====================== 5. 主程序（完整流程演示） ======================
    public static void main(String[] args) {
        // 1. 初始化Agent
        Agent agent = new Agent();

        // 2. 注册Skill到Agent（核心：Skill存在Agent里）
        agent.registerSkill(new CalculatorSkill());
        agent.registerSkill(new OrderQuerySkill());

        // 3. 用户提问
        String userQuestion = "计算1+1*2等于多少？";
        // String userQuestion = "查询用户1001的订单OD20260318信息"; // 可切换测试查订单

        // 4. 大模型选择Skill
        MockLLM llm = new MockLLM();
        String skillList = agent.getSkillListPrompt(); // Agent把Skill清单传给大模型
        String llmResponse = llm.chooseSkill(userQuestion, skillList); // 大模型选Skill

        System.out.println("===== 大模型返回 =====\n" + llmResponse + "\n");

        // 5. Agent解析大模型结果并执行Skill（简化JSON解析，实际用FastJSON/Jackson）
        // 模拟解析：从大模型返回的JSON中提取skillName和params
        String skillName = "calculator"; // 实际从JSON解析
        Map<String, String> params = new HashMap<>();
        params.put("expression", "1+1*2"); // 实际从JSON解析

        // 6. Agent执行Skill并返回结果
        String result = agent.executeSkill(skillName, params);
        System.out.println("===== 最终结果 =====\n" + result);
    }
}
```