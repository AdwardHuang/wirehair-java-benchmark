@echo off
setlocal

if not exist "build\native\Release\wirehairjni.dll" (
    echo ERROR: build\native\Release\wirehairjni.dll not found.
    echo Use GitHub Actions if Visual Studio is not installed locally.
    exit /b 1
)

mvn -B package -DskipTests
if errorlevel 1 exit /b 1

java -Dwirehairjni.path="%CD%\build\native\Release\wirehairjni.dll" ^
  -cp "target\wirehair-java-benchmark-1.0.0.jar" ^
  com.hrz.wirehairbench.Benchmark %*

endlocal
