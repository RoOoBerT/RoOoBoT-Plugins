@echo off
cls

cd /D %~dp0

FOR %%F IN (Test Google Scan Pan) DO (
	echo Copying %%F ...
	COPY target\rooobot-plugins-0.1.jar /B /Y ..\RoOoBoT\plugins\%%F.jar
)

pause
