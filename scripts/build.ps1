$ErrorActionPreference = 'Stop'

$ProjectRoot = Split-Path -Parent $PSScriptRoot
$ProxyRoot = 'D:\MC\server\[25565] 代理端'
$Javac = 'D:\Java\jdk-25\bin\javac.exe'
$JarTool = 'D:\Java\jdk-25\bin\jar.exe'
$VelocityJar = Join-Path $ProxyRoot 'velocity-3.5.0-SNAPSHOT-605.jar'
$ConnectorJar = Join-Path $ProxyRoot 'libraries\com\mysql\mysql-connector-j\9.2.0\mysql-connector-j-9.2.0.jar'
$OutClasses = Join-Path $ProjectRoot 'out\classes'
$MysqlOut = Join-Path $ProjectRoot 'out\mysql'
$JarPath = Join-Path $ProjectRoot 'outputs\KaelorvynBan-1.0.0.jar'
$PluginJar = Join-Path $ProxyRoot 'plugins\KaelorvynBan-1.0.0.jar'

$libraries = Get-ChildItem -LiteralPath (Join-Path $ProxyRoot 'libraries') -Recurse -Filter '*.jar' |
    ForEach-Object { $_.FullName }
$classpath = (@($VelocityJar) + $libraries) -join ';'

foreach ($target in @($OutClasses, $MysqlOut)) {
    if (Test-Path -LiteralPath $target) {
        [System.IO.Directory]::Delete($target, $true)
    }
}
New-Item -ItemType Directory -Force -Path $OutClasses | Out-Null
New-Item -ItemType Directory -Force -Path $MysqlOut | Out-Null
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $JarPath) | Out-Null

$sources = Get-ChildItem -LiteralPath (Join-Path $ProjectRoot 'src\main\java') -Recurse -Filter '*.java' |
    ForEach-Object { $_.FullName }

& $Javac --release 21 -encoding UTF-8 -cp $classpath -d $OutClasses $sources
if ($LASTEXITCODE -ne 0) {
    throw '编译失败'
}

Copy-Item -LiteralPath (Join-Path $ProjectRoot 'src\main\resources\velocity-plugin.json') -Destination $OutClasses -Force
Copy-Item -LiteralPath (Join-Path $ProjectRoot 'src\main\resources\config.properties') -Destination $OutClasses -Force

if (Test-Path -LiteralPath $JarPath) {
    [System.IO.File]::Delete($JarPath)
}
& $JarTool --create --file $JarPath -C $OutClasses .
if ($LASTEXITCODE -ne 0) {
    throw '打包失败'
}

Push-Location $MysqlOut
try {
    & $JarTool xf $ConnectorJar
    if ($LASTEXITCODE -ne 0) {
        throw '解压 MySQL 驱动失败'
    }
} finally {
    Pop-Location
}
& $JarTool --update --file $JarPath -C $MysqlOut .
if ($LASTEXITCODE -ne 0) {
    throw '写入 MySQL 驱动失败'
}

Copy-Item -LiteralPath $JarPath -Destination $PluginJar -Force
Write-Output "构建完成：$JarPath"
Write-Output "已部署：$PluginJar"
