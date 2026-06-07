# Vision LLM 基准测试结果

> 设计稿 → 结构化规范工具 (`extract_design_*` / `design_to_compose`) 的视觉 LLM 性能对比报告。
> 目的：在多个提供商/模型中选型；为 prompt 与流程优化提供数据基础。

**测试日期**：2026-06-07
**测试环境**：
- 测试图：`design/首页-推荐.jpg`（909KB，1080×2400）
- 工具脚本：`skills/src/test/vision-benchmark.test.ts`
- 运行环境：MacOS，公司代理 (MITM 自签名证书) → 启用 `*_INSECURE_TLS=1`
- SDK：OpenAI 兼容客户端 + 自定义 `https.request` fetch 绕过 TLS 拦截
- 总耗时：303 秒（5 分钟）

---

## 一、测试矩阵

| 任务 | 工具 | 输入 | 输出 |
|------|------|------|------|
| 结构化抽取 | `extract_design_spec(format=json)` | 设计稿 | 完整 JSON 规范（colorTokens/typography/components/sections 等） |
| 颜色 token | `extract_design_tokens` | 设计稿 | 5-10 个主色 token（hex + 用途 + 占比） |
| 组件列表 | `extract_design_components` | 设计稿 | 按 Y 排序的组件列表（kind/bounds/text/source） |

每个工具对 3 个 Minimax 模型各跑一次（共 9 次 vision API 调用）。

Kimi k2.6 默认跳过（见 [§五 控制开关](#五-控制开关)）。

---

## 二、Minimax 三模型对比（实测结果）

| 模型 | 平均耗时 | 平均输出 tokens | JSON 解析 |
|------|---------|-----------------|-----------|
| **MiniMax-M2.7-highspeed** | **23.7s** | 4,431 | 3/3 ✅ |
| MiniMax-M2.7 | 31.7s | 1,444 | 3/3 ✅ |
| MiniMax-M3 | 39.6s | 5,537 | 3/3 ✅ |

### 详细耗时分布

| 任务 | MiniMax-M3 | MiniMax-M2.7 | MiniMax-M2.7-highspeed |
|------|------------|--------------|-------------------------|
| `extract_design_spec(json)` | 67.1s | 52.3s | **40.1s** |
| `extract_design_components` | 37.5s | 35.9s | **27.6s** |
| `extract_design_tokens` | 14.1s | 7.0s | **3.4s** |
| **平均** | 39.6s | 31.7s | **23.7s** |

### 关键观察

1. **M2.7-highspeed 是速度最优**：
   - 平均 23.7s（3 倍提升于 Kimi baseline）
   - 单任务最快 3.4s（颜色 token 提取）
2. **M3 输出最详尽**（5,537 tokens），适合代码生成（`design_to_compose`）
3. **3 个模型 JSON 解析全部成功**（Kimi 时是 0/3，需要 fallback `reasoning_content`）
4. **`extract_design_tokens` 最快**（3-14s）：prompt 短、输出少，色彩提取天然轻量

---

## 三、Kimi Baseline（已废弃，参考用）

> 2026-06-07 早期 benchmark 数据；Minimax 切换后 Kimi 不再作为默认提供商。

| 模型 | 平均耗时 | 平均输出 tokens | JSON 解析 |
|------|---------|-----------------|-----------|
| kimi-k2.6 (reasoning) | 100-150s | 13,000+ | 0/3 |

- **慢 3-25x**：reasoning model 输出 13K+ reasoning_tokens 后才出 `content`
- **解析需 fallback**：`content` 字段在 reasoning 模型中为空，必须取 `reasoning_content`
- **温度强约束**：仅支持 `temperature=1.0`

---

## 四、推荐配置

| 场景 | 推荐模型 | 理由 |
|------|---------|------|
| 颜色 token 提取（`extract_design_tokens`） | `MiniMax-M2.7-highspeed` | 3.4s 完成 |
| 组件列表（`extract_design_components`） | `MiniMax-M2.7-highspeed` | 27.6s |
| 完整规范（`extract_design_spec`） | `MiniMax-M2.7` | 性价比最佳，JSON 稳定 |
| Compose 代码生成（`design_to_compose`） | `MiniMax-M3` | 1M 上下文、思考深度高 |
| 大批量扫描（14 张设计稿） | `MiniMax-M2.7-highspeed` | 100 TPS |

**默认配置**（写入 `skills/.env`）：

```bash
VISION_PROVIDER=minimax
MINIMAX_API_KEY=sk-cp-xxxxxxxx
MINIMAX_INSECURE_TLS=1  # 仅在有 MITM 代理时启用
```

---

## 五、控制开关

### 5.1 `npm test`（常规测试，< 1 分钟）

Kimi 模式自动跳过 4 个 vision 测试（避免 100-150s 等待），不影响其它 23 个测试。

| env 变量 | 效果 |
|---------|------|
| `VISION_PROVIDER=minimax` + `MINIMAX_API_KEY` 已设 | 4 个 vision 测试正常跑（~4-70s/调用） |
| Kimi 模式（`MOONSHOT_API_KEY` 但无 `MINIMAX_API_KEY`） | 4 个 vision 测试自动 skip，输出"⏭️ Skipping: Kimi vision mode disabled" |
| `RUN_KIMI_VISION_TESTS=1` | 强制启用 Kimi vision 测试（CI 上慎用，单测会跑 5+ 分钟） |

### 5.2 `npm run test:vision-bench`（独立基准，~5 分钟）

```bash
npm run test:vision-bench
```

Kimi 默认跳过。要强制启用：

```bash
RUN_VISION_BENCH_WITH_KIMI=1 npm run test:vision-bench
```

---

## 六、已知瓶颈与优化空间

### 6.1 当前已实施
- ✅ 图片预处理：长边缩到 768px + JPEG quality=85（传输量 -50%）
- ✅ 智能 resize skip：< 80KB 文件跳过 resize
- ✅ 6 要素 description 模板：Agent 调工具的准确率提升
- ✅ provider 透传：单次调用可指定 provider + model

### 6.2 未来优化（v3.2+）
1. **流式输出**：Minimax 支持 SSE 流式，可感知进度（但 MCP 协议当前用阻塞 JSON 输出）
2. **批处理**：14 张设计稿并发调用，理论可缩到 ~70s
3. **缓存**：相同图片 + 相同 pageHint 走本地缓存
4. **response_format: json_object**：在 prompt 里显式请求 JSON mode（Minimax 文档说支持）
5. **Prompt 示例注入**：每个工具的 system prompt 注入 1 个完整 JSON 示例
6. **thinking 控制优化**：当前 M3 的 `extra_body: { thinking: { type: "disabled" } }` 似乎未生效，tokens 仍高

---

## 七、复现命令

```bash
cd skills
npm install
# 默认（Minimax 3 模型）
npm run test:vision-bench
# 含 Kimi
RUN_VISION_BENCH_WITH_KIMI=1 npm run test:vision-bench
```

输出会写入 vitest 控制台，结果如本文件 [§二](#二minimax-三模型对比实测结果) 所示。
