# [Mirai Selenium Plugin](https://github.com/cssxsh/mirai-selenium-plugin)

> Mirai Selenium 前置插件

Mirai-Console的前置插件，用于使用Selenium调用浏览器进行截图等

[![maven-central](https://img.shields.io/maven-central/v/xyz.cssxsh.mirai/mirai-selenium-plugin)](https://search.maven.org/artifact/xyz.cssxsh.mirai/mirai-selenium-plugin)

## 运行平台支持

|   OS    | Browser | Support |
|:-------:|:-------:|:-------:|
| Windows | Chrome  |   Yes   |
| Windows | Firefox |   Yes   |
| Windows |  Edge   |   Yes   |
|  Linux  | Chrome  |   Yes   |
|  Linux  | Firefox |   Yes   |
|  MacOS  | Chrome  |  Test   |
|  MacOS  | Firefox |  Test   |

## 在插件项目中引用

```
repositories {
    mavenCentral()
}

dependencies {
    compileOnly("xyz.cssxsh.mirai:mirai-selenium-plugin:${version}")
}
```

### 示例代码

* [kotlin](src/test/kotlin/xyz/cssxsh/mirai/plugin/MiraiSeleniumPluginTest.kt)

## 使用本前置插件的项目

* [BiliBili Helper](https://github.com/cssxsh/bilibili-helper)

## 管理指令

* `/selenium setup` 安装驱动文件
* `/selenium clear` 清理驱动文件
* `/selenium destroy` 清理驱动进程
* `/selenium status` 驱动进程状态

## 配置

### [MiraiSeleniumConfig.yml](src/main/kotlin/xyz/cssxsh/mirai/plugin/data/MiraiSeleniumConfig.kt)

* expires 驱动文件过期时间，默认一星期 (单位：天)
* user_agent 截图UA
* width 截图宽度
* height 截图高度
* pixel_ratio 截图像素比
* headless 无头模式（后台模式）
* proxy 代理地址
* log 启用日志文件
* browser 指定使用的浏览器: `Chrome`,`Chromium`,`Firefox`,`Edge`
* factory 指定使用的Factory: `ktor`,`netty`
* arguments 自定义 arguments, 可以尝试加入 `--no-sandbox`, `--disable-dev-shm-usage`, 解决兼容性问题  

## 安装

### MCL 指令安装

`./mcl --update-package xyz.cssxsh.mirai:mirai-selenium-plugin --channel stable --type plugin`

### 手动安装

1. 运行 [Mirai Console](https://github.com/mamoe/mirai-console) 生成`plugins`文件夹
1. 从 [Releases](https://github.com/cssxsh/mirai-selenium-plugin/releases) 下载`jar`并将其放入`plugins`文件夹中