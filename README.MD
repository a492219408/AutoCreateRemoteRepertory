# 🛠️ 项目构建指南（基于 GraalVM Native Image）

本项目使用 [GraalVM Native Image](https://www.graalvm.org/) 技术，将 Java 程序构建为无需 JVM 的本地可执行文件。

---

## 📦 环境要求

| 工具 | 版本要求                | 下载地址 |
|------|---------------------|----------|
| JDK  | **GraalVM CE ≥ 24** | [GraalVM Downloads](https://www.graalvm.org/downloads/) |
| Maven | 推荐 3.9.9           | https://maven.apache.org |

---

## 📦 构建前准备

请参考官方文档安装并配置好 GraalVM：

🔗 [GraalVM for Windows – Getting Started](https://www.graalvm.org/latest/getting-started/windows/)

---

## 🚀 构建可执行文件

在项目根目录运行以下命令：

```bash
mvn clean package -Pnative
```

构建成功后，将在 `target/` 目录下生成可执行文件，例如：

```text
target/AutoCreateRemoteRepertory.exe
```

---

## ⚠️ 元数据收集说明（反射 / 动态代理 / JNI / 资源文件）

如果你在代码中使用了以下机制：

- Java 反射（如 `Class.forName`、`Method.invoke`）
- 动态代理（如 JNA、`Proxy.newProxyInstance`）
- JNI（调用本地 C/C++ 库）
- `getResourceAsStream(...)` 加载类路径资源

必须先通过 agent 收集元数据，再重新构建 Native Image，否则运行构建好的原生可执行程序时会遇到找不到某些文件。

---

## 🧪 元数据收集流程（推荐）

请参考官方文档：  
🔗 [GraalVM Automatic Metadata Collection](https://www.graalvm.org/latest/reference-manual/native-image/metadata/AutomaticMetadataCollection/)

### 执行带 agent 的 Java 程序

```bash
java ^
  -agentlib:native-image-agent=config-output-dir=src/main/resources/META-INF/native-image ^
  -cp target/classes;...（含所有依赖） ^
  com.ymdelf.Main
```

如果使用 IntelliJ IDEA：

> 在 IntelliJ IDEA 的“Run/Debug Configurations”中，找到 Main 类的运行配置，在 `VM options` 中添加：

```bash
-agentlib:native-image-agent=config-output-dir=src/main/resources/META-INF/native-image
```

生成的 json 文件应存放在以下目录结构中

```
src/
 └── main/
     └── resources/
         └── META-INF/
             └── native-image/
                 └── reachability-metadata.json
```

---

### 构建 Native Executable

```bash
mvn clean package -Pnative
```

> GraalVM 会自动加载 `META-INF/native-image/*.json` 元数据并完成打包。

