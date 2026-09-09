# KaelorvynBan

## 项目说明

- 名称：KaelorvynBan
- 编号：0005
- 开始日期：2026-08-08
- 类型：Velocity 代理端中文封禁插件
- 主包：`com.kael.punish`
- 状态：已构建并部署到 `D:\MC\server\[25565] 代理端\plugins`

## 功能

- 玩家封禁、临时封禁、IP 封禁、临时 IP 封禁、解封、踢出、查询
- 离线玩家封禁，使用 `OfflinePlayer:<玩家名>` 本地离线 UUID
- 登录 IP 历史记录与最近真实公网 IP 选择
- MariaDB 数据存储：`kaerban` 库，`ban_logs / ip_bans / player_ips`
- 全中文命令提示、封禁界面和日志
- 可选 VPN/代理检测，默认关闭
- 封禁指令仅限代理端控制台使用，游戏内不可执行
- 控制台命令：`kban` / `ktempban` / `kipban` / `ktempipban` / `kunban` / `kkick` / `kcheck`
- 不注册 `/ban` 等别名，避免拦截子服原有封禁指令
- `kban` 只封账号，不自动封 IP
- `kipban` / `ktempipban`：封该玩家已知公网 IP，同时封禁同网络关联的大号/小号名字
- 四个封 IP 指令都会同步封禁玩家名字/账号：`kipban` / `ktempipban` /
  `kbanlink` / `ktempipbanlink`
- `kbanlink <玩家> <原因>`：永久封禁关联账号名字 + 链式封禁全部关联公网 IP
- `ktempipbanlink <玩家> <时长> <原因>`：临时封禁关联账号名字 + 链式封禁关联公网 IP
- 链式连坐会自动延伸：任一被封关联账号从新公网 IP 登录时，代理端自动封掉这个新 IP，
  并把新关联到的大号/小号名字也一起封掉
- 单独豁免：`kopunban <玩家>` 豁免该账号的 IP 封禁；`kopreban <玩家>` 取消豁免
- `kopunban` 豁免跟随封禁到期自动失效；`kunban` 输入链上任一名字会解封整条关联链并清除豁免

## 目录

- `src/main/java`：插件源码
- `src/main/resources`：`velocity-plugin.json` 与默认配置
- `scripts/build.ps1`：编译、打包、部署
- `scripts/setup-db.ps1`：创建 MariaDB 库和专用账号
- `scripts/smoke-test.ps1`：纯逻辑自检
- `scripts/db-smoke-test.ps1`：数据库读写自检
- `outputs`：最终 jar
- `logs`：构建与验证日志

## 部署命令

```powershell
.\scripts\setup-db.ps1
.\scripts\build.ps1
```

部署后需停用旧 PunishSystem 并重启 Velocity，插件才会加载。
