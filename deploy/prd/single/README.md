# 单机生产部署

`vm1`、`vm2` 用于未来双机部署；本目录只用于当前单机部署。

## 准备

```bash
cd deploy/prd/single
cp .env.example .env
# 填写必需凭据；示例没有可用的默认密码。不要覆盖已有服务器 .env。
```

### 凭据与版本控制

四服务通过 `NACOS_USERNAME`、`NACOS_PASSWORD` 登录 Nacos；这与服务端 `NACOS_AUTH_TOKEN`、身份 key/value 不是同一组凭据。客户端 bootstrap 不再内置用户名或密码，应用 Compose 在任一登录变量缺失或为空时会直接报错。非 Compose 启动（包括本地 IDE）也必须提供这两个环境变量，不要使用带密码的命令行参数。

现有服务器 `.env` 已于 2026-09-21 补齐 `NACOS_USERNAME` / `NACOS_PASSWORD`，服务器 `app/docker-compose-app.yml` 也已同步注入这两个变量（见文末记录）。二者缺一不可：只改 `.env` 而 compose 不引用，容器依然收不到。在其它环境重建时应复用已存在且验证过的账号，不要创建默认账号、擅自轮换密码或覆盖整个 `.env`。填写含 `$`、`#` 等特殊字符的值时使用 Compose dotenv 单引号语法；不要 `source .env`，也不要把解析后的完整 Compose 配置输出到日志。

以下文件是私有运行输入，不纳入 Git：`.env` 及其备份、`data/`、`backups/`、`res/sql/*.sql`、`releases/`（服务器上的发布状态、保留制品与排障日志）。SQL 导出包含数据库/Redis/RabbitMQ 凭据、OSS/地图密钥、账号密码哈希及用户数据；它们不是公开示例。`vm1/`、`vm2/` 是未审核的未来双机方案，已在上级忽略规则中排除，不属于本次可提交范围。不要使用 `git add -f` 绕过忽略规则。

新环境必须从受控渠道准备并审核私有 SQL（见 `res/sql/README.md`）；本仓库不提供通用默认账号或生产数据库导出。现有生产配置保持不变，不因源码脱敏重新导入 SQL。

源码中此前存在的秘密应按可能已暴露处理：评估 Nacos 客户端密码、数据库/Redis/RabbitMQ 密码、OSS/地图密钥、Nacos 签名密钥及身份信息的轮换需求，先确认使用方和影响范围，再获得授权轮换。本次不改历史提交；`deploy/dev`、`lien-mstemplate` 等历史配置不在本次修复范围，不能据此宣称全仓库历史已脱敏。

业务镜像的 Dockerfile 期望对应服务目录中存在 JAR：

```text
app/service/admin/*.jar
app/service/file/*.jar
app/service/gateway/*.jar
app/service/portal/*.jar
```

## 启动

两个 Compose 文件必须使用同一个 project name 和网络；生产环境使用独立的 `frameworkjava-prd`，避免与开发环境容器冲突：

```bash
cd app

docker compose --env-file ../.env -p frameworkjava-prd \
  -f docker-compose-mid.yml up -d

docker compose --env-file ../.env -p frameworkjava-prd \
  -f docker-compose-app.yml up -d --build
```

中间件默认仅在 Docker 网络内互通；对外暴露 Nginx 的 `${WEB_PORT}`，以及 Nacos 控制台的 `8866` 端口。
业务服务配置由初始化导入的 Nacos 数据提供，不需要再在业务容器中重复配置 MySQL、Redis、RabbitMQ、OSS 或地图密钥。

## 数据初始化

### 首次部署

从本目录的 `app/` 执行 Compose 命令。SQL 挂载源 `../res/sql` 相对 Compose 文件所在目录解析，对应本目录的 `res/sql/`，不是上一级 `prd/res/sql/`。部署时必须同步这两份 SQL；其中配置与业务数据可能含敏感信息，不要公开或未经审核提交。

启动前检查文件，并通过 `docker compose config --quiet` 验证配置，避免完整输出包含凭据的解析结果：

```bash
# 当前目录：single/app
for sql in mysql/sql/nacos.sql ../res/sql/nacos-auth.sql ../res/sql/nacosdata.sql ../res/sql/db.sql; do
  test -s "$sql" || { printf '缺少 SQL：%s\n' "$sql"; exit 1; }
done
docker compose --env-file ../.env -p frameworkjava-prd \
  -f docker-compose-mid.yml config --quiet
```

首次初始化空的 MySQL 数据目录时，`mysql/init/init.sh` 会依次导入：

1. `app/mysql/sql/nacos.sql`：不含默认账号的 Nacos 表结构
2. `res/sql/nacos-auth.sql`：私有 Nacos 账号、bcrypt 密码哈希及角色
3. `res/sql/nacosdata.sql`：经审核的单机生产 Nacos 配置
4. `res/sql/db.sql`：经审核的业务表和数据

初始化脚本在建库前检查全部输入文件可读且非空，缺失时明确失败。不能把 `${VAR}` 直接写入 SQL 并期待 MySQL/Nacos 自动替换；私有 SQL 必须包含经审核的目标环境配置。

其中业务库导入目标是 `frameworkjava_prd`，Nacos 库是 `frameworkjava_nacos_prd`。

MySQL 数据目录已有数据时，`docker-entrypoint-initdb.d` 下的初始化脚本不会再次执行；不要为了重跑初始化而删除数据卷。MySQL 健康检查只验证连接，不代表业务表已成功导入。

### 已有环境补齐业务库

此流程只适用于已确认业务库为空的情况，不是通用迁移或重复导入流程。

1. 重新确认生产容器、目标库 `frameworkjava_prd`、表数量及当前挂载；如果已有业务表，停止并单独制定迁移方案。
2. 审核本次 SQL。当前 `db.sql` 来自开发库导出，包含六条 `DROP TABLE IF EXISTS` 和六张表的数据，不能覆盖已有业务表。它还包含测试用户、管理账号及测试参数；是否原样导入必须获得确认，不能默认视为生产种子数据。
3. 取得生产变更和数据范围授权后，在服务器上使用仅所有者可访问的目录备份业务库、Nacos 库及将修改的 Compose 文件。确认备份成功且可读，记录校验和；备份与日志不得进入 Git。
4. 同步经审核的 SQL 到正确目录，修正服务器 Compose 挂载。只改 Compose 文件不会更新正在运行的容器挂载；本次业务补齐可通过标准输入将 SQL 交给现有 MySQL 容器，不需要重建或重启中间件。
5. 导入前再次确认业务库仍为空，明确指定目标数据库为 `frameworkjava_prd`，仅执行经批准的业务 SQL。不要执行完整 `init.sh` 或重新导入 Nacos SQL；失败后停止，保留现场，不自动重复执行含 DROP 的文件。DDL 不具备整份文件事务回滚保证，恢复也需要单独确认。
6. 使用应用数据库账号核验表结构、精确行数和读取权限；比较导入前后的 Nacos 备份或配置摘要，确认 Nacos 未变更。完整业务接口验收留待应用发布。

当前导出文件的预期行数（只有批准原样导入时才作为验收基线）：

| 表 | 行数 |
|---|---:|
| `app_user` | 5 |
| `sys_argument` | 1 |
| `sys_dictionary_data` | 4 |
| `sys_dictionary_type` | 9 |
| `sys_region` | 3621 |
| `sys_user` | 2 |

每次生产导入均需单独授权及实际验证；不能仅因挂载修正或 MySQL 显示 healthy 就视为完成。

### Issue #1 执行记录（2026-09-21，服务器时间）

- 经用户授权，跳过备份并原样导入现有数据，包括测试用户、管理账号和测试参数；这不是后续生产操作的默认授权。
- 导入前确认 `frameworkjava_prd` 为空，服务器业务 SQL 与本地审核文件的 SHA-256 一致。
- 本地及服务器 Compose SQL 挂载均已修正为 `../res/sql`；服务器 Compose 配置校验通过。
- 经标准输入将业务 SQL 导入现有生产 MySQL，使用应用账号确认六张表及上表全部行数，并验证读取权限。
- 生产 Nacos 配置导入前后的摘要一致；8848 端口的原有 Nacos 和生产 Nacos 均未重启或重建。未执行 Nacos 初始化 SQL。
- 本次未重启或重建任何容器；运行中的 MySQL 仍保留原挂载，修正后的挂载将在未来经授权重建该容器时生效。本次业务补齐不依赖该挂载。
- 业务 API 验收留待应用发布。本次仅验证业务库初始化及应用数据库账号访问。

### Issue #2 验证记录（2026-09-21，服务器时间）

- 本地四服务已改为环境变量登录，公开示例清空凭据；Nacos 表结构与私有账号种子分离。没有同步覆盖服务器配置或轮换凭据。
- `python3 -m unittest discover -s deploy/prd/single/tests -v` 验证四服务占位符、Compose 必填约束、忽略规则、无账号种子的 schema 和 SQL 挂载路径。
- 服务器 Docker Compose 只解析传入的本地应用配置：填入测试登录变量时通过，变量缺失或为空时失败；没有创建或启动容器。
- 使用项目实际 Nacos SDK 2.2.1，通过临时 SSH 隧道直达生产 Nacos 容器 HTTP/gRPC 端口，每次使用独立空缓存。错误密码被 403 拒绝；正确密码登录成功、配置列表可见十项，但读取生产服务配置返回 404/空内容。四服务配置读取验收尚未通过，Issue #2 不能据此关闭。
- 已添加只读 `tests/NacosConfigProbe.java`，校验四服务实际声明的主配置、共享配置与服务发现查询；不注册实例、不发布配置。运行时由环境提供地址、namespace 和登录凭据，必须使用私有日志目录及全新 SDK 缓存，且不输出配置内容。负向鉴权测试用独立进程传入 `reject` 参数和错误密码。
- 四服务 Maven resources 阶段通过，输出 bootstrap 不含明文默认登录凭据。标准 reactor package 在公共模块 `lien-common-cache` 因 Boot repackage 找不到入口类失败，交由 Issue #4 处理；现有旧 JAR 尚未验证为安全制品，不能直接发布。
- 经用户授权重启生产 Nacos 容器（对外 8866）后复测通过：HTTP 配置读取返回 200，SDK 2.2.1 读到四服务主配置与共享配置并完成服务发现查询，错误密码仍被 403 拒绝。8848 端口的原有 Nacos、MySQL、Redis、RabbitMQ 容器启动时间与重启计数均未变化。

### 服务器 Nacos 客户端凭据补齐（2026-09-21）

- 按显式授权执行，只新增、不覆盖：`.env` 由 10 键增至 12 键，原有 10 个键逐个比对确认仍在。
- 服务器 `app/docker-compose-app.yml` 为四个服务注入 `NACOS_USERNAME` / `NACOS_PASSWORD`；改后 `docker compose config --quiet` 通过。仓库内同名文件已是同一形态。
- 有效性经实机验证：用该账号向生产 Nacos（宿主机 8866）登录成功，说明 `.env` 中的值确实被生产 Nacos 接受；验证过程未输出或记录凭据。
- 未创建、未启动、未重启任何容器；四个应用容器仍不存在，服务尚未做过首次启动。
- 尚未解决：服务器可用内存约 799 MiB，四个 JVM（各 `-Xmx256m`，未计非堆）装不下，开发环境中间件仍占用约 630 MiB——容量问题归 Issue #3。

### 直接写入 Nacos 配置的已知限制

生产 Nacos 的配置内容保存在 MySQL，但服务进程仅在启动时把 `config_info` 全量 dump 到容器内部缓存（`dump-all-ok`）。通过 SQL 直接插入或更新配置行后，运行中的 Nacos 会继续用旧缓存响应，表现为配置列表可见但读取 404 或返回旧值；容器重建会清空该缓存并重新 dump。

因此：直接用 SQL 初始化配置后，必须重启（或重建）Nacos 才能生效，重启前不要按“配置已导入”继续发布。更稳妥的做法是通过控制台或 Open API 发布配置，避免依赖重启；修改现有环境配置同样需要先确认影响范围。

`his_config_info` 中存在历史记录属于既有数据，不代表本次操作修改过配置；判断是否被改动应以配置行摘要和数据行数比对为准。

## 生产发布与回滚（GitHub Actions，issue #5）

发布编排在 `.github/workflows/release-prd.yml`，服务器侧逻辑在 `scripts/app_release.sh`
与只读验收脚本 `scripts/verify_deployment.sh`（两者都会随发布上传到服务器暂存目录执行，
不要求在服务器上放一份仓库；`scripts/check_service_health.py` 是其中的 HTTP 校验实现）。

### 固定事实与边界

| 项 | 值 |
|---|---|
| Compose 项目 | `frameworkjava-prd` |
| 服务器部署根目录 | 由 production Environment 的 `PRD_DEPLOY_ROOT` secret 给出，不写入仓库；脚本会校验生产标识 |
| env 文件 | `<部署根目录>/.env`（不在 `app/` 下） |
| 应用 Compose | `<部署根目录>/app/docker-compose-app.yml` |
| 应用服务范围 | `frameworkjava-{gateway,admin,file,portal}`（固定四个，不接受子集） |
| 容器内 Nacos | `frameworkjava-nacos:8848`（宿主机 `8866`，旧开发 Nacos 是 `8848`，两者不要混） |
| Web 端口 | `8666` |
| 登录用户 | 由 `PRD_SSH_USER` secret 给出（当前生产部署目录与容器都是 root 所有，非 root 用户无 docker 权限） |

脚本的硬性边界（改动发布脚本时不要放宽）：

- 绝不 `compose down`、绝不重建/重启中间件与 Nginx 容器、绝不删数据卷、绝不批量删除部署根目录；
- 每个构建目录 `app/service/<name>/` 必须恰好一份 JAR（`.jar.original`、第二份 JAR 都会让发布在改动前失败）；
- 发布包必须先通过结构校验（复用 `scripts/verify_service_artifacts.py`）与逐份 SHA-256 复核，才动应用制品；
- 每次发布用 `releases/lock` 互斥（工作流级 `concurrency` 之外的第二道保险），并用独立的
  `releases/staging/<release_id>/` 暂存。

### 服务器目录布局（脚本创建，已忽略入库）

```text
<部署根目录>/releases/
├── <release_id>/              每次成功发布保留的完整发布包（含 JAR、sha256、build-info.txt、manifest.txt）
├── state/current              当前生效的 release_id（成功后才写）
├── state/history.log          追加式操作记录（时间/动作/release/结果/操作人）
├── staging/<release_id>/      本次发布的隔离暂存：上传的包、previous/ 备份、logs/ 排障日志
└── lock/                      发布互斥锁（owner 文件记录 host/pid/release/时间）
```

成功发布后本次 `staging/<release_id>/` 整目录清理；失败时保留（含 `previous/` 与四服务日志）
供排障。`releases/<release_id>/` 是回滚目标，不要手工删除；`state/`、`lock/` 同理。
需要手工清理时只删具体某个 `staging/` 子目录，确认没有进行中的发布（`lock/owner` 为空）后再删。

### 一次性准备

1. **创建 production Environment**：仓库 Settings → Environments → New environment → `production`。
   建议启用 Required reviewers（发布前需人工批准）。该 Environment 承载全部发布凭据，不要放到仓库级。
2. **生成专用 SSH 密钥**（不要用开发机 `~/.ssh/config` 里的 `tx` 别名或个人私钥）：

   ```bash
   # 在受控管理机上
   ssh-keygen -t ed25519 -C "github-actions-prd-release" -f ./prd_release_ed25519 -N ''
   # 只把公钥装到服务器指定用户的 authorized_keys（追加，不覆盖既有内容）
   ssh tx "install -d -m 700 ~/.ssh && cat >> ~/.ssh/authorized_keys" < prd_release_ed25519.pub
   # 私钥只进 secret，用完删除本地副本
   gh secret set PRD_SSH_PRIVATE_KEY --env production --repo Jul1en-Lin/Framework-Java < prd_release_ed25519
   rm prd_release_ed25519
   ```

   若以后要把发布权限从 root 降到普通用户：需要同时把服务器上的部署目录（含 `app/service/*`）
   交给该用户、把用户加入 `docker` 组，并注意 `data/` 下 MySQL 数据目录的属主不要改动；
   这是一次需要单独授权的生产变更。

3. **准备完整可信的 known_hosts**：用可信渠道核对主机密钥指纹后再入库，不要在部署时 `ssh-keyscan`
   并直接信任结果（脚本与工作流都不会执行 keyscan）。

   ```bash
   ssh-keyscan -t ed25519 134.175.107.242 > prd_known_hosts
   ssh-keygen -lf prd_known_hosts          # 与腾讯云控制台/服务器本机 ssh-keygen -lf 的输出比对一致
   gh secret set PRD_SSH_KNOWN_HOSTS --env production --repo Jul1en-Lin/Framework-Java < prd_known_hosts
   ```

4. **配置 Environment 密钥**（仓库是公开的，服务器地址/用户/部署路径一律放 Secret，
   不要放 Variable——公开仓库的 Variables 任何人可读，会把服务器信息暴露出去）：

   | 类型 | 名称 | 说明 |
   |---|---|---|
   | Secret | `PRD_SSH_HOST` | 服务器地址（必填） |
   | Secret | `PRD_SSH_USER` | 登录用户（必填；当前生产为 `root`，因为部署目录与容器都是 root 所有） |
   | Secret | `PRD_DEPLOY_ROOT` | 服务器部署根目录（必填；具体值见 production Environment，不写入仓库） |
   | Secret | `PRD_SSH_PRIVATE_KEY` | 专用部署私钥（必填） |
   | Secret | `PRD_SSH_KNOWN_HOSTS` | 完整主机密钥条目（必填） |
   | Variable | `PRD_SSH_PORT` | 可选，默认 `22`（只有一个端口号） |

   ```bash
   gh secret set PRD_SSH_HOST --env production --repo Jul1en-Lin/Framework-Java --body "<服务器地址>"
   gh secret set PRD_SSH_USER --env production --repo Jul1en-Lin/Framework-Java --body root
   gh secret set PRD_DEPLOY_ROOT --env production --repo Jul1en-Lin/Framework-Java --body "<服务器上的部署根目录>"
   ```

   缺失任一必填项时工作流会在做任何操作前失败；工作流不打印任何凭据内容（可达性检查与 Job Summary
   都不输出地址与用户）。部署 job 的发布工具固定检出 build job 实际构建的那个 commit（`commit_sha`），
   不会把「构建时的分支」和「部署时已移动的分支」混用；因此请用分支保护限制谁能推送到被发布的 ref。

5. **确认 runner 到服务器的 SSH 可达性**：GitHub 托管 runner 的出口地址不固定，如果服务器防火墙
   只放行固定来源，需要放行 GitHub Actions 出口网段，或改用能直达服务器的自托管 runner。
   工作流里有专门的「确认 runner 到服务器的 SSH 可达性」步骤，重试三次仍失败就直接中止，不会上传任何东西。

6. **服务器前置条件**（预检会逐项检查，缺失即失败）：`docker`（当前用户可访问 daemon）、`python3`、
   `free`/`df`、四个 `app/service/<name>/` 目录与 Dockerfile、`.env` 含 `NACOS_USERNAME`/`NACOS_PASSWORD`/`WEB_PORT=8666`、
   `docker-compose-mid.yml` 把 Nacos 映射到 `8866`（生产标识，防止指错目录）。

### 发布流程

工作流是手动触发（`workflow_dispatch`），没有 push/tag 自动上线；同一时刻只允许一个发布
（`concurrency: prd-release`，`cancel-in-progress: false`——不会在关键阶段取消正在执行的发布）。

1. `build` job：检出指定 `git_ref` → Java 17 + Maven 构建四服务（复用 `scripts/build_release.sh`）
   → `scripts/verify_service_artifacts.py` 复核结构与凭据占位符 → 打包成 artifact。
2. `release` job（`production` Environment，先等人工审批）：校验环境配置 → 写入专用密钥与
   known_hosts（`StrictHostKeyChecking=yes`、`-F /dev/null`，不使用 runner 上的任何 SSH 配置）
   → 确认 SSH 可达 → 只上传发布工具 → 服务器只读预检 → 上传发布包 → 执行发布 → 写入 Job Summary。
3. **首次真实发布前建议先跑一次 `dry_run=yes`**：只做预检和打印计划，不替换 JAR、不重启容器
   （上传到暂存目录是无害的）。

触发参数：

| 参数 | 说明 |
|---|---|
| `action` | `deploy`（默认）/ `rollback` |
| `git_ref` | deploy 时构建的 ref，默认 `main` |
| `rollback_to` | rollback 的目标 `release_id`（见上次发布的 Job Summary） |
| `dry_run` | `no`（默认）/ `yes`，只适用于 deploy |
| `post_release_sample_seconds` | 发布后资源观察窗口秒数，默认 `60`；首次真实发布用 `600`（issue #3） |
| `confirm` | 必须精确输入 `frameworkjava-prd`，否则在工作流第一步就失败 |

服务器上执行的实际动作（`app_release.sh deploy`）：

```text
预检 → 校验发布包（结构 + SHA-256）→ 取锁 → 保留发布包到 releases/<id>/
→ 备份四个 service/<name>/*.jar 到 staging/<id>/previous/，替换为新制品
→ docker compose -p frameworkjava-prd -f docker-compose-app.yml up -d --build <四个应用服务>
→ docker exec <webprd 容器> nginx -t && nginx -s reload
→ 只读验收（容器/日志/Nacos 注册/实例端口/经 Nginx 后端 API）与资源阈值
→ 写 state/current 与 manifest，清理本次暂存目录，释放锁
```

### 验收标准（不是「compose up 成功」）

`verify_deployment.sh` 全部通过才算发布成功：

- 四个应用容器存在、`State.Running=true`、`RestartCount=0`；
- 每个服务日志里有 `Started ... in ... seconds`，且没有 `APPLICATION FAILED TO START` /
  `Error starting ApplicationContext` / `OutOfMemoryError` 等致命标记（出现即立刻判失败，不再等待）；
- 通过 Nacos Open API 登录生产 Nacos（宿主机 `8866`，命名空间 `frameworkjava-prd`，`DEFAULT_GROUP`），
  四个服务都有健康实例；
- 注册的 `ip:port` 真的能建立 HTTP 连接（根路径 401/403/404 都算存活）；
- 经 Nginx（`127.0.0.1:8666`）访问 `/admin/`、`/file/`、`/portal/` 不出现 502/503/504（这些状态说明网关不可达）；
- 资源阈值（issue #3）：四服务起来后 `available ≥ 300 MiB`，观察窗口内 swap 增长 ≤ 128 MiB，
  且任一个应用容器内存占用 ≤ 500 MiB（`docker stats` 的 RSS）。
  默认观察 60 秒（工作流参数 `post_release_sample_seconds`）；**首次发布按 #3 的要求用 10 分钟窗口复核**：

  ```bash
  # 在服务器上；$R 是 PRD_DEPLOY_ROOT，$S 是某次发布暂存目录里的 prd-scripts
  # （工作流发布时会把脚本上传到 <部署根目录>/releases/staging/<release_id>/prd-scripts/）
  bash "$S/verify_deployment.sh" --deploy-root "$R" --sample-seconds 600
  ```

只读验收也单独记录了不依赖前端的边界：这里校验的是网关链路的可达性，不校验尚未部署的前端首页。

### 回滚

每个成功发布都保留完整发布包（`releases/<release_id>/`），回滚就是「把某个已保留的包重新应用一遍」，
走同一套校验（含 `--verifier` 的制品结构校验）、重建与验收。`status` 会标出每个已保留版本是否
成功发布（`result=success`）；对失败发布的残留做回滚时脚本会先警告，需要人确认这是有意的目标。

- 工作流：`action=rollback`、`rollback_to=<release_id>`、`confirm=frameworkjava-prd`；
- 服务器上手工执行：

  ```bash
  cd <部署根目录>
  bash <某次发布暂存或仓库里的>/scripts/app_release.sh status --deploy-root "$PWD"
  bash .../app_release.sh rollback --deploy-root "$PWD" --to 12-1a2b3c4 --operator 你的名字
  ```

镜像 tag 是固定的 `1.0-SNAPSHOT`，所以回滚靠「恢复上一版 JAR + 重新构建」实现，可靠且可重复；
不需要也不允许停中间件、删卷或清镜像。

### 失败处置

| 场景 | 脚本行为 |
|---|---|
| 预检/发布包校验失败（改动之前） | 直接失败，应用制品与已保留版本都不动 |
| 替换 JAR 过程中失败 | 容器未重建、仍跑上一版；备份在 `staging/<id>/previous/`；提示用上一版 rollback 覆盖回来 |
| 后续发布已重建但验收失败 | **不自动回滚**（回滚也要重建镜像，属生产操作）；打印回滚命令并保留现场与日志，由授权人执行 |
| 首次发布已重建但验收失败 | 无回滚目标：先抓四服务日志，再 `compose stop` 这四个应用服务（不动中间件、不 down、不删卷）并保留现场 |

被中断的发布：`releases/lock/owner` 里记录了 host/pid/release/时间。确认没有发布进程后，
手工 `rm -rf <部署根目录>/releases/lock` 再重试；脚本不会自动清除他人的锁。

### 网关重建与 Nginx DNS

网关容器重建后 IP 变化，而 Nginx 只在加载配置时解析一次 `upstream lien-gateway` 的地址。
因此每次发布在 `compose up -d --build` 之后单独执行 `nginx -t` + `nginx -s reload`
（只 reload，不重启中间件）。如果验收里 `/admin/` 等返回 502/503/504，先确认 Nginx 是否 reload 成功，
再查网关容器与 Nacos 注册，不要用「重启整套中间件」来解决。

### 本地可重复验证

```bash
python3 deploy/prd/single/tests/test_release_workflow.py   # 工作流与脚本的安全边界（静态）
python3 deploy/prd/single/tests/test_app_release.py        # 发布/回滚端到端（docker 用桩替换）
python3 deploy/prd/single/tests/test_check_service_health.py  # Nacos/实例/Nginx 校验逻辑
python3 deploy/prd/single/tests/test_deployment_config.py  # 部署配置与忽略规则
python3 scripts/tests/test_verify_service_artifacts.py     # 制品结构校验
```

`test_app_release.py` 用假 Nacos + 假服务端口 + 假 Nginx + PATH 上的 `docker` 桩驱动真实脚本，
覆盖成功发布、校验和不符、服务目录多份 JAR、锁被占、首次/后续失败、回滚等场景，
并断言产生过的 docker 调用里绝不出现 `down`/`prune`/中间件 Compose。

### 尚未完成：首次真实发布（需生产授权）

到本次提交为止，**四个应用服务从未在生产启动过，首次真实发布还没有执行**。
发布链路已经构建并在本地用桩验证通过，但以下只能在获得生产操作授权后完成，故此条验收项保持未勾选：

- [ ] 服务器资源余量实测（发布前 `free -m` available ≥ 1400 MiB、磁盘 ≥ 3 GiB、swap 稳定）；
- [ ] 确认部署根目录路径与生产标识（`.env` 的 `WEB_PORT=8666`，`docker-compose-mid.yml` 映射 `8866:8848`）；
- [ ] 记录发布前状态（四个应用容器尚不存在，**首次发布没有回滚目标**，失败即按上表停服务保留现场）；
- [ ] 先跑一次 `dry_run=yes`，再执行真实发布；
- [ ] 首次发布后用 `--sample-seconds 600` 复核 issue #3 的验收阈值（available ≥ 300 MiB、swap 不再增长、无服务 RSS > 500 MiB）；
- [ ] 在一个已保留版本上演练一次回滚（授权后进行）；
- [ ] 把实测结果（时间、release_id、资源数字、异常与处置）补记到下面的记录里。

#### Issue #5 环境准备记录（2026-09-22）

- 已创建 GitHub Environment `production`，并设置部署分支策略：只允许从 `main` 部署
  （`workflow_dispatch` 选其它分支时会被 Environment 拒绝）。未启用 Required reviewers：
  单人仓库里唯一审批人就是触发者本人，会形成自我审批死锁；发布保护改由该分支策略 +
  手动触发 + `confirm` 短语共同承担。
- 已按 Secret 配置（仓库是公开的，Variables 会被任何人读到，因此服务器信息全部走 Secret）：
  `PRD_SSH_HOST`、`PRD_SSH_USER`、`PRD_DEPLOY_ROOT`、`PRD_SSH_PRIVATE_KEY`、`PRD_SSH_KNOWN_HOSTS`。
  值只存在于 GitHub，仓库与文档不记录。
- 部署用**专用** ed25519 密钥（指纹 `SHA256:eBbfa1FRp7T2eegXkqwDmzr4XPorxNJ8i1H3+ZXSfmY`）：
  公钥追加到服务器 `root` 的 `authorized_keys`（原有两把密钥未改动，文件由 2 行变 3 行），
  私钥只写入 Secret 且本地副本已删除；未使用、也未复制任何个人私钥。
  `PRD_SSH_USER` 为 `root`：当前生产部署目录与容器均为 root 所有，其它用户没有 docker 权限；
  降到普通用户需要单独授权的生产变更（见上文第 2 步的备注）。
- `PRD_SSH_KNOWN_HOSTS` 取自本机已信任的 known_hosts 条目（3 条：ED25519/RSA/ECDSA），
  写入前核对了指纹；部署时不执行 `ssh-keyscan`。
- 服务器只读预检已用真实环境验证通过（脚本与工作流同一条路径：上传工具到
  `releases/staging/<id>/prd-scripts/` → `app_release.sh preflight`）：资源余量
  available 1794 MiB、磁盘可用 20040 MiB、`docker compose config` 通过、生产标识与
  四个构建目录检查通过。验证后已删除该暂存目录，生产部署根目录恢复原状。
- 预检时确认四个 `app/service/<name>/` 目录**只有 Dockerfile、没有任何 JAR**，即首次发布前
  四服务从未在生产启动过。基础镜像 `eclipse-temurin:17-jdk` 本地不存在，首次 `--build`
  会从已配置的 registry mirror 拉取（会占用额外磁盘与时间）。
- **尚未执行任何真实发布**：首次发布仍需单独的生产操作授权，授权后按上面的清单先跑
  `dry_run=yes`，再执行真实发布并回填「Issue #5 执行记录」。

#### Issue #5 执行记录

（待首次真实发布后填写：日期、操作人、release_id、构建 commit、服务器可用内存/swap/磁盘、
验收输出摘要、遇到的问题与处置、回滚演练结果。）
