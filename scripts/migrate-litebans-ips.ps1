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
    $before = & $mysql --user=root --host=127.0.0.1 --port=3306 --database=kaerban --batch --skip-column-names --execute="SELECT COUNT(*) FROM player_ips;"

    $sqlHistory = "INSERT INTO kaerban.player_ips "
    $sqlHistory += "(uuid, player_name, player_name_lower, ip, first_seen, last_seen) "
    $sqlHistory += "SELECT uuid, name, LOWER(name), ip, UNIX_TIMESTAMP(date) * 1000, UNIX_TIMESTAMP(date) * 1000 "
    $sqlHistory += "FROM litebans.litebans_history "
    $sqlHistory += "WHERE name NOT IN ('[CONSOLE]', 'CONSOLE') "
    $sqlHistory += "AND uuid NOT IN ('CONSOLE') "
    $sqlHistory += "AND ip NOT IN ('127.0.0.1', '::1', '0.0.0.0', '#') "
    $sqlHistory += "AND ip <> '' "
    $sqlHistory += "ON DUPLICATE KEY UPDATE "
    $sqlHistory += "player_name = VALUES(player_name), "
    $sqlHistory += "player_name_lower = VALUES(player_name_lower), "
    $sqlHistory += "first_seen = LEAST(first_seen, VALUES(first_seen)), "
    $sqlHistory += "last_seen = GREATEST(last_seen, VALUES(last_seen));"

    $sqlBans = @"
INSERT INTO kaerban.player_ips
(uuid, player_name, player_name_lower, ip, first_seen, last_seen)
SELECT x.uuid, x.player_name, LOWER(x.player_name), x.ip, x.ts, x.ts
FROM (
    SELECT b.uuid,
        COALESCE(
            (SELECT h.name FROM litebans.litebans_history h WHERE h.uuid = b.uuid ORDER BY h.date DESC LIMIT 1),
            (SELECT s.name FROM litebans.litebans_servers s WHERE s.uuid = b.uuid ORDER BY s.date DESC LIMIT 1)
        ) AS player_name,
        b.ip,
        b.time AS ts
    FROM (
        SELECT uuid, ip, time FROM litebans.litebans_bans
        UNION ALL
        SELECT uuid, ip, time FROM litebans.litebans_kicks
        UNION ALL
        SELECT uuid, ip, time FROM litebans.litebans_mutes
        UNION ALL
        SELECT uuid, ip, time FROM litebans.litebans_warnings
    ) b
    WHERE b.ip IS NOT NULL
      AND b.ip <> ''
      AND b.uuid IS NOT NULL
      AND b.uuid <> ''
      AND b.ip NOT IN ('127.0.0.1', '::1', '0.0.0.0', '#')
) x
WHERE x.player_name IS NOT NULL
  AND CHAR_LENGTH(x.player_name) BETWEEN 1 AND 16
ON DUPLICATE KEY UPDATE
    player_name = VALUES(player_name),
    player_name_lower = VALUES(player_name_lower),
    first_seen = LEAST(first_seen, VALUES(first_seen)),
    last_seen = GREATEST(last_seen, VALUES(last_seen));
"@

    & $mysql --user=root --host=127.0.0.1 --port=3306 --execute=$sqlHistory
    if ($LASTEXITCODE -ne 0) {
        throw '迁移 LiteBans 登录历史失败'
    }
    & $mysql --user=root --host=127.0.0.1 --port=3306 --execute=$sqlBans
    if ($LASTEXITCODE -ne 0) {
        throw '迁移 LiteBans 封禁/处罚 IP 失败'
    }

    $sqlCleanup = @"
DELETE FROM kaerban.player_ips
WHERE ip IN ('#', '::1', '0.0.0.0')
   OR ip LIKE 'fe80:%'
   OR ip LIKE 'fc%'
   OR ip LIKE 'fd%'
   OR ip LIKE '2001:db8:%'
   OR (INET_ATON(ip) IS NOT NULL AND (
       INET_ATON(ip) BETWEEN INET_ATON('127.0.0.0') AND INET_ATON('127.255.255.255')
       OR INET_ATON(ip) BETWEEN INET_ATON('10.0.0.0') AND INET_ATON('10.255.255.255')
       OR INET_ATON(ip) BETWEEN INET_ATON('100.64.0.0') AND INET_ATON('100.127.255.255')
       OR INET_ATON(ip) BETWEEN INET_ATON('169.254.0.0') AND INET_ATON('169.254.255.255')
       OR INET_ATON(ip) BETWEEN INET_ATON('172.16.0.0') AND INET_ATON('172.31.255.255')
       OR INET_ATON(ip) BETWEEN INET_ATON('192.168.0.0') AND INET_ATON('192.168.255.255')
       OR INET_ATON(ip) BETWEEN INET_ATON('224.0.0.0') AND INET_ATON('255.255.255.255')
   ));
"@
    & $mysql --user=root --host=127.0.0.1 --port=3306 --database=kaerban --execute=$sqlCleanup
    if ($LASTEXITCODE -ne 0) {
        throw '清理非公网 IP 失败'
    }

    $after = & $mysql --user=root --host=127.0.0.1 --port=3306 --database=kaerban --batch --skip-column-names --execute="SELECT COUNT(*) FROM player_ips;"
    if ($LASTEXITCODE -ne 0) {
        throw '迁移后校验失败'
    }
    Write-Output "迁移前 player_ips：$before"
    Write-Output "迁移后 player_ips：$after"
} finally {
    Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
}

Write-Output 'LiteBans 全部公网 IP 数据已迁移到 kaerban.player_ips。'
