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

以下文件是私有运行输入，不纳入 Git：`.env` 及其备份、`data/`、`backups/`、`res/sql/*.sql`。SQL 导出包含数据库/Redis/RabbitMQ 凭据、OSS/地图密钥、账号密码哈希及用户数据；它们不是公开示例。`vm1/`、`vm2/` 是未审核的未来双机方案，已在上级忽略规则中排除，不属于本次可提交范围。不要使用 `git add -f` 绕过忽略规则。

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
