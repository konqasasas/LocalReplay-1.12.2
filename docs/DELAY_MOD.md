# Delay Mod 対応コンポーネント — Minecraft 1.12.2

Local Replay はDelay Mod 1.12.2向けの読み取り専用互換レンダラーを持っています。

## 対応済み

| コンポーネント | 対応状況 |
|---|---|
| Grounded Delay Counter | 対応 |
| HUD X / Y位置 | 対応 |
| Label Text | 対応 |
| `{n}` を使うValue Template | 対応 |
| Label Color | 対応 |
| Value Color | 対応 |
| F3表示中のHUD表示 | 対応 |

カウンター値は録画済みの`onGround`履歴から決定的に再構築します。リプレイ中のDelay Mod本体のライブなカウンター値は使用しません。

## 実装方針

`config/delaymod.cfg` を読み取り専用で参照します。不正または欠落している設定値は、Delay Mod互換のデフォルト値へフォールバックします。
