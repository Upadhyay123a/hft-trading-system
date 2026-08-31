@echo off
setlocal enabledelayedexpansion
set /p CP=<cp.txt
java -cp "target\classes;%CP%" com.hft.demo.BinanceSmokeDemo
