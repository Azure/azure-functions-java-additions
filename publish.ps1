param (
    [Parameter(Mandatory=$true)]
    [ValidateNotNullOrEmpty()]
    [System.String]
    $KeyVaultServiceKey,

    [Parameter(Mandatory=$true)]
    [ValidateNotNullOrEmpty()]
    [System.String]
    $KeyVaultApplicationId,

    [Parameter(Mandatory=$true)]
    [ValidateNotNullOrEmpty()]
    [System.String]
    $MavenInstallLocation,

    [Parameter(Mandatory=$true)]
    [ValidateNotNullOrEmpty()]
    [System.String]
    $AzureSDKPartnerContainerUrl
)

# A function that checks exit codes and fails script if an error is found
function StopOnFailedExecution {
  if ($LastExitCode)
  {
    exit $LastExitCode
  }
}

# Build and publish azure-functions--core-library
Write-Host "Publish azure-functions-java-core-library artifacts to Azure container"
$coreLibraryPom = Get-Content "pom.xml" -Raw
$coreLibraryPom -match "<version>(.*)</version>"
$CoreLibraryVersion = $matches[1]
Write-Host "CoreLibraryVersion: " $CoreLibraryVersion
cmd.exe /c '.\publishToContainer.bat' $coreLibraryVersion $KeyVaultServiceKey $KeyVaultApplicationId $MavenInstallLocation $AzureSDKPartnerContainerUrl
StopOnFailedExecution