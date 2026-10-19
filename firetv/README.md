# Arcade Orbit for Fire TV

This Android TV/Fire TV WebView app opens the live Arcade Orbit site:
https://nayumahmed.github.io/arcade-orbit47/

## Build and download

A GitHub Actions workflow builds a debug APK when files in `firetv/` change on `main`, then publishes it as the `ArcadeOrbit.apk` asset on the `firetv-latest` GitHub Release.

Direct APK URL for Downloader:
https://github.com/NayumAhmed/arcade-orbit47/releases/latest/download/ArcadeOrbit.apk

## Install using Downloader

1. On Fire TV, open **Settings → My Fire TV → About** and select the device name 7 times if Developer Options is hidden.
2. Open **Settings → My Fire TV → Developer Options**. Enable **Install unknown apps** for Downloader (or enable **ADB debugging** only if your chosen installer requires it).
3. Install/open the **Downloader** app from the Amazon Appstore.
4. Enter this direct URL: `https://github.com/NayumAhmed/arcade-orbit47/releases/latest/download/ArcadeOrbit.apk`
5. Download the APK, choose **Install**, and then **Open**.
6. If Fire TV blocks installation, open **Settings → My Fire TV → Developer Options → Install unknown apps → Downloader → On**, then retry.

Menu names vary by Fire TV OS version. Only install APKs you trust.

## Notes

- This is a web wrapper, not a native port of every game. It needs internet access.
- The APK uses the current live website, so updates to the hosted site normally appear without reinstalling the app.
- External games open inside the embedded web view when compatible; some third-party games may block embedded browsers, require a different browser, or not support remote/controller input.
- The first workflow run may take a few minutes. If the direct URL returns 404, open the repository's **Actions** tab and check that **Build Fire TV APK** completed successfully, then try again.
