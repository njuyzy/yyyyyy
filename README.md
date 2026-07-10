# 研学导览（Japp）

一款面向研学出行场景的 Android 应用，连接普通用户与领队，提供 AI 路线规划、研学拼单、领队接单、地图导航和站内聊天等功能。

> 当前版本：`1.0`（开发中）
>
> Android 包名：`com.example.Japp`

## 主要功能

### 用户端

- 注册、密码登录、验证码登录与自动登录
- 选择用户/领队身份，维护头像、昵称、简介和研学偏好
- 通过对话描述城市、天数和主题，由 AI 生成研学路线
- 在高德地图中预览路线、途经点和步行方案
- 将生成的路线发布为拼单项目
- 浏览、筛选并加入研学团队
- 查看历史路线、会话列表并进行站内聊天

### 领队端

- 浏览和筛选待接研学项目
- 查看项目、团队及路线详情
- 接取项目并查看分段步行路线
- 管理个人资料、偏好和历史路线

## 技术栈

- Java 11
- Android SDK 36（最低支持 Android 8.0 / API 26）
- Android Gradle Plugin 9.0.0、Gradle 9.1.0
- AndroidX、Material Design、Navigation
- Retrofit 2 + Gson（网络请求）
- SQLite（本地会话与消息）
- 高德地图、搜索与定位 SDK

## 项目结构

```text
app/src/main/
├── java/com/example/Japp/
│   ├── Chat/       # 会话与聊天
│   ├── data/       # 本地数据模型
│   ├── database/   # SQLite 与 DAO
│   ├── leader/     # 领队端页面与路线功能
│   ├── network/    # Retrofit API、请求及响应模型
│   └── user/       # 用户端页面、拼单与 AI 路线规划
├── res/            # 布局、图片、主题、菜单等资源
└── AndroidManifest.xml
```

## 开发环境

开始前请准备：

- Android Studio（支持 AGP 9.0）
- JDK 17 或更高版本（用于运行 Gradle；源码兼容级别为 Java 11）
- Android SDK 36
- 可访问项目后端服务的网络环境
- 高德开放平台 Android Key

## 配置与运行

1. 克隆仓库并进入项目目录：

   ```bash
   git clone <repository-url>
   cd yyyyyy
   ```

2. 在项目根目录的 `local.properties` 中配置 Android SDK 和高德 Key：

   ```properties
   sdk.dir=/path/to/Android/Sdk
   AMAP_API_KEY=你的高德AndroidKey
   ```

   `AMAP_API_KEY` 也可以通过同名 Gradle 属性或环境变量提供。请勿将真实 Key 提交到仓库。

3. 使用 Android Studio 打开项目并等待 Gradle 同步完成，然后选择 Android 8.0 及以上的设备运行 `app`。

也可以通过命令行构建：

```bash
# macOS / Linux
./gradlew assembleDebug

# Windows
.\gradlew.bat assembleDebug
```

调试 APK 生成于 `app/build/outputs/apk/debug/app-debug.apk`。

## 后端接口

应用通过 Retrofit 连接后端，当前服务地址定义在：

```text
app/src/main/java/com/example/Japp/network/ApiClient.java
```

后端主要提供账户、地区、研学项目、AI 路线和聊天会话接口。若要切换开发或部署环境，请修改 `ApiClient` 中的 `BASE_URL`；地址必须以 `/` 结尾。

> 当前客户端允许明文 HTTP，仅适合开发/测试环境。正式发布时建议使用 HTTPS，并将不同环境的地址移入构建配置。

## 权限说明

应用会按功能申请以下权限：

- 网络与网络状态：访问后端和地图服务
- 精确/粗略定位：地图定位与步行路线规划
- 相机与图片：选择或拍摄头像
- Wi-Fi/设备状态：高德定位 SDK 所需

## 测试

```bash
# JVM 单元测试
.\gradlew.bat test

# 连接设备后的仪器测试
.\gradlew.bat connectedAndroidTest
```

## 相关项目

- Kotlin 版本：<https://github.com/hyp-nju-edu/app/>

## 当前状态

项目仍在持续开发中，部分页面和交互可能使用演示数据或尚未完整接入后端。提交问题时请附上 Android 版本、复现步骤和相关日志。

## 项目文档

- [用户端需求规格说明](./用户端需求规格说明.md)
- [用户与领队交互状态图](./用户与领队交互状态图.md)
