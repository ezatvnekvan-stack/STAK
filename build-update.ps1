param([switch]$Offline,[switch]$RunTests,[switch]$ExportOnly)
$ErrorActionPreference='Stop'
$projectRoot=$PSScriptRoot
$signingPath=if($env:STAK_KEYSTORE_PATH){$env:STAK_KEYSTORE_PATH}else{Join-Path $projectRoot 'signing/stak-preview.keystore'}
if(-not (Test-Path -LiteralPath $signingPath)){throw 'Missing original signing key. Do not build an update with a replacement key.'}
$env:STAK_KEYSTORE_PATH=$signingPath
if(-not $env:JAVA_HOME){
    $bundledJava=Join-Path $env:ProgramFiles 'Android/Android Studio/jbr'
    if(Test-Path -LiteralPath $bundledJava){$env:JAVA_HOME=$bundledJava}
}
if(-not $env:JAVA_HOME){throw 'Install JDK 17+ or Android Studio and set JAVA_HOME.'}
if(-not $env:ANDROID_HOME){
    if($env:ANDROID_SDK_ROOT){$env:ANDROID_HOME=$env:ANDROID_SDK_ROOT}
    else{$env:ANDROID_HOME=Join-Path $env:LOCALAPPDATA 'Android/Sdk'}
}
if(-not (Test-Path -LiteralPath $env:ANDROID_HOME)){throw 'Install Android SDK platform/build-tools 35 and set ANDROID_HOME.'}
if(Test-Path -LiteralPath (Join-Path $projectRoot 'cloud-config.env')){
Get-Content -LiteralPath (Join-Path $projectRoot 'cloud-config.env') | ForEach-Object {
    if($_ -match '^(STAK_CLOUD_URL|STAK_CLOUD_PUBLIC_KEY)=(.+)$'){
        [Environment]::SetEnvironmentVariable($Matches[1],$Matches[2],'Process')
    }
}
}
if(-not $env:STAK_CLOUD_URL -or -not $env:STAK_CLOUD_PUBLIC_KEY){throw 'Configure the existing cloud deployment through environment variables or local cloud-config.env.'}
if(-not $env:STAK_CLOUD_URL.StartsWith('https://') -or -not $env:STAK_CLOUD_PUBLIC_KEY.StartsWith('sb_publishable_')){throw 'Cloud deployment settings are missing or invalid.'}
$buildTasks=@(':app:assemblePreview')
if($RunTests){$buildTasks=@(':app:testDebugUnitTest')+$buildTasks}
$buildOptions=@('--console=plain')
if($Offline){$buildOptions+='--offline'}
Push-Location $projectRoot
try{
    if(-not $ExportOnly){
        & (Join-Path $projectRoot 'gradlew.bat') @buildTasks @buildOptions
        if($LASTEXITCODE -ne 0){throw 'Gradle build failed; no update was exported.'}
    }
    $builtApk=Join-Path $projectRoot 'app/build/outputs/apk/preview/app-preview.apk'
    $buildTools=Join-Path $env:ANDROID_HOME 'build-tools/35.0.0'
    $certificate=& (Join-Path $buildTools 'apksigner.bat') verify --print-certs $builtApk
    if($LASTEXITCODE -ne 0){throw 'APK signature verification failed.'}
    $expectedCertificate='bd9c33ebf4a4a62fc034cbb363635ed8d4d55f081ce43fcaff59706d2230c430'
    if(($certificate -join "`n") -notmatch $expectedCertificate){throw 'Wrong signing identity. Refusing to export this APK as an update.'}
    $packageLine=(& (Join-Path $buildTools 'aapt.exe') dump badging $builtApk | Select-String '^package:').Line
    if($LASTEXITCODE -ne 0 -or $packageLine -notmatch "name='app.stak.nativeapp.parity' versionCode='(\d+)' versionName='([^']+)'" ){throw 'Wrong application ID or invalid APK.'}
    $versionCode=[int]$Matches[1];$versionName=$Matches[2]
    if($versionCode -lt 70){throw 'Refusing to export an older version.'}
    $distribution=Join-Path $projectRoot 'distribution'
    New-Item -ItemType Directory -Force -Path $distribution | Out-Null
    $exportedApk=Join-Path $distribution 'STAK-current.apk'
    Copy-Item -LiteralPath $builtApk -Destination $exportedApk -Force
    [ordered]@{applicationId='app.stak.nativeapp.parity';versionName=$versionName;versionCode=$versionCode;sha256=(Get-FileHash -LiteralPath $exportedApk -Algorithm SHA256).Hash.ToLowerInvariant();certificateSha256=$expectedCertificate;cloudConfigured=$true} | ConvertTo-Json | Set-Content -Encoding UTF8 -LiteralPath (Join-Path $distribution 'current-version.json')
    Write-Host "Verified update: $exportedApk ($versionName, code $versionCode)"
}finally{Pop-Location}
