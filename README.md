# Local Replay

Minecraft Java Edition 1.12.2向けのクライアント側リプレイMODです。直近の移動履歴を常時記録し、ゲーム内ですぐに見返したり、`.lreplay`ファイルとして保存・読み込みできます。

## 主な機能

- 直近10～120秒の移動をtick単位で記録
- ゲーム内での再生、一時停止、シーク、速度変更
- リプレイの保存、読み込み、名前変更、削除
- 再生位置に追従するカメラとタイムライン
- CYV、MPK Mod、MPK Mod 2、Delay ModのHUD互換表示

## 動作環境

- Minecraft 1.12.2
- Minecraft Forge 14.23.5.2859
- Java 8

## 導入

1. Minecraft Forge 1.12.2を導入します。
2. `localreplay-1.12.2-0.1.0.jar`をMinecraftの`mods`フォルダーへ入れます。
3. ワールドに入り、`G`キーでLocal Replayを開きます。

キー設定はMinecraftの「操作設定」から変更できます。保存したリプレイは`O`キーで開けます。

## 基本操作

| 操作 | デフォルト |
|---|---|
| Local Replayを開く | `G` |
| Replay Libraryを開く | `O` |
| 再生／一時停止 | `Space` |
| 1 tick戻る／進む | `Left`／`Right` |
| 10 ticks戻る／進む | `Ctrl` + `Left`／`Right` |
| 100 ticks戻る／進む | `Shift` + `Left`／`Right` |
| 再生速度を下げる／上げる | `-`／`+` |
| リプレイを閉じる | `Esc` |

録画時間は10、15、30、60、120秒から設定できます。

## リプレイの仕組み

Local Replayは、実プレイヤーを録画地点へ移動させません。リプレイ中も実プレイヤーはライブのワールド上に残り、表示用カメラだけを切り替えて録画済みの動きを描画します。

- 実プレイヤーの座標、回転、motionを書き換えません。
- 録画した移動やアクションを再実行しません。
- 録画内容を使って移動パケットを送信しません。
- リプレイ中は誤操作を防ぐためゲーム入力を遮断します。
- リプレイカメラを通常のワールド／サーバーEntityとして登録しません。

クライアント側のリプレイ・分析MODが許可されている環境での使用を想定しています。マルチプレイでの利用可否は、各サーバー、イベント、大会のルールを確認してください。

## HUD MOD互換

対応HUDは、可能な範囲で各MODの設定ファイルを読み、録画済みデータから表示値を再構築します。そのため、シーク後もHUDの値がリプレイ位置に追従します。

- [CYV](docs/CYV.md)
- [MPK Mod 1.x](docs/MPKMOD.md)
- [MPK Mod 2](docs/MPKMOD2.md)
- [Delay Mod](docs/DELAY_MOD.md)

CYV／MPK系HUDは通常時と同様にF3表示中は非表示です。Delay Modは元MODの挙動に合わせ、F3表示中も表示されます。

## リプレイファイル

保存データはゲームディレクトリの`LocalReplay/replays/`に作成されます。`.lreplay`はMinecraftバージョンごとに扱ってください。1.12.2版と1.8.9版のリプレイ形式は分離されています。

## ソースからビルド

Java 8を使用します。リポジトリのルートで次を実行してください。

```powershell
.\gradlew.bat build
```

LinuxまたはmacOSでは次を実行します。

```sh
./gradlew build
```

生成されたjarは`build/libs/`に出力されます。

## ライセンス

Local Replayは[MIT License](LICENSE)で公開しています。互換対応している第三者MODについては[Third-Party Notices](THIRD_PARTY_NOTICES.md)を参照してください。
