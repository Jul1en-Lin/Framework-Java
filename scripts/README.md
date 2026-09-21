# scripts/：制品构建与校验

服务于 issue #4（建立四服务可执行制品构建与校验）。这里只做「构建 + 组织 + 校验」，
不涉及部署、不接触服务器；生产发布编排属 issue #5。

## 1. 打包前置条件

`spring-boot-maven-plugin` 只允许出现在两个地方：

* 根 `pom.xml` 的 `<pluginManagement>`（统一 lombok exclude 等配置）；
* **可执行服务模块**自己的 `<build><plugins>`，且**只写 `<plugin>`，不写 `<executions>`**。

父 pom 用 `<build><plugins>` 声明会让所有子模块（库模块、聚合 pom）都绑定 `repackage`，
没有主类的模块会直接构建失败；模块自己再写一份 `<executions>` 则会让 `repackage`
跑两遍（spring-boot-starter-parent 的 pluginManagement 里已经带了 `repackage` execution）。
库模块不需要任何 `<skip>true</skip>` 之类的开关。

四个可执行服务：`lien-gateway`、`lien-admin/lien-admin-service`、`lien-file/lien-file-service`、
`lien-portal/lien-portal-service`。

## 2. 构建发布包

```bash
scripts/build_release.sh                 # 默认 -o 之外用在线依赖，-DskipTests 打包
scripts/build_release.sh --maven-args "-o"   # 离线构建
scripts/build_release.sh --with-tests    # 不传 -DskipTests（需真实 MySQL/Redis/RabbitMQ）
scripts/build_release.sh --skip-build    # 只暂存现有 target/ 制品，不跑 maven
scripts/build_release.sh --service admin # 只处理单个服务（可重复）
```

产出（`release/` 已被 `.gitignore` 忽略）：

```
release/
├── build-info.txt                          # 时间/版本/命令/每个服务的源路径与 sha256
├── gateway/lien-gateway.jar(+.sha256)
├── admin/lien-admin-service.jar(+.sha256)
├── file/lien-file-service.jar(+.sha256)
└── portal/lien-portal-service.jar(+.sha256)
```

命名约定：发布包内一律 `<artifactId>.jar`，把 `lien-gateway`（设了 `finalName`，无版本号）
与其余模块（带 `-0.0.1-SNAPSHOT`）的差异收敛在这一处；版本信息记录在 `build-info.txt`。
这样 `deploy/prd/single/app/service/<name>/Dockerfile` 的构建上下文
（`COPY ./*.jar /workspace/app.jar`）可以每服务只放一份 JAR 直接用。

暂存后脚本会自动执行校验；校验失败则以非零退出码结束。

## 3. 校验制品

```bash
scripts/verify_service_artifacts.py --release-dir release
scripts/verify_service_artifacts.py --release-dir release --service admin
```

只依赖 Python 3 标准库（zipfile / hashlib），可离线重复执行。每个服务必须满足：

* 服务目录存在，且**恰好一份** `<artifactId>.jar` + 一份与之匹配的 `.sha256`；
  目录里出现 `.jar.original`、第二个 JAR 等非发布制品即失败（非零退出码）；
* JAR 是合法 zip 且未损坏；
* `META-INF/MANIFEST.MF` 的 `Main-Class` 是 Spring Boot launcher、
  `Start-Class` 非空且对应 class 存在于 `BOOT-INF/classes/`；
* `BOOT-INF/classes/` 与 `BOOT-INF/lib/` 结构齐全，`lib/` 下至少一个依赖 JAR；
* `BOOT-INF/classes/bootstrap.yml` 存在、`spring.application.name` 与目录一致
  （防止制品串位）、且 Nacos 凭据是 `${NACOS_USERNAME}` / `${NACOS_PASSWORD}` 占位符，
  出现明文凭据即失败。

四类场景（正常 / 缺失 / 多份候选 / 结构无效）都有用例：

```bash
python3 scripts/tests/test_verify_service_artifacts.py -v   # 标准库 unittest，17 个用例
```

也支持先构建再校验的一步到位方式（见上一节）。CI 里建议至少调用
`verify_service_artifacts.py`，是否接入 workflow 尚未决定（仓库当前没有 `.github/`）。
