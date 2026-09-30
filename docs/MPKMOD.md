# MPK Mod 1.x 対応コンポーネント — Minecraft 1.12.2

Local Replay は旧MPK Mod 1.xの設定を**読み取り専用**で参照してHUDを再構築します。MPK Mod 1.xとMPKMod2は同じForge mod idを使用するため、Local Replay側で世代を判定して対応アダプタを切り替えます。

## 対応済み

| コンポーネント | 対応状況 |
|---|---|
| X / Y / Z | 対応 |
| Facing | 対応 |
| Pitch | 対応 |
| Last Landing X / Y / Z | 対応 |
| Last Turning | 対応 |
| Last 45 | 対応 |
| Last Timing | 対応 |
| Last Input | 対応 |
| Airtime | 対応 |
| Jump Angle | 対応 |
| Jump X / Y / Z | 対応 |
| Hit Angle | 対応 |
| Hit X / Y / Z | 対応 |
| Speed | 対応 |
| Speed Vector | 対応 |
| Motion | 対応 |
| Keystrokes | Forward / Left / Back / Right / Jump / Sprint / Sneakに対応 |

Speed / Motionは、MPK側の設定に応じてblocks-per-tick / blocks-per-second表示を反映します。

## 現在は再現しないもの

- FPSなど、現在セッションだけの値。
- PB / XPB / ZPB。
- Landing Block offset。
- Total offset。
- MPKのライブなランタイム状態に依存するミリ秒単位Timing。

## 実装方針

有効ラベル、位置、サイズなどの対応可能な設定はMPKのconfigから読みますが、MPKのライブな内部状態を読み取ったり書き換えたりはしません。
