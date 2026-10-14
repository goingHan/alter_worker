# 项目Git分支管理完整方案（通用企业级）

本文档为团队通用的Git分支管理规范，基于Git Flow简化版优化，覆盖新增功能、Bug修复、框架变更等核心场景，明确分支命名、操作流程及管理规则，新手可直接参照执行，确保多人协作高效、代码版本可控。

# 一、固定核心分支（永久存在，不直接开发）

## 1. main / master（生产分支）

- **核心作用**：存放生产环境可直接部署的稳定代码，是项目的最终版本基准。

- **核心规则**：任何人不得直接在该分支提交代码，仅接受从其他分支（hotfix、release）合并的代码。

## 2. develop / dev（开发集成分支）

- **核心作用**：团队日常开发的集成分支，所有新功能、非紧急Bug修复、架构变更均在此分支汇总、测试。

- **核心规则**：作为开发主线，不直接开发，仅接受从feature、fix、refactor分支合并的代码。

# 二、分支命名规范（统一格式，一眼识别用途）

命名原则：前缀+用途描述，小写字母，用斜杠（/）分隔，描述简洁明了，避免模糊词汇。

## 1. 新增功能分支（feature）

- **命名格式**：`feature/功能描述`

- **示例**：
        

    - feature/login-module（登录模块）

    - feature/user-center（用户中心）

    - feature/order-pay（订单支付功能）

- **来源分支**：从develop分支切出

- **合并目标**：功能开发完成、测试通过后，合并回develop分支

## 2. Bug修复分支（fix / hotfix）

分为「开发环境Bug」和「生产环境紧急Bug」两类，区分管理避免混淆。

### ① 开发环境Bug（非线上，fix前缀）

- **命名格式**：`fix/bug描述`

- **示例**：
        

    - fix/login-fail（登录失败Bug）

    - fix/form-validate-error（表单校验错误）

    - fix/data-display-error（数据展示异常）

- **来源分支**：从develop分支切出

- **合并目标**：修复完成、测试通过后，合并回develop分支

### ② 生产环境紧急Bug（hotfix前缀）

- **命名格式**：`hotfix/bug描述`

- **示例**：

    - hotfix/pay-crash（支付功能崩溃）

    - hotfix/page-white-screen（页面白屏异常）

    - hotfix/data-loss（数据丢失紧急修复）

- **来源分支**：从main分支切出（确保基于生产环境最新代码修复）

- **合并目标**：修复完成后，先合并回main分支（更新生产版本），再同步合并到develop分支（同步到开发主线）

## 3. 框架变更/架构调整/代码重构（refactor）

- **命名格式**：`refactor/变更描述`

- **示例**：
        

    - refactor/vue3-upgrade（Vue3框架升级）

    - refactor/webpack-optimize（Webpack构建优化）

    - refactor/db-structure（数据库结构重构）

    - refactor/code-standard（代码规范重构）

- **来源分支**：从develop分支切出

- **合并目标**：重构完成、测试稳定后，合并回develop分支

## 4. 版本发布分支（可选，release）

- **命名格式**：`release/版本号`

- **示例**：


    - release/v1.0.0

    - release/v1.2.1

- **来源分支**：从develop分支切出（当develop分支功能满足发布需求时）

- **合并目标**：测试无Bug后，合并到main分支（发布生产）和develop分支（同步发布版本）



# 三、完整操作流程（可直接复制命令执行）

## 1. 开发新功能（feature分支流程）

```bash
# 1. 切换到develop分支，拉取最新代码
git checkout develop
git pull origin develop

# 2. 从develop切出feature分支，开始开发
git checkout -b feature/登录模块

# 3. 开发过程中，频繁提交代码（规范提交信息）
git add .
git commit -m "feat: 完成登录页面布局"
git commit -m "feat: 实现账号密码校验逻辑"

# 4. 功能开发完成、测试通过后，合并回develop
git checkout develop
git pull origin develop  # 再次拉取最新代码，避免冲突
git merge --no-ff feature/登录模块  # --no-ff保留分支历史，便于追溯
git push origin develop

# 5. 合并完成后，删除feature分支（保持分支整洁）
git branch -d feature/登录模块
# 删除远程分支
# git push origin --delete remoteBranchName

```

## 2. 修复开发环境Bug（fix分支流程）

```bash
# 1. 切换到develop分支，拉取最新代码
git checkout develop
git pull origin develop

# 2. 从develop切出fix分支，开始修复
git checkout -b fix/登录失败

# 3. 修复完成后，提交代码
git add .
git commit -m "fix: 修复登录时账号密码加密异常问题"

# 4. 合并回develop分支
git checkout develop
git pull origin develop
git merge --no-ff fix/登录失败
git push origin develop

# 5. 删除fix分支
git branch -d fix/登录失败
```

## 3. 修复生产环境紧急Bug（hotfix分支流程）

```bash
# 1. 切换到main分支，拉取生产环境最新代码
git checkout main
git pull origin main

# 2. 从main切出hotfix分支，紧急修复
git checkout -b hotfix/支付崩溃

# 3. 修复完成后，提交代码
git add .
git commit -m "hotfix: 修复支付接口超时导致的崩溃问题"

# 4. 合并到main分支，更新生产版本
git checkout main
git merge --no-ff hotfix/支付崩溃
git tag v1.0.1  # 打版本标签，便于版本回溯
git push origin main --tags  # 推送版本标签

# 5. 同步合并到develop分支，避免开发主线遗漏修复
git checkout develop
git merge --no-ff hotfix/支付崩溃
git push origin develop

# 6. 删除hotfix分支
git branch -d hotfix/支付崩溃
```

## 4. 框架变更/架构重构（refactor分支流程）

```bash
# 1. 切换到develop分支，拉取最新代码
git checkout develop
git pull origin develop

# 2. 从develop切出refactor分支，开始重构
git checkout -b refactor/vue3-upgrade

# 3. 重构过程中，分阶段提交代码
git add .
git commit -m "refactor: 完成Vue2到Vue3的基础依赖升级"
git commit -m "refactor: 适配Vue3语法，修改组件渲染逻辑"

# 4. 重构完成、测试稳定后，合并回develop
git checkout develop
git pull origin develop
git merge --no-ff refactor/vue3-upgrade
git push origin develop

# 5. 删除refactor分支
git branch -d refactor/vue3-upgrade
```

# 四、分支管理黄金规则（必遵守）

1. 「禁止直接提交」：main、develop分支禁止直接提交代码，所有修改必须通过子分支合并。

2. 「单一职责」：一个分支只做一件事（一个功能、一个Bug、一次重构），避免分支功能混乱。

3. 「及时合并」：功能/修复完成后，尽快合并到目标分支，不要长期保留子分支（建议不超过3个工作日）。

4. 「合并前测试」：子分支合并到develop/main前，必须完成自测、联调测试，避免引入Bug。

5. 「版本追溯」：hotfix、release分支合并后，必须给main分支打版本标签（如v1.0.0），便于后续回溯。

6. 「分支整洁」：子分支合并完成后，及时删除，避免分支过多导致混乱。

# 五、分支类型速查表（快速查阅）

|应用场景|分支前缀|来源分支|合并目标分支|核心说明|
|---|---|---|---|---|
|新增功能|feature/|develop|develop|日常功能开发，测试通过后合并|
|开发环境Bug修复|fix/|develop|develop|非线上Bug，不影响生产环境|
|生产环境紧急Bug修复|hotfix/|main|main + develop|紧急修复生产问题，需同步到开发主线|
|框架/架构变更/重构|refactor/|develop|develop|不改变功能，仅优化架构/代码|
|版本发布|release/|develop|main + develop|用于版本发布前的测试、Bug修复|
# 六、补充说明（新手避坑）

- 冲突处理：合并分支时若出现冲突，先通过`git status`查看冲突文件，打开文件删除Git冲突标记（<<<<<<<、=======、>>>>>>>），保留正确代码，提交后再完成合并。

- 提交规范：建议遵循「前缀+描述」的提交信息，如feat（新功能）、fix（修复）、refactor（重构）、docs（文档），便于追溯提交记录。

- 多人协作：开发前先拉取目标分支最新代码，避免出现代码分叉（避免non-fast-forward错误）。

- 版本标签：main分支的版本标签建议遵循「v主版本.次版本.修订版本」（如v1.0.0），修订版本用于hotfix修复（如v1.0.1）。
> （注：文档部分内容可能由 AI 生成）

大致流程图

                     feature/A  feature/B  feature/C
                         ↓         ↓         ↓
                         └─────────┴─────────┘
                                  ↓
                              develop（集成、测试、无风险）
                                  ↓
                          release/v1.2（发布验证）
                                  ↓
                                  ↓
                         master（兜底回滚库）