# LibertyBridge

**CarPlay for Android tablets.** Wired and wireless, full-screen
landscape on your tablet — no car head unit required. Install on the
tablet, not the iPhone. No jailbreak, dongle, Mac, account or
authentication server is required for use.

LibertyBridge is a fork of [DiPlay](https://github.com/shihabal3amri/DiPlay)
by shihabal3amri, itself based on
[xcertplay](https://github.com/shilapi/xcertplay), GPL-3.0. The
home/settings UI adapts DiAuto, AGPL-3.0; those notices are preserved
in `docs/licenses`. This fork removes all BYD, DiLink, Geely, cluster,
HUD and vehicle-data code and concentrates the app on Android tablets:
tablet hotspot / Wi-Fi Direct / existing-Wi-Fi, Bluetooth pairing,
tablet-speaker or Bluetooth-to-stereo audio, and a landscape tablet UI.

**Not Apple-certified.** This is not an Apple-certified CarPlay
accessory. Like upstream DiPlay, it uses an experimental accessory
identity recovered from public Carlinkit firmware, not a newly
provisioned MFi identity for LibertyBridge. A bundled private key is
extractable. Acceptance after future iOS updates, reliability across
tablets and suitability of that identity for general distribution are
unresolved. Stripping the BYD code does not change any of this.
CarPlay and its icon belong to Apple Inc.; no Apple or BYD affiliation
or endorsement is implied.

**Tablet setup in brief:** mount the tablet landscape, connect the
iPhone by USB or wirelessly (tablet hotspot, Wi-Fi Direct, or the same
Wi-Fi network), and use the tablet's speaker or Bluetooth to your car
stereo for audio. The tablet's rear camera is a separate project and
is not part of this app.

**Support:** if LibertyBridge is useful to you, you can support its development at [Buy Me a Coffee](https://buymeacoffee.com/wilsonsamiano).

**Build from source:** see `docs/BUILD.md` — build `:mobile` only.
Source builds contain no accessory identity; a standalone CarPlay
connection needs the external auth assets described there. Signing
keys and auth assets are never committed.

## What was removed

- All BYD, DiLink, Geely and cluster/HUD code, including the
  `BydHudRouteState` / `ClusterSongState` engines (replaced by the
  generic `RouteGuidanceState` / `NowPlayingState` parsers), the
  wheel-key service, the ADB cluster activity, the vehicle-data probe
  and its settings, and the Geely AIDL interfaces.
- The `:automotive` app and the `:home` / `:maphost` samples, and the
  upstream DiPlay website (`site/`).
- Settings sections `BYD_ADB`, `BYD_NAVIGATION`, `CLUSTER_MAP`,
  `AMBIENT_LIGHTING` (the BYD lamp / car-rhythm card — night-mode
  ambient controls stay), `NAVIGATION_WHEEL`, `WHEEL_KEYS`,
  `CAR_BUTTON`, and the whole `VEHICLE` category. The
  `CARPLAY_CONTROLS` section is kept, under Display.
- "Car hotspot" is now "tablet hotspot" throughout the app, including
  class and file names (`TabletHotspot*`).
- Vehicle-speed handling now comes only from tablet GPS (for example
  an external Bluetooth GPS receiver), never from wheel-speed or other
  car sensors. The iAP2 vehicle-status reporter is removed.

## What stays

Main-stream AirPlay session, video and touch; IAP2 USB and wireless
minus vehicle status; Wi-Fi Direct, local-only hotspot, existing-Wi-Fi
and Bluetooth pairing; media, telecom and Siri paths minus BYD
branches; GPS/NMEA speed to the iPhone.
