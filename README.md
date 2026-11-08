# Voter Checking & Vote Counting

Offline native Android application built with Kotlin, Jetpack Compose and Room.

## Included
- Excel (.xlsx) import from the first worksheet.
- Automatic Ward list from imported voter records; ALL and Ward filters.
- Check Voters: Green, Yellow, Red and Not Checked counts.
- Vote Counting: shared candidate list, one vote per voter, NOTA, clear/change vote, ALL and Ward totals.
- Local Room database persistence; no login, Firebase or internet required.
- Delete confirmation, Recycle Bin, recover and permanent delete for voter records and candidates.
- Delete the full active voter list to Recycle Bin; a newly imported list also archives the previous active list.

## Excel columns
Header row should contain Serial Number, Voter Name, Father Name / Mother Name / Husband Name (one or more supported columns), EPIC Number and Ward Number. EPIC may be blank. Voter Name and Ward Number are required. The importer currently reads the first worksheet of an .xlsx workbook.

## Build APK
GitHub Actions workflow: Android APK Build (.github/workflows/android-build.yml).
It runs on pushes to main or master and manually through Actions → Android APK Build → Run workflow.
After a successful run, download the voter-app-debug-apk artifact from that run.

The generated debug APK is for installation/testing. A signed release build is a separate release step.
