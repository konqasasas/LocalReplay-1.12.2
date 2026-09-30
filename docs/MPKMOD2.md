# MPKMod2 対応コンポーネント — Minecraft 1.12.2

Local Replay はMPKMod2のJSON overlay設定を**読み取り専用**で読み込み、ReplayTickから再現できる値だけを描画します。

`InfoLabel` にLocal Replayが正確に復元できないInfoVarが含まれる場合、現在値や未解決トークンを混ぜず、**そのInfoLabel全体をリプレイ中は非表示**にします。

## 対応済みComponent

| Component | 対応状況 |
|---|---|
| `InfoLabel` | 下記の対応InfoVarで使用可能 |
| `KeyBindingLabel` | 下記の移動キーに対応 |
| `InputHistory` | 対応 |
| Anchor / Parent Anchor | 対応 |
| Percent Position / Size | 対応 |
| Font Size / Label Color | 対応 |
| Key Down / Up Color | 対応 |
| InputHistory Background / Message / Edge Color | 対応 |
| InputHistory `inverted` / `maxTickAge` / `transparentBackground` | 対応 |

### 対応 `KeyBindingLabel`

- `key.forward`
- `key.left`
- `key.back`
- `key.right`
- `key.jump`
- `key.sprint`
- `key.sneak`

## 対応済みInfoVar

### Vector系

以下は本体値に加え、`.x` / `.y` / `.z` / `.lengthXZ` / `.angleXZ` に対応します。

- `pos`
- `lastPos`
- `motion`
- `speed`

### Player / Movement系

- `yaw`
- `trueYaw`
- `pitch`
- `truePitch`
- `facing`
- `airtime`
- `tier`
- `last45`
- `lastTiming`
- `lastInputs`
- `preturn`
- `deltaYaw`
- `deltaPitch`
- `onGround`
- `sprinting`
- `flying`
- `grinds`
- `lastSidestep`
- `compressedInputHistory`
- `mcVersion`

### Event系

`lastLanding` / `lastHit` / `lastJump` は以下に対応します。

- 本体値
- `.trueYaw`
- `.truePitch`
- `.yaw`
- `.pitch`
- `.pos`
- `.pos.x` / `.pos.y` / `.pos.z` / `.pos.lengthXZ` / `.pos.angleXZ`

`lastBlip` は以下に対応します。

- 本体値
- `.chainedBlips`
- `.lastChainedBlips`
- `.pos`
- `.pos.x` / `.pos.y` / `.pos.z` / `.pos.lengthXZ` / `.pos.angleXZ`

## 現在は再現しないもの

### Component

- `MessageQueue`
- `BarrierDisplay`
- raw mouse movementが必要なMouse Plot / Angle Path系
- サードパーティ / Custom Moduleの独自Component

### InfoVar / Runtime値

- `fps`
- `time`
- `date`
- `ip`
- `ping`
- `username`
- `sprintToggled`
- latest Landing Block / PB / offset状態
- Bounding Box状態
- Raw Mouse History
- サードパーティ / Custom Module独自InfoVar

## 実装方針

MPKMod2本体のライブなオブジェクトへreflection等でアクセスせず、MPKMod2の設定ファイルとLocal Replayの録画情報だけを使用します。
