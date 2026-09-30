# CYV 対応コンポーネント — Minecraft 1.12.2

Local Replay はCYVの設定を**読み取り専用**で参照し、位置や有効/無効設定をできるだけ合わせながら、表示値をReplayTickから再構築します。

## 対応済み

| コンポーネント | 対応状況 |
|---|---|
| X / Y / Z | 対応 |
| Yaw / Facing | 対応 |
| Pitch | 対応 |
| Last Timing | 対応 |
| Airtime | 対応 |
| Landing X / Y / Z | 対応 |
| Jump Angle | 対応 |
| Second Turn | 対応 |
| Preturn | 対応 |
| Hit X / Y / Z | 対応 |
| Jump X / Y / Z | 対応 |
| X / Y / Z Speed | 対応 |
| Individual Speeds | 対応 |
| Speed Vector | 対応 |
| Last 45 | 対応 |
| Last Turning | 対応 |
| Last Input | 対応 |
| Last Sidestep | 対応 |
| Tier | 対応 |
| Hit Angle | 対応 |
| Hit Vector | 対応 |
| Blips | 対応 |
| Grinds | 対応 |
| Direction HUD | 対応 |
| Keystrokes | W/A/S/D・Sprint・Sneak・Jumpに対応 |
| Turn HUD | 1〜12 tick、Single / Split表示に対応 |

## 現在は再現しないもの

- FPSなど、現在セッションに依存する値。
- ReplayTickに含まれないTime/時計系の値。
- 独立したToggleSprint状態HUD。
- 現在のアダプタに含まれない独立Input History / Turn Historyコンポーネント。
- PB、Landing Block、保存済みoffsetなど、ReplayTickだけでは正確に復元できないCYV内部状態。

## 実装方針

リプレイ中にCYV本体のライブな内部オブジェクトを読み取って値を流用することはしません。位置、motion、yaw/pitch、入力フラグ、接地状態などの録画情報から再計算するため、シーク時も同じtickなら同じ表示になります。
