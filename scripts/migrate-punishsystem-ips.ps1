$ErrorActionPreference = 'Stop'

$mysql = 'C:\Program Files\MariaDB 12.3\bin\mysql.exe'
$jsonPath = 'D:\MC\server\[25565] 代理端\plugins\punishsystem\player_ips.json'
$sharedConfig = 'D:\MC\server\shared\config.toml'

if (-not (Test-Path -LiteralPath $mysql)) {
    throw "未找到 mysql：$mysql"
}
if (-not (Test-Path -LiteralPath $jsonPath)) {
    throw "未找到 PunishSystem IP 数据：$jsonPath"
}
if (-not (Test-Path -LiteralPath $sharedConfig)) {
    throw "未找到共享配置：$sharedConfig"
}

$shared = Get-Content -LiteralPath $sharedConfig -Raw
$match = [regex]::Match($shared, '(?m)^pass\s*=\s*"([^"]+)"')
if (-not $match.Success) {
    throw '无法从共享配置读取 MariaDB 管理员凭据'
}

$data = Get-Content -LiteralPath $jsonPath -Raw | ConvertFrom-Json
$values = @()
foreach ($uuidProperty in $data.PSObject.Properties) {
    $uuid = $uuidProperty.Name
    $entry = $uuidProperty.Value
    $name = [string]$entry.playerName
    if ($name -in @('[CONSOLE]', 'CONSOLE') -or [string]::IsNullOrWhiteSpace($name)) {
        continue
    }
    $lower = $name.ToLowerInvariant()
    foreach ($ipProperty in $entry.ips.PSObject.Properties) {
        $ip = $ipProperty.Name
        $lastSeen = 0L
        if ($ipProperty.Value.PSObject.Properties.Name -contains 'lastSeen') {
            $lastSeen = [int64]$ipProperty.Value.lastSeen
        }
        $values += "('$uuid', '$($name.Replace("'", "''"))', '$($lower.Replace("'", "''"))', '$($ip.Replace("'", "''"))', $lastSeen, $lastSeen)"
    }
}

if ($values.Count -eq 0) {
    throw 'PunishSystem IP 数据为空'
}

$env:MYSQL_PWD = $match.Groups[1].Value
try {
    $before = & $mysql --user=root --host=127.0.0.1 --port=3306 --database=kaerban --batch --skip-column-names --execute="SELECT COUNT(*) FROM player_ips;"

    $sql = "INSERT INTO kaerban.player_ips "
    $sql += "(uuid, player_name, player_name_lower, ip, first_seen, last_seen) VALUES "
    $sql += ($values -join ',')
    $sql += " ON DUPLICATE KEY UPDATE "
    $sql += "player_name = VALUES(player_name), "
    $sql += "player_name_lower = VALUES(player_name_lower), "
    $sql += "first_seen = LEAST(first_seen, VALUES(first_seen)), "
    $sql += "last_seen = GREATEST(last_seen, VALUES(last_seen));"

    & $mysql --user=root --host=127.0.0.1 --port=3306 --execute=$sql
    if ($LASTEXITCODE -ne 0) {
        throw 'PunishSystem IP 导入失败'
    }

    $cleanup = @"
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
    & $mysql --user=root --host=127.0.0.1 --port=3306 --database=kaerban --execute=$cleanup
    if ($LASTEXITCODE -ne 0) {
        throw '清理非公网 IP 失败'
    }

    $after = & $mysql --user=root --host=127.0.0.1 --port=3306 --database=kaerban --batch --skip-column-names --execute="SELECT COUNT(*) FROM player_ips;"
    Write-Output "迁移前 player_ips：$before"
    Write-Output "迁移后 player_ips：$after"
} finally {
    Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
}

Write-Output 'PunishSystem 公网 IP 历史已导入 kaerban.player_ips。'
