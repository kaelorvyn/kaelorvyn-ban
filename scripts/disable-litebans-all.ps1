$ErrorActionPreference = 'Stop'

$root = 'D:\MC\server'
$targets = Get-ChildItem -LiteralPath $root -Recurse -Force -File -ErrorAction SilentlyContinue |
    Where-Object {
        $_.Name -match 'litebans|lbridge|封禁' -and
        $_.Name -like '*.jar' -and
        $_.Name -notlike '*.dsable'
    } |
    Sort-Object FullName

$renamed = @()
$blocked = @()
foreach ($file in $targets) {
    $newPath = Join-Path $file.DirectoryName ($file.Name + '.dsable')
    if (Test-Path -LiteralPath $newPath) {
        Write-Output "SKIP 已存在：$($file.FullName)"
        continue
    }
    try {
        Rename-Item -LiteralPath $file.FullName -NewName ($file.Name + '.dsable')
        $renamed += $file.FullName
    } catch {
        $blocked += $file.FullName
    }
}

Write-Output "已禁用 LiteBans/lbridge 文件数：$($renamed.Count)"
$renamed | ForEach-Object { Write-Output $_ }
Write-Output "被占用无法禁用文件数：$($blocked.Count)"
$blocked | ForEach-Object { Write-Output $_ }
