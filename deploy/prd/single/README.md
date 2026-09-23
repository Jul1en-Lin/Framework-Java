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
| 发布包中转 | 阿里云 OSS 私有桶（`oss-cn-guangzhou`），凭据只放服务器 `.env` 的 `OSS_*` 四个键 |

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

3. **准备阿里云 OSS 中转桶**（只做一次；凭据不进 GitHub，只进服务器 `.env`）：

   - 建一个**国内区域**的私有 Bucket（当前用`oss-cn-guangzhou`，与服务器同地域最近）
   - 加一条生命周期规则：前缀 `github-release/`，文件过期 7 天后删除（自动清理历史发布包）
   - 建一个 RAM 用户（只开编程访问），权限策略限定到该桶的该前缀：

     ```json
     {
       "Version": "1",
       "Statement": [{
         "Effect": "Allow",
         "Action": ["oss:PutObject", "oss:GetObject", "oss:InitiateMultipartUpload",
                    "oss:UploadPart", "oss:CompleteMultipartUpload",
                    "oss:AbortMultipartUpload", "oss:ListParts"],
         "Resource": ["acs:oss:*:*:<bucket>/github-release/*"]
       }]
     }
     ```

   - 把 4 个键追加到服务器 `.env`（只追加、不覆盖，权限收紧为 600）：
     `OSS_BUCKET`、`OSS_ENDPOINT`、`OSS_ACCESS_KEY_ID`、`OSS_ACCESS_KEY_SECRET`。
     该用户没有删除权限；泄漏的影响面仅限这个前缀的读写。

3. **在云控制台放通入站端口**（只做一次，且不在本仓库的管理范围内）：

   - 必须放通 **TCP 8666**（生产 Web 入口，`frameworkjava-webprd` 映射 `0.0.0.0:8666->80`），
     否则只有服务器本机能访问，公网一律静默超时；
   - 建议把 **TCP 8866（Nacos）** 限制为管理 IP：它默认对全网开放，
     Nacos 虽有客户端鉴权，但不该暴露在公网；
   - 判断方法：服务器网卡上只有内网地址（公网 IP 是 NAT 映射，`ip route get <公网IP>` 显示
     `via <网关>`），所以连「服务器访问自己的公网 IP」也要过云网关和这层规则。
     若 `curl http://127.0.0.1:8666/admin/` 正常（401）而 `curl http://<公网IP>:8666/admin/`
     超时，同时 8866 从公网可达，问题就在这一层，不在服务器防火墙（`ufw inactive`、
     `iptables -S INPUT` 为 `ACCEPT`）。

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
   → 确认 SSH 可达 → 只上传发布工具（几十 KB）→ 服务器只读预检 → 发布包分片并发上传到 OSS
   → 服务器从 OSS 取回并核对 SHA-256 → 执行发布 → 写入 Job Summary。
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

服务器上执行的实际动作（`app_release.sh fetch-package` + `deploy`）：

```text
预检 → 从 OSS 取回分片 → 拼包并核对 SHA-256 → 解包 → 校验发布包（结构 + 凭据占位符）
→ 取锁 → 保留发布包到 releases/<id>/
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

**这些检查全部在服务器本机执行**（`127.0.0.1:8666`），所以它们通过**不代表公网能访问**——
公网入口还取决于云安全组（见「一次性准备」第 3 条）。外部可达性需要从另一台机器验证，例如：

```bash
curl -s -o /dev/null -w '%{http_code}\n' http://<公网IP>:8666/admin/   # 401 即为通（鉴权响应）
curl -s -o /dev/null -w '%{http_code}\n' -X POST \
  http://<公网IP>:8666/admin/sys_user/login/password \
  -H 'Content-Type: application/json' -d '{"phone":"...","password":"..."}'   # 200 + accessToken
```

2026-09-23 首次发布后就是这样发现 8666 未放通的：服务器本机 `POST /admin/sys_user/login/password`
返回 200 且带 token，但公网访问超时（外部观测点 TCP 22/8866 可连、8666 超时）。

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

**验证状态（交接必读）**：发布路径已在生产真实跑通（release `6-180a84c`），但**回滚路径只有本地
端到端用例覆盖**——仓库负责人 2026-09-23 决定不做回滚演练（见上文发布记录清单）。
因此首次真实回滚属于未验证操作：建议先在 `dry_run` 之外的窗口执行，并准备好「回滚也失败」时的
人工兜底（把某个 `releases/<release_id>/` 里的 JAR 手工放回 `app/service/<name>/` 再
`compose up -d --build`）。

### 失败处置

| 场景 | 脚本行为 |
|---|---|
| 预检/发布包校验失败（改动之前） | 直接失败，应用制品与已保留版本都不动 |
| 替换 JAR 过程中失败 | 容器未重建、仍跑上一版；备份在 `staging/<id>/previous/`；提示用上一版 rollback 覆盖回来 |
| 后续发布已重建但验收失败 | **不自动回滚**（回滚也要重建镜像，属生产操作）；打印回滚命令并保留现场与日志，由授权人执行 |
| 首次发布已重建但验收失败 | 无回滚目标：先抓四服务日志，再 `compose stop` 这四个应用服务（不动中间件、不 down、不删卷）并保留现场 |

被中断的发布：`releases/lock/owner` 里记录了 host/pid/release/时间。确认没有发布进程后，
手工 `rm -rf <部署根目录>/releases/lock` 再重试；脚本不会自动清除他人的锁。

### 网关首次上线与 Nginx

Nginx 只在加载配置时解析一次 `upstream lien-gateway` 的地址，两种情况都必须单独处理：

1. **网关首次上线前**：`frameworkjava-webprd` 会一直重启失败
   （`[emerg] host not found in upstream "frameworkjava-gateway:18080"`）。实测该容器自创建后
   已按 `restart: always` 重启了 2700 多次。发布脚本因此用 `docker ps -a` 而不是 `docker ps`
   查找它：先等它自动恢复（默认最多 120 秒，`NGINX_WAIT_SECONDS` 可调），仍未运行就单独
   `docker start` 这一个容器，然后 `nginx -t` + `nginx -s reload`；整个过程不触碰中间件、
   不重建其它容器。
2. **网关重建后 IP 变化**：`compose up -d --build` 之后执行 `nginx -t` + `nginx -s reload`
   让新 IP 生效（只 reload，不重启中间件）。

如果验收里 `/admin/` 等返回 502/503/504，先确认 Nginx 是否真的在运行并 reload 成功，
再查网关容器与 Nacos 注册，不要用「重启整套中间件」来解决。

### 本地可重复验证

```bash
python3 deploy/prd/single/tests/test_release_workflow.py   # 工作流与脚本的安全边界（静态）
python3 deploy/prd/single/tests/test_app_release.py        # 发布/回滚端到端（docker 用桩替换）
python3 deploy/prd/single/tests/test_check_service_health.py  # Nacos/实例/Nginx 校验逻辑
python3 deploy/prd/single/tests/test_oss_presign.py        # OSS 预签名（签名向量对齐官方 oss2 SDK）
python3 deploy/prd/single/tests/test_deployment_config.py  # 部署配置与忽略规则
python3 scripts/tests/test_verify_service_artifacts.py     # 制品结构校验
```

`test_app_release.py` 用假 Nacos + 假服务端口 + 假 Nginx + PATH 上的 `docker` 桩驱动真实脚本，
覆盖成功发布、校验和不符、服务目录多份 JAR、锁被占、首次/后续失败、回滚等场景，
并断言产生过的 docker 调用里绝不出现 `down`/`prune`/中间件 Compose。

### 发布包传输：经国内 OSS 中转（不能用直传）

这台服务器的国际线路带宽极低，2026-09-22 用与工作流相同的路径实测（一次性探针，测完已删）：

| 链路 | 实测吞吐 |
|---|---|
| GitHub runner → 本服务器（单流 / 8 并发 / 16 并发） | 14 KB/s / 69 KB/s / — |
| 本服务器 → GitHub | 10–18 KB/s |
| runner → 阿里云 OSS（单流 / 8 并发 / 16 并发 PUT） | 74 KB/s / 612 KB/s / **1.33 MB/s** |
| 本服务器 ← 阿里云 OSS（GET） | **13.8 MB/s** |
| 本服务器 → 阿里云 OSS（PUT） | 0.36 MB/s（这台实例上行约 3 Mbps，所以只能由 runner 上传） |

按单流算，262 MB 直传要约 3 小时（远超 30 分钟超时）；走国内 OSS 则约 3.3 分钟上传 + 20 秒下载。
因此发布包改为：**runner 分片并发上传到国内 OSS → 服务器从国内地址取回并逐级校验**。
发布包仍是 runner 上构建、`verify_service_artifacts.py` 校验过的那一份，服务器侧部署、验收、回滚逻辑不变。

对象布局与流程：

```text
github-release/<release_id>/package.tar.gz.part-000 ... part-NNN   每个 16 MB
github-release/<release_id>/package.tar.gz.sha256 由 runner 提供（整体 SHA-256）
```

1. runner 把发布包切成 16 MB 分片（`split -d -a 3`），算出整体 SHA-256；
2. runner 把对象键通过 SSH 发给服务器，服务器用 `scripts/oss_presign.py` 生成**预签名 PUT URL**
   （OSS 凭据只在服务器 `.env`，GitHub 侧零云凭据），URL 在日志里加掩码；
3. runner 以 16 并发 PUT，每片独立重试，任一片失败即整体失败；上传吞吐与总量打印在日志里；
4. 服务器 `app_release.sh fetch-package` 用预签名 GET 取回各分片 → 拼成 tar → 核对整体 SHA-256
   → 解包 → 再跑一遍 `verify_service_artifacts.py`（结构与凭据占位符），全部通过才进入替换 JAR 阶段。

分片与 tar 在解包成功后即删除；OSS 上的对象由桶的**生命周期规则 7 天后自动过期**，
发布侧不需要也不具备删除权限。若以后想更快，可以在控制台给桶开「传输加速」并把 `.env` 的
`OSS_ENDPOINT` 改成 `oss-accelerate.aliyuncs.com`（`oss_presign.py` 已支持）。

### 首次真实发布记录（2026-09-23，release `6-180a84c`）

首次真实发布已执行并**成功**（工作流 run 35804137264：`action=deploy`、`dry_run=no`、
`post_release_sample_seconds=600`），四个应用服务自此在生产运行。

- 发布前实测：`available 826 MiB + 四应用可回收 1264 MiB = 预计 2090 MiB`（下限 1400）、
  磁盘可用 18128 MiB、swap 已用 740 MiB；
- 传输：221.9 MB 分 14 片，上传 143 s、服务器取回 + 校验 + 解包 34 s（与 dry-run 实测一致）；
- 制品替换前后的 SHA-256 记在 `releases/6-180a84c/manifest.txt`（四份 before/after 齐全）；
- 发布前四个 `app/service/<name>/` 只有 Dockerfile，`previous_release=none`，即**首次发布没有回滚目标**；
- 验收：就绪校验第 1、2 次因 JVM 仍在启动而失败、第 3 次通过（`READY_TIMEOUT` 重试逻辑生效）；
  四容器 `RestartCount=0`、四个服务在 Nacos 注册健康、经 Nginx 的 `/admin/ /file/ /portal/`
  均为 401 鉴权响应（非 502）；发布内建 60 s 观察：available 1337 MiB、swap 增长 0、
  单服务 RSS 252–382 MiB；
- 发布后单独跑只读验收做 10 分钟窗口复核（`--sample-seconds 600`）：available 1112 MiB、
  swap 增长 0 MiB、单服务 RSS 259–388 MiB，全部满足 issue #3 阈值；
- 网关首次上线后 `frameworkjava-webprd` 自行恢复运行（此前自 9-20 起重启 3248 次），
  脚本的等待逻辑与实际行为一致；中间件（Nacos/MySQL/Redis/RabbitMQ）与数据卷全程未动；
- `releases/state/current` = `6-180a84c`；`history.log` 记录了本次成功以及三次失败尝试
  （含阶段与原因），可供事后追溯。

首次真实发布过程中暴露并修掉的三个缺陷（均有对应用例，见 commit）：

1. **重启中的容器不能看 `State.Running`**：重启循环里 `Running` 仍是 `true`、只有
   `Status=restarting`，于是脚本以为 webprd 可用就直接 `docker exec`，报
   `Container ... is restarting, wait until the container is running`。测试桩已改为忠实模拟该语义
   （旧代码在桩上能复现与生产一字不差的报错）。兜底启动同时改为 `docker stop -t 10` + `docker start`：
   重启循环里的 `docker start` 无效，stop+start 才会立刻重置退避。
2. **预检没把「本次会重建的四应用占用」算作可回收**：四服务已在运行时 available 只剩 660 MiB，
   低于 1400 MiB 下限，等于「生产一跑起应用就再也发不出去」。现在比较 `available + 四应用 RSS`
   与下限，两个数都打印在日志里。
3. **观察窗口秒数没传到服务器**：`ssh` 不携带 runner 的环境变量，`SAMPLE_SECONDS` 在服务器上
   回落到默认 60 s，所以发布内建只观察了 60 s（10 分钟窗口是发布后单独复核的）。
   现在远端命令前显式传 `SAMPLE_SECONDS`。**同理，脚本的其它环境变量阈值
   （`MIN_AVAIL_MEM_MB`/`READY_TIMEOUT`/`NGINX_WAIT_SECONDS` 等）也只能在服务器上设置，
   从工作流触发时一律用默认值。**

另外：预检阶段失败的 run 会在 `releases/staging/` 留下只含发布工具的缓存目录，现已加收尾清理
（发布/回滚一旦开始过则保留为失败现场）。因此 `releases/staging/` 里目前还留着前几次失败尝试的
目录（`4-a34b422` 约 222 MB、`5-126fe83`），确认无用后可自行删除。

首次发布清单：

- [x] 服务器资源余量实测（发布前 available ≥ 1400 MiB、磁盘 ≥ 3 GiB、swap 稳定）；
- [x] 确认部署根目录路径与生产标识（`.env` 的 `WEB_PORT=8666`，`docker-compose-mid.yml` 映射 `8866:8848`）；
- [x] 记录发布前状态（四个应用容器尚不存在，首次发布没有回滚目标）；
- [x] 先跑 `dry_run=yes`，再执行真实发布；
- [x] 首次发布后用 `--sample-seconds 600` 复核 issue #3 的验收阈值；
- [x] ~~在一个已保留版本上演练一次回滚~~ —— 仓库负责人于 2026-09-23 明确决定**不做**回滚演练，
  该验收项按豁免处理。**因此回滚路径至今只在本地端到端用例（假 docker 桩）里验证过，
  未经真实生产验证**；首次回滚时请当作未验证路径对待，先看 `status`、确认目标版本，
  并预留失败处置（见上文「失败处置」）。
- [x] 把实测结果（时间、release_id、资源数字、异常与处置）补记到本节。

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
- **尚未执行任何真实发布**：首次发布仍需单独的生产操作授权。
- 2026-09-22 在真实 GitHub runner 上完成 `dry_run=yes` 全链路验证（run 35761718303）：
  构建 + 制品校验 → SSH 可达性 → 上传发布工具 → 服务器预检 → 分片并发上传 OSS
  → 服务器取回并核对 SHA-256 → 解包后再验制品 → `deploy --dry-run` 打印计划。
  实测：221.9 MB 分 14 片，上传 143 秒（约 1.55 MB/s，单片最高 3.8 MB/s），
  服务器取回 + 校验 + 解包 34 秒，全程未改动生产（服务目录仍只有 Dockerfile、
  中间件容器未动、无 `releases/<id>`、未取锁），dry-run 的暂存目录事后已清理。
  过程中发现并修掉两个真实缺陷：分片命名位数不一致（服务器找 `part-00`、runner 传 `part-000`）、
  上传校验只数日志行数（`curl --fail` 失败时仍输出 `-w`）——见 commit 与对应静态用例。
- 同一趟验证发现问题并已修复：`frameworkjava-webprd` 自 2026-09-20 创建起因 upstream
  解析不到网关重启了 2740 次（`RestartCount=2740`）；原 `reload_nginx` 用 `docker ps` 查找容器
  会找不到而直接跳过。现改为 `docker ps -a` + 等待（默认 120 s）+ 必要时单独 `docker start`
  该容器，再 `nginx -t` / `nginx -s reload`，并有对应用例覆盖。
- 2026-09-22 打通 OSS 中转并在服务器实测（roundtrip 64 MB）：
  PUT 返回 200（361 KB/s，受本机 3 Mbps 上行限制）、GET 返回 200（13.8 MB/s）、两侧 SHA-256 一致。
  服务器 `.env` 追加了 4 个 `OSS_*` 键（只追加、备份为 `.env.bak-oss-20260923`），
  并把 `.env` 与备份的权限从 644 收紧为 600（原先全局可读）。
  桶为国内私有桶，生命周期规则 7 天过期；RAM 用户权限限定在该桶 `github-release/*` 前缀，
  没有删除权限。OSS 凭据只在服务器，GitHub 侧没有任何云凭据。
- runner → OSS 实测（临时探针，已从 main 删除）：单流 74 KB/s、8 并发 612 KB/s、16 并发 1.33 MB/s，
  所以工作流用 16 MB 分片 + 16 并发，262 MB 约 3.3 分钟；服务器取回约 20 秒。

#### Issue #5 执行记录

（待首次真实发布后填写：日期、操作人、release_id、构建 commit、服务器可用内存/swap/磁盘、
验收输出摘要、遇到的问题与处置、回滚演练结果。）
