# A function that checks exit codes and fails script if an error is found
function StopOnFailedExecution {
  if ($LastExitCode)
  {
    exit $LastExitCode
  }
}

# Build and publish azure-functions--core-library
Write-Host "Build and install azure-functions-java-core-library"
cmd.exe /c '.\mvnBuild.bat'
StopOnFailedExecution
$coreLibraryPom = Get-Content "pom.xml" -Raw
$coreLibraryPom -match "<version>(.*)</version>"
$coreLibraryVersion = $matches[1]
Write-Host "coreLibraryVersion: " $coreLibraryVersion
cmd.exe /c '.\publishToContainer.bat' $coreLibraryVersion
StopOnFailedExecution