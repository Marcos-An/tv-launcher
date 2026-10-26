param(
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$SdkRoot = $env:ANDROID_SDK_ROOT,
    [string]$BuildToolsVersion = '35.0.0',
    [string]$Platform = 'android-35',
    [string]$BuildToolsPath,
    [string]$PlatformJar
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
if (!$JavaHome) { throw 'Defina JAVA_HOME para o JDK 17 ou 21.' }
if (!$SdkRoot) { $SdkRoot = $env:ANDROID_HOME }
if (!$BuildToolsPath) { if (!$SdkRoot) { throw 'Defina ANDROID_SDK_ROOT.' }; $BuildToolsPath = Join-Path $SdkRoot "build-tools/$BuildToolsVersion" }
if (!$PlatformJar) { if (!$SdkRoot) { throw 'Defina ANDROID_SDK_ROOT.' }; $PlatformJar = Join-Path $SdkRoot "platforms/$Platform/android.jar" }
$buildRoot = Join-Path $projectRoot '.build'
$localRoot = Join-Path $projectRoot '.local'
$classes = Join-Path $buildRoot 'classes'
New-Item -ItemType Directory -Path $classes,$localRoot -Force | Out-Null
function Assert-ToolExit([string]$step) { if ($LASTEXITCODE -ne 0) { throw "Falha em: $step" } }
$java = Join-Path $JavaHome 'bin/java.exe'
$javac = Join-Path $JavaHome 'bin/javac.exe'
$jar = Join-Path $JavaHome 'bin/jar.exe'
$keytool = Join-Path $JavaHome 'bin/keytool.exe'
$lambdaStubs = Join-Path $BuildToolsPath 'core-lambda-stubs.jar'
$sources = @(Get-ChildItem (Join-Path $projectRoot 'app/src/main/java') -Filter *.java -Recurse | Select-Object -ExpandProperty FullName)
& $javac -encoding UTF-8 -source 8 -target 8 -bootclasspath "$PlatformJar;$lambdaStubs" -d $classes $sources
Assert-ToolExit 'javac'
$base = Join-Path $buildRoot 'base.apk'
& (Join-Path $BuildToolsPath 'aapt.exe') package -f -M (Join-Path $projectRoot 'app/src/main/AndroidManifest.xml') -I $PlatformJar -F $base
Assert-ToolExit 'manifesto'
$classesJar = Join-Path $buildRoot 'classes.jar'
& $jar cf $classesJar -C $classes .
Assert-ToolExit 'classes.jar'
& $java -cp (Join-Path $BuildToolsPath 'lib/d8.jar') com.android.tools.r8.D8 --lib $PlatformJar --min-api 26 --output $buildRoot $classesJar
Assert-ToolExit 'D8'
$unsigned = Join-Path $buildRoot 'unsigned.apk'
Copy-Item -LiteralPath $base -Destination $unsigned -Force
& $jar uf $unsigned -C $buildRoot classes.dex
Assert-ToolExit 'empacotamento DEX'
$aligned = Join-Path $buildRoot 'aligned.apk'
& (Join-Path $BuildToolsPath 'zipalign.exe') -f 4 $unsigned $aligned
Assert-ToolExit 'zipalign'
$key = Join-Path $localRoot 'debug.jks'
if (!(Test-Path -LiteralPath $key)) {
    & $keytool -genkeypair -keystore $key -storepass android -keypass android -alias debug -dname 'CN=Android Debug,O=Android,C=US' -keyalg RSA -keysize 2048 -validity 10000
    Assert-ToolExit 'chave de desenvolvimento'
}
$output = Join-Path $buildRoot 'tv-launcher-debug.apk'
$signer = Join-Path $BuildToolsPath 'lib/apksigner.jar'
& $java -jar $signer sign --ks $key --ks-pass pass:android --out $output $aligned
Assert-ToolExit 'assinatura'
& $java -jar $signer verify $output
Assert-ToolExit 'verificacao da assinatura'
Write-Output 'APK criado em .build/tv-launcher-debug.apk'
