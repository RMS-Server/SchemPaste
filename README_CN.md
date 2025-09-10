# SchemPaste

[English](README.md) | **简体中文**

一个帮助您在服务器中粘贴 Litematica 原理图的 Minecraft Fabric 模组。

## 依赖

[![Fabric Loader](https://img.shields.io/badge/Fabric%20Loader-0.14.8+-brightgreen?style=flat-square&logo=fabricmc&logoColor=white)](https://fabricmc.net/)
[![Fabric API](https://img.shields.io/badge/Fabric%20API-Required-orange?style=flat-square&logo=fabricmc&logoColor=white)](https://github.com/FabricMC/fabric)
[![Syncmatica](https://img.shields.io/badge/Syncmatica-Required-blue?style=flat-square&logo=minecraft&logoColor=white)](https://github.com/End-Tech/syncmatica)

## 针对大型原理图的优化

- 经过在RMS中的实测，在RMS Intel i7-14600k CPU的配置上，粘贴一个方块数1000w，体积2亿的原理图，耗时9min46s（平均2w/s），全程tps稳定无掉刻

> [!Note] 
> 此效率仅代表该mod在RMS Server上的表现，仅做参考作用，不代表在您的服务器上的实际表现

## 命令详解

### 主命令

#### `/sp paste` - 粘贴命令

基本语法：

- `/sp paste stop` - 停止所有活跃的粘贴任务
- `/sp paste <索引>` - 根据索引粘贴原理图
- `/sp paste <索引> addition <参数>` - 使用额外参数粘贴原理图

**索引参数：**

- `<索引>` - 整数，从1开始的建筑索引号，对应 `placements.json` 中的建筑

**额外参数详解：**

**替换行为参数：**

- `replace none` - 不替换任何现有方块
- `replace all` - 替换所有方块（包括空气）
- `replace with_non_air` - 只在非空气方块位置放置（默认行为）

**图层控制参数：**

- `axis <x|y|z>` - 设置图层轴向（默认y轴）
- `mode <模式>` - 设置图层模式：
    - `all` - 粘贴所有图层（默认）
    - `single` 或 `single_layer` - 只粘贴单一图层
    - `all_above` - 粘贴指定坐标以上的所有图层
    - `all_below` - 粘贴指定坐标以下的所有图层
    - `range` 或 `layer_range` - 粘贴指定范围内的图层

**图层坐标参数：**

- `single <坐标>` - 单图层模式的坐标值
- `above <坐标>` - "以上"模式的起始坐标
- `below <坐标>` - "以下"模式的结束坐标
- `rangemin <坐标>` - 范围模式的最小坐标
- `rangemax <坐标>` - 范围模式的最大坐标

**使用示例：**

```
/sp paste 1                                           # 粘贴第1个建筑
/sp paste 2 addition replace none                     # 粘贴第2个建筑，不替换现有方块
/sp paste 3 addition rate 100                        # 粘贴第3个建筑，每tick处理100个方块
/sp paste 4 addition replace all rate 50             # 粘贴第4个建筑，替换所有方块，每tick处理50个方块
/sp paste 5 addition axis y mode single single 64    # 粘贴第5个建筑，只粘贴Y=64层
/sp paste 6 addition mode all_above above 100        # 粘贴第6个建筑，只粘贴Y≥100的部分
/sp paste 7 addition mode range rangemin 60 rangemax 80  # 粘贴第7个建筑，只粘贴Y=60-80范围
/sp paste 8 addition axis x mode single single 150 replace none  # 粘贴第8个建筑，X=150切面，不替换
/sp paste stop                                        # 停止所有粘贴任务
```

#### `/sp list` - 列表命令

- 显示所有可用的投影列表

### 权限要求

所有命令都需要OP权限

## 配置文件

### 主配置文件 (`config/schempaste.json`)

**性能相关配置：**

```json
{
    "backgroundThreads": 2,
    // 后台处理线程数
    "mainThreadBudgetMs": 40,
    // 主线程每tick预算时间(毫秒)
    "enableProgressMessages": true,
    // 启用进度消息
    "progressUpdateIntervalMs": 2000
    // 进度更新间隔(毫秒)
}
```

**默认行为配置：**

```json
{
    "defaultReplace": "with_non_air",
    // 默认替换行为
    "suppressNeighborUpdates": true,
    // 抑制邻近方块更新
    "fixChestMirror": true,
    // 修复箱子镜像问题
    "clearInventories": false
    // 清空容器物品
}
```

**图层控制配置：**

```json
{
    "defaultLayerAxis": "y",
    // 图层轴向 (x/y/z)
    "defaultLayerMode": "all",
    // 图层模式
    "defaultLayerSingle": 0,
    // 单图层坐标
    "defaultLayerRangeMin": 0,
    // 范围模式最小坐标
    "defaultLayerRangeMax": 0
    // 范围模式最大坐标
}
```

**区块管理配置：**

```json
{
    "enableDynamicChunkLoading": true,
    // 启用动态区块加载
    "maxLoadedChunks": 32
    // 最大加载区块数
}
```

## 支持的版本

- 1.17.1
- 1.20.1

## 安装方法

1. 安装 Fabric Loader
2. 下载模组 jar 文件
3. 将文件放入 `mods` 文件夹

## TODO

- 脱离对于Syncmatica 的依赖，可以直接从客户端获取.litematica 文件

## 许可证

CC0-1.0
