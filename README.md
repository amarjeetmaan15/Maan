# Voter App

Native Android app for Excel-based voter list import, voter attendance marking, and ward-wise vote counting.

## Features
- Home screen: Check Voters and Vote Counting
- Import voter records from an Excel workbook (.xlsx)
- Automatic ward list from the Ward Number column
- ALL and ward filtering
- Green / Yellow / Red attendance status
- Candidate and NOTA selection per voter
- Local Room database for persistence and offline use

## Excel columns
The first row must contain headers for Voter Name and Ward Number. Supported header variants include Serial Number/Serial/S.No, Voter Name/Name, Father Name/Mother Name/Husband Name/Relation, EPIC Number/EPIC, Ward Number/Ward.

## Build
Open this repository in Android Studio and sync Gradle. Build the debug APK from Build > Build Bundle(s) / APK(s) > Build APK(s).
