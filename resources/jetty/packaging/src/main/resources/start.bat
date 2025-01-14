@echo off
REM -------------------------------------------------------------------------
REM See the NOTICE file distributed with this work for additional
REM information regarding copyright ownership.
REM
REM This program is free software: you can redistribute it and/or modify
REM it under the terms of the GNU Affero General Public License as published by
REM the Free Software Foundation, either version 3 of the License, or
REM (at your option) any later version.
REM
REM This program is distributed in the hope that it will be useful,
REM but WITHOUT ANY WARRANTY; without even the implied warranty of
REM MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
REM GNU Affero General Public License for more details.
REM
REM You should have received a copy of the GNU Affero General Public License
REM along with this program.  If not, see http://www.gnu.org/licenses/
REM -------------------------------------------------------------------------


REM -------------------------------------------------------------------------
REM Optional ENV vars
REM -----------------
REM   START_OPTS - parameters passed to the Java VM when running Jetty
REM     e.g. to increase the memory allocated to the JVM to 1GB, use
REM       set START_OPTS=-Xmx1024m
REM   JETTY_PORT - the port on which to start Jetty, 8080 by default
REM   JETTY_STOP_PORT - the port on which Jetty listens for a Stop command, 8079 by default
REM -------------------------------------------------------------------------

setlocal EnableDelayedExpansion

set JETTY_HOME=jetty
if not defined START_OPTS set START_OPTS=-Xmx2048m

REM The port on which to start Jetty can be defined in an enviroment variable called JETTY_PORT
if not defined JETTY_PORT (
  REM Alternatively, it can be passed to this script as the first argument
  set JETTY_PORT=%1
  if not defined JETTY_PORT (
    set JETTY_PORT=8080
  )
)

REM The port on which Jetty listens for a Stop command can be defined in an enviroment variable called JETTY_STOP_PORT
if not defined JETTY_STOP_PORT (
  REM Alternatively, it can be passed to this script as the second argument
  set JETTY_STOP_PORT=%2
  if not defined JETTY_STOP_PORT (
    set JETTY_STOP_PORT=8079
  )
)

echo Starting Jetty on port %JETTY_PORT%, please wait...

REM Get javaw.exe from the latest properly installed JRE
for /f tokens^=2^ delims^=^" %%i in ('reg query HKEY_CLASSES_ROOT\jarfile\shell\open\command /ve') do set JAVAW_PATH=%%i
set JAVA_PATH=%JAVAW_PATH:\javaw.exe=%\java.exe
if "%JAVA_PATH%"=="" set JAVA_PATH=java

REM Location where XWiki stores generated data and where database files are.
set XWIKI_DATA_DIR="%AppData%\PhenoTips"
set START_OPTS=%START_OPTS% -Dxwiki.data.dir=%XWIKI_DATA_DIR%

REM Ensure the data directory exists so that XWiki can use it for storing permanent data.
if not exist %XWIKI_DATA_DIR% (
  mkdir %XWIKI_DATA_DIR%
  xcopy /E /Q data %XWIKI_DATA_DIR%
)

REM Ensure the logs directory exists as otherwise Jetty reports an error
if not exist %XWIKI_DATA_DIR%\logs mkdir %XWIKI_DATA_DIR%\logs

REM Specify port on which HTTP requests will be handled
set START_OPTS=%START_OPTS% -Djetty.port=%JETTY_PORT%

REM Specify Jetty's home directory
set START_OPTS=%START_OPTS% -Djetty.home=%JETTY_HOME%

REM Specify port and key to stop a running Jetty instance
set START_OPTS=%START_OPTS% -DSTOP.KEY=xwiki -DSTOP.PORT=%JETTY_STOP_PORT%

REM Specify the encoding to use
set START_OPTS=%START_OPTS% -Dfile.encoding=UTF8

REM Optional: enable remote debugging
REM set START_OPTS=%START_OPTS% -Xdebug -Xnoagent -Djava.compiler=NONE -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005

REM In order to avoid getting a "java.lang.IllegalStateException: Form too large" error
REM when editing large page in XWiki we need to tell Jetty to allow for large content
REM since by default it only allows for 20K. We do this by passing the
REM org.eclipse.jetty.server.Request.maxFormContentSize property.
REM Note that setting this value too high can leave your server vulnerable to denial of
REM service attacks.
set START_OPTS=%START_OPTS% -Dorg.eclipse.jetty.server.Request.maxFormContentSize=1000000 -Dorg.eclipse.jetty.server.Request.maxFormKeys=10000

set JETTY_CONFIGURATION_FILES=
for /r %%i in (%JETTY_HOME%\etc\jetty-*.xml) do set JETTY_CONFIGURATION_FILES=!JETTY_CONFIGURATION_FILES! "%%i"

"%JAVA_PATH%" %START_OPTS% %3 %4 %5 %6 %7 %8 %9 -jar "%JETTY_HOME%/start.jar" "%JETTY_HOME%/etc/jetty.xml" %JETTY_CONFIGURATION_FILES%

REM Pause so that the command window used to run this script doesn't close automatically in case of problem
REM (like when the JDK/JRE is not installed)
PAUSE
