set coreLibraryVersion=%1
set libraryVersion=%2
set pluginVersion=%3
echo setting azure.functions.java.core.library.version to %coreLibraryVersion%
call mvn versions:set-property -Dproperty=azure.functions.java.core.library.version -DnewVersion=%coreLibraryVersion% -U -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn -B
IF DEFINED libraryVersion (
echo setting azure.functions.java.library.version to %libraryVersion%
call mvn versions:set-property -Dproperty=azure.functions.java.library.version -DnewVersion=%libraryVersion% -U -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn -B
)
IF DEFINED pluginVersion (
echo setting azure.functions.maven.plugin.version to %pluginVersion% 
call mvn versions:set-property -Dproperty=azure.functions.maven.plugin.version -DnewVersion=%pluginVersion% -U -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn -B
)
