@echo off
jre\bin\java -classpath "downloader\*" io.supertokens.downloader.Main %*

IF %errorlevel% NEQ 0 (
    echo Installation failed. Please try again
    goto:eof
)

echo.

jre\bin\java -classpath "cli\*" io.supertokens.cli.Main true %*

:eof
