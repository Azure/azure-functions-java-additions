set $CoreLibraryVersion=%1
set $KeyVaultServiceKey=%2
set $KeyVaultApplicationId=%3
set $MavenInstallLocation=%4
set $AzureSDKPartnerContainerUrl=%5

echo publishing azure.functions.java.core.library.version to %CoreLibraryVersion% to azure container

set AZCOPY_SPA_CLIENT_SECRET=%KeyVaultServiceKey%
azcopy login --service-principal --application-id %KeyVaultApplicationId%
azcopy copy "%MavenInstallLocation%%CoreLibraryVersion%/*" "%AzureSDKPartnerContainerUrl%%CoreLibraryVersion%"