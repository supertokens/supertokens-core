@echo off
set st_install_loc=$ST_INSTALL_LOC
"%st_install_loc%jre\bin"\java -classpath "%st_install_loc%cli\*" io.supertokens.cli.Main false "%st_install_loc%\" %*
IF %errorlevel% NEQ 0 (
echo exiting
goto:eof
)
IF "%1" == "uninstall" (
rmdir /S /Q "%st_install_loc%"
del "%~f0"
)
:eof
