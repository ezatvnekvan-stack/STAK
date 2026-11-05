param([Parameter(ValueFromRemainingArguments=$true)][string[]]$GradleArgs)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
if(-not $env:JAVA_HOME){$env:JAVA_HOME=Join-Path $env:ProgramFiles 'Android/Android Studio/jbr'}
if(-not $env:STAK_KEYSTORE_PATH){
    $privateKey=Join-Path $projectRoot 'signing/stak-preview.keystore'
    if(Test-Path -LiteralPath $privateKey){$env:STAK_KEYSTORE_PATH=$privateKey}
}
if(Test-Path -LiteralPath (Join-Path $projectRoot 'cloud-config.env')){
Get-Content -LiteralPath (Join-Path $projectRoot 'cloud-config.env') | ForEach-Object {
 if($_ -match '^(STAK_CLOUD_URL|STAK_CLOUD_PUBLIC_KEY)=(.+)$'){[Environment]::SetEnvironmentVariable($Matches[1],$Matches[2],'Process')}
}
}
Push-Location $projectRoot
try{& (Join-Path $projectRoot 'gradlew.bat') @GradleArgs;exit $LASTEXITCODE}finally{Pop-Location}
