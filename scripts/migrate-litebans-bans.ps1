$ErrorActionPreference = 'Stop'

$mysql = 'C:\Program Files\MariaDB 12.3\bin\mysql.exe'
$sharedConfig = 'D:\MC\server\shared\config.toml'

if (-not (Test-Path -LiteralPath $mysql)) {
    throw "未找到 mysql：$mysql"
}
if (-not (Test-Path -LiteralPath $sharedConfig)) {
    throw "未找到共享配置：$sharedConfig"
}

$shared = Get-Content -LiteralPath $sharedConfig -Raw
$match = [regex]::Match($shared, '(?m)^pass\s*=\s*"([^"]+)"')
if (-not $match.Success) {
    throw '无法从共享配置读取 MariaDB 管理员凭据'
}

$env:MYSQL_PWD = $match.Groups[1].Value
try {
    $before = & $mysql --user=root --host=127.0.0.1 --port=3306 --database=kaerban --batch --skip-column-names --execute="SELECT (SELECT COUNT(*) FROM ban_logs), (SELECT COUNT(*) FROM ip_bans);"

    $sqlPlayer = @"
INSERT INTO kaerban.ban_logs
(ban_id, type, target_uuid, target_name, target_name_lower, reason, banned_by, start_at, end_at, active)
SELECT CONCAT('LB-', x.id),
       CASE WHEN x.until > 0 THEN 'TEMPBAN' ELSE 'BAN' END,
       x.uuid,
       x.player_name,
       LOWER(x.player_name),
       x.reason,
       COALESCE(x.banned_by_name, 'LiteBans'),
       x.time,
       CASE WHEN x.until > 0 THEN x.until ELSE NULL END,
       x.active
FROM (
    SELECT b.id, b.uuid, b.reason, b.banned_by_name, b.time, b.until, b.active,
        COALESCE(
            (SELECT h.name FROM litebans.litebans_history h WHERE h.uuid = b.uuid ORDER BY h.date DESC LIMIT 1),
            (SELECT s.name FROM litebans.litebans_servers s WHERE s.uuid = b.uuid ORDER BY s.date DESC LIMIT 1)
        ) AS player_name
    FROM litebans.litebans_bans b
    WHERE b.ipban = 0
      AND b.uuid IS NOT NULL
      AND b.uuid <> ''
) x
WHERE x.player_name IS NOT NULL
  AND CHAR_LENGTH(x.player_name) BETWEEN 1 AND 16
ON DUPLICATE KEY UPDATE
    type = VALUES(type),
    target_uuid = VALUES(target_uuid),
    target_name = VALUES(target_name),
    target_name_lower = VALUES(target_name_lower),
    reason = VALUES(reason),
    banned_by = VALUES(banned_by),
    start_at = VALUES(start_at),
    end_at = VALUES(end_at),
    active = VALUES(active);
"@

    $sqlIp = @"
INSERT INTO kaerban.ip_bans
(ban_id, type, target_uuid, target_name, target_name_lower, ip, reason, banned_by, start_at, end_at, active)
SELECT CONCAT('LB-', x.id),
       CASE WHEN x.until > 0 THEN 'TEMP_IP_BAN' ELSE 'IP_BAN' END,
       x.uuid,
       x.player_name,
       LOWER(x.player_name),
       x.ip,
       x.reason,
       COALESCE(x.banned_by_name, 'LiteBans'),
       x.time,
       CASE WHEN x.until > 0 THEN x.until ELSE NULL END,
       x.active
FROM (
    SELECT b.id, b.uuid, b.ip, b.reason, b.banned_by_name, b.time, b.until, b.active,
        COALESCE(
            (SELECT h.name FROM litebans.litebans_history h WHERE h.uuid = b.uuid ORDER BY h.date DESC LIMIT 1),
            (SELECT s.name FROM litebans.litebans_servers s WHERE s.uuid = b.uuid ORDER BY s.date DESC LIMIT 1)
        ) AS player_name
    FROM litebans.litebans_bans b
    WHERE b.ipban = 1
      AND b.ip IS NOT NULL
      AND b.ip <> ''
      AND b.uuid IS NOT NULL
      AND b.uuid <> ''
) x
WHERE x.player_name IS NOT NULL
  AND CHAR_LENGTH(x.player_name) BETWEEN 1 AND 16
ON DUPLICATE KEY UPDATE
    type = VALUES(type),
    target_uuid = VALUES(target_uuid),
    target_name = VALUES(target_name),
    target_name_lower = VALUES(target_name_lower),
    ip = VALUES(ip),
    reason = VALUES(reason),
    banned_by = VALUES(banned_by),
    start_at = VALUES(start_at),
    end_at = VALUES(end_at),
    active = VALUES(active);
"@

    & $mysql --user=root --host=127.0.0.1 --port=3306 --database=kaerban --execute=$sqlPlayer
    if ($LASTEXITCODE -ne 0) {
        throw '迁移 LiteBans 玩家封禁失败'
    }
    & $mysql --user=root --host=127.0.0.1 --port=3306 --database=kaerban --execute=$sqlIp
    if ($LASTEXITCODE -ne 0) {
        throw '迁移 LiteBans IP 封禁失败'
    }

    $after = & $mysql --user=root --host=127.0.0.1 --port=3306 --database=kaerban --batch --skip-column-names --execute="SELECT (SELECT COUNT(*) FROM ban_logs), (SELECT COUNT(*) FROM ip_bans);"
    if ($LASTEXITCODE -ne 0) {
        throw '迁移后校验失败'
    }
    Write-Output "迁移前 ban_logs/ip_bans：$before"
    Write-Output "迁移后 ban_logs/ip_bans：$after"
} finally {
    Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
}

Write-Output 'LiteBans 封禁记录已迁移到 KaelorvynBan。'
