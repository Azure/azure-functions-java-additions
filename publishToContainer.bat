set coreLibraryVersion=%1
echo publishing azure.functions.java.core.library.version to %coreLibraryVersion% to azure container

echo logging into azcopy
azcopy login --tenant-id="%USER_TENANT_ID%"
azcopy /Source:1.3.1-SNAPSHOT /Dest:%CONTAINER_URL%%coreLibraryVersion% /DestKey:%UPLOAD_ACCESS_KEY% /S
