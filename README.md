# Crackin Showroom TV (Fire TV app)

Plays the showroom TV page full screen, keeps the screen awake, starts when the Firestick turns on, and turns the picture to match a physically turned TV.

## Get the APK
GitHub builds it on every push. Open the Actions tab, select the latest run, and download `crackin-showroom-tv-apk` at the bottom.

## Install on a Firestick
1. Firestick: Settings, My Fire TV, Developer options, turn on ADB debugging and Apps from Unknown Sources.
2. Note the Firestick IP address: Settings, My Fire TV, About, Network.
3. On a computer on the same network (adb installed): `adb connect FIRESTICK_IP` then `adb install -r crackin-showroom-tv.apk`.
4. Open Crackin Showroom TV from Your Apps and Channels. Set the rotation, then select Start.

## In the dashboard
Set that TV's rotation to none, because the app already turns the screen.

## Remote
Menu opens the setup screen. Holding Back also opens it.
