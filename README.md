# PICO 天气 1.5

PICO Spatial 原生 Plain / Planar 窗口应用，仅支持国内城市。使用正式高德天气服务，打开即加载真实天气。

## 功能

- 首页根据当前实况天气选择15组原创粉彩黏土景观背景，复用3D模型分类规则覆盖69个天气标签。晴天暖色草地、多云棉花云、雨天涟漪、雷雨金色闪电、雪天冰蓝雪丘、雾天薄雾、沙尘杏色沙丘与风天飘叶等相互呼应；城市管理页使用中性背景。
- 背景图内置WebP资源，无需联网下载；后台解码、最多缓存3张、450ms淡入切换。左右拖动时跟随当前主要可见城市的真实缓存天气；没有实况时使用中性景观。文字区与底部信息区叠加柔和浅色遮罩保持可读性。

- 实况温度、湿度、风向、风力；短期预报卡片直接显示昼夜天气、温度及风，无点击或详情弹窗。
- 右上角真实卡通 GLB 模型：圆润粉彩、眼睛和腮红，覆盖高德天气类别及过渡天气，统一风格。SpatialView 使用440dp前方深度；模型中心位于面板前方160dp，比1.2贴近60dp，260dp大小不变，轻微浮动及转动。
- 首页左右拖动切换已添加城市；按保存顺序切换，到边界停止，停稳后请求对应城市的真实数据，相邻页仅预览该城市真实缓存。
- 国内城市与区县中文、拼音、常用英文别名搜索；城市添加、重复保护、删除确认、上移/下移排序。
- 城市管理和搜索结果使用连续列表，上下拖动查看；无上一页/下一页按钮。
- 首页采用48sp城市名、124sp实况温度、42sp天气描述与中间昼夜温度区；底部只显示原生PageControl圆点。
- 设置使用原生Sheet左上关闭按钮，标题为“设置”，无底部完成按钮，版本文案为“版本号：V1.5”。
- 我的城市页使用左上角原生48dp关闭按钮返回首页，不保留设置入口或右侧返回按钮。
- 首页与城市页使用原生TitleBar操作槽，统一64dp紧凑页头并与搜索/卡片左边界对齐。城市条目使用原生ListItem，高度随内容增长，最小88dp；城市名和行政区说明分别使用主题标题/正文样式，操作按钮位于trailingContent。
- 已添加城市整条记录可点击，选择后返回首页并加载该城市天气；删除“查看/当前城市”按钮。右侧上移、下移、删除独立处理点击；搜索结果保留添加操作。
- 刷新、设置、定位和排序采用次级按钮色；确认删除使用主题error色，取消与确认按钮在原生AlertDialog右侧排列。设置依靠Sheet自带内边距，不重复增加24dp。
- 未添加城市及搜索无结果时显示居中的标题和引导，空首页不展示无数据的实况/预报卡片。
- 离开城市页返回主页（包括查看/新增城市）会清空搜索，取消待完成搜索任务；再次进入默认显示已添加城市。搜索结果标题不显示拖动提示。
- 温度单位统一切换摄氏/华氏；城市顺序、当前城市和单位跨启动保存。
- IP 城市定位需主动确认，可能受代理或网络出口影响。
- 实况和预报卡片高度随内容计算，保留16dp内边距；取消固定124dp/228dp高度及实况卡片重复说明行。“当前实况”标题和44dp状态占位已移除；删除“网络数据不完整，显示最近缓存”提示，实际错误消息仍按状态显示。
- 首页预报标题为“未来3天预报”，保留今天及后续3天，首张卡片日期后标注“今天”。城市页IP定位与热门城市下方直接显示列表，无列表标题或排序说明行。
- 预报卡片的昼夜两组使用相同布局：36dp图标旁显示时段和天气，下方同一行显示温度、风向和风力。夜间晴天/多云使用月亮图标；昼夜图标、文字起点及信息基线对齐。卡片高度随内容计算，组间距12dp，无固定高度或额外风信息行。
- 主窗口固定1440×768dp：Manifest resizetype=2、resizerestriction=1，根 windowConstraints 最小与最大相同。实况卡片到预报标题间距24dp，标题到预报卡片间距12dp，删除右侧天数/昼夜说明；顶部天气区分配剩余高度，预报和底部信息按内容计算，避免分页区域内部留空。卡片统一24dp圆角及16dp内边距；真机日志确认窗口及最小/最大边界均为2880×1536px（density=320）。
- 网络失败只显示错误或已有真实缓存，不生成天气数据。

## 构建

AGP 8.13.2、Kotlin 2.1.20、Spatial BOM 6.1.9、SDK 35、arm64-v8a。Android Studio 打开本目录并设置本机 local.properties。

正式服务凭证从 `WEATHER_AMAP_KEY` 环境变量或 `.private/amap-key.txt` 注入 BuildConfig。源码包排除私密目录和构建生成文件；重新构建时必须自行提供凭证。APK 已包含运行所需服务配置，应用内无需填写。

```sh
export JAVA_HOME="/Users/bytedance/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew assembleDebug testDebugUnitTest lintDebug -Pkotlin.compiler.execution.strategy=in-process
```

发布签名使用 `WEATHER_SIGNING_STORE` 和 `WEATHER_SIGNING_PASSWORD`，alias=`weather`；签名材料保存在本地 `.signing/`，必须备份以保证后续升级。源码包不含签名材料。未设置签名环境变量会输出 unsigned release。

```sh
pico-cli app install app/build/outputs/apk/release/app-release.apk --replace --device <device-id>
pico-cli app launch com.pico.spatialweather --activity .platform.LaunchActivity --device <device-id>
```

## 模型资源

`app/src/main/assets/models/catalogue.json` 定义每个天气标签、模型文件、类别、三角形数和体积。`python3 tools/generate_weather_models.py` 用标准库重建全部原创GLB和Kotlin映射。模型无需网络图片或纹理，在Android端通过 `Entity.loadSuspend` 一次加载并附加到场景，离开页面销毁实体；未知或空天气只显示中性问号云，不生成天气数值。

## 数据与权限

天气、地理编码、IP定位均直接通过 HTTPS 请求高德。仅 INTERNET 权限，不申请精确位置、相机或麦克风权限；禁用备份和迁移。

高德标准天气 API 不提供小时/15天、AQI、体感、气压、生活指数和预警，按用户确认隐藏。白天/夜间温度不冒充最高/最低，风力级别不换算为精确风速。

国内目录3211项，来自高德2026-01-07编码表，仅用于名称搜索，天气请求前在线解析最新adcode。地区天气可用性以实际服务响应为准。

参考：[天气](https://lbs.amap.com/api/webservice/guide/api/weatherinfo)、[地理编码](https://lbs.amap.com/api/webservice/guide/api/georegeo)、[IP定位](https://lbs.amap.com/api/webservice/guide/api/ipconfig)、[城市目录](https://lbs.amap.com/api/webservice/download)。

1.4已生成签名APK并覆盖安装到swan头显，设备确认versionName=1.4、versionCode=5，应用启动及天气模型加载成功。此次使用本机Kotlin/Android编译工具直接编译当前源码，复用原APK资源、原生库及已有依赖DEX，未运行工作区工程脚本或Gradle检查。pico-cli 0.7.0空间截图返回全黑，卡片底边、长文本和异常提示状态尚未完成视觉验证。说明见工作区 `PICO天气-1.4-修改说明.md`。Vibe Lab上传和审核尚未执行。
