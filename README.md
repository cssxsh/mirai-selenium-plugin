# Mirai Selenium 前置插件

> 基于 [MxLib](https://github.com/Karlatemp/MxLib) 的 Mirai Selenium 前置插件

Mirai-Console的前置插件，用于使用Selenium调用浏览器进行截图等

![maven-central](https://img.shields.io/maven-central/v/xyz.cssxsh.mirai/mirai-selenium-plugin)

## 在插件项目中引用

```
repositories {
    mavenCentral()
}

dependencies {
    compileOnly("xyz.cssxsh.mirai:mirai-selenium-plugin:${version}")
}
```

## 使用本前置插件的项目

* [BiliBili Helper](https://github.com/cssxsh/bilibili-helper)

## 安装

### MCL 指令安装

`./mcl --update-package xyz.cssxsh.mirai:mirai-selenium-plugin --channel stable --type plugin`

### 手动安装

1. 运行 [Mirai Console](https://github.com/mamoe/mirai-console) 生成`plugins`文件夹
1. 从 [Releases](https://github.com/cssxsh/mirai-selenium-plugin/releases) 下载`jar`并将其放入`plugins`文件夹中